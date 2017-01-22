#include <iostream>

/*
 * Empty C++ Application
 */

int main()
{
	return 0;
}

/*

/*********************************************************************/
/*                 TRANSFER MEMORY TO ZYNQ PL (MM2S)                 */
/*                 AND FROM ZYNQ PL TO MEMORY (S2MM)                 */
/*                                                                   */
/*********************************************************************/

#include <iostream>
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
#define    SG_DMA_DESCRIPTORS_WIDTH           0xFFFF
#define	   ACP_CNT							  4096
#define	   TRIG_SGNL_CNT				      100 * 4 // 100 32bit numbers or 400 bytes
#define    MEMBLOCK_WIDTH					  (ACP_CNT * TRIG_SGNL_CNT * 4)

/*********************************************************************/
/*                       define mmap locations                       */
/*          consult the README for the exact memory layout           */
/*********************************************************************/

#define    RSIM_CTRL_REGISTER_LOCATION           0x43C00000        // AXI LITE Register Address Map for the control/statistics unit

#define    FT_AXI_DMA_REGISTER_LOCATION          0x40400000        // AXI DMA Register Address Map for fixed targets
#define    MT_AXI_DMA_REGISTER_LOCATION          0x40410000        // AXI DMA Register Address Map for moving targets

#define    FT_DESCRIPTOR_REGISTERS_SIZE          0x1               // Only one FT descriptor is needed
#define    FT_MM2S_DMA_DESCRIPTORS_OFFSET        0x0               // FT descriptor address offset

#define    MT_DESCRIPTOR_REGISTERS_SIZE          0x3               // We will be using 3 descriptors for the fixed targets
#define    MT_MM2S_DMA_DESCRIPTORS_OFFSET        (FT_MM2S_DMA_DESCRIPTORS_OFFSET + FT_DESCRIPTOR_REGISTERS_SIZE * SG_DMA_DESCRIPTORS_WIDTH)               // FT descriptor address offset

#define    FT_SOURCE_MEM_ADDR_OFFSET             (MT_MM2S_DMA_DESCRIPTORS_OFFSET + MT_DESCRIPTOR_REGISTERS_SIZE * SG_DMA_DESCRIPTORS_WIDTH)               // FT memory location offset
#define    FT_SOURCE_MEM_SIZE                    (FT_DESCRIPTOR_REGISTERS_SIZE * MEMBLOCK_WIDTH)

#define    MT_SOURCE_MEM_ADDR_OFFSET             (FT_SOURCE_MEM_ADDR_OFFSET + FT_SOURCE_MEM_SIZE + 1 + MT_DESCRIPTOR_REGISTERS_SIZE * SG_DMA_DESCRIPTORS_WIDTH)               // MT memory location offset
#define    MT_SOURCE_MEM_SIZE                    (MT_DESCRIPTOR_REGISTERS_SIZE * MEMBLOCK_WIDTH)

/*********************************************************************/
/*                       define mmap locations                       */
/*          consult the README for the exact memory layout           */
/*********************************************************************/

#define    AXI_DMA_REGISTER_LOCATION          0x40400000        //AXI DMA Register Address Map
#define    BUFFER_BLOCK_WIDTH                 0x7D0000        //size of memory block per descriptor in bytes
#define    NUM_OF_DESCRIPTORS                 0x7        //number of descriptors for each direction

#define    HP0_DMA_BUFFER_MEM_ADDRESS         0x20000000
#define    HP0_MM2S_DMA_BASE_MEM_ADDRESS      (HP0_DMA_BUFFER_MEM_ADDRESS)
#define    HP0_S2MM_DMA_BASE_MEM_ADDRESS      (HP0_DMA_BUFFER_MEM_ADDRESS + MEMBLOCK_WIDTH + 1)
#define    HP0_MM2S_DMA_DESCRIPTORS_ADDRESS   (HP0_MM2S_DMA_BASE_MEM_ADDRESS)
#define    HP0_S2MM_DMA_DESCRIPTORS_ADDRESS   (HP0_S2MM_DMA_BASE_MEM_ADDRESS)
#define    HP0_MM2S_SOURCE_MEM_ADDRESS        (HP0_MM2S_DMA_BASE_MEM_ADDRESS + SG_DMA_DESCRIPTORS_WIDTH + 1)
#define    HP0_S2MM_TARGET_MEM_ADDRESS        (HP0_S2MM_DMA_BASE_MEM_ADDRESS + SG_DMA_DESCRIPTORS_WIDTH + 1)


