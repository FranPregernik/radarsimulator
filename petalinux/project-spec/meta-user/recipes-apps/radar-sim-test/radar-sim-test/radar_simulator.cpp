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
#include "xilinx/xaxidma_bd.h"
#include "xilinx/xaxidma_bdring.h"
#include "xilinx/xparameters.h"
#include "inc/exceptions.hpp"

#include "radar_simulator.hpp"

void PrintMem(u32 *mem, u32 size, u32 wrapping) {
    printf("--- MEM from: 0x%08x - 0x%08x --- \r\n", mem, mem + size);

    for (int i = 0; i < size; i += wrapping) {
        printf("0x%08x: ", mem + i);
        for (int j = 0; j < wrapping; j++) {
            printf("%08x ", mem[i + j]);
        }
        printf("\r\n");
    }
}

static void PrintDmaStatus(XAxiDma *InstancePtr) {
    u32 reg = XAxiDma_ReadReg(InstancePtr->RegBase + XAXIDMA_TX_OFFSET, XAXIDMA_SR_OFFSET);

    if (reg & 0x00000001) {
        printf(" halted");
    } else {
        printf(" running");
    }
    if (reg & 0x00000002)
        printf(" idle");
    if (reg & 0x00000008)
        printf(" SGIncld");
    if (reg & 0x00000010)
        printf(" DMAIntErr");
    if (reg & 0x00000020)
        printf(" DMASlvErr");
    if (reg & 0x00000040)
        printf(" DMADecErr");
    if (reg & 0x00000100)
        printf(" SGIntErr");
    if (reg & 0x00000200)
        printf(" SGSlvErr");
    if (reg & 0x00000400)
        printf(" SGDecErr");
    if (reg & 0x00001000)
        printf(" IOC_Irq");
    if (reg & 0x00002000)
        printf(" Dly_Irq");
    if (reg & 0x00004000)
        printf(" Err_Irq");
    printf("\r\n");
}

