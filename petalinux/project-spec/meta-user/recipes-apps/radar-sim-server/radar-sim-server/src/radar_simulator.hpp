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
#define DMA_DATA_WIDTH          XPAR_AXI_DMA_MT_M_AXI_MM2S_DATA_WIDTH

#define MEM_BASE_ADDR           0x19000000
#define MEM_HIGH_ADDR           (MEM_BASE_ADDR + 0x05848000)
#define MEM_SCRATCH_SIZE        (MEM_HIGH_ADDR - MEM_BASE_ADDR + 1)

#define CL_BD_SPACE_BASE        (MEM_BASE_ADDR)
#define CL_BD_SPACE_HIGH        (MEM_BASE_ADDR + 0x00000FFF)

#define MT_BD_SPACE_BASE        (MEM_BASE_ADDR + 0x00001000)
#define MT_BD_SPACE_HIGH        (MEM_BASE_ADDR + 0x00001FFF)

#define DATA_BASE               (MEM_BASE_ADDR + 0x00100000)

#define CL_BLK_CNT              1
#define MT_BLK_CNT              4

// AXI LITE Register Address Map for the control/statistics IP
#define    RSIM_CTRL_REGISTER_LOCATION           (XPAR_RADAR_SIM_SUBSYTEM_RADAR_SIMULATOR_RADAR_SIM_CTRL_AXI_BASEADDR)

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
#define PADHEX(width, val) showbase << setfill('0') << setw(width) << hex << internal << (unsigned)(val) << noshowbase << dec

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
    void reset();

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
    void enableMti();

    /**
     * Enables the NORM simulator output.
     *
     */
    void enableNorm();

    /**
     * Disables the simulator output.
     *
     */
    void disable();

    /**
     * Disables the MTI simulator output.
     *
     */
    void disableMti();

    /**
     * Disables the NORM simulator output.
     *
     */
    void disableNorm();

    /**
     * Loads the clutter and target map data from the common location.
     *
     *
     * @param arpPosition
     */
    void loadMap(const int32_t arpPosition);

    /**
     * Returns the state of the simulator.
     *
     */
    void getState(SimState &_return);

    void clearAll();

    void clearClutterMap();

    void clearTargetMap();

private:
    thread refreshThread = thread();

    /** Device handle to /dev/mem **/
    int devMemHandle;

    /** Pointer to the radar simulator HW registers **/
    Simulator *ctrl;

    /** Stores the calibrated values **/
    u32 calAcpCnt;
    u32 calArpUs;
    u32 calTrigUs;

    /** Pointer to the mmap-ed scratch memory region **/
    u32 *scratchMem;

    /** Calculated block size for one antenna rotation **/
    u32 blockByteSize;

    /** Clutter map memory region **/
    u32 *clutterMemPtr;

    u32 clutterArpLoadIdx;

    /** Clutter memory region size in 32bit words **/
    u32 clutterMapWordSize;

    /** Target map memory region **/
    u32 *targetMemPtr;

    u32 targetArpLoadIdx;

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
    UINTPTR addrToPhysical(UINTPTR virtualAddress);

    /**
     * Converts a physical address to the virtual (mmap-ed) address.
     */
    UINTPTR addrToVirtual(UINTPTR physicalAddress);

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
    static XAxiDma_Bd *startDmaTransfer(XAxiDma *dmaPtr, UINTPTR physMemAddr, u32 simBlockByteSize, int blockCount, XAxiDma_Bd *oldFirstBtPtr);

    void stopDmaTransfer(XAxiDma *dmaPtr);

    void loadNextMaps();

    void loadNextTargetMap(istream &input);

    void loadNextClutterMap(istream &input);

    XAxiDma_Bd *firstClutterBdPtr;

    XAxiDma_Bd *firstTargetBdPtr;

};

#endif /* RADAR_SIMULATOR_ */
