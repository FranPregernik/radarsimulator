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

XAxiDma_Bd *SimulatorHandler::startDmaTransfer(XAxiDma *dmaPtr,
                                               UINTPTR physMemAddr,
                                               u32 simBlockByteSize,
                                               int blockCount) {

    XAxiDma_Bd *FirstBdPtr;
    XAxiDma_Bd *PrevBdPtr;
    XAxiDma_Bd *CurrBdPtr;
    int Status;

    XAxiDma_BdRing *TxRingPtr = XAxiDma_GetTxRing(dmaPtr);

    /* Allocate a couple of BD */
    int bdCount = max(2, blockCount);
    Status = XAxiDma_BdRingAlloc(TxRingPtr, bdCount, &FirstBdPtr);
    if (Status != XST_SUCCESS) {
        RAISE(DmaInitFailedException, "Unable to allocate BD ring");
    }

    /* For set SOF on first BD */
    XAxiDma_BdSetCtrl(FirstBdPtr, XAXIDMA_BD_CTRL_TXSOF_MASK);

    CurrBdPtr = FirstBdPtr;
    u32 memIdx = 0;
    for (int i = 0; i < bdCount; i++) {

        /* Set up the BD using the information of the packet to transmit */
        Status = XAxiDma_BdSetBufAddr(CurrBdPtr, physMemAddr + memIdx * simBlockByteSize);
        if (Status != XST_SUCCESS) {
            printf("Tx set buffer addr %x on BD %x failed %d\r\n", physMemAddr, (UINTPTR) CurrBdPtr, Status);
            RAISE(DmaInitFailedException, "Unable to set BD buffer address");
        }

        Status = XAxiDma_BdSetLength(CurrBdPtr, simBlockByteSize, TxRingPtr->MaxTransferLen);
        if (Status != XST_SUCCESS) {
            printf("Tx set length %d on BD %x failed %d\r\n", simBlockByteSize, (UINTPTR) CurrBdPtr, Status);
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
    Status = XAxiDma_BdRingToHw(TxRingPtr, bdCount, FirstBdPtr);
    if (Status != XST_SUCCESS) {
        printf("to hw failed %d\r\n", Status);
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

void SimulatorHandler::initScatterGatherBufferDescriptors(XAxiDma *dma,
                                                          UINTPTR virtDataAddr,
                                                          UINTPTR physDataAddr,
                                                          long size) {

    int Status;

    XAxiDma_BdRing *TxRingPtr = XAxiDma_GetTxRing(dma);

    /* Disable all TX interrupts before TxBD space setup */
    XAxiDma_BdRingIntDisable(TxRingPtr, XAXIDMA_IRQ_ALL_MASK);

    /* Set TX delay and coalesce */
    u32 Delay = 0;
    u32 Coalesce = 1;
    XAxiDma_BdRingSetCoalesce(TxRingPtr, Coalesce, Delay);

    /* Setup TxBD space  */
    u32 BdCount = XAxiDma_BdRingCntCalc(XAXIDMA_BD_MINIMUM_ALIGNMENT, size);
    Status = XAxiDma_BdRingCreate(TxRingPtr, (UINTPTR) physDataAddr, (UINTPTR) virtDataAddr,
                                  XAXIDMA_BD_MINIMUM_ALIGNMENT, BdCount);
    if (Status != XST_SUCCESS) {
        RAISE(ScatterGatherInitException, "Failed create BD ring");
    }

    /*
     * We create an all-zero BD as the template.
     */
    XAxiDma_Bd BdTemplate;
    XAxiDma_BdClear(&BdTemplate);

    Status = XAxiDma_BdRingClone(TxRingPtr, &BdTemplate);
    if (Status != XST_SUCCESS) {
        RAISE(ScatterGatherInitException, "Failed BD ring clone");
    }

    /* Start the TX channel */
    Status = XAxiDma_BdRingStart(TxRingPtr);
    if (Status != XST_SUCCESS) {
        RAISE(ScatterGatherInitException, "Failed to start bdring with status " << Status);
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
    initScatterGatherBufferDescriptors(&clutterDma, addrToVirtual(CL_BD_SPACE_BASE), CL_BD_SPACE_BASE,
                                       CL_BD_SPACE_HIGH - CL_BD_SPACE_BASE + 1);

}

void SimulatorHandler::initTargetDma() {

    if (!calibrated) {
        //RAISE(RadarSignalNotCalibratedException, "ARP/ACP/TRIG values are not calibrated");
    }

    // store pointer to the beginnings of the individual memory blocks
    u32 dataOffset = (DATA_BASE - MEM_BASE_ADDR) / WORD_SIZE;
    targetMemPtr = scratchMem + dataOffset + clutterMapWordSize;

    initDmaEngine(MT_DMA_DEV_ID, devMemHandle, &targetDma);
    initScatterGatherBufferDescriptors(&targetDma,
                                       addrToVirtual(MT_BD_SPACE_BASE),
                                       MT_BD_SPACE_BASE,
                                       MT_BD_SPACE_HIGH - MT_BD_SPACE_BASE + 1);
}

SimulatorHandler::SimulatorHandler() {

    devMemHandle = open("/dev/mem", O_RDWR | O_SYNC);
    if (devMemHandle < 0) {
        RAISE(NoAccessToDevMemException, "Unable to open device handle to /dev/mem");
    }

    ctrl = (Simulator *) mmap(NULL,
                              DESCRIPTOR_REGISTERS_SIZE,
                              PROT_READ | PROT_WRITE,
                              MAP_SHARED,
                              devMemHandle,
                              RSIM_CTRL_REGISTER_LOCATION);

    scratchMem = (u32 *) mmap(NULL,
                              MEM_HIGH_ADDR - MEM_BASE_ADDR + 1,
                              PROT_READ | PROT_WRITE,
                              MAP_SHARED,
                              devMemHandle,
                              MEM_BASE_ADDR);

}

SimulatorHandler::~SimulatorHandler() {
    reset();
    close(devMemHandle);
}

void SimulatorHandler::enable() {

    if (!calibrated) {
        throw RadarSignalNotCalibratedException();
    }

    startDmaTransfer(&clutterDma, addrToPhysical((UINTPTR) clutterMemPtr), blockByteSize, CL_BLK_CNT);
    startDmaTransfer(&targetDma, addrToPhysical((UINTPTR) targetMemPtr), blockByteSize, MT_BLK_CNT);

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

    stopDmaTransfer(&clutterDma);
    stopDmaTransfer(&targetDma);

    ctrl->enabled = 0x0;

    if (refreshThread.joinable()) {
        refreshThread.join();
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
    auto headerOffset = (u32)input.tellg();

    auto currAcpIdx = ctrl->simAcpIdx;
    auto currArp = fromArpIdx + currAcpIdx / calAcpCnt;

    // ensure circular queue is not full
    if (targetArpLoadIdx - currArp >= MT_BLK_CNT - 1) {
        return;
    }

    cout << "CURR_ARP_IDX=" << currArp << "/" << (currArp % MT_BLK_CNT) << endl;

    // load the next block
    targetArpLoadIdx = targetArpLoadIdx + 1;
    if (targetArpLoadIdx >= blockCount - 1) {
        return;
    }

    // rewind the file past the headers to correct position of next block to load
    input.seekg(headerOffset + targetArpLoadIdx * blockByteSize);
    if (input.eof()) {
        return;
    }

    // block index to write (circular buffer) with 0 being the starting ARP (fromArpIdx)
    int idx = (targetArpLoadIdx - fromArpIdx) % MT_BLK_CNT;
    UINTPTR memPtr = ((UINTPTR) targetMemPtr) + idx * blockByteSize;

    // read from file or clear
    input.read((char *) memPtr, blockByteSize);
    cout << "LOAD_MT_ARP_MAP=" << targetArpLoadIdx << "/" << idx << endl;

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
    auto headerOffset = (u32)input.tellg();

    auto currAcpIdx = ctrl->simAcpIdx;
    auto currArp = fromArpIdx + currAcpIdx / calAcpCnt;

    // ensure circular queue is not full
    if (clutterArpLoadIdx - currArp >= CL_BLK_CNT - 1) {
        return;
    }

    cout << "CURR_ARP_IDX=" << currArp << "/" << (currArp % CL_BLK_CNT) << endl;

    // load the next block
    clutterArpLoadIdx = clutterArpLoadIdx + 1;
    if (clutterArpLoadIdx >= blockCount - 1) {
        return;
    }

    // rewind the file past the headers to correct position of next block to load
    input.seekg(headerOffset + clutterArpLoadIdx * blockByteSize);
    if (input.eof()) {
        return;
    }

    // block index to write (circular buffer) with 0 being the starting ARP (fromArpIdx)
    int idx = (clutterArpLoadIdx - fromArpIdx) % CL_BLK_CNT;
    UINTPTR memPtr = ((UINTPTR) clutterMemPtr) + idx * blockByteSize;

    // read from file or clear
    input.read((char *) memPtr, blockByteSize);
    cout << "LOAD_CL_ARP_MAP=" << clutterArpLoadIdx << "/" << idx << endl;

}

void SimulatorHandler::calibrate() {

    calibrated = false;
    ctrl->calibrated = 0;

    std::chrono::seconds sleepDuration(1);

    while (!ctrl->calibrated) {
        cout << "SIM_ARP_US=" << dec << ctrl->arpUs << endl;
        cout << "SIM_ACP_CNT=" << dec << ctrl->acpCnt << endl;
        cout << "SIM_TRIG_US=" << dec << ctrl->trigUs << endl;
        this_thread::sleep_for(sleepDuration);
    };

    calibrated = ctrl->calibrated == 1;

    calArpUs = ctrl->arpUs;
    calAcpCnt = ctrl->acpCnt;
    calTrigUs = ctrl->trigUs;

    // calc how many words are needed for a radar whole revolution
    u32 mem_blk_word_cnt = calAcpCnt * TRIG_WORD_CNT;

    // store the block size in
    blockByteSize = mem_blk_word_cnt * WORD_SIZE;

    // calculate the needed sizes for the individual block sizes
    targetMapWordSize = MT_BLK_CNT * mem_blk_word_cnt;
    clutterMapWordSize = CL_BLK_CNT * mem_blk_word_cnt;

    // TODO throw if not calibrated
}