/*********************************************************************/
/*                   define all register locations                   */
/*               based on "LogiCORE IP Product Guide"                */
/*********************************************************************/


// MM2S CONTROL
#define MM2S_CONTROL_REGISTER       0x00    // MM2S_DMACR
#define MM2S_STATUS_REGISTER        0x04    // MM2S_DMASR
#define MM2S_CURDESC                0x08    // must align 0x40 addresses
#define MM2S_CURDESC_MSB            0x0C    // unused with 32bit addresses
#define MM2S_TAILDESC               0x10    // must align 0x40 addresses
#define MM2S_TAILDESC_MSB           0x14    // unused with 32bit addresses

#define SG_CTL                      0x2C    // CACHE CONTROL

// S2MM CONTROL
#define S2MM_CONTROL_REGISTER       0x30    // S2MM_DMACR
#define S2MM_STATUS_REGISTER        0x34    // S2MM_DMASR
#define S2MM_CURDESC                0x38    // must align 0x40 addresses
#define S2MM_CURDESC_MSB            0x3C    // unused with 32bit addresses
#define S2MM_TAILDESC               0x40    // must align 0x40 addresses
#define S2MM_TAILDESC_MSB           0x44    // unused with 32bit addresses



struct {
	int device_handle;
	unsigned int *ft_adma_register_mmap;
	unsigned int *ft_mm2s_descriptor_register_mmap;
	unsigned int *ft_source_mem_map;

	unsigned int *mt_adma_register_mmap;
	unsigned int *mt_mm2s_descriptor_register_mmap;
	unsigned int *mt_source_mem_map_1;
	unsigned int *mt_source_mem_map_2;
	unsigned int *mt_source_mem_map_3;


} rsim_dma;

rsim_dma setup_sg_dma(
		unsigned int ft_adma_register_addr,
		unsigned int mt_adma_register_addr,
		unsigned int scratch_addr) {

	rsim_dma dma;

	dma.device_handle = open("/dev/mem", O_RDWR | O_SYNC);

	dma.ft_adma_register_mmap = (unsigned int *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, dh, (off_t)ft_adma_register_addr);
    dma.ft_mm2s_descriptor_register_mmap = (unsigned int *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, dh, (off_t)(scratch_addr + FT_MM2S_DMA_DESCRIPTORS_OFFSET));
    dma.ft_source_mem_map = (unsigned int *) mmap(NULL, MEMBLOCK_WIDTH * 1, PROT_READ | PROT_WRITE, MAP_SHARED, dh,  (off_t)(scratch_addr + FT_SOURCE_MEM_OFFSET));

    dma.mt_adma_register_mmap = (unsigned int *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, dh, (off_t)mt_adma_register_addr);
	dma.mt_mm2s_descriptor_register_mmap = (unsigned int *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, dh, (off_t)(scratch_addr + MT_MM2S_DMA_DESCRIPTORS_ADDRESS));
	dma.mt_source_mem_map_1 = (unsigned int *) mmap(NULL, MEMBLOCK_WIDTH, PROT_READ | PROT_WRITE, MAP_SHARED, dh,  (off_t)(scratch_addr + MT_SOURCE_MEM_OFFSET));
	dma.mt_source_mem_map_2 = (unsigned int *) mmap(NULL, MEMBLOCK_WIDTH, PROT_READ | PROT_WRITE, MAP_SHARED, dh,  (off_t)(scratch_addr + MT_SOURCE_MEM_OFFSET + MEMBLOCK_WIDTH));
	dma.mt_source_mem_map_3 = (unsigned int *) mmap(NULL, MEMBLOCK_WIDTH, PROT_READ | PROT_WRITE, MAP_SHARED, dh,  (off_t)(scratch_addr + MT_SOURCE_MEM_OFFSET + 2*MEMBLOCK_WIDTH));

    return dma;

}

