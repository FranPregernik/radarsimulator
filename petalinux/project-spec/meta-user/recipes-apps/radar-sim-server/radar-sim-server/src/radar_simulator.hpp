/*
 * radar_simulator.hpp
 *
 *  Created on: Feb 25, 2017
 *      Author: fpregernik
 */

#include "xilinx/xaxidma.h"
#include "xilinx/xparameters.h"

#include "thrift/Simulator.h"

#include <iostream>
#include <iomanip>
#include <stdexcept>
#include <chrono>
#include <ctime>
#include <thread>         // std::thread

using namespace std;
using namespace ::hr::franp::rsim;

#include "inc/exceptions.hpp"

#ifndef RADAR_SIMULATOR_
#define RADAR_SIMULATOR_

/**  CONSTANTS **/
#define WORD_SIZE               (sizeof(u32))
#define WORD_BITS               (8 * WORD_SIZE)
#define MAX_TRIG_BITS           (3072)
#define TRIG_WORD_CNT           (MAX_TRIG_BITS / WORD_BITS)

#define CL_DMA_DEV_ID           XPAR_AXIDMA_0_DEVICE_ID
#define MT_DMA_DEV_ID           XPAR_AXIDMA_1_DEVICE_ID

#define MEM_BASE_ADDR           0x19000000
#define MEM_HIGH_ADDR           (MEM_BASE_ADDR + 0x05848000)

// TODO rename TX_ to CL_DMA_
// TODO rename RX_ to MT_DMA_
#define CL_BD_SPACE_BASE        (MEM_BASE_ADDR)
#define CL_BD_SPACE_HIGH        (MEM_BASE_ADDR + 0x00000FFF)

#define MT_BD_SPACE_BASE        (MEM_BASE_ADDR + 0x00001000)
#define MT_BD_SPACE_HIGH        (MEM_BASE_ADDR + 0x00001FFF)

#define DATA_BASE               (MEM_BASE_ADDR + 0x00100000)

#define CL_BLK_CNT              1
#define MT_BLK_CNT              4

// AXI LITE Register Address Map for the control/statistics IP
#define    RSIM_CTRL_REGISTER_LOCATION           (XPAR_RADAR_SIM_SUBSYTEM_RADAR_SIMULATOR_RADAR_SIM_CTRL_AXI_BASEADDR)
//#define    RSIM_CTRL_ENABLED                     0x0
//#define    RSIM_CTRL_CALIBRATED                  0x1
//#define    RSIM_CTRL_ARP_US                      0x2
//#define    RSIM_CTRL_ACP_CNT                     0x3
//#define    RSIM_CTRL_TRIG_US                     0x4
//#define    RSIM_CTRL_ACP_IDX                     0x5
//#define    RSIM_CTRL_ACP_ARP_IDX                 0x6
//#define    RSIM_CTRL_FT_FIFO_CNT                 0x7
//#define    RSIM_CTRL_MT_FIFO_CNT                 0x8

/** STRUCTS **/

struct Simulator {
    u32 enabled;
    u32 mtiEnabled;
    u32 normEnabled;
    u32 calibrated;
    u32 arpUs;
    u32 acpCnt;
    u32 trigUs;
    u32 simAcpIdx;
    u32 currAcpIdx;
    u32 loadedClutterAcp;
    u32 loadedTargetAcp;
};

/***************** Macros (Inline Functions) Definitions *********************/
#define PADHEX(width, val) showbase << setfill('0') << setw(width) << std::hex << internal << (unsigned)(val)

/***************** Functions Definitions *********************/

/**  CLASSES **/

EXCEPTION(Exception, NoAccessToDevMemException);

EXCEPTION(Exception, DmaConfigNotFoundException);

EXCEPTION(Exception, DmaInitFailedException);

EXCEPTION(Exception, NonScatterGatherDmaException);

EXCEPTION(Exception, ScatterGatherInitException);

class SimulatorHandler : virtual public SimulatorIf {
public:
    SimulatorHandler();

    /**
     * Frees the reserved memory.
     */
    ~SimulatorHandler();

    /**
     * Resets the DMA and simulator hardware.
     *
     */
    void reset() {
        disable();
        clearAll();
    }

    /**
     * Ensure the HW is calibrated with the clock signals.
     *
     */
    void calibrate();

    /**
     * Enables the simulator output.
     *
     */
    void enable();

    /**
     * Enables the MTI simulator output.
     *
     */
    void enableMti() {
        ctrl->mtiEnabled = 1;
    }

    /**
     * Enables the NORM simulator output.
     *
     */
    void enableNorm() {
        ctrl->normEnabled = 1;
    }

    /**
     * Disables the simulator output.
     *
     */
    void disable();

    /**
     * Disables the MTI simulator output.
     *
     */
    void disableMti() {
        ctrl->mtiEnabled = 0;
    }

    /**
     * Disables the NORM simulator output.
     *
     */
    void disableNorm() {
        ctrl->normEnabled = 0;
    }

    /**
     * Loads the clutter and target map data from the common location.
     *
     *
     * @param arpPosition
     */
    void loadMap(const int32_t arpPosition) {

        auto cf = "/var/clutter.bin";
        ifstream clFile(cf, ios_base::in | ios_base::binary);
        if (!clFile || !clFile.is_open()) {
            cerr << "ERR=Unable to open file " << cf << endl;
            // TODO: throw
            exit(2);
        }

        auto mf = "/var/target.bin";
        ifstream mtFile(mf, ios_base::in | ios_base::binary);
        if (!mtFile || !mtFile.is_open()) {
            cerr << "ERR=Unable to open file " << mf << endl;
            // TODO: throw
            exit(2);
        }

        // stop simulator
        reset();

        // store current ARP
        fromArpIdx = (u32) arpPosition;

        if (!clutterMemPtr) {

            /* Initialize CLUTTER DMA engine */
            initClutterDma();

            /* Initialize scratch mem */
            clearClutterMap();
        }

        if (!targetMemPtr) {

            /* Initialize TARGET DMA engine */
            initTargetDma();

            /* Initialize scratch mem */
            clearTargetMap();
        }

        // set initial queue pointer and force initial load
        clutterArpLoadIdx = fromArpIdx - 1;
        targetArpLoadIdx = fromArpIdx - 1;

        loadNextTargetMap(mtFile);
        loadNextClutterMap(clFile);
    }

