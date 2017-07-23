/*
 * radar_simulator.cpp
 *
 *  Created on: Feb 25, 2017
 *      Author: fpregernik
 */

#include <stdio.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>

#include <fstream>
#include <iostream>
#include <iomanip>
#include <algorithm>

using namespace std;

#include "xilinx/xaxidma.h"
#include "inc/exceptions.hpp"
#include "radar_simulator.hpp"

#define MIN(X, Y) (((X) < (Y)) ? (X) : (Y))
#define MAX(X, Y) (((X) > (Y)) ? (X) : (Y))

void FXAxiDma_DumpBd(XAxiDma_Bd *BdPtr) {
    cout << "Dump BD " << PADHEX(8, BdPtr) << endl;
    cout << "\tNext Bd Ptr: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_NDESC_OFFSET)) << endl;
    cout << "\tBuff addr: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_BUFA_OFFSET)) << endl;
    cout << "\tMCDMA Fields: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_MCCTL_OFFSET)) << endl;
    cout << "\tVSIZE_STRIDE: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_STRIDE_VSIZE_OFFSET)) << endl;

    unsigned int cr = (unsigned int) XAxiDma_BdRead(BdPtr, XAXIDMA_BD_CTRL_LEN_OFFSET);
    cout << "\tContrl reg (" << PADHEX(8, cr) << "):";
    if (cr & 0x8000000) {
        cout << " TXSOF";
    }
    if (cr & 0x4000000) {
        cout << " TXEOF";
    }
    cout << " bytes = " << PADHEX(8, cr & 0x7FFFFF);
    cout << endl;

    unsigned int sr = (unsigned int) XAxiDma_BdRead(BdPtr, XAXIDMA_BD_STS_OFFSET);
    cout << "\tStatus reg (" << PADHEX(8, sr) << "):";
    if (sr & 0x8000000) {
        cout << " Cmplt";
    }
    if (sr & 0x4000000) {
        cout << " DMADecErr";
    }
    if (sr & 0x2000000) {
        cout << " DMASlvErr";
    }
    if (sr & 0x2000000) {
        cout << " DMAIntErr";
    }
    cout << " bytes = " << PADHEX(8, sr & 0x7FFFFF);
    cout << endl;

    cout << "\tAPP 0: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR0_OFFSET)) << endl;
    cout << "\tAPP 1: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR1_OFFSET)) << endl;
    cout << "\tAPP 2: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR2_OFFSET)) << endl;
    cout << "\tAPP 3: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR3_OFFSET)) << endl;
    cout << "\tAPP 4: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR4_OFFSET)) << endl;

    cout << "\tSW ID: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_ID_OFFSET)) << endl;
    cout << "\tStsCtrl: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_HAS_STSCNTRL_OFFSET)) << endl;
    cout << "\tDRE: " << PADHEX(8, XAxiDma_BdRead(BdPtr, XAXIDMA_BD_HAS_DRE_OFFSET)) << endl;

    cout << endl;
}

void dumpMem(char const *data, size_t const bytes) {
    std::ofstream b_stream("/var/radar_sim_server_mem.bin", std::fstream::out | std::fstream::binary);
    if (b_stream) {
        b_stream.write(data, bytes);
        cout << "DUMP_COMPLETE" << endl;
    } else {
        cout << "DUMP_ERR" << endl;
    }
}

void SimulatorHandler::stopDmaTransfer(XAxiDma *dmaPtr) {
    // TODO: RESET and Reinitialize
    //XAxiDma_Reset(dmaPtr);
}