int main() {

    unsigned int *axi_dma_register_mmap;
    unsigned int *mm2s_descriptor_register_mmap;
    unsigned int *s2mm_descriptor_register_mmap;
    unsigned int *source_mem_map;
    unsigned int *dest_mem_map;
    int controlregister_ok = 0, mm2s_status, s2mm_status;
    uint32_t mm2s_current_descriptor_address;
    uint32_t s2mm_current_descriptor_address;
    uint32_t mm2s_tail_descriptor_address;
    uint32_t s2mm_tail_descriptor_address;


    /*********************************************************************/
    /*               mmap the AXI DMA Register Address Map               */
    /*               the base address is defined in vivado               */
    /*                 by editing the offset address in                  */
    /*            address editor tab ("open block diagramm")             */
    /*********************************************************************/

    int dh = open("/dev/mem", O_RDWR | O_SYNC);
    axi_dma_register_mmap = (unsigned int *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, dh, AXI_DMA_REGISTER_LOCATION);
    mm2s_descriptor_register_mmap = (unsigned int *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, dh, HP0_MM2S_DMA_DESCRIPTORS_ADDRESS);
    s2mm_descriptor_register_mmap = (unsigned int *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, dh, HP0_S2MM_DMA_DESCRIPTORS_ADDRESS);
    source_mem_map = (unsigned int *) mmap(NULL, BUFFER_BLOCK_WIDTH * NUM_OF_DESCRIPTORS, PROT_READ | PROT_WRITE, MAP_SHARED, dh, (off_t) (HP0_MM2S_SOURCE_MEM_ADDRESS));
    dest_mem_map = (unsigned int *) mmap(NULL, BUFFER_BLOCK_WIDTH * NUM_OF_DESCRIPTORS, PROT_READ | PROT_WRITE, MAP_SHARED, dh, (off_t) (HP0_S2MM_TARGET_MEM_ADDRESS));
    unsigned int i;

    // fill mm2s-register memory with zeros
    for (i = 0; i < DESCRIPTOR_REGISTERS_SIZE; i++) {
        char *p = (char *) mm2s_descriptor_register_mmap;
        p[i] = 0x00000000;
    }

    // fill s2mm-register memory with zeros
    for (i = 0; i < DESCRIPTOR_REGISTERS_SIZE; i++) {
        char *p = (char *) s2mm_descriptor_register_mmap;
        p[i] = 0x00000000;
    }

    // fill source memory with a counter value
    for (i = 0; i < (BUFFER_BLOCK_WIDTH / 4) * NUM_OF_DESCRIPTORS; i++) {
        unsigned int *p = source_mem_map;
        p[i] = 0x00000000 + i;
    }

    // fill target memory with ones
    for (i = 0; i < (BUFFER_BLOCK_WIDTH / 4) * NUM_OF_DESCRIPTORS; i++) {
        unsigned int *p = dest_mem_map;
        p[i] = 0xffffffff;
    }




    /*********************************************************************/
    /*                 reset and halt all dma operations                 */
    /*********************************************************************/

    axi_dma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 0x4;
    axi_dma_register_mmap[S2MM_CONTROL_REGISTER >> 2] = 0x4;
    axi_dma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 0x0;
    axi_dma_register_mmap[S2MM_CONTROL_REGISTER >> 2] = 0x0;

    /*********************************************************************/
    /*           build mm2s and s2mm stream and control stream           */
    /* chains will be filled with next desc, buffer width and registers  */
    /*                         [0]: next descr                           */
    /*                         [1]: reserved                             */
    /*                         [2]: buffer addr                          */
    /*********************************************************************/

    mm2s_current_descriptor_address = HP0_MM2S_DMA_DESCRIPTORS_ADDRESS; // save current descriptor address

    mm2s_descriptor_register_mmap[0x0 >> 2] = HP0_MM2S_DMA_DESCRIPTORS_ADDRESS + 0x40; // set next descriptor address
    mm2s_descriptor_register_mmap[0x8 >> 2] = HP0_MM2S_SOURCE_MEM_ADDRESS + 0x0; // set target buffer address
    mm2s_descriptor_register_mmap[0x18 >> 2] = 0x87D0000; // set mm2s/s2mm buffer length to control register

    mm2s_descriptor_register_mmap[0x40 >> 2] = HP0_MM2S_DMA_DESCRIPTORS_ADDRESS + 0x80; // set next descriptor address
    mm2s_descriptor_register_mmap[0x48 >> 2] = HP0_MM2S_SOURCE_MEM_ADDRESS + 0x7D0000; // set target buffer address
    mm2s_descriptor_register_mmap[0x58 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    mm2s_descriptor_register_mmap[0x80 >> 2] = HP0_MM2S_DMA_DESCRIPTORS_ADDRESS + 0xC0; // set next descriptor address
    mm2s_descriptor_register_mmap[0x88 >> 2] = HP0_MM2S_SOURCE_MEM_ADDRESS + 0xFA0000; // set target buffer address
    mm2s_descriptor_register_mmap[0x98 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    mm2s_descriptor_register_mmap[0xC0 >> 2] = HP0_MM2S_DMA_DESCRIPTORS_ADDRESS + 0x100; // set next descriptor address
    mm2s_descriptor_register_mmap[0xC8 >> 2] = HP0_MM2S_SOURCE_MEM_ADDRESS + 0x1770000; // set target buffer address
    mm2s_descriptor_register_mmap[0xD8 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    mm2s_descriptor_register_mmap[0x100 >> 2] = HP0_MM2S_DMA_DESCRIPTORS_ADDRESS + 0x140; // set next descriptor address
    mm2s_descriptor_register_mmap[0x108 >> 2] = HP0_MM2S_SOURCE_MEM_ADDRESS + 0x1F40000; // set target buffer address
    mm2s_descriptor_register_mmap[0x118 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    mm2s_descriptor_register_mmap[0x140 >> 2] = HP0_MM2S_DMA_DESCRIPTORS_ADDRESS + 0x180; // set next descriptor address
    mm2s_descriptor_register_mmap[0x148 >> 2] = HP0_MM2S_SOURCE_MEM_ADDRESS + 0x2710000; // set target buffer address
    mm2s_descriptor_register_mmap[0x158 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    mm2s_descriptor_register_mmap[0x180 >> 2] = 0x00; // set next descriptor address (unused?)
    mm2s_descriptor_register_mmap[0x188 >> 2] = HP0_MM2S_SOURCE_MEM_ADDRESS + 0x2EE0000; // set target buffer address
    mm2s_descriptor_register_mmap[0x198 >> 2] = 0x47D0000; // set mm2s/s2mm buffer length to control register

    mm2s_tail_descriptor_address = HP0_MM2S_DMA_DESCRIPTORS_ADDRESS + 0x180; // save tail descriptor address


    s2mm_current_descriptor_address = HP0_S2MM_DMA_DESCRIPTORS_ADDRESS; // save current descriptor address

    s2mm_descriptor_register_mmap[0x0 >> 2] = HP0_S2MM_DMA_DESCRIPTORS_ADDRESS + 0x40; // set next descriptor address
    s2mm_descriptor_register_mmap[0x8 >> 2] = HP0_S2MM_TARGET_MEM_ADDRESS + 0x0; // set target buffer address
    s2mm_descriptor_register_mmap[0x18 >> 2] = 0x87D0000; // set mm2s/s2mm buffer length to control register

    s2mm_descriptor_register_mmap[0x40 >> 2] = HP0_S2MM_DMA_DESCRIPTORS_ADDRESS + 0x80; // set next descriptor address
    s2mm_descriptor_register_mmap[0x48 >> 2] = HP0_S2MM_TARGET_MEM_ADDRESS + 0x7D0000; // set target buffer address
    s2mm_descriptor_register_mmap[0x58 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    s2mm_descriptor_register_mmap[0x80 >> 2] = HP0_S2MM_DMA_DESCRIPTORS_ADDRESS + 0xC0; // set next descriptor address
    s2mm_descriptor_register_mmap[0x88 >> 2] = HP0_S2MM_TARGET_MEM_ADDRESS + 0xFA0000; // set target buffer address
    s2mm_descriptor_register_mmap[0x98 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    s2mm_descriptor_register_mmap[0xC0 >> 2] = HP0_S2MM_DMA_DESCRIPTORS_ADDRESS + 0x100; // set next descriptor address
    s2mm_descriptor_register_mmap[0xC8 >> 2] = HP0_S2MM_TARGET_MEM_ADDRESS + 0x1770000; // set target buffer address
    s2mm_descriptor_register_mmap[0xD8 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    s2mm_descriptor_register_mmap[0x100 >> 2] = HP0_S2MM_DMA_DESCRIPTORS_ADDRESS + 0x140; // set next descriptor address
    s2mm_descriptor_register_mmap[0x108 >> 2] = HP0_S2MM_TARGET_MEM_ADDRESS + 0x1F40000; // set target buffer address
    s2mm_descriptor_register_mmap[0x118 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    s2mm_descriptor_register_mmap[0x140 >> 2] = HP0_S2MM_DMA_DESCRIPTORS_ADDRESS + 0x180; // set next descriptor address
    s2mm_descriptor_register_mmap[0x148 >> 2] = HP0_S2MM_TARGET_MEM_ADDRESS + 0x2710000; // set target buffer address
    s2mm_descriptor_register_mmap[0x158 >> 2] = 0x7D0000; // set mm2s/s2mm buffer length to control register

    s2mm_descriptor_register_mmap[0x180 >> 2] = 0x00; // set next descriptor address (unused?)
    s2mm_descriptor_register_mmap[0x188 >> 2] = HP0_S2MM_TARGET_MEM_ADDRESS + 0x2EE0000; // set target buffer address
    s2mm_descriptor_register_mmap[0x198 >> 2] = 0x47D0000; // set mm2s/s2mm buffer length to control register

    s2mm_tail_descriptor_address = HP0_S2MM_DMA_DESCRIPTORS_ADDRESS + 0x180; // save tail descriptor address


    /*********************************************************************/
    /*                 set current descriptor addresses                  */
    /*           and start dma operations (S2MM_DMACR.RS = 1)            */
    /*********************************************************************/

    axi_dma_register_mmap[MM2S_CURDESC >> 2] = mm2s_current_descriptor_address;
    axi_dma_register_mmap[S2MM_CURDESC >> 2] = s2mm_current_descriptor_address;
    axi_dma_register_mmap[MM2S_CONTROL_REGISTER >> 2] = 0x1;
    axi_dma_register_mmap[S2MM_CONTROL_REGISTER >> 2] = 0x1;

    /*********************************************************************/
    /*                          start transfer                           */
    /*                 (by setting the taildescriptors)                  */
    /*********************************************************************/

    axi_dma_register_mmap[MM2S_TAILDESC >> 2] = mm2s_tail_descriptor_address;
    axi_dma_register_mmap[S2MM_TAILDESC >> 2] = s2mm_tail_descriptor_address;

    /*********************************************************************/
    /*                 wait until all transfers finished                 */
    /*********************************************************************/

    while (!controlregister_ok) {
        mm2s_status = axi_dma_register_mmap[MM2S_STATUS_REGISTER >> 2];
        s2mm_status = axi_dma_register_mmap[S2MM_STATUS_REGISTER >> 2];
        controlregister_ok = ((mm2s_status & 0x00001000) && (s2mm_status & 0x00001000));

        cout << showbase << hex;
        cout << "Memory-mapped to stream status (" << mm2s_status << "@" << MM2S_STATUS_REGISTER << "):" << endl;

        cout << "MM2S_STATUS_REGISTER status register values" << std::endl;
        if (mm2s_status & 0x00000001) {
        	cout << " halted";
        } else {
        	cout << " running";
        }
        if (mm2s_status & 0x00000002) cout << " idle";
        if (mm2s_status & 0x00000008) cout << " SGIncld";
        if (mm2s_status & 0x00000010) cout << " DMAIntErr";
        if (mm2s_status & 0x00000020) cout << " DMASlvErr";
        if (mm2s_status & 0x00000040) cout << " DMADecErr";
        if (mm2s_status & 0x00000100) cout << " SGIntErr";
        if (mm2s_status & 0x00000200) cout << " SGSlvErr";
        if (mm2s_status & 0x00000400) cout << " SGDecErr";
        if (mm2s_status & 0x00001000) cout << " IOC_Irq";
        if (mm2s_status & 0x00002000) cout << " Dly_Irq";
        if (mm2s_status & 0x00004000) cout << " Err_Irq";
        cout << endl;


        cout << "Stream to memory-mapped status (" << s2mm_status << "@" << S2MM_STATUS_REGISTER << "):" << endl;

        cout << "S2MM_STATUS_REGISTER status register values" << std::endl;
        if (s2mm_status & 0x00000001) {
        	cout << " halted";
        } else {
        	cout << " running";
        }
        if (s2mm_status & 0x00000002) cout << " idle";
        if (s2mm_status & 0x00000008) cout << " SGIncld";
        if (s2mm_status & 0x00000010) cout << " DMAIntErr";
        if (s2mm_status & 0x00000020) cout << " DMASlvErr";
        if (s2mm_status & 0x00000040) cout << " DMADecErr";
        if (s2mm_status & 0x00000100) cout << " SGIntErr";
        if (s2mm_status & 0x00000200) cout << " SGSlvErr";
        if (s2mm_status & 0x00000400) cout << " SGDecErr";
        if (s2mm_status & 0x00001000) cout << " IOC_Irq";
        if (s2mm_status & 0x00002000) cout << " Dly_Irq";
        if (s2mm_status & 0x00004000) cout << " Err_Irq";
        cout << endl;

    }


    return 0;
}
