/*********************************************************************/
/*                 TRANSFER MEMORY TO ZYNQ PL (MM2S)                 */
/*                 AND FROM ZYNQ PL TO MEMORY (S2MM)                 */
/*                                                                   */
/*********************************************************************/

#include <iostream>
#include  <iomanip>
#include <chrono>
#include <thread>
#include <bitset>

#include <stdint.h>
#include <fcntl.h>
#include <sys/mman.h>

using namespace std;

/*

 from AXI DMA v7.1 - LogiCORE IP Product Guide http://www.xilinx.com/support/documentation/ip_documentation/axi_dma/v7_1/pg021_axi_dma.pdf

 1. Write the address of the starting descriptor to the Current Descriptor register. If AXI DMA is configured for an address space greater than 32, then also program the MSB 32 bits of the current descriptor.
 2. Start the MM2S channel running by setting the run/stop bit to 1 (MM2S_DMACR.RS =1). The Halted bit (DMASR.Halted) should deassert indicating the MM2S channel is running.
 3. If desired, enable interrupts by writing a 1 to MM2S_DMACR.IOC_IrqEn and MM2S_DMACR.Err_IrqEn.
 4. Write a valid address to the Tail Descriptor register. If AXI DMA is configured for an address space greater than 32, then also program the MSB 32 bits of the tail descriptor.
 5. Writing to the Tail Descriptor register triggers the DMA to start fetching the descriptors from the memory. In case of multichannel configuration, the fetching of descriptors starts when the packet arrives on the S2MM channel.
 6. The fetched descriptors are processed, Data is read from the memory and then output to the MM2S streaming channel.

 */

#define    DESCRIPTOR_REGISTERS_SIZE          0xFFFF
#define    CTRL_REGISTERS_SIZE                0xFFFF
#define    SG_DMA_DESCRIPTORS_WIDTH           0xFFFF
#define    MM_DATA_WIDTH					  1024
#define    TRIG_MAX							  (3 * MM_DATA_WIDTH) // 3072 bits = 96 32bit numbers
#define	   TRIG_BYTE_CNT				      (TRIG_MAX / 8) // 400 bytes

/*********************************************************************/
/*                       define mmap locations                       */
/*          consult the README for the exact memory layout           */
/*********************************************************************/

// AXI LITE Register Address Map for the control/statistics unit
#define    RSIM_CTRL_REGISTER_LOCATION           0x43C00000
#define    RSIM_CTRL_ENABLED                     0x0
#define    RSIM_CTRL_CALIBRATED                  0x1
#define    RSIM_CTRL_ARP_US                      0x2
#define    RSIM_CTRL_ACP_CNT                     0x3
#define    RSIM_CTRL_TRIG_US                     0x4
#define    RSIM_CTRL_ACP_IDX                     0x5

// AXI DMA Register Address Map for fixed targets
#define    FT_AXI_DMA_REGISTER_LOCATION          0x40400000

// AXI DMA Register Address Map for moving targets
#define    MT_AXI_DMA_REGISTER_LOCATION          0x40410000

// specified by the kernel boot args
#define    SCRATCH_MEM_LOCATION                  0x19000000
#define    SCRATCH_MEM_SIZE                      0x7000000

// We will be using 3 descriptors for the fixed targets pointing to the same memory region
#define    FT_DESCRIPTOR_REGISTERS_SIZE          0x3
#define    FT_MM2S_DMA_DESCRIPTORS_OFFSET        0x0
#define    FT_MM2S_DMA_DESCRIPTORS_SIZE          (FT_DESCRIPTOR_REGISTERS_SIZE * SG_DMA_DESCRIPTORS_WIDTH)

// We will be using 3 descriptors for the moving targets pointing to separate memory regions
#define    MT_DESCRIPTOR_REGISTERS_SIZE          0x3
#define    MT_MM2S_DMA_DESCRIPTORS_OFFSET        (FT_MM2S_DMA_DESCRIPTORS_OFFSET + FT_MM2S_DMA_DESCRIPTORS_SIZE + MM_DATA_WIDTH)
#define    MT_MM2S_DMA_DESCRIPTORS_SIZE          (MT_DESCRIPTOR_REGISTERS_SIZE * SG_DMA_DESCRIPTORS_WIDTH)

