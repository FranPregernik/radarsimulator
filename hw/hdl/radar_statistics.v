`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
//
// Create Date: 12/29/2016 08:04:28 PM
// Design Name:
// Module Name: radar_statistics
// Project Name:
// Target Devices:
// Tool Versions:
// Description:
//
// Dependencies:
//
// Revision:
// Revision 0.01 - File Created
// Additional Comments:
//
//////////////////////////////////////////////////////////////////////////////////

module radar_statistics #
    (
        parameter DATA_WIDTH = 32
    )
    (
        // RADAR ARP signal - posedge signals North, one turn of the antenna
        (* MARK_DEBUG="true" *)
        input RADAR_ARP_PE,

        // RADAR ACP signal - LSB of the rotation encoder
        (* MARK_DEBUG="true" *)
        input RADAR_ACP_PE,

        // RADAR TRIG signal - posedge signals the antenna transmission start
        (* MARK_DEBUG="true" *)
        input RADAR_TRIG_PE,

        // constant microseconds clock
        input wire USEC_PE,

        // PL system clock

        input S_AXIS_ACLK,

        // Signals that the measurements are stable
        output CALIBRATED,

        // ARP signal period
        (* MARK_DEBUG="true" *)
        output reg [DATA_WIDTH-1:0] RADAR_ARP_US = 0,

        // Number of ACPs between two ARPs
        (* MARK_DEBUG="true" *)
        output reg [DATA_WIDTH-1:0] RADAR_ACP_CNT = 0,

        // TRIG signal period
        (* MARK_DEBUG="true" *)
        output reg [DATA_WIDTH-1:0] RADAR_TRIG_US = 0
    );

    reg [DATA_WIDTH-1:0] arp_us_tmp = 0;
    reg [DATA_WIDTH-1:0] arp_us_prev [3:0] = {0,0,0,0};
    wire [DATA_WIDTH-1:0] arp_us_avg;

    reg [DATA_WIDTH-1:0] acp_cnt_tmp = 0;
    reg [DATA_WIDTH-1:0] acp_cnt_prev [3:0] = {0,0,0,0};
    wire [DATA_WIDTH-1:0] acp_cnt_avg;

    reg [DATA_WIDTH-1:0] trig_us_tmp = 0;
    reg [DATA_WIDTH-1:0] trig_us_prev [3:0] = {0,0,0,0};
    wire [DATA_WIDTH-1:0] trig_us_avg;

    assign arp_us_avg = (arp_us_prev[0] + arp_us_prev[1] + arp_us_prev[2] + arp_us_prev[3]) >> 2;
    assign acp_cnt_avg = (acp_cnt_prev[0] + acp_cnt_prev[1] + acp_cnt_prev[2] + acp_cnt_prev[3]) >> 2;
    assign trig_us_avg = (trig_us_prev[0] + trig_us_prev[1] + trig_us_prev[2] + trig_us_prev[3]) >> 2;

    reg arp_us_cal = 0;
    reg acp_cnt_cal = 0;
    reg trig_us_cal = 0; 
    assign CALIBRATED = arp_us_cal && acp_cnt_cal && trig_us_cal;

    always @(posedge S_AXIS_ACLK) begin
        if (CALIBRATED) begin
            RADAR_ARP_US <= arp_us_avg;
            RADAR_ACP_CNT <= acp_cnt_avg;            
            RADAR_TRIG_US <= trig_us_avg;
        end
    end

    // keep track of microseconds passed between ARPs
    always @(posedge S_AXIS_ACLK) begin
        if (RADAR_ARP_PE) begin
        
            arp_us_prev[0] <= arp_us_tmp;
            arp_us_prev[1] <= arp_us_prev[0];
            arp_us_prev[2] <= arp_us_prev[1];
            arp_us_prev[3] <= arp_us_prev[2];

            // determine if signal is stable
            if ((arp_us_tmp >= arp_us_avg - 2) && (arp_us_tmp <= arp_us_avg + 2)) begin
                arp_us_cal <= 1;
            end else begin
                arp_us_cal <= 0;
            end
            
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1
            if (USEC_PE) begin
                arp_us_tmp <= 1;
            end else begin
                arp_us_tmp <= 0;
            end

        end else if (USEC_PE) begin
            arp_us_tmp <= arp_us_tmp + 1;
        end
    end


    // keep track of ACP counts between ARPs
    always @(posedge S_AXIS_ACLK) begin
        if (RADAR_ARP_PE) begin
            
            acp_cnt_prev[0] <= acp_cnt_tmp;
            acp_cnt_prev[1] <= acp_cnt_prev[0];
            acp_cnt_prev[2] <= acp_cnt_prev[1];
            acp_cnt_prev[3] <= acp_cnt_prev[2];

            // determine if signal is stable
            if ((acp_cnt_tmp >= acp_cnt_avg - 2) && (acp_cnt_tmp <= acp_cnt_avg + 2)) begin
                acp_cnt_cal <= 1;
            end else begin
                acp_cnt_cal <= 0;
            end
                        
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1
            if (RADAR_ACP_PE) begin
                acp_cnt_tmp <= 1;
            end else begin
                acp_cnt_tmp <= 0;
            end

        end else if (RADAR_ACP_PE) begin
            acp_cnt_tmp <= acp_cnt_tmp + 1;
        end
    end


    // keep track of microseconds between TRIGs
    always @(posedge S_AXIS_ACLK) begin
        if (RADAR_TRIG_PE) begin
            
            trig_us_prev[0] <= trig_us_tmp;
            trig_us_prev[1] <= trig_us_prev[0];
            trig_us_prev[2] <= trig_us_prev[1];
            trig_us_prev[3] <= trig_us_prev[2];

            // determine if signal is stable
            if ((trig_us_tmp >= trig_us_avg - 5) && (trig_us_tmp <= trig_us_avg + 5)) begin
                trig_us_cal <= 1;
            end else begin
                trig_us_cal <= 0;
            end
                        
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1
            if (USEC_PE) begin
                trig_us_tmp <= 1;
            end else begin
                trig_us_tmp <= 0;
            end

        end else if (USEC_PE) begin
            trig_us_tmp <= trig_us_tmp + 1;
        end
    end

endmodule