XAxiDma_Bd *SimulatorHandler::startDmaTransfer(XAxiDma *dmaPtr,
                                               UINTPTR physMemAddr,
                                               u32 simBlockByteSize,
                                               int blockCount,
                                               XAxiDma_Bd *oldFirstBtPtr) {

    XAxiDma_Bd *firstBdPtr;
    XAxiDma_Bd *prevBdPtr;
    XAxiDma_Bd *currBdPtr;
    int status;

    XAxiDma_BdRing *txRingPtr = XAxiDma_GetTxRing(dmaPtr);

    // free old BDs
    if (oldFirstBtPtr) {
        cout << "DMA_INIT_CLEAN_OLD" << endl;
        int oldBdCnt = XAxiDma_BdRingFromHw(txRingPtr, XAXIDMA_ALL_BDS, &oldFirstBtPtr);
        if (oldBdCnt > 0) {
            cout << "DMA_INIT_CLEAN_OLD_CNT=" << oldBdCnt << endl;
            status = XAxiDma_BdRingFree(txRingPtr, oldBdCnt, oldFirstBtPtr); // Return the list
            if (status != XST_SUCCESS) {
                RAISE(DmaInitFailedException, "Unable to clean old BD ring");
            }
        }
    }

    cout << "DMA_INIT_BLOCK_SIZE=" << simBlockByteSize << endl;

    int bdCount = max(2, blockCount);
    cout << "DMA_INIT_BD_COUNT=" << bdCount << endl;

    /* Allocate a couple of BD */
    status = XAxiDma_BdRingAlloc(txRingPtr, bdCount, &firstBdPtr);
    if (status != XST_SUCCESS) {
        RAISE(DmaInitFailedException, "Unable to allocate BD ring");
    }

    /* For set SOF on first BD */
    XAxiDma_BdSetCtrl(firstBdPtr, XAXIDMA_BD_CTRL_TXSOF_MASK);
    cout << "DMA_INIT_FIRST_BD_PTR " << PADHEX(8, firstBdPtr) << endl;

    currBdPtr = firstBdPtr;
    u32 memIdx = 0;
    for (int i = 0; i < bdCount; i++) {

        /* Set up the BD using the information of the packet to transmit */
        status = XAxiDma_BdSetBufAddr(currBdPtr, physMemAddr + memIdx * simBlockByteSize);
        if (status != XST_SUCCESS) {
            cerr << "Tx set buffer addr "
                 << PADHEX(8, physMemAddr + memIdx * simBlockByteSize)
                 << " on BD "
                 << PADHEX(8, currBdPtr)
                 << " failed with status "
                 << dec << noshowbase << status
                 << endl;
            RAISE(DmaInitFailedException, "Unable to set BD buffer address");
        }

        status = XAxiDma_BdSetLength(currBdPtr, simBlockByteSize, txRingPtr->MaxTransferLen);
        if (status != XST_SUCCESS) {
            cerr << "Tx set length "
                 << simBlockByteSize
                 << " on BD "
                 << PADHEX(8, currBdPtr)
                 << " failed with status "
                 << dec << noshowbase << status
                 << endl;
            RAISE(DmaInitFailedException, "Unable to set BD buffer length");
        }

        XAxiDma_BdSetId(currBdPtr, i);

        /* advance pointer */
        prevBdPtr = currBdPtr;
        currBdPtr = (XAxiDma_Bd *) XAxiDma_BdRingNext(txRingPtr, currBdPtr);

        /* advance memory (with loop around protection) */
        if (blockCount > 1) {
            memIdx = (memIdx + 1) % blockCount;
        }

    }

    // set up cyclic mode, i.e. wrap around pointer to first (CurrBdPtr = last BD ptr)
    XAxiDma_BdSetNext(prevBdPtr, firstBdPtr, txRingPtr);

    /* For set EOF on last BD */
    XAxiDma_BdSetCtrl(prevBdPtr, XAXIDMA_BD_CTRL_TXEOF_MASK);

#ifdef FDEBUG
    /*  debug print */
    currBdPtr = firstBdPtr;
    for (int i = 0; i < bdCount; i++) {
        FXAxiDma_DumpBd(currBdPtr);
        currBdPtr = (XAxiDma_Bd *) XAxiDma_BdRingNext(txRingPtr, currBdPtr);
    }
#endif

    /* Give the BD to DMA to kick off the transmission. */
    status = XAxiDma_BdRingToHw(txRingPtr, bdCount, firstBdPtr);
    if (status != XST_SUCCESS) {
        cerr << "to hw failed " << status << endl;
        RAISE(DmaInitFailedException, "Unable for HW to process BDs");
    }

    return firstBdPtr;

}