void FXAxiDma_DumpBd(XAxiDma_Bd* BdPtr) {
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

void RadarSimulator::startDmaTransfer(XAxiDma *dmaPtr, UINTPTR physMemAddr, u32 simBlockSize, int blockCount) {

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
        Status = XAxiDma_BdSetBufAddr(CurrBdPtr, physMemAddr + memIdx * simBlockSize);
        if (Status != XST_SUCCESS) {
            printf("Tx set buffer addr %x on BD %x failed %d\r\n", physMemAddr, (UINTPTR) CurrBdPtr, Status);
            RAISE(DmaInitFailedException, "Unable to set BD buffer address");
        }

        Status = XAxiDma_BdSetLength(CurrBdPtr, simBlockSize, TxRingPtr->MaxTransferLen);
        if (Status != XST_SUCCESS) {
            printf("Tx set length %d on BD %x failed %d\r\n", simBlockSize, (UINTPTR) CurrBdPtr, Status);
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

}

void RadarSimulator::initDmaEngine(int devId, int devMemHandle, XAxiDma *dma) {

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

void RadarSimulator::initScatterGatherBufferDescriptors(XAxiDma *dma, Simulator *ctrl, UINTPTR virtDataAddr, UINTPTR physDataAddr, long size) {

    int Status;

    XAxiDma_BdRing *TxRingPtr = XAxiDma_GetTxRing(dma);

    /* Disable all TX interrupts before TxBD space setup */
    XAxiDma_BdRingIntDisable(TxRingPtr, XAXIDMA_IRQ_ALL_MASK);

    /* Set TX delay and coalesce */
    int Delay = 0;
    int Coalesce = 1;
    XAxiDma_BdRingSetCoalesce(TxRingPtr, Coalesce, Delay);

    /* Setup TxBD space  */
    u32 BdCount = XAxiDma_BdRingCntCalc(XAXIDMA_BD_MINIMUM_ALIGNMENT, size);
    Status = XAxiDma_BdRingCreate(TxRingPtr, (UINTPTR) physDataAddr, (UINTPTR) virtDataAddr, XAXIDMA_BD_MINIMUM_ALIGNMENT, BdCount);
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

void RadarSimulator::initMapMemory() {

    // calculate individual map block size
    u32 acpCnt = ctrl->acpCnt;
    u32 trigUs = ctrl->trigUs;

    // calc how many words can store the microseconds as bits
    u32 trig_word_cnt = (trigUs / WORD_BITS);

    // calc how many words are needed for a radar whole revolution
    u32 mem_blk_word_cnt = acpCnt * trig_word_cnt;

    // store the block size in
    blockByteSize = mem_blk_word_cnt * WORD_SIZE;

    // calculate the needed sizes for the individual block sizesz
    clutterMapWordSize = CL_BLK_CNT * blockByteSize / WORD_SIZE;
    targetMapWordSize = MT_BLK_CNT * blockByteSize / WORD_SIZE;

    // store pointer to the beginnings of the individual memory blocks
    u32 dataOffset = (DATA_BASE - MEM_BASE_ADDR) / WORD_SIZE;
    clutterMemPtr = scratchMem + dataOffset;
    targetMemPtr = scratchMem + dataOffset + clutterMapWordSize;

}

RadarSimulator::RadarSimulator() {
    devMemHandle = open("/dev/mem", O_RDWR | O_SYNC);
    if (devMemHandle < 0) {
        RAISE(NoAccessToDevMemException, "Unable to open device handle to /dev/mem");
    }

    ctrl = (Simulator *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, devMemHandle, RSIM_CTRL_REGISTER_LOCATION);
    if (!ctrl->calibrated) {
        RAISE(RadarSignalNotCalibratedException, "Radar signal not calibrated");
    }

    scratchMem = (u32*) mmap(NULL, MEM_HIGH_ADDR - MEM_BASE_ADDR + 1, PROT_READ | PROT_WRITE, MAP_SHARED, devMemHandle, MEM_BASE_ADDR);
    memset((u8*) scratchMem, 0x0, MEM_HIGH_ADDR - MEM_BASE_ADDR + 1);

    /* Initialize CLUTTER DMA engine */
    initDmaEngine(CL_DMA_DEV_ID, devMemHandle, &clutterDma);
    initScatterGatherBufferDescriptors(&clutterDma, ctrl, addrToVirtual(CL_BD_SPACE_BASE), CL_BD_SPACE_BASE, CL_BD_SPACE_HIGH - CL_BD_SPACE_BASE + 1);

    /* Initialize TARGET DMA engine */
    initDmaEngine(MT_DMA_DEV_ID, devMemHandle, &targetDma);
    initScatterGatherBufferDescriptors(&targetDma, ctrl, addrToVirtual(MT_BD_SPACE_BASE), MT_BD_SPACE_BASE, MT_BD_SPACE_HIGH - MT_BD_SPACE_BASE + 1);

    /* Initialize MAP memory for clutter and moving targets */
    initMapMemory();
}

RadarSimulator::~RadarSimulator() {
    close(devMemHandle);
}

void RadarSimulator::enable() {
    startDmaTransfer(&clutterDma, addrToPhysical((UINTPTR) clutterMemPtr), blockByteSize, CL_BLK_CNT);
    startDmaTransfer(&targetDma, addrToPhysical((UINTPTR) targetMemPtr), blockByteSize, MT_BLK_CNT);
    ctrl->enabled = 0x1;
}

void RadarSimulator::disable() {
    ctrl->enabled = 0x0;
}

Simulator RadarSimulator::getStatus() {
    return *ctrl;
}

void RadarSimulator::clearAll() {
    memset((u8*) scratchMem, 0x0, MEM_HIGH_ADDR - MEM_BASE_ADDR + 1);
}

void RadarSimulator::clearClutterMap() {
    memset((u8*) clutterMemPtr, 0x0, CL_BLK_CNT * blockByteSize);
}

void RadarSimulator::clearTargetMap() {
    memset((u8*) targetMemPtr, 0x0, MT_BLK_CNT * blockByteSize);
}

void RadarSimulator::initClutterMap(istream& input) {
    u32 arpUs = 0;
    u32 acpCnt = 0;
    u32 trigUs = 0;
    u32 trigSize = 0;

    input.read((char*) &arpUs, sizeof(u32));
    input.read((char*) &acpCnt, sizeof(u32));
    input.read((char*) &trigUs, sizeof(u32));
    input.read((char*) &trigSize, sizeof(u32));

    if (ctrl->arpUs != arpUs || ctrl->acpCnt != acpCnt || ctrl->trigUs != trigUs) {
        cerr << "Expecting " << ctrl->arpUs << "/" << ctrl->acpCnt << "/" << ctrl->trigUs << " but got " << arpUs << "/" << acpCnt << "/" << trigUs;
        RAISE(IncompatibleFileException, "Incompatible simulation data file. Expecting " << ctrl->arpUs << "/" << ctrl->acpCnt << "/" << ctrl->trigUs << " but got " << arpUs << "/" << acpCnt << "/" << trigUs)
    }

    for (int i = 0; i < CL_BLK_CNT; i++) {
        input.read((char*) (clutterMemPtr + i * blockByteSize), blockByteSize);
    }
}

void RadarSimulator::initTargetMap(istream& input) {
    u32 arpUs = 0;
    u32 acpCnt = 0;
    u32 trigUs = 0;
    u32 trigSize = 0;

    input.read((char*) &arpUs, sizeof(u32));
    input.read((char*) &acpCnt, sizeof(u32));
    input.read((char*) &trigUs, sizeof(u32));
    input.read((char*) &trigSize, sizeof(u32));

    if (ctrl->arpUs != arpUs || ctrl->acpCnt != acpCnt || ctrl->trigUs != trigUs) {
        RAISE(IncompatibleFileException, "Incompatible simulation data file. Expecting " << ctrl->arpUs << "/" << ctrl->acpCnt << "/" << ctrl->trigUs << " but got " << arpUs << "/" << acpCnt << "/" << trigUs)
    }

    for (int i = 0; i < MT_BLK_CNT; i++) {
        input.read((char*) (targetMemPtr + i * blockByteSize), blockByteSize);
    }

    targetMemLoadIdx = MT_BLK_CNT - 1;
}

void RadarSimulator::loadNextTargetMaps(istream& input) {

    u32 currAcpIdx = ctrl->simAcpIdx;
    u32 currArp = currAcpIdx / ctrl->acpCnt;

    // allow only up to current
    if (targetMemLoadIdx - currArp >= MT_BLK_CNT - 1) {
        return;
    }

    cout << "CURR_MT_ARP_MAP=" << currArp << "/" << (currArp % MT_BLK_CNT) << endl;

    // time to load the next block
    targetMemLoadIdx++;

    // block index to write - should be the one before where the currArp is located in (circular buffer)
    int i = targetMemLoadIdx % MT_BLK_CNT;

    // rewind the file past the headers to correct position of next block to load
    input.seekg(4 * sizeof(u32) + targetMemLoadIdx * blockByteSize);

    if (!input.eof()) {
        input.read((char*) (targetMemPtr + i * blockByteSize), blockByteSize);
        cout << "LOAD_MT_ARP_MAP=" << targetMemLoadIdx << "/" << i << endl;
    } else {
        memset((u8*) (targetMemPtr + i * blockByteSize), 0x0, blockByteSize);
    }

}