    /**
     * Returns the state of the simulator.
     *
     */
    void getState(SimState &_return) {

        _return.enabled = ctrl->enabled > 0;
        _return.mtiEnabled = ctrl->mtiEnabled > 0;
        _return.normEnabled = ctrl->normEnabled > 0;
        _return.calibrated = ctrl->calibrated > 0;

        _return.arpUs = calArpUs;
        _return.acpCnt = calAcpCnt;
        _return.trigUs = calTrigUs;

        _return.simAcpIdx = ctrl->simAcpIdx;
        _return.currAcpIdx = ctrl->currAcpIdx;

        _return.loadedClutterAcpIndex = ctrl->simAcpIdx;
        _return.loadedTargetAcpIndex = ctrl->simAcpIdx;

        _return.loadedClutterAcp = ctrl->loadedClutterAcp;
        _return.loadedTargetAcp = ctrl->loadedTargetAcp;

        auto startTime = chrono::steady_clock::now();
        chrono::milliseconds timeSinceEpoch = chrono::duration_cast<chrono::milliseconds>(startTime.time_since_epoch());
        _return.__set_time(timeSinceEpoch.count());

    }

    void clearAll() {
        memset((u8 *) scratchMem, 0x0, MEM_HIGH_ADDR - MEM_BASE_ADDR + 1);
    }

    void clearClutterMap() {
        memset((u8 *) clutterMemPtr, 0x0, CL_BLK_CNT * blockByteSize);
    }

    void clearTargetMap() {
        memset((u8 *) targetMemPtr, 0x0, MT_BLK_CNT * blockByteSize);
    }

private:
    thread refreshThread = thread();

    /** Device handle to /dev/mem **/
    int devMemHandle;

    /** Pointer to the radar simulator HW registers **/
    Simulator *ctrl;

    /** Stores the calibrated values **/
    bool calibrated = false;
    u32 calAcpCnt;
    u32 calArpUs;
    u32 calTrigUs;

    /** Pointer to the mmap-ed scratch memory region **/
    u32 *scratchMem;

    /** Calculated block size for one antenna rotation **/
    u32 blockByteSize;

    /** Clutter map memory region **/
    u32 *clutterMemPtr;

    int clutterArpLoadIdx;

    /** Clutter memory region size in 32bit words **/
    u32 clutterMapWordSize;

    /** Target map memory region **/
    u32 *targetMemPtr;

    int targetArpLoadIdx;

    /** Target memory region size in 32bit words **/
    u32 targetMapWordSize;

    /** AXI DMA for clutter maps **/
    XAxiDma clutterDma;

    /** AXI DMA for target maps **/
    XAxiDma targetDma;

    /** initial ARP offset **/
    u32 fromArpIdx;

    /**
     * Converts a virtual (mmap-ed) address to the physical address.
     */
    UINTPTR addrToPhysical(UINTPTR virtualAddress) {
        return MEM_BASE_ADDR + (virtualAddress - (UINTPTR) scratchMem);
    }

    /**
     * Converts a physical address to the virtual (mmap-ed) address.
     */
    UINTPTR addrToVirtual(UINTPTR physicalAddress) {
        return (UINTPTR) scratchMem + (physicalAddress - MEM_BASE_ADDR);
    }

    /**
     * Initializes the memory pointers and region to the clutter maps and DMA engine.
     */
    void initClutterDma();

    /**
     * Initializes the memory pointers and region to the target maps and DMA engine.
     */
    void initTargetDma();


    /**
     * Initializes the AXI DMA engine using the Xilinx APIs.
     */
    static void initDmaEngine(int devId,
                              int devMemHandle,
                              XAxiDma *dma);

    /**
     * Initializes the AXI DMA SG buffer descriptors using the Xilinx APIs.
     */
    static void initScatterGatherBufferDescriptors(XAxiDma *dma,
                                                   UINTPTR virtDataAddr,
                                                   UINTPTR physDataAddr,
                                                   long size);

    /**
     * Initiates the AXI DMA engine using the Xilinx APIs.
     */
    static XAxiDma_Bd *startDmaTransfer(XAxiDma *dmaPtr,
                                        UINTPTR physMemAddr,
                                        u32 simBlockByteSize,
                                        int blockCount);

    void stopDmaTransfer(XAxiDma *dmaPtr);

    void loadNextMaps() {

        auto mtFileName = "/var/target.bin";
        ifstream mtFile(mtFileName, ios_base::in | ios_base::binary);
        if (!mtFile || !mtFile.is_open()) {
            throw IncompatibleFileException();
        }

        auto ctFileName = "/var/clutter.bin";
        ifstream ctFile(ctFileName, ios_base::in | ios_base::binary);
        if (!ctFile || !ctFile.is_open()) {
            throw IncompatibleFileException();
        }

        while (ctrl->enabled) {
            loadNextTargetMap(mtFile);
            loadNextClutterMap(ctFile);
        }
    }

    void loadNextTargetMap(istream &input);

    void loadNextClutterMap(istream &input);
};

#endif /* RADAR_SIMULATOR_ */
