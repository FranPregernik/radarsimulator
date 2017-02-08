/******************************************************************************
 *
 * Copyright (C) 2010 - 2016 Xilinx, Inc.  All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Use of the Software is limited solely to applications:
 * (a) running on a Xilinx device, or
 * (b) that interact with a Xilinx device through a bus or interconnect.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * XILINX  BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Except as contained in this notice, the name of the Xilinx shall not be used
 * in advertising or otherwise to promote the sale, use or other dealings in
 * this Software without prior written authorization from Xilinx.
 *
 ******************************************************************************/
/*****************************************************************************/
/**
 *
 * @file xaxidma_example_sg_poll.c
 *
 * This file demonstrates how to use the xaxidma driver on the Xilinx AXI
 * DMA core (AXIDMA) to transfer packets in polling mode when the AXIDMA
 * core is configured in Scatter Gather Mode.
 *
 * This code assumes a loopback hardware widget is connected to the AXI DMA
 * core for data packet loopback.
 *
 * To see the debug print, you need a Uart16550 or uartlite in your system,
 * and please set "-DDEBUG" in your compiler options. You need to rebuild your
 * software executable.
 *
 * Make sure that MEMORY_BASE is defined properly as per the HW system. The
 * h/w system built in Area mode has a maximum DDR memory limit of 64MB. In
 * throughput mode, it is 512MB.  These limits are need to ensured for
 * proper operation of this code.
 *
 *
 * <pre>
 * MODIFICATION HISTORY:
 *
 * Ver   Who  Date     Changes
 * ----- ---- -------- -------------------------------------------------------
 * 1.00a jz   05/17/10 First release
 * 2.00a jz   08/10/10 Second release, added in xaxidma_g.c, xaxidma_sinit.c,
 *                     updated tcl file, added xaxidma_porting_guide.h, removed
 *                     workaround for endianness
 * 4.00a rkv  02/22/11 Name of the file has been changed for naming consistency
 *                     Added interrupt support for ARM.
 * 5.00a srt  03/05/12 Added Flushing and Invalidation of Caches to fix CRs
 *                     648103, 648701.
 *                     Added V7 DDR Base Address to fix CR 649405.
 * 6.00a srt  03/27/12 Changed API calls to support MCDMA driver.
 * 7.00a srt  06/18/12 API calls are reverted back for backward compatibility.
 * 7.01a srt  11/02/12 Buffer sizes (Tx and Rx) are modified to meet maximum
 *             DDR memory limit of the h/w system built with Area mode
 * 7.02a srt  03/01/13 Updated DDR base address for IPI designs (CR 703656).
 * 9.1   adk  01/07/16 Updated DDR base address for Ultrascale (CR 799532) and
 *             removed the defines for S6/V6.
 * 9.2   vak  15/04/16 Fixed compilation warnings in th example
 * </pre>
 *
 * ***************************************************************************
 */
/***************************** Include Files *********************************/
#include <stdio.h>
#include <string.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>
#include <time.h>
#include <sys/time.h>

#include "xilinx/xaxidma.h"
#include "xilinx/xparameters.h"

/******************** Constant Definitions **********************************/

/*
 * Device hardware build related constants.
 */

#define DMA_DEV_ID      XPAR_AXIDMA_0_DEVICE_ID

#define MEM_BASE_ADDR       0x19000000

#define TX_BD_SPACE_BASE    (MEM_BASE_ADDR)
#define TX_BD_SPACE_HIGH    (MEM_BASE_ADDR + 0x00000FFF)
#define RX_BD_SPACE_BASE    (MEM_BASE_ADDR + 0x00001000)
#define RX_BD_SPACE_HIGH    (MEM_BASE_ADDR + 0x00001FFF)
#define TX_BUFFER_BASE      (MEM_BASE_ADDR + 0x00100000)
#define TX_BUFFER_HIGH      (MEM_BASE_ADDR + 0x05848000)

#define MAX_PKT_U32_LEN     0x2400
#define MARK_UNCACHEABLE        0x701

#define TEST_START_VALUE    0xC

/**************************** Type Definitions *******************************/

/***************** Macros (Inline Functions) Definitions *********************/

/************************** Function Prototypes ******************************/

static int TxSetup(XAxiDma * AxiDmaInstPtr, int devMemHandle);
static int SendPacket(XAxiDma * AxiDmaInstPtr);
static int CheckDmaResult(XAxiDma * AxiDmaInstPtr);

/************************** Variable Definitions *****************************/
/*
 * Device instance definitions
 */
XAxiDma AxiDma;

/*
 * Buffer for transmit packet. Must be 32-bit aligned to be used by DMA.
 */
u32 *Packet;

// AXI LITE Register Address Map for the control/statistics unit
#define    RSIM_CTRL_REGISTER_LOCATION           0x43C00000
#define    RSIM_CTRL_ENABLED                     0x0
#define    RSIM_CTRL_CALIBRATED                  0x1
#define    RSIM_CTRL_ARP_US                      0x2
#define    RSIM_CTRL_ACP_CNT                     0x3
#define    RSIM_CTRL_TRIG_US                     0x4
#define    RSIM_CTRL_ACP_IDX                     0x5
#define    RSIM_CTRL_FT_FIFO_CNT                 0x6
#define    RSIM_CTRL_FT_FIFO_RD_CNT              0x7
#define    RSIM_CTRL_FT_FIFO_WR_CNT              0x8
#define    RSIM_CTRL_MT_FIFO_CNT                 0x9
#define    RSIM_CTRL_MT_FIFO_RD_CNT              0xA
#define    RSIM_CTRL_MT_FIFO_WR_CNT              0xB

static void PrintMem(u32 *mem, u32 size, u32 wrapping) {
    printf("--- MEM from: 0x%08x - 0x%08x --- \r\n", mem, mem + size);

    for (int i = 0; i < size; i += wrapping) {
        printf("0x%08x: ", mem + i);
        for (int j = 0; j < wrapping; j++) {
            printf("%08x ", mem[i + j]);
        }
        printf("\r\n");
    }
}

/*****************************************************************************/
/**
 * Dump the fields of a BD.
 *
 * @param   BdPtr is the BD to operate on.
 *
 * @return  None
 *
 * @note    This function can be used only when DMA is in SG mode
 *
 *****************************************************************************/