void SimulatorHandler::initDmaEngine(int devId,
                                     int devMemHandle,
                                     XAxiDma *dma) {

    int status;
    XAxiDma_Config *Config;

    /* Get Clutter DMA config */
    Config = XAxiDma_LookupConfig(devId);
    if (!Config) {
        RAISE(DmaConfigNotFoundException, "No config found for " << devId);
    }

    /* Initialize CLUTTER DMA engine */
    status = XAxiDma_CfgInitialize(dma, devMemHandle, Config);
    if (status != XST_SUCCESS) {
        RAISE(DmaInitFailedException, "Initialization failed for DMA " << devId << ": " << status);
    }

    if (!XAxiDma_HasSg(dma)) {
        RAISE(NonScatterGatherDmaException, "Device " << devId << " configured as Simple mode");
    }

    // enable cyclic
    status = XAxiDma_SelectCyclicMode(dma, XAXIDMA_DMA_TO_DEVICE, TRUE);
    if (status != XST_SUCCESS) {
        RAISE(DmaInitFailedException, "Failed to create set cyclic mode for " << devId);
    }

}

void SimulatorHandler::initScatterGatherBufferDescriptors(XAxiDma *dma,
                                                          UINTPTR virtDataAddr,
                                                          UINTPTR physDataAddr,
                                                          long size) {

    int status;

    XAxiDma_BdRing *TxRingPtr = XAxiDma_GetTxRing(dma);

    /* Disable all TX interrupts before TxBD space setup */
    XAxiDma_BdRingIntDisable(TxRingPtr, XAXIDMA_IRQ_ALL_MASK);

    /* Set TX delay and coalesce */
    u32 Delay = 0;
    u32 Coalesce = 1;
    XAxiDma_BdRingSetCoalesce(TxRingPtr, Coalesce, Delay);

    /* Setup TxBD space  */
    u32 BdCount = XAxiDma_BdRingCntCalc(XAXIDMA_BD_MINIMUM_ALIGNMENT, size);
    status = XAxiDma_BdRingCreate(
        TxRingPtr,
        (UINTPTR) physDataAddr,
        (UINTPTR) virtDataAddr,
        XAXIDMA_BD_MINIMUM_ALIGNMENT,
        BdCount
    );
    if (status != XST_SUCCESS) {
        RAISE(ScatterGatherInitException, "Failed create BD ring");
    }

    /*
     * We create an all-zero BD as the template.
     */
    XAxiDma_Bd BdTemplate;
    XAxiDma_BdClear(&BdTemplate);

    status = XAxiDma_BdRingClone(TxRingPtr, &BdTemplate);
    if (status != XST_SUCCESS) {
        RAISE(ScatterGatherInitException, "Failed BD ring clone");
    }

    /* Start the TX channel */
    status = XAxiDma_BdRingStart(TxRingPtr);
    if (status != XST_SUCCESS) {
        RAISE(ScatterGatherInitException, "Failed to start BD ring with status " << status);
    }

}

void SimulatorHandler::initClutterDma() {

    // store pointer to the beginnings of the individual memory blocks
    u32 dataOffset = (DATA_BASE - MEM_BASE_ADDR) / WORD_SIZE;
    clutterMemPtr = scratchMem + dataOffset;
    cout << "CLUTTER_MEM_PTR="
         << PADHEX(8, addrToPhysical((UINTPTR) clutterMemPtr)) << "/"
         << dec << clutterMapWordSize
         << endl;

    initDmaEngine(CL_DMA_DEV_ID, devMemHandle, &clutterDma);
    initScatterGatherBufferDescriptors(
        &clutterDma,
        addrToVirtual(CL_BD_SPACE_BASE),
        CL_BD_SPACE_BASE,
        CL_BD_SPACE_HIGH - CL_BD_SPACE_BASE + 1
    );

}

