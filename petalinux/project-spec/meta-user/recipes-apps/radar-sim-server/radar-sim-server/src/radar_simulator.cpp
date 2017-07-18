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

void SimulatorHandler::stopDmaTransfer(XAxiDma *dmaPtr) {
    XAxiDma_Reset(dmaPtr);
}

XAxiDma_Bd *SimulatorHandler::startDmaTransfer(XAxiDma *dmaPtr, UINTPTR physMemAddr, u32 simBlockByteSize, int blockCount, XAxiDma_Bd *oldFirstBtPtr) {

    XAxiDma_Bd *FirstBdPtr;
    XAxiDma_Bd *PrevBdPtr;
    XAxiDma_Bd *CurrBdPtr;
    int status;

    XAxiDma_BdRing *TxRingPtr = XAxiDma_GetTxRing(dmaPtr);

    // free old BDs
    if (oldFirstBtPtr) {
        cout << "DMA_INIT_CLEAN_OLD" << endl;
        int oldBdCnt = XAxiDma_BdRingFromHw(TxRingPtr, XAXIDMA_ALL_BDS, &oldFirstBtPtr);
        if (oldBdCnt > 0) {
            cout << "DMA_INIT_CLEAN_OLD_CNT=" << oldBdCnt << endl;
            status = XAxiDma_BdRingFree(TxRingPtr, oldBdCnt, oldFirstBtPtr); // Return the list
            if (status != XST_SUCCESS) {
                RAISE(DmaInitFailedException, "Unable to clean old BD ring");
            }
        }
    }

    cout << "DMA_INIT_BLOCK_SIZE=" << simBlockByteSize << endl;

    int bdCount = max(2, blockCount);
    cout << "DMA_INIT_BD_COUNT=" << bdCount << endl;

    /* Allocate a couple of BD */
    status = XAxiDma_BdRingAlloc(TxRingPtr, bdCount, &FirstBdPtr);
    if (status != XST_SUCCESS) {
        RAISE(DmaInitFailedException, "Unable to allocate BD ring");
    }

    /* For set SOF on first BD */
    XAxiDma_BdSetCtrl(FirstBdPtr, XAXIDMA_BD_CTRL_TXSOF_MASK);
    cout << "DMA_INIT_FIRST_BD_PTR " << PADHEX(8, FirstBdPtr) << endl;

    CurrBdPtr = FirstBdPtr;
    u32 memIdx = 0;
    for (int i = 0; i < bdCount; i++) {

        /* Set up the BD using the information of the packet to transmit */
        status = XAxiDma_BdSetBufAddr(CurrBdPtr, physMemAddr + memIdx * simBlockByteSize);
        if (status != XST_SUCCESS) {
            cerr << "Tx set buffer addr "
                 << PADHEX(8, physMemAddr + memIdx * simBlockByteSize)
                 << " on BD "
                 << PADHEX(8, CurrBdPtr)
                 << " failed with status "
                 << dec << noshowbase << status
                 << endl;
            RAISE(DmaInitFailedException, "Unable to set BD buffer address");
        }

        status = XAxiDma_BdSetLength(CurrBdPtr, simBlockByteSize, TxRingPtr->MaxTransferLen);
        if (status != XST_SUCCESS) {
            cerr << "Tx set length "
                 << simBlockByteSize
                 << " on BD "
                 << PADHEX(8, CurrBdPtr)
                 << " failed with status "
                 << dec << noshowbase << status
                 << endl;
            RAISE(DmaInitFailedException, "Unable to set BD buffer length");
        }

        XAxiDma_BdSetId(CurrBdPtr, i);

        /* advance pointer */
        PrevBdPtr = CurrBdPtr;
        CurrBdPtr = (XAxiDma_Bd *) XAxiDma_BdRingNext(TxRingPtr, CurrBdPtr);

        /* advance memory (with loop around protection) */
        if (blockCount > 1) {
            memIdx = (memIdx + 1) % blockCount;
        }

    }

    // set up cyclic mode, i.e. wrap around pointer to first (CurrBdPtr = last BD ptr)
    XAxiDma_BdSetNext(PrevBdPtr, FirstBdPtr, TxRingPtr);

    /* For set EOF on last BD */
    XAxiDma_BdSetCtrl(PrevBdPtr, XAXIDMA_BD_CTRL_TXEOF_MASK);

    /*  debug print */
//    CurrBdPtr = FirstBdPtr;
//    for (int i = 0; i < bdCount; i++) {
//        FXAxiDma_DumpBd(CurrBdPtr);
//        CurrBdPtr = (XAxiDma_Bd *) XAxiDma_BdRingNext(TxRingPtr, CurrBdPtr);
//    }
//    PrintDmaStatus(dmaPtr);

    /* Give the BD to DMA to kick off the transmission. */
    status = XAxiDma_BdRingToHw(TxRingPtr, bdCount, FirstBdPtr);
    if (status != XST_SUCCESS) {
        cerr << "to hw failed " << status << endl;
        RAISE(DmaInitFailedException, "Unable for HW to process BDs");
    }

    return FirstBdPtr;

}

