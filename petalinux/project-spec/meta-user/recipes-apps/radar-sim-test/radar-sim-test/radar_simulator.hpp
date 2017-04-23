/*
 * radar_simulator.hpp
 *
 *  Created on: Feb 25, 2017
 *      Author: fpregernik
 */

#include "xilinx/xaxidma.h"
#include "xilinx/xparameters.h"

#include <iostream>
#include <iomanip>
#include <stdexcept>
using namespace std;

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

#define CL_BD_CNT               (2 + ((CL_BLK_SIZE - 2) < 0 ? 0 : (CL_BLK_SIZE - 2)))
#define MT_BD_CNT               (2 + ((MT_BLK_SIZE - 2) < 0 ? 0 : (MT_BLK_SIZE - 2)))

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
    u32 loadedClutterAcpIndex;
    u32 loadedTargetAcpIndex;
    u32 loadedClutterAcp;
    u32 loadedTargetAcp;
};

/***************** Macros (Inline Functions) Definitions *********************/
#define PADHEX(width, val) showbase << setfill('0') << setw(width) << std::hex << internal << (unsigned)(val)

/***************** Functions Definitions *********************/

/**  CLASSES **/

EXCEPTION(Exception, NoAccessToDevMemException);
EXCEPTION(Exception, RadarSignalNotCalibratedException);
EXCEPTION(Exception, DmaConfigNotFoundException);
EXCEPTION(Exception, DmaInitFailedException);
EXCEPTION(Exception, NonScatterGatherDmaException);
EXCEPTION(Exception, ScatterGatherInitException);
EXCEPTION(Exception, IncompatibleFileException);

/**
 *
 */
class RadarSimulator {
public:

    /**
     * Constructs the radar simulator interface and initializes the HW.
     */
    RadarSimulator();

    /**
     * Frees the reserved memory.
     */
    ~RadarSimulator();

    /**
     * Attempt calibration
     */
    bool calibrate();

    /**
     * Enables the radar simulator output.
     */
    void enable();

    /**
     * Disables the radar simulator output.
     */
    void disable();

    /**
     * Clear all the data from the reserved scratch memory.
     */
    void clearAll();

    /**
     * Clears the clutter map portion of the scratch memory.
     */
    void clearClutterMap();

    /**
     * Clears the moving targets map portion of the scratch memory.
     */
    void clearTargetMap();

    /**
     * Fills the memory with the initial data from the simulation definition file.
     */
    void initClutterMap(istream& input);

    /**
     * Fills the memory with the initial data from the simulation definition file.
     */
    void initTargetMap(istream& input);

    /**
     * Fills the memory with the initial data of a test scenario for signal testing.
     */
    void initTestClutterMap();

    /**
     * Fills the memory with the initial data of a test scenario for signal testing.
     */
    void initTestTargetMap();

    /**
     * Loads the next simulation definitions from the file in the freed up portions of the moving target memory.
     */
    void loadNextTargetMaps(istream& input);

    /**
     * Returns the simulator control status data.
     */
    Simulator getStatus();

    u32 getCalArpUs();
    u32 getCalAcpCnt();
    u32 getCalTrigUs();

    bool isScenarioFinished() {
        return targetMemLoadIdx >= targetBlockCount + MT_BLK_CNT;
    }

private:
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

    /** Clutter memory region size in 32bit words **/
    u32 clutterMapWordSize;

    /** Target map memory region **/
    u32 *targetMemPtr;

    u32 targetMemLoadIdx;
    u32 targetBlockCount;

    /** Target memory region size in 32bit words **/
    u32 targetMapWordSize;

    /** AXI DMA for clutter maps **/
    XAxiDma clutterDma;

    /** AXI DMA for target maps **/
    XAxiDma targetDma;

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
    static void initDmaEngine(int devId, int devMemHandle, XAxiDma *dma);

    /**
     * Initializes the AXI DMA SG buffer descriptors using the Xilinx APIs.
     */
    static void initScatterGatherBufferDescriptors(XAxiDma *dma, UINTPTR virtDataAddr, UINTPTR physDataAddr, long size);

    /**
     * Initiates the AXI DMA engine using the Xilinx APIs.
     */
    static XAxiDma_Bd* startDmaTransfer(XAxiDma *dma, UINTPTR physMemAddr, u32 simBlockByteSize, int blockCount);

    void stopDmaTransfer(XAxiDma *dmaPtr);

};

#endif /* RADAR_SIMULATOR_ */