void SimulatorHandler::initTargetDma() {

    // store pointer to the beginnings of the individual memory blocks
    u32 dataOffset = (DATA_BASE - MEM_BASE_ADDR) / WORD_SIZE;
    targetMemPtr = scratchMem + dataOffset + clutterMapWordSize;
    cout << "TARGET_MEM_PTR="
         << PADHEX(8, addrToPhysical((UINTPTR) targetMemPtr)) << "/"
         << dec << targetMapWordSize
         << endl;

    initDmaEngine(MT_DMA_DEV_ID, devMemHandle, &targetDma);
    initScatterGatherBufferDescriptors(
        &targetDma,
        addrToVirtual(MT_BD_SPACE_BASE),
        MT_BD_SPACE_BASE,
        MT_BD_SPACE_HIGH - MT_BD_SPACE_BASE + 1
    );
}

SimulatorHandler::SimulatorHandler() {

    devMemHandle = open("/dev/mem", O_RDWR | O_SYNC);
    if (devMemHandle < 0) {
        RAISE(NoAccessToDevMemException, "Unable to open device handle to /dev/mem");
    }

    ctrl = (Simulator *) mmap(
        NULL,
        DESCRIPTOR_REGISTERS_SIZE,
        PROT_READ | PROT_WRITE,
        MAP_SHARED,
        devMemHandle,
        RSIM_CTRL_REGISTER_LOCATION
    );

    scratchMem = (u32 *) mmap(
        NULL,
        MEM_SCRATCH_SIZE,
        PROT_READ | PROT_WRITE,
        MAP_SHARED,
        devMemHandle,
        MEM_BASE_ADDR
    );

    clearAll();

    calibrate();

    /* Initialize CLUTTER DMA engine */
    initClutterDma();

    /* Initialize TARGET DMA engine */
    initTargetDma();

    cout << "STARTED_SERVER" << endl;
}

SimulatorHandler::~SimulatorHandler() {
    reset();

    cout << "STOPING_REFRESH_THREAD" << endl;
    if (refreshThread.joinable()) {
        refreshThread.join();
        cout << "STOP_REFRESH_THREAD" << endl;
    }

    close(devMemHandle);
}

void SimulatorHandler::reset() {
    disable();
    fromArpIdx = 0;
    clearClutterMap();
    clearTargetMap();
}

void SimulatorHandler::enableMti() {
    ctrl->mtiEnabled = 1;
    cout << "MTI_STATUS=" << (ctrl->normEnabled == 1) << endl;
}

void SimulatorHandler::enableNorm() {
    ctrl->normEnabled = 1;
    cout << "NORM_STATUS=" << (ctrl->normEnabled == 1) << endl;
}

void SimulatorHandler::enable() {

    if (!ctrl->calibrated) {
        throw RadarSignalNotCalibratedException();
    }

    if (!clutterDma.Initialized) {
        cout << "ERR_CL_DMA_NOT_INITIALIZED" << endl;
        auto ex = DmaNotInitializedException();
        ex.subSystem = SubSystem::CLUTTER;
        throw ex;
    }

    if (!targetDma.Initialized) {
        cout << "ERR_MT_DMA_NOT_INITIALIZED" << endl;
        auto ex = DmaNotInitializedException();
        ex.subSystem = SubSystem::MOVING_TARGET;
        throw ex;
    }

    firstClutterBdPtr = startDmaTransfer(&clutterDma, addrToPhysical((UINTPTR) clutterMemPtr), blockByteSize, CL_BLK_CNT, firstClutterBdPtr);
    firstTargetBdPtr = startDmaTransfer(&targetDma, addrToPhysical((UINTPTR) targetMemPtr), blockByteSize, MT_BLK_CNT, firstTargetBdPtr);

#ifdef FDEBUG
    dumpMem((char *) scratchMem, MEM_SCRATCH_SIZE);
#endif

    ctrl->enabled = 0x1;

    // periodically load next maps in line
    if (refreshThread.joinable()) {
        refreshThread.join();
    }
    refreshThread = thread([=] {
        loadNextMaps();
    });

    cout << "ENABLED_SIM" << endl;
}