// location of simulation data aligned with to MM_DATA_WIDTH address
#define    RSIM_SOURCE_MEM_OFFSET                (((MT_MM2S_DMA_DESCRIPTORS_OFFSET + MT_MM2S_DMA_DESCRIPTORS_SIZE + MM_DATA_WIDTH) / MM_DATA_WIDTH) * MM_DATA_WIDTH)

/*********************************************************************/
/*                   define all register locations                   */
/*               based on "LogiCORE IP Product Guide"                */
/*********************************************************************/

// MM2S CONTROL
#define MM2S_CONTROL_REGISTER       0x00    // MM2S_DMACR
#define MM2S_STATUS_REGISTER        0x04    // MM2S_DMASR

// scatter gather
#define MM2S_CURDESC                0x08    // must align 0x40 addresses
#define MM2S_CURDESC_MSB            0x0C    // unused with 32bit addresses
#define MM2S_TAILDESC               0x10    // must align 0x40 addresses
#define MM2S_TAILDESC_MSB           0x14    // unused with 32bit addresses

// direct DMA
#define MM2SA_SA					0x18    // source address
#define MM2SA_LENGTH    			0x28    // source address

struct rsim {
    int device_handle;
    unsigned int scratch_mem_addr;

    unsigned int *ctrl_register_mmap;
    unsigned int mem_blk_byte_size;

    unsigned int *ft_adma_register_mmap;

    unsigned int ft_mm2s_descriptor_register_addr;
    unsigned int *ft_mm2s_descriptor_register_mmap;

    unsigned int ft_source_mem_addr;
    unsigned int *ft_source_mem_map;
    unsigned int ft_source_mem_blk_cnt;

    unsigned int *mt_adma_register_mmap;

    unsigned int mt_mm2s_descriptor_register_addr;
    unsigned int *mt_mm2s_descriptor_register_mmap;

    unsigned int mt_source_mem_addr;
    unsigned int *mt_source_mem_map_1;
    unsigned int *mt_source_mem_map_2;
    unsigned int *mt_source_mem_map_3;
    unsigned int mt_source_mem_blk_cnt;

};

struct rsim_stat {
    bool calibrated;
    unsigned int arp_us;
    unsigned int acp_cnt;
    unsigned int trig_us;
    unsigned int acp_idx;

    unsigned int ft_dma_status;
    unsigned int mt_dma_status;
};

void print_mem(unsigned int *mem_loc, unsigned int word_count) {
    for (unsigned int i = 0; i < word_count; i++) {
        cout << noshowbase << "0x" << setfill('0') << setw(8) << right << hex << mem_loc + i << " ";
        cout << noshowbase << "0x" << setfill('0') << setw(8) << right << hex << mem_loc[i] << " ";
        cout << std::bitset < 32 > (mem_loc[i]) << endl;
    }
}

/*********************************************************************/
/*                 reset and halt all dma operations                 */
/*********************************************************************/
void reset_halt_dma(unsigned int * axi_dma_register_mmap) {
    axi_dma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 0x4;
    axi_dma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 0x0;
}

/**
 * Disables the simulator output then stops and resets the DMA engine.
 */
bool stop_rsim(rsim sim) {

    reset_halt_dma(sim.ft_adma_register_mmap);
    reset_halt_dma(sim.mt_adma_register_mmap);

    sim.ctrl_register_mmap[0x0] = 0;
    return sim.ctrl_register_mmap[0x0] == 0;
}

void prog_exit(int exit_code, rsim sim) {
    if (sim.ctrl_register_mmap) {
        // disable simulator
        sim.ctrl_register_mmap[0x0] = 0;
    }
    exit(exit_code);
}