void SimulatorHandler::initDmaEngine(int devId,
                                     int devMemHandle,
                                     XAxiDma *dma) {

    int Status;
    XAxiDma_Config *Config;

    /* Get Clutter DMA config */
    Config = XAxiDma_LookupConfig(devId);
    if (!Config) {
        RAISE(DmaConfigNotFoundException, "No config found for " << devId);
    }

    /* Initialize CLUTTER DMA engine */
    Status = XAxiDma_CfgInitialize(dma, devMemHandle, Config);
    if (Status != XST_SUCCESS) {
        RAISE(DmaInitFailedException, "Initialization failed for DMA " << devId << ": " << Status);
    }

    if (!XAxiDma_HasSg(dma)) {
        RAISE(NonScatterGatherDmaException, "Device " << devId << " configured as Simple mode");
    }

    // enable cyclic
    Status = XAxiDma_SelectCyclicMode(dma, XAXIDMA_DMA_TO_DEVICE, TRUE);
    if (Status != XST_SUCCESS) {
        RAISE(DmaInitFailedException, "Failed to create set cyclic mode for " << devId);
    }

}

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

    if (!calibrated) {
        //RAISE(RadarSignalNotCalibratedException, "ARP/ACP/TRIG values are not calibrated");
    }

    // store pointer to the beginnings of the individual memory blocks
    u32 dataOffset = (DATA_BASE - MEM_BASE_ADDR) / WORD_SIZE;
    clutterMemPtr = scratchMem + dataOffset;

    initDmaEngine(CL_DMA_DEV_ID, devMemHandle, &clutterDma);
    initScatterGatherBufferDescriptors(
        &clutterDma,
        addrToVirtual(CL_BD_SPACE_BASE),
        CL_BD_SPACE_BASE,
        CL_BD_SPACE_HIGH - CL_BD_SPACE_BASE + 1
    );

}

void SimulatorHandler::initTargetDma() {

    if (!calibrated) {
        //RAISE(RadarSignalNotCalibratedException, "ARP/ACP/TRIG values are not calibrated");
    }

    // store pointer to the beginnings of the individual memory blocks
    u32 dataOffset = (DATA_BASE - MEM_BASE_ADDR) / WORD_SIZE;
    targetMemPtr = scratchMem + dataOffset + clutterMapWordSize;

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

    /* Initialize CLUTTER DMA engine */
    initClutterDma();

    /* Initialize scratch mem */
    clearClutterMap();

    /* Initialize TARGET DMA engine */
    initTargetDma();

    /* Initialize scratch mem */
    clearTargetMap();

    calibrate();

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

    if (!calibrated) {
        throw RadarSignalNotCalibratedException();
    }

    firstClutterBdPtr = startDmaTransfer(&clutterDma, addrToPhysical((UINTPTR) clutterMemPtr), blockByteSize, CL_BLK_CNT, firstClutterBdPtr);
    firstTargetBdPtr = startDmaTransfer(&targetDma, addrToPhysical((UINTPTR) targetMemPtr), blockByteSize, MT_BLK_CNT, firstTargetBdPtr);

    ctrl->enabled = 0x1;

    // periodically load next maps in line
    if (refreshThread.joinable()) {
        refreshThread.join();
    }
    refreshThread = thread([=] {
        loadNextMaps();
    });

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
        // TODO: throw
        exit(2);
    }

    auto mf = "/var/targets.bin";
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

    // set initial queue pointer and force initial load
    clutterArpLoadIdx = arpPosition - 1;
    targetArpLoadIdx = arpPosition - 1;

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
    memset((u8 *) scratchMem, 0x0, MEM_SCRATCH_SIZE);
    cout << "CLR_ALL" << endl;
}

void SimulatorHandler::clearClutterMap() {
    // TODO: Align to DMA_DATA_WIDTH
    memset((u8 *) clutterMemPtr, 0x0, CL_BLK_CNT * blockByteSize);
    cout << "CLR_CL" << endl;
}

void SimulatorHandler::clearTargetMap() {
    // TODO: Align to DMA_DATA_WIDTH
    memset((u8 *) targetMemPtr, 0x0, MT_BLK_CNT * blockByteSize);
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
        loadNextTargetMap(mtFile);
        loadNextClutterMap(ctFile);
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

    auto currAcpIdx = ctrl->simAcpIdx;
    auto currArp = fromArpIdx + currAcpIdx / calAcpCnt;

    auto fileBlockByteSize = acpCnt * TRIG_WORD_CNT * WORD_SIZE;
//    cout << "DBG_LOAD_NEXT_TARGET_MAP_FILE_BYTE_BLOCK_SIZE=" << fileBlockByteSize << "/" << blockByteSize << endl;
//    cout << "DBG_LOAD_NEXT_TARGET_MAP_IDX=" << targetArpLoadIdx << "/" << currArp << "/" << MT_BLK_CNT - 1 << endl;

    // ensure circular queue is not full
    if ((targetArpLoadIdx >= 0) && (targetArpLoadIdx - (int) currArp >= MT_BLK_CNT - 1)) {
        return;
    }

    cout << "CURR_ARP_IDX=" << currArp << "/" << ((currArp - fromArpIdx) % MT_BLK_CNT) << endl;

    while ((targetArpLoadIdx < 0) || (targetArpLoadIdx - (int) currArp < MT_BLK_CNT - 1)) {

        // load the next block
        targetArpLoadIdx = targetArpLoadIdx + 1;
        if (targetArpLoadIdx >= blockCount - 1) {
            cout << "STOP_TARGET_MAP_NO_MORE_DATA" << endl;
            return;
        }

        // rewind the file past the headers to correct position of next block to load
        input.seekg(headerOffset + targetArpLoadIdx * fileBlockByteSize);
        if (input.eof()) {
            cout << "STOP_TARGET_MAP_EOF" << endl;
            return;
        }

        // block index to write (circular buffer) with 0 being the starting ARP (fromArpIdx)
        int idx = (targetArpLoadIdx - fromArpIdx) % MT_BLK_CNT;
        UINTPTR memPtr = ((UINTPTR) targetMemPtr) + idx * blockByteSize;

        // read from file or clear
        memset((u8 *) memPtr, 0x0, blockByteSize);
        input.read((char *) memPtr, fileBlockByteSize);
        cout << "LOAD_MT_ARP_MAP=" << targetArpLoadIdx << "/" << idx << endl;
    }

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

    auto currAcpIdx = ctrl->simAcpIdx;
    auto currArp = fromArpIdx + currAcpIdx / calAcpCnt;

    auto fileBlockByteSize = acpCnt * TRIG_WORD_CNT * WORD_SIZE;
//    cout << "DBG_LOAD_NEXT_CLUTTER_MAP_FILE_BYTE_BLOCK_SIZE=" << fileBlockByteSize << "/" << blockByteSize << endl;
//    cout << "DBG_LOAD_NEXT_CLUTTER_MAP_IDX=" << targetArpLoadIdx << "/" << currArp << "/" << CL_BLK_CNT - 1 << endl;

    // ensure circular queue is not full
    if ((clutterArpLoadIdx >= 0) && (clutterArpLoadIdx - (int) currArp >= CL_BLK_CNT - 1)) {
        return;
    }

    cout << "CURR_ARP_IDX=" << currArp << "/" << ((currArp - fromArpIdx) % CL_BLK_CNT) << endl;

    while ((clutterArpLoadIdx < 0) || (clutterArpLoadIdx - (int)currArp < CL_BLK_CNT - 1)) {

        // load the next block
        clutterArpLoadIdx = clutterArpLoadIdx + 1;
        if (clutterArpLoadIdx >= blockCount - 1) {
            cout << "STOP_CLUTTER_MAP_NO_MORE_DATA" << endl;
            return;
        }

        // rewind the file past the headers to correct position of next block to load
        input.seekg(headerOffset + clutterArpLoadIdx * fileBlockByteSize);
        if (input.eof()) {
            cout << "STOP_CLUTTER_MAP_EOF" << endl;
            return;
        }

        // block index to write (circular buffer) with 0 being the starting ARP (fromArpIdx)
        int idx = (clutterArpLoadIdx - fromArpIdx) % CL_BLK_CNT;
        UINTPTR memPtr = ((UINTPTR) clutterMemPtr) + idx * blockByteSize;

        // read from file or clear
        memset((u8 *) memPtr, 0x0, blockByteSize);
        input.read((char *) memPtr, fileBlockByteSize);
        cout << "LOAD_CL_ARP_MAP=" << clutterArpLoadIdx << "/" << idx << endl;
    }


}

void SimulatorHandler::calibrate() {

    calibrated = false;
    ctrl->calibrated = 0;

    std::chrono::seconds sleepDuration(1);

    while (!ctrl->calibrated) {
        cout << "CAL_SIM_ARP_US=" << dec << ctrl->arpUs << endl;
        cout << "CAL_SIM_ACP_CNT=" << dec << ctrl->acpCnt << endl;
        cout << "CAL_SIM_TRIG_US=" << dec << ctrl->trigUs << endl;
        this_thread::sleep_for(sleepDuration);
    };

    calibrated = ctrl->calibrated == 1;

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

void SimulatorHandler::logState() {
    cout << "SIM_STATUS" << endl;
    cout << "SIM_ENABLED=" << ctrl->enabled << endl;
    cout << "SIM_CAL=" << ctrl->calibrated << endl;
    cout << "SIM_NORM_ENABLED=" << ctrl->normEnabled << endl;
    cout << "SIM_MTI_ENABLED=" << ctrl->mtiEnabled << endl;
    cout << "SIM_ACP_CNT=" << ctrl->acpCnt << endl;
    cout << "SIM_ARP_US=" << ctrl->arpUs << endl;
    cout << "SIM_TRIG_US=" << ctrl->trigUs << endl;
    cout << "SIM_LOAD_CL_ACP=" << ctrl->loadedClutterAcp << endl;
    cout << "SIM_LOAD_TT_ACP=" << ctrl->loadedTargetAcp << endl;
    cout << "SIM_SIM_ACP_IDX=" << ctrl->simAcpIdx << endl;
    cout << "SIM_CURR_ACP_IDX=" << ctrl->currAcpIdx << endl;
}