void SimulatorHandler::disable() {

    if (clutterDma.Initialized) {
        stopDmaTransfer(&clutterDma);
        cout << "STOP_CL_DMA" << endl;
    }
    if (targetDma.Initialized) {
        stopDmaTransfer(&targetDma);
        cout << "STOP_MT_DMA" << endl;
    }

    ctrl->enabled = 0;
    cout << "DISABLE_SIM" << endl;

    cout << "STOPING_REFRESH_THREAD" << endl;
    if (refreshThread.joinable()) {
        refreshThread.join();
        cout << "STOP_REFRESH_THREAD" << endl;
    }

    cout << "DISABLED" << endl;
}

void SimulatorHandler::disableMti() {
    ctrl->mtiEnabled = 0;
    cout << "MTI_STATUS=" << (ctrl->normEnabled == 1) << endl;
}

void SimulatorHandler::disableNorm() {
    ctrl->normEnabled = 0;
    cout << "NORM_STATUS=" << (ctrl->normEnabled == 1) << endl;
}

void SimulatorHandler::loadMap(const int32_t arpPosition) {

    auto cf = "/var/clutter.bin";
    ifstream clFile(cf, ios_base::in | ios_base::binary);
    if (!clFile || !clFile.is_open()) {
        cerr << "ERR=Unable to open file " << cf << endl;
        auto ex = IncompatibleFileException();
        ex.subSystem = SubSystem::CLUTTER;
        throw ex;
    }

    auto mf = "/var/targets.bin";
    ifstream mtFile(mf, ios_base::in | ios_base::binary);
    if (!mtFile || !mtFile.is_open()) {
        cerr << "ERR=Unable to open file " << mf << endl;
        auto ex = IncompatibleFileException();
        ex.subSystem = SubSystem::MOVING_TARGET;
        throw ex;
    }

    // stop simulator
    reset();

    // store current ARP
    fromArpIdx = (u32) arpPosition;

    // set initial queue pointer and force initial load
    clutterArpLoadIdx = 0;
    targetArpLoadIdx = 0;

    cout << "LOADING_MAPS_FROM_ARP=" << fromArpIdx << endl;

    loadNextTargetMap(mtFile);
    loadNextClutterMap(clFile);
}