void XAxiDma_DumpBd(XAxiDma_Bd* BdPtr, XAxiDma_BdRing *BdRingPtr)
{

    printf("Dump BD %x:\r\n", (UINTPTR)XAXIDMA_BD_VIRT_TO_PHYS(BdPtr, BdRingPtr));
    printf("\tNext Bd Ptr: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_NDESC_OFFSET));
    printf("\tBuff addr: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_BUFA_OFFSET));
    printf("\tMCDMA Fields: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_MCCTL_OFFSET));
    printf("\tVSIZE_STRIDE: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr,
                    XAXIDMA_BD_STRIDE_VSIZE_OFFSET));
    printf("\tContrl len: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_CTRL_LEN_OFFSET));
    printf("\tStatus: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_STS_OFFSET));

    printf("\tAPP 0: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR0_OFFSET));
    printf("\tAPP 1: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR1_OFFSET));
    printf("\tAPP 2: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR2_OFFSET));
    printf("\tAPP 3: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR3_OFFSET));
    printf("\tAPP 4: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_USR4_OFFSET));

    printf("\tSW ID: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_ID_OFFSET));
    printf("\tStsCtrl: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr,
               XAXIDMA_BD_HAS_STSCNTRL_OFFSET));
    printf("\tDRE: %x\r\n",
        (unsigned int)XAxiDma_BdRead(BdPtr, XAXIDMA_BD_HAS_DRE_OFFSET));

    printf("\r\n");
}

static void PrintDmaStatus(XAxiDma *InstancePtr) {
    u32 statusRegister = XAxiDma_ReadReg(InstancePtr->RegBase + XAXIDMA_TX_OFFSET, XAXIDMA_SR_OFFSET);

    printf("Dump DMA %x:\r\n", XAXIDMA_VIRT_TO_PHYS(InstancePtr->RegBase, InstancePtr));
    printf("\t STATUS ");
    if (statusRegister & 0x00000001) {
        printf(" halted");
    } else {
        printf(" running");
    }
    if (statusRegister & 0x00000002)
        printf(" idle");
    if (statusRegister & 0x00000008)
        printf(" SGIncld");
    if (statusRegister & 0x00000010)
        printf(" DMAIntErr");
    if (statusRegister & 0x00000020)
        printf(" DMASlvErr");
    if (statusRegister & 0x00000040)
        printf(" DMADecErr");
    if (statusRegister & 0x00000100)
        printf(" SGIntErr");
    if (statusRegister & 0x00000200)
        printf(" SGSlvErr");
    if (statusRegister & 0x00000400)
        printf(" SGDecErr");
    if (statusRegister & 0x00001000)
        printf(" IOC_Irq");
    if (statusRegister & 0x00002000)
        printf(" Dly_Irq");
    if (statusRegister & 0x00004000)
        printf(" Err_Irq");
    printf("\r\n");
    printf("\t CDESC: %x\r\n", XAxiDma_ReadReg(InstancePtr->RegBase + XAXIDMA_TX_OFFSET, XAXIDMA_CDESC_OFFSET));
    printf("\t TDESC: %x\r\n", XAxiDma_ReadReg(InstancePtr->RegBase + XAXIDMA_TX_OFFSET, XAXIDMA_TDESC_OFFSET));
}

static int XAxiDma_Running(XAxiDma *InstancePtr) {
    u32 sr = XAxiDma_ReadReg(InstancePtr->RegBase + XAXIDMA_TX_OFFSET, XAXIDMA_SR_OFFSET);
    return (sr && 0x00000001 == 1) && (sr && 0x00000002 == 0);
}

/*****************************************************************************/
/**
 *
 * Main function
 *
 * This function is the main entry of the tests on DMA core. It sets up
 * DMA engine to be ready to receive and send packets, then a packet is
 * transmitted and will be verified after it is received via the DMA loopback
 * widget.
 *
 * @param    None
 *
 * @return
 *       - XST_SUCCESS if test passes
 *       - XST_FAILURE if test fails.
 *
 * @note     None.
 *
 ******************************************************************************/
/*****************************************************************************/
/**
 *
 * Main function
 *
 * This function is the main entry of the tests on DMA core. It sets up
 * DMA engine to be ready to receive and send packets, then a packet is
 * transmitted and will be verified after it is received via the DMA loopback
 * widget.
 *
 * @param    None
 *
 * @return
 *       - XST_SUCCESS if test passes
 *       - XST_FAILURE if test fails.
 *
 * @note     None.
 *
 ******************************************************************************/
int main(void) {
    int Status;
    XAxiDma_Config *Config;

    printf("\r\n--- Entering main() --- \r\n");

    int dh = open("/dev/mem", O_RDWR | O_SYNC);

    printf("--- Disable RSIM --- \r\n");

    u32 *ctrl_regs = (u32 *) mmap(NULL, DESCRIPTOR_REGISTERS_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, dh, RSIM_CTRL_REGISTER_LOCATION);
    ctrl_regs[RSIM_CTRL_ENABLED] = 0;

    u32 trig_word_cnt = (3 * 1024 / 8) / sizeof(u32);
    u32 mem_blk_word_size = 4096 * (3 * 1024 / 8) / sizeof(u32);
    u32 mem_blk_total_byte_cnt = 4 * mem_blk_word_size * sizeof(u32);

    // TODO:
    Packet = (u32 *) mmap(NULL, mem_blk_total_byte_cnt, PROT_READ | PROT_WRITE, MAP_SHARED, dh, TX_BUFFER_BASE);

    printf("--- Clean memory --- \r\n");

    // clean
    for (u32 i = 0; i < 3 * mem_blk_word_size; i++) {
        Packet[i] = 0x00000000;
    }

    printf("--- Prepare test data --- \r\n");

    for (u32 i = 0; i < 4096; i++) {
        u32 pos = (i % 3072);
        u32 inv_pos = 3072 - pos;

        u32 word_offset = (pos / 32);
        u32 bit_offset = 32 - pos % 32 - 1;

        u32 inv_word_offset = (inv_pos / 32);
        u32 inv_bit_offset = 32 - inv_pos % 32 - 1;

        u8 pattern = 0x7;

        Packet[i * trig_word_cnt + word_offset] |= 0x00000000 + (pattern << bit_offset);
        Packet[i * trig_word_cnt + mem_blk_word_size + inv_word_offset] |= 0x00000000 + (pattern << inv_bit_offset);
        Packet[i * trig_word_cnt + 2 * mem_blk_word_size + word_offset] |= 0x00000000 + (pattern << bit_offset);
        Packet[i * trig_word_cnt + 3 * mem_blk_word_size + inv_word_offset] |= 0x00000000 + (pattern << inv_bit_offset);
    }

    //PrintMem(Packet, mem_blk_word_size, 96);

    Config = XAxiDma_LookupConfig(DMA_DEV_ID);
    if (!Config) {
        printf("No config found for %d\r\n", DMA_DEV_ID);

        return XST_FAILURE;
    }

    /* Initialize DMA engine */
    Status = XAxiDma_CfgInitialize(&AxiDma, dh, Config);
    if (Status != XST_SUCCESS) {
        printf("Initialization failed %d\r\n", Status);
        return XST_FAILURE;
    }

    if (!XAxiDma_HasSg(&AxiDma)) {
        printf("Device configured as Simple mode \r\n");

        return XST_FAILURE;
    }

    Status = TxSetup(&AxiDma, dh);
    if (Status != XST_SUCCESS) {
        return XST_FAILURE;
    }

    printf("--- RSIM Status  --- \r\n");

    printf("RSIM_CTRL_ENABLED %d\r\n", ctrl_regs[RSIM_CTRL_ENABLED]);
    printf("RSIM_CTRL_CALIBRATED %d\r\n", ctrl_regs[RSIM_CTRL_CALIBRATED]);
    printf("RSIM_CTRL_ARP_US %d\r\n", ctrl_regs[RSIM_CTRL_ARP_US]);
    printf("RSIM_CTRL_ACP_CNT %d\r\n", ctrl_regs[RSIM_CTRL_ACP_CNT]);
    printf("RSIM_CTRL_TRIG_US %d\r\n", ctrl_regs[RSIM_CTRL_TRIG_US]);
    printf("RSIM_CTRL_ACP_IDX %d\r\n", ctrl_regs[RSIM_CTRL_ACP_IDX]);
    printf("RSIM_CTRL_FT_FIFO_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_CNT]);
    printf("RSIM_CTRL_FT_FIFO_RD_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_RD_CNT]);
    printf("RSIM_CTRL_FT_FIFO_WR_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_WR_CNT]);
    printf("RSIM_CTRL_MT_FIFO_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_CNT]);
    printf("RSIM_CTRL_MT_FIFO_RD_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_RD_CNT]);
    printf("RSIM_CTRL_MT_FIFO_WR_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_WR_CNT]);

    printf("--- Send a packet --- \r\n");

    /* Send a packet */
    Status = SendPacket(&AxiDma);
    if (Status != XST_SUCCESS) {
        return XST_FAILURE;
    }

    printf("--- RSIM Status  --- \r\n");

    printf("RSIM_CTRL_ENABLED %d\r\n", ctrl_regs[RSIM_CTRL_ENABLED]);
    printf("RSIM_CTRL_CALIBRATED %d\r\n", ctrl_regs[RSIM_CTRL_CALIBRATED]);
    printf("RSIM_CTRL_ARP_US %d\r\n", ctrl_regs[RSIM_CTRL_ARP_US]);
    printf("RSIM_CTRL_ACP_CNT %d\r\n", ctrl_regs[RSIM_CTRL_ACP_CNT]);
    printf("RSIM_CTRL_TRIG_US %d\r\n", ctrl_regs[RSIM_CTRL_TRIG_US]);
    printf("RSIM_CTRL_ACP_IDX %d\r\n", ctrl_regs[RSIM_CTRL_ACP_IDX]);
    printf("RSIM_CTRL_FT_FIFO_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_CNT]);
    printf("RSIM_CTRL_FT_FIFO_RD_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_RD_CNT]);
    printf("RSIM_CTRL_FT_FIFO_WR_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_WR_CNT]);
    printf("RSIM_CTRL_MT_FIFO_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_CNT]);
    printf("RSIM_CTRL_MT_FIFO_RD_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_RD_CNT]);
    printf("RSIM_CTRL_MT_FIFO_WR_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_WR_CNT]);

    ctrl_regs[RSIM_CTRL_ENABLED] = 1;
    printf("RSIM_CTRL_ENABLED %d\r\n", ctrl_regs[RSIM_CTRL_ENABLED]);

    /* Check DMA transfer result */
    Status = CheckDmaResult(&AxiDma);
    if (Status != XST_SUCCESS) {
        return XST_FAILURE;
    }
    printf("AXI DMA SG Polling Test %s\r\n", (Status == XST_SUCCESS) ? "passed" : "failed");

    do {

        printf("--- RSIM Status  --- \r\n");

        printf("RSIM_CTRL_ENABLED %d\r\n", ctrl_regs[RSIM_CTRL_ENABLED]);
        printf("RSIM_CTRL_CALIBRATED %d\r\n", ctrl_regs[RSIM_CTRL_CALIBRATED]);
        printf("RSIM_CTRL_ARP_US %d\r\n", ctrl_regs[RSIM_CTRL_ARP_US]);
        printf("RSIM_CTRL_ACP_CNT %d\r\n", ctrl_regs[RSIM_CTRL_ACP_CNT]);
        printf("RSIM_CTRL_TRIG_US %d\r\n", ctrl_regs[RSIM_CTRL_TRIG_US]);
        printf("RSIM_CTRL_ACP_IDX %d\r\n", ctrl_regs[RSIM_CTRL_ACP_IDX]);
        printf("RSIM_CTRL_FT_FIFO_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_CNT]);
        printf("RSIM_CTRL_FT_FIFO_RD_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_RD_CNT]);
        printf("RSIM_CTRL_FT_FIFO_WR_CNT %d\r\n", ctrl_regs[RSIM_CTRL_FT_FIFO_WR_CNT]);
        printf("RSIM_CTRL_MT_FIFO_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_CNT]);
        printf("RSIM_CTRL_MT_FIFO_RD_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_RD_CNT]);
        printf("RSIM_CTRL_MT_FIFO_WR_CNT %d\r\n", ctrl_regs[RSIM_CTRL_MT_FIFO_WR_CNT]);

        PrintDmaStatus(&AxiDma);

        sleep(10);

    } while (XAxiDma_Running(&AxiDma) == 1);

    ctrl_regs[RSIM_CTRL_ENABLED] = 0;
    printf("RSIM_CTRL_ENABLED %d\r\n", ctrl_regs[RSIM_CTRL_ENABLED]);

    printf("--- Exiting main() --- \r\n");

    return XST_SUCCESS;
}

/*****************************************************************************/
/**
 *
 * This function sets up the TX channel of a DMA engine to be ready for packet
 * transmission
 *
 * @param    AxiDmaInstPtr is the instance pointer to the DMA engine.
 *
 * @return   XST_SUCCESS if the setup is successful, XST_FAILURE otherwise.
 *
 * @note     None.
 *
 ******************************************************************************/
static int TxSetup(XAxiDma * AxiDmaInstPtr, int devMemHandle) {
    XAxiDma_BdRing *TxRingPtr;
    XAxiDma_Bd BdTemplate;
    int Delay = 0;
    int Coalesce = 1;
    int Status;
    u32 BdCount;

    TxRingPtr = XAxiDma_GetTxRing(&AxiDma);

    /* Disable all TX interrupts before TxBD space setup */
    XAxiDma_BdRingIntDisable(TxRingPtr, XAXIDMA_IRQ_ALL_MASK);

    /* Set TX delay and coalesce */
    XAxiDma_BdRingSetCoalesce(TxRingPtr, Coalesce, Delay);

    /* Setup TxBD space  */
    BdCount = XAxiDma_BdRingCntCalc(XAXIDMA_BD_MINIMUM_ALIGNMENT, TX_BD_SPACE_HIGH - TX_BD_SPACE_BASE + 1);

    Status = XAxiDma_BdRingCreate(TxRingPtr,
    TX_BD_SPACE_BASE, (UINTPTR) mmap(NULL, TX_BD_SPACE_HIGH - TX_BD_SPACE_BASE + 1, PROT_READ | PROT_WRITE, MAP_SHARED, devMemHandle, TX_BD_SPACE_BASE),
    XAXIDMA_BD_MINIMUM_ALIGNMENT, BdCount);
    if (Status != XST_SUCCESS) {
        printf("failed create BD ring in txsetup\r\n");

        return XST_FAILURE;
    }

    /*
     * We create an all-zero BD as the template.
     */
    XAxiDma_BdClear(&BdTemplate);

    Status = XAxiDma_BdRingClone(TxRingPtr, &BdTemplate);
    if (Status != XST_SUCCESS) {
        printf("failed bdring clone in txsetup %d\r\n", Status);

        return XST_FAILURE;
    }

    /* Start the TX channel */
    Status = XAxiDma_BdRingStart(TxRingPtr);
    if (Status != XST_SUCCESS) {
        printf("failed start bdring txsetup %d\r\n", Status);

        return XST_FAILURE;
    }

    return XST_SUCCESS;
}

/*****************************************************************************/
/**
 *
 * This function transmits one packet non-blockingly through the DMA engine.
 *
 * @param    AxiDmaInstPtr points to the DMA engine instance
 *
 * @return   - XST_SUCCESS if the DMA accepts the packet successfully,
 *       - XST_FAILURE otherwise.
 *
 * @note     None.
 *
 ******************************************************************************/
static int SendPacket(XAxiDma * AxiDmaInstPtr) {
    XAxiDma_BdRing *TxRingPtr;
    u8 *TxPacket;
    XAxiDma_Bd *FirstBdPtr;
    XAxiDma_Bd *SecondBdPtr;
    XAxiDma_Bd *ThirdBdPtr;
    int Status;
    int Index;

    TxRingPtr = XAxiDma_GetTxRing(AxiDmaInstPtr);

    TxPacket = (u8 *) TX_BUFFER_BASE;

    /* Flush the SrcBuffer before the DMA transfer, in case the Data Cache
     * is enabled
     */
    //Xil_DCacheFlushRange((UINTPTR) TxPacket, MAX_PKT_U32_LEN);
    /* Allocate a couple of BD */
    Status = XAxiDma_BdRingAlloc(TxRingPtr, 3, &FirstBdPtr);
    if (Status != XST_SUCCESS) {
        return XST_FAILURE;
    }

    // get the other two descriptors
    SecondBdPtr = (XAxiDma_Bd *) ((void *) XAxiDma_BdRingNext(TxRingPtr, FirstBdPtr));
    ThirdBdPtr = (XAxiDma_Bd *) ((void *) XAxiDma_BdRingNext(TxRingPtr, SecondBdPtr));

    // set up cyclic mode
    XAxiDma_BdSetNext(ThirdBdPtr, FirstBdPtr, TxRingPtr);

    /* Set up the BD using the information of the packet to transmit */
    Status = XAxiDma_BdSetBufAddr(FirstBdPtr, (UINTPTR) TxPacket);
    if (Status != XST_SUCCESS) {
        printf("Tx set buffer addr %x on BD %x failed %d\r\n", (UINTPTR) TxPacket, (UINTPTR) FirstBdPtr, Status);

        return XST_FAILURE;
    }

    Status = XAxiDma_BdSetBufAddr(SecondBdPtr, (UINTPTR) TxPacket);
    if (Status != XST_SUCCESS) {
        printf("Tx set buffer addr %x on BD %x failed %d\r\n", (UINTPTR) TxPacket, (UINTPTR) SecondBdPtr, Status);

        return XST_FAILURE;
    }

    Status = XAxiDma_BdSetBufAddr(ThirdBdPtr, (UINTPTR) TxPacket);
    if (Status != XST_SUCCESS) {
        printf("Tx set buffer addr %x on BD %x failed %d\r\n", (UINTPTR) TxPacket, (UINTPTR) ThirdBdPtr, Status);

        return XST_FAILURE;
    }

    Status = XAxiDma_BdSetLength(FirstBdPtr, MAX_PKT_U32_LEN, TxRingPtr->MaxTransferLen);
    if (Status != XST_SUCCESS) {
        printf("Tx set length %d on BD %x failed %d\r\n",
        MAX_PKT_U32_LEN, (UINTPTR) FirstBdPtr, Status);

        return XST_FAILURE;
    }

    Status = XAxiDma_BdSetLength(SecondBdPtr, MAX_PKT_U32_LEN, TxRingPtr->MaxTransferLen);
    if (Status != XST_SUCCESS) {
        printf("Tx set length %d on BD %x failed %d\r\n",
        MAX_PKT_U32_LEN, (UINTPTR) SecondBdPtr, Status);

        return XST_FAILURE;
    }

    Status = XAxiDma_BdSetLength(ThirdBdPtr, MAX_PKT_U32_LEN, TxRingPtr->MaxTransferLen);
    if (Status != XST_SUCCESS) {
        printf("Tx set length %d on BD %x failed %d\r\n",
        MAX_PKT_U32_LEN, (UINTPTR) ThirdBdPtr, Status);

        return XST_FAILURE;
    }

    /* For set SOF/EOF on first and last BD
     */
    XAxiDma_BdSetCtrl(FirstBdPtr, XAXIDMA_BD_CTRL_TXSOF_MASK);
    XAxiDma_BdSetCtrl(ThirdBdPtr, XAXIDMA_BD_CTRL_TXEOF_MASK);

    XAxiDma_BdSetId(FirstBdPtr, (UINTPTR ) TX_BUFFER_BASE);
    XAxiDma_BdSetId(SecondBdPtr, (UINTPTR ) TX_BUFFER_BASE);
    XAxiDma_BdSetId(ThirdBdPtr, (UINTPTR ) TX_BUFFER_BASE);

    XAxiDma_DumpBd(FirstBdPtr, TxRingPtr);
    XAxiDma_DumpBd(SecondBdPtr, TxRingPtr);
    XAxiDma_DumpBd(ThirdBdPtr, TxRingPtr);
    PrintDmaStatus(AxiDmaInstPtr);

    /* Give the BD to DMA to kick off the transmission. */
    Status = XAxiDma_BdRingToHw(TxRingPtr, 3, FirstBdPtr);
    if (Status != XST_SUCCESS) {
        printf("to hw failed %d\r\n", Status);
        return XST_FAILURE;
    }

    PrintDmaStatus(AxiDmaInstPtr);

    return XST_SUCCESS;
}
/*****************************************************************************/
/**
 *
 * This function waits until the DMA transaction is finished, checks data,
 * and cleans up.
 *
 * @param    None
 *
 * @return   - XST_SUCCESS if DMA transfer is successful and data is correct,
 *       - XST_FAILURE if fails.
 *
 * @note     None.
 *
 ******************************************************************************/
static int CheckDmaResult(XAxiDma * AxiDmaInstPtr) {
    XAxiDma_BdRing *TxRingPtr;
    XAxiDma_Bd *BdPtr;
    int ProcessedBdCount;
    int Status;

    TxRingPtr = XAxiDma_GetTxRing(AxiDmaInstPtr);

    /* Wait until the one BD TX transaction is done */
    while ((ProcessedBdCount = XAxiDma_BdRingFromHw(TxRingPtr,
    XAXIDMA_ALL_BDS, &BdPtr)) == 0) {
    }

    /* Free all processed TX BDs for future transmission */
    Status = XAxiDma_BdRingFree(TxRingPtr, ProcessedBdCount, BdPtr);
    if (Status != XST_SUCCESS) {
        printf("Failed to free %d tx BDs %d\r\n", ProcessedBdCount, Status);
        return XST_FAILURE;
    }

    return XST_SUCCESS;
}