rsim setup_rsim(unsigned int ctrl_register_addr, unsigned int ft_adma_register_addr, unsigned int mt_adma_register_addr, unsigned int scratch_addr) {

    rsim sim = { };
    sim.scratch_mem_addr = scratch_addr;

    sim.device_handle = open("/dev/mem", O_RDWR | O_SYNC);

    unsigned int *scratch_mem_raw = (unsigned int *) mmap(NULL,
    SCRATCH_MEM_SIZE,
    PROT_READ | PROT_WRITE,
    MAP_SHARED, sim.device_handle, (off_t) SCRATCH_MEM_LOCATION);

    if (scratch_mem_raw == MAP_FAILED) {
        cerr << "Unable to map to scratch memory" << endl;
        prog_exit(-1, sim);
    }

    // clear scratch memory
    for (unsigned int i = 0; i < SCRATCH_MEM_SIZE / 4; i++) {
        scratch_mem_raw[i] = 0x00000000;
    }

    sim.ctrl_register_mmap = (uint *) mmap(NULL,
    CTRL_REGISTERS_SIZE,
    PROT_READ | PROT_WRITE,
    MAP_SHARED, sim.device_handle, (off_t) ctrl_register_addr);

    if (!sim.ctrl_register_mmap[RSIM_CTRL_CALIBRATED]) {
        cerr << "system is not calibrated" << endl;
        prog_exit(-2, sim);
    }

    // disable simulator
    sim.ctrl_register_mmap[0x0] = 0;

    sim.mem_blk_byte_size = sim.ctrl_register_mmap[RSIM_CTRL_ACP_CNT] * TRIG_BYTE_CNT;

    sim.ft_adma_register_mmap = (unsigned int *) mmap(NULL,
    DESCRIPTOR_REGISTERS_SIZE,
    PROT_READ | PROT_WRITE,
    MAP_SHARED, sim.device_handle, (off_t) ft_adma_register_addr);

    sim.mt_adma_register_mmap = (unsigned int *) mmap(NULL,
    DESCRIPTOR_REGISTERS_SIZE,
    PROT_READ | PROT_WRITE,
    MAP_SHARED, sim.device_handle, (off_t) mt_adma_register_addr);

    sim.ft_mm2s_descriptor_register_addr = scratch_addr + FT_MM2S_DMA_DESCRIPTORS_OFFSET;
    sim.ft_mm2s_descriptor_register_mmap = scratch_mem_raw + FT_MM2S_DMA_DESCRIPTORS_OFFSET;

    sim.mt_mm2s_descriptor_register_addr = scratch_addr + MT_MM2S_DMA_DESCRIPTORS_OFFSET;
    sim.mt_mm2s_descriptor_register_mmap = scratch_mem_raw + MT_MM2S_DMA_DESCRIPTORS_OFFSET;

    sim.ft_source_mem_addr = scratch_addr + RSIM_SOURCE_MEM_OFFSET;
    sim.ft_source_mem_blk_cnt = 1;
    sim.ft_source_mem_map = scratch_mem_raw + (RSIM_SOURCE_MEM_OFFSET) / sizeof(unsigned int);

    sim.mt_source_mem_addr = scratch_addr + RSIM_SOURCE_MEM_OFFSET + sim.mem_blk_byte_size;
    sim.mt_source_mem_blk_cnt = MT_DESCRIPTOR_REGISTERS_SIZE;
    sim.mt_source_mem_map_1 = scratch_mem_raw + (RSIM_SOURCE_MEM_OFFSET + sim.mem_blk_byte_size) / sizeof(unsigned int);
    sim.mt_source_mem_map_2 = scratch_mem_raw + (RSIM_SOURCE_MEM_OFFSET + 2 * sim.mem_blk_byte_size) / sizeof(unsigned int);
    sim.mt_source_mem_map_3 = scratch_mem_raw + (RSIM_SOURCE_MEM_OFFSET + 3 * sim.mem_blk_byte_size) / sizeof(unsigned int);

    // SETUP DMA
    reset_halt_dma(sim.ft_adma_register_mmap);
    reset_halt_dma(sim.mt_adma_register_mmap);

    return sim;

}

/**
 * Fetches the statistics from the control unit
 */
rsim_stat get_rsim_stats(rsim sim) {
    rsim_stat stats = { };

    stats.calibrated = sim.ctrl_register_mmap[RSIM_CTRL_CALIBRATED];
    stats.arp_us = sim.ctrl_register_mmap[RSIM_CTRL_ARP_US];
    stats.acp_cnt = sim.ctrl_register_mmap[RSIM_CTRL_ACP_CNT];
    stats.trig_us = sim.ctrl_register_mmap[RSIM_CTRL_TRIG_US];
    stats.acp_idx = sim.ctrl_register_mmap[RSIM_CTRL_ACP_IDX];

    stats.ft_dma_status = sim.ft_adma_register_mmap[MM2S_STATUS_REGISTER >> 2];
    stats.mt_dma_status = sim.mt_adma_register_mmap[MM2S_STATUS_REGISTER >> 2];

    return stats;
}

bool is_dma_ok(rsim_stat stat) {
    return ((stat.ft_dma_status & 0x00001000) && (stat.mt_dma_status & 0x00001000));
}

void print_rsim_stat(rsim_stat stat) {
    cout << showbase << hex;
    cout << "Radar simulator stats:" << endl;
    cout << "1. Calibrated: " << stat.calibrated << endl;
    cout << "2. ARP [us]: " << stat.arp_us << endl;
    cout << "3. ACP count (per ARP): " << stat.acp_cnt << endl;
    cout << "4. TRIG [us]: " << stat.trig_us << endl;
    cout << "5. ACP index: " << stat.acp_idx << endl;

    cout << "6. FT DMA: ";
    if (stat.ft_dma_status & 0x00000001) {
        cout << " halted";
    } else {
        cout << " running";
    }
    if (stat.ft_dma_status & 0x00000002)
        cout << " idle";
    if (stat.ft_dma_status & 0x00000008)
        cout << " SGIncld";
    if (stat.ft_dma_status & 0x00000010)
        cout << " DMAIntErr";
    if (stat.ft_dma_status & 0x00000020)
        cout << " DMASlvErr";
    if (stat.ft_dma_status & 0x00000040)
        cout << " DMADecErr";
    if (stat.ft_dma_status & 0x00000100)
        cout << " SGIntErr";
    if (stat.ft_dma_status & 0x00000200)
        cout << " SGSlvErr";
    if (stat.ft_dma_status & 0x00000400)
        cout << " SGDecErr";
    if (stat.ft_dma_status & 0x00001000)
        cout << " IOC_Irq";
    if (stat.ft_dma_status & 0x00002000)
        cout << " Dly_Irq";
    if (stat.ft_dma_status & 0x00004000)
        cout << " Err_Irq";
    cout << endl;

    cout << "7. MT DMA: ";
    if (stat.mt_dma_status & 0x00000001) {
        cout << " halted";
    } else {
        cout << " running";
    }
    if (stat.mt_dma_status & 0x00000002)
        cout << " idle";
    if (stat.mt_dma_status & 0x00000008)
        cout << " SGIncld";
    if (stat.mt_dma_status & 0x00000010)
        cout << " DMAIntErr";
    if (stat.mt_dma_status & 0x00000020)
        cout << " DMASlvErr";
    if (stat.mt_dma_status & 0x00000040)
        cout << " DMADecErr";
    if (stat.mt_dma_status & 0x00000100)
        cout << " SGIntErr";
    if (stat.mt_dma_status & 0x00000200)
        cout << " SGSlvErr";
    if (stat.mt_dma_status & 0x00000400)
        cout << " SGDecErr";
    if (stat.mt_dma_status & 0x00001000)
        cout << " IOC_Irq";
    if (stat.mt_dma_status & 0x00002000)
        cout << " Dly_Irq";
    if (stat.mt_dma_status & 0x00004000)
        cout << " Err_Irq";
    cout << endl;

}

/**
 * Fetches the statistics from the control unit
 */
unsigned int get_rsim_acp_idx(rsim sim) {
    return sim.ctrl_register_mmap[0x14];
}

/**
 * Starts the DMA engine and enables the simulator output
 */
bool start_rsim(rsim sim) {

    /*********************************************************************/
    /*               build FT MM2S stream and control stream             */
    /* chains will be filled with next desc, buffer width and registers  */
    /*                         [0]: next descr                           */
    /*                         [1]: reserved                             */
    /*                         [2]: buffer addr                          */
    /*********************************************************************/
    uint32_t ft_current_descriptor_address;
    uint32_t ft_tail_descriptor_address;

    ft_current_descriptor_address = sim.ft_mm2s_descriptor_register_addr; // save current descriptor address

    sim.ft_mm2s_descriptor_register_mmap[0x0 >> 2] = sim.ft_mm2s_descriptor_register_addr + 0x40; // set next descriptor address (1st descriptor)
    sim.ft_mm2s_descriptor_register_mmap[0x8 >> 2] = sim.ft_source_mem_addr; // set target buffer address
    sim.ft_mm2s_descriptor_register_mmap[0x18 >> 2] = sim.mem_blk_byte_size; // set mm2s/s2mm buffer length to control register

    sim.ft_mm2s_descriptor_register_mmap[0x40 >> 2] = sim.ft_mm2s_descriptor_register_addr + 0x80; // set next descriptor address
    sim.ft_mm2s_descriptor_register_mmap[0x48 >> 2] = sim.ft_source_mem_addr; // set target buffer address
    sim.ft_mm2s_descriptor_register_mmap[0x58 >> 2] = sim.mem_blk_byte_size; // set mm2s/s2mm buffer length to control register

    sim.ft_mm2s_descriptor_register_mmap[0x80 >> 2] = sim.mt_mm2s_descriptor_register_addr; // set next descriptor address (1st descriptor)
    sim.ft_mm2s_descriptor_register_mmap[0x88 >> 2] = sim.ft_source_mem_addr; // set target buffer address
    sim.ft_mm2s_descriptor_register_mmap[0x98 >> 2] = sim.mem_blk_byte_size; // set mm2s/s2mm buffer length to control register

    ft_tail_descriptor_address = sim.ft_mm2s_descriptor_register_addr + 0x50; // save tail descriptor address

    /*********************************************************************/
    /*               build MT MM2S stream and control stream             */
    /* chains will be filled with next desc, buffer width and registers  */
    /*                         [0]: next descr                           */
    /*                         [1]: reserved                             */
    /*                         [2]: buffer addr                          */
    /*********************************************************************/
    uint32_t mt_current_descriptor_address;
    uint32_t mt_tail_descriptor_address;

    mt_current_descriptor_address = sim.mt_mm2s_descriptor_register_addr; // save current descriptor address

    sim.mt_mm2s_descriptor_register_mmap[0x0 >> 2] = sim.mt_mm2s_descriptor_register_addr + 0x40; // set next descriptor address
    sim.mt_mm2s_descriptor_register_mmap[0x8 >> 2] = sim.mt_source_mem_addr + 0x0; // set target buffer address
    sim.mt_mm2s_descriptor_register_mmap[0x18 >> 2] = sim.mem_blk_byte_size; // set mm2s/s2mm buffer length to control register

    sim.mt_mm2s_descriptor_register_mmap[0x40 >> 2] = sim.mt_mm2s_descriptor_register_addr + 0x80; // set next descriptor address
    sim.mt_mm2s_descriptor_register_mmap[0x48 >> 2] = sim.mt_source_mem_addr + (sim.mem_blk_byte_size / sizeof(unsigned int)); // set target buffer address
    sim.mt_mm2s_descriptor_register_mmap[0x58 >> 2] = sim.mem_blk_byte_size; // set mm2s/s2mm buffer length to control register

    sim.mt_mm2s_descriptor_register_mmap[0x80 >> 2] = sim.mt_mm2s_descriptor_register_addr; // set next descriptor address (1st descriptor)
    sim.mt_mm2s_descriptor_register_mmap[0x88 >> 2] = sim.mt_source_mem_addr + (2 * sim.mem_blk_byte_size / sizeof(unsigned int)); // set target buffer address
    sim.mt_mm2s_descriptor_register_mmap[0x98 >> 2] = sim.mem_blk_byte_size; // set mm2s/s2mm buffer length to control register

    mt_tail_descriptor_address = mt_current_descriptor_address + 0x50; // save tail descriptor address

    /*********************************************************************/
    /*                 set current descriptor addresses                  */
    /*           and start dma operations (S2MM_DMACR.RS = 1)            */
    /*********************************************************************/

    sim.ft_adma_register_mmap[MM2S_CURDESC >> 2] = ft_current_descriptor_address;
    // Cyclic BD Enable
    sim.ft_adma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 1 << 4;
    // Run / Stop control for controlling running and stopping of the DMA channel.
    sim.ft_adma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 1 << 0;

    sim.mt_adma_register_mmap[MM2S_CURDESC >> 2] = mt_current_descriptor_address;
    // Cyclic BD Enable
    sim.mt_adma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 1 << 4;
    // Run / Stop control for controlling running and stopping of the DMA channel.
    sim.mt_adma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 1 << 0;

    /*********************************************************************/
    /*                          start transfer                           */
    /*                 (by setting the tail descriptors)                 */
    /*********************************************************************/

    sim.ft_adma_register_mmap[MM2S_TAILDESC >> 2] = ft_tail_descriptor_address;
    sim.mt_adma_register_mmap[MM2S_TAILDESC >> 2] = mt_tail_descriptor_address;

    // ENABLE SIMULATOR OUTPUT

    sim.ctrl_register_mmap[0x0] = 1;
    return sim.ctrl_register_mmap[0x0] == 1;
}

/**
 * Cleans a simulation memory page.
 */
void clear_mem(unsigned int *mem_loc, unsigned int mem_byte_size) {
    for (unsigned int i = 0; i < mem_byte_size / sizeof(unsigned int); i++) {
        mem_loc[i] = 0x00000000;
    }
}

int main() {

    // setup radar simulator
    rsim sim = setup_rsim(
    RSIM_CTRL_REGISTER_LOCATION,
    FT_AXI_DMA_REGISTER_LOCATION,
    MT_AXI_DMA_REGISTER_LOCATION,
    SCRATCH_MEM_LOCATION);

    cout << showbase << hex;
    cout << "Memory map:" << endl;
    cout << "1. Scratch mem addr: " << SCRATCH_MEM_LOCATION << "-" << SCRATCH_MEM_LOCATION + SCRATCH_MEM_LOCATION << " size:" << SCRATCH_MEM_SIZE << endl;
    cout << "2. FT descriptors addr: " << sim.ft_mm2s_descriptor_register_addr << "-" << sim.ft_mm2s_descriptor_register_addr + FT_MM2S_DMA_DESCRIPTORS_SIZE << " size: " << FT_MM2S_DMA_DESCRIPTORS_SIZE << endl;
    cout << "4. MT descriptors addr: " << sim.mt_mm2s_descriptor_register_addr << "-" << sim.mt_mm2s_descriptor_register_addr + MT_MM2S_DMA_DESCRIPTORS_SIZE << " size: " << MT_MM2S_DMA_DESCRIPTORS_SIZE << endl;
    cout << "5. FT mem addr: " << sim.ft_source_mem_addr << "-" << sim.ft_source_mem_addr + sim.ft_source_mem_blk_cnt * sim.mem_blk_byte_size << " size: " << sim.ft_source_mem_blk_cnt * sim.mem_blk_byte_size << endl;
    cout << "6. MT mem addr: " << sim.mt_source_mem_addr << "-" << sim.mt_source_mem_addr + sim.mt_source_mem_blk_cnt * sim.mem_blk_byte_size << " size: " << sim.mt_source_mem_blk_cnt * sim.mem_blk_byte_size << endl;

    // get statistics
    rsim_stat stat = get_rsim_stats(sim);
    print_rsim_stat(stat);

    // TODO: fill memory with TEST data
    clear_mem(sim.ft_source_mem_map, sim.mem_blk_byte_size);
    clear_mem(sim.mt_source_mem_map_1, sim.mem_blk_byte_size);
    clear_mem(sim.mt_source_mem_map_2, sim.mem_blk_byte_size);
    clear_mem(sim.mt_source_mem_map_3, sim.mem_blk_byte_size);
    unsigned int trig_word_cnt = TRIG_BYTE_CNT / sizeof(unsigned int);
    for (unsigned int i = 0; i < stat.acp_cnt; i++) {
        unsigned int pos = (i % stat.trig_us);
        unsigned int inv_pos = stat.trig_us - pos;

        unsigned int word_offset = i * trig_word_cnt + (pos / 32);
        unsigned int bit_offset = pos % 32;

        unsigned int inv_word_offset = i * trig_word_cnt + (inv_pos / 32);
        unsigned int inv_bit_offset = inv_pos % 32;

        sim.ft_source_mem_map[word_offset] = 0x00000000 + (1 << bit_offset);
        sim.mt_source_mem_map_1[inv_word_offset] = 0x00000000 + (1 << inv_bit_offset);
        sim.mt_source_mem_map_2[word_offset] = 0x00000000 + (1 << bit_offset);
        sim.mt_source_mem_map_3[inv_word_offset] = 0x00000000 + (1 << inv_bit_offset);
    }

    // start simulator
    start_rsim(sim);
    cout << " ... FT Descriptor mem ..." << endl;
    print_mem(sim.ft_mm2s_descriptor_register_mmap, 0x100 >> 2);
    cout << " ... MT Descriptor mem ..." << endl;
    print_mem(sim.mt_mm2s_descriptor_register_mmap, 0x100 >> 2);

    cout << " ... waiting for first ARP ..." << endl;
    this_thread::sleep_for(chrono::microseconds(stat.arp_us));

    stat = get_rsim_stats(sim);
    print_rsim_stat(stat);
    if (!is_dma_ok(stat)) {
        cerr << "DMA Not OK" << endl;
        prog_exit(-3, sim);
    }

    while (is_dma_ok(stat)) {
        stat = get_rsim_stats(sim);
        print_rsim_stat(stat);
        this_thread::sleep_for(chrono::milliseconds(2000));
    }

    // stop simulator
    prog_exit(0, sim);
}