void SimulatorHandler::getState(SimState &_return) {

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

void SimulatorHandler::clearAll() {
    memset(scratchMem, 0x0, MEM_SCRATCH_SIZE);
    cout << "CLR_ALL" << endl;
}

void SimulatorHandler::clearClutterMap() {
    memset(clutterMemPtr, 0x0, CL_BLK_CNT * blockByteSize);
    cout << "CLR_CL" << endl;
}

void SimulatorHandler::clearTargetMap() {
    memset(targetMemPtr, 0x0, MT_BLK_CNT * blockByteSize);
    cout << "CLR_MT" << endl;
}

/**
 * Converts a virtual (mmap-ed) address to the physical address.
 */
UINTPTR SimulatorHandler::addrToPhysical(UINTPTR virtualAddress) {
    return MEM_BASE_ADDR + (virtualAddress - (UINTPTR) scratchMem);
}

/**
 * Converts a physical address to the virtual (mmap-ed) address.
 */
UINTPTR SimulatorHandler::addrToVirtual(UINTPTR physicalAddress) {
    return (UINTPTR) scratchMem + (physicalAddress - MEM_BASE_ADDR);
}

void SimulatorHandler::loadNextMaps() {

    auto mtFileName = "/var/targets.bin";
    ifstream mtFile(mtFileName, ios_base::in | ios_base::binary);
    if (!mtFile || !mtFile.is_open()) {
        throw IncompatibleFileException();
    }

    auto ctFileName = "/var/clutter.bin";
    ifstream ctFile(ctFileName, ios_base::in | ios_base::binary);
    if (!ctFile || !ctFile.is_open()) {
        throw IncompatibleFileException();
    }

    std::chrono::seconds sleepDuration(1);

    while (ctrl->enabled) {
        loadNextClutterMap(ctFile);
        loadNextTargetMap(mtFile);
        // sleep
        this_thread::sleep_for(sleepDuration);
    }
}

void SimulatorHandler::loadNextTargetMap(istream &input) {

    u32 arpUs = 0;
    u32 acpCnt = 0;
    u32 trigUs = 0;
    u32 trigSize = 0;
    u32 blockCount = 0;

    input.seekg(0, ios_base::beg);
    input.read((char *) &arpUs, sizeof(u32));
    input.read((char *) &acpCnt, sizeof(u32));
    input.read((char *) &trigUs, sizeof(u32));
    input.read((char *) &trigSize, sizeof(u32));
    input.read((char *) &blockCount, sizeof(u32));

    auto headerOffset = (u32) input.tellg();
    auto fileBlockByteSize = acpCnt * TRIG_WORD_CNT * WORD_SIZE;

    // a zero based, non modulo, set of indexes for the circular queue of a fixed size
    auto currArp = MAX(ctrl->simAcpIdx / calAcpCnt, 0);
    targetArpLoadIdx = MAX(targetArpLoadIdx, 0);

    // early exit for EOF
    auto blockFilePos = fromArpIdx + targetArpLoadIdx;
    if (blockFilePos >= blockCount) {
        return;
    }

    // early exit for full queue
    auto queueSize = targetArpLoadIdx - currArp;
    if (queueSize >= MT_BLK_CNT) {
//        cout << "DBG_STOP_MT_QUEUE_FULL="
//             << targetArpLoadIdx << "/"
//             << currArp << "/"
//             << queueSize
//             << endl;
        return;
    }

//    cout << "DBG_LOAD_NEXT_TARGET_MAP_FILE_BYTE_BLOCK_SIZE=" << fileBlockByteSize << "/" << blockByteSize << endl;
//    cout << "DBG_LOAD_NEXT_TARGET_MAP_IDX=" << targetArpLoadIdx << "/" << currArp << "/" << MT_BLK_CNT - 1 << endl;
//    cout << "CURR_ARP_IDX=" << currArp << "/" << currArp % MT_BLK_CNT << endl;


    auto runCount = 0;
    while (queueSize < MT_BLK_CNT && runCount < MT_BLK_CNT) {
        // prevent endless loop
        runCount++;

        // early exit for EOF
        blockFilePos = fromArpIdx + targetArpLoadIdx;
        if (blockFilePos >= blockCount) {
            break;
        }

        // block index to write (circular buffer) with 0 being the starting ARP (fromArpIdx)
        auto writeBlockIdx = targetArpLoadIdx % MT_BLK_CNT;
        char *memPtr = ((char *) targetMemPtr) + writeBlockIdx * blockByteSize;

        // rewind the file past the headers to correct position of next block to load
        size_t offset = headerOffset + blockFilePos * fileBlockByteSize;
        input.seekg(offset);
        if (input.eof()) {
            cout << "STOP_MT_EOF" << endl;
            break;
        }

        // read from file or clear
        input.read(memPtr, blockByteSize);

        cout << "LOAD_MT_ARP_MAP="
             << fromArpIdx + targetArpLoadIdx << "/"
             << writeBlockIdx << "/"
             << offset << "/"
             << PADHEX(8, addrToPhysical((UINTPTR) memPtr)) << "/"
             << dec << blockCount
             << endl;

        targetArpLoadIdx = targetArpLoadIdx + 1;
        queueSize = targetArpLoadIdx - currArp;
    }

    cout << "LOAD_MT_COMPLETE="
         << targetArpLoadIdx << "/"
         << currArp << "/"
         << queueSize
         << endl;
}

void SimulatorHandler::loadNextClutterMap(istream &input) {

    u32 arpUs = 0;
    u32 acpCnt = 0;
    u32 trigUs = 0;
    u32 trigSize = 0;
    u32 blockCount = 0;

    input.seekg(0, ios_base::beg);
    input.read((char *) &arpUs, sizeof(u32));
    input.read((char *) &acpCnt, sizeof(u32));
    input.read((char *) &trigUs, sizeof(u32));
    input.read((char *) &trigSize, sizeof(u32));
    input.read((char *) &blockCount, sizeof(u32));

    auto headerOffset = (u32) input.tellg();
    auto fileBlockByteSize = acpCnt * TRIG_WORD_CNT * WORD_SIZE;

    // a zero based, non modulo, set of indexes for the circular queue of a fixed size
    auto currArp = MAX(ctrl->simAcpIdx / calAcpCnt, 0);
    clutterArpLoadIdx = MAX(clutterArpLoadIdx, 0);

    // early exit for EOF
    auto blockFilePos = fromArpIdx + clutterArpLoadIdx;
    if (blockFilePos >= blockCount) {
        return;
    }

    // early exit for full queue
    auto queueSize = MAX(clutterArpLoadIdx - currArp, 0);
    if (queueSize >= CL_BLK_CNT) {
//        cout << "DBG_STOP_CL_QUEUE_FULL="
//             << clutterArpLoadIdx << "/"
//             << currArp << "/"
//             << queueSize
//             << endl;
        return;
    }

//    cout << "DBG_LOAD_NEXT_CLUTTER_MAP_FILE_BYTE_BLOCK_SIZE=" << fileBlockByteSize << "/" << blockByteSize << endl;
//    cout << "DBG_LOAD_NEXT_CLUTTER_MAP_IDX=" << targetArpLoadIdx << "/" << currArp << "/" << CL_BLK_CNT - 1 << endl;
//    cout << "CURR_ARP_IDX=" << currArp << "/" << currArp % CL_BLK_CNT << endl;

    auto runCount = 0;
    while (queueSize < CL_BLK_CNT && runCount < CL_BLK_CNT) {
        // prevent endless loop
        runCount++;

        // early exit for EOF
        blockFilePos = fromArpIdx + clutterArpLoadIdx;
        if (blockFilePos >= blockCount) {
            break;
        }

        // block index to write (circular buffer) with 0 being the starting ARP (fromArpIdx)
        auto writeBlockIdx = clutterArpLoadIdx % CL_BLK_CNT;
        char *memPtr = ((char *) clutterMemPtr) + writeBlockIdx * blockByteSize;

        // rewind the file past the headers to correct position of next block to load
        size_t offset = headerOffset + blockFilePos * fileBlockByteSize;
        input.seekg(offset);
        if (input.eof()) {
            cout << "STOP_CL_EOF" << endl;
            break;
        }

        // read from file or clear
        input.read(memPtr, blockByteSize);

        cout << "LOAD_CL_ARP_MAP="
             << clutterArpLoadIdx << "/"
             << writeBlockIdx << "/"
             << offset << "/"
             << PADHEX(8, addrToPhysical((UINTPTR) memPtr)) << "/"
             << dec << blockCount
             << endl;

        clutterArpLoadIdx = clutterArpLoadIdx + 1;
        queueSize = MAX(clutterArpLoadIdx - currArp, 0);
    }

    cout << "LOAD_CL_COMPLETE="
         << clutterArpLoadIdx << "/"
         << currArp << "/"
         << queueSize
         << endl;
}

void SimulatorHandler::calibrate() {

    ctrl->calibrated = 0;

    std::chrono::seconds sleepDuration(1);

    while (!ctrl->calibrated) {
        cout << "CAL_SIM_ARP_US=" << dec << ctrl->arpUs << endl;
        cout << "CAL_SIM_ACP_CNT=" << dec << ctrl->acpCnt << endl;
        cout << "CAL_SIM_TRIG_US=" << dec << ctrl->trigUs << endl;
        this_thread::sleep_for(sleepDuration);
    };

    calArpUs = ctrl->arpUs;
    calAcpCnt = ctrl->acpCnt;
    calTrigUs = ctrl->trigUs;

    // calc how many words are needed for a radar whole revolution
    u32 mem_blk_word_cnt = calAcpCnt * TRIG_WORD_CNT;

    // store the block size in
//    blockByteSize = roundUp(mem_blk_word_cnt * WORD_SIZE, DMA_DATA_WIDTH);
    blockByteSize = mem_blk_word_cnt * WORD_SIZE;
    cout << "CAL_BLOCK_BYTE_SIZE=" << dec << blockByteSize << endl;

    // calculate the needed sizes for the individual block sizes
    targetMapWordSize = MT_BLK_CNT * mem_blk_word_cnt;
    clutterMapWordSize = CL_BLK_CNT * mem_blk_word_cnt;

}