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
        input ARP,

        // RADAR ACP signal - LSB of the rotation encoder
        (* MARK_DEBUG="true" *)
        input ACP,

        // RADAR TRIG signal - posedge signals the antenna transmission start
        (* MARK_DEBUG="true" *)
        input TRIG,

        // constant microseconds clock
        input wire USEC,

        // PL system clock

        input S_AXIS_ACLK,

        // Signals that the measurements are stable
        output CALIBRATED,

        // ARP signal period
        (* MARK_DEBUG="true" *)
        output reg [DATA_WIDTH-1:0] ARP_US = 0,

        // Number of ACPs between two ARPs
        (* MARK_DEBUG="true" *)
        output reg [DATA_WIDTH-1:0] ACP_CNT = 0,

        // TRIG signal period
        (* MARK_DEBUG="true" *)
        output reg [DATA_WIDTH-1:0] TRIG_US = 0
    );
    
       
    reg [DATA_WIDTH-1:0] arp_us_tmp = 0;
    reg [DATA_WIDTH-1:0] arp_us_prev = 0;
    
    reg [DATA_WIDTH-1:0] acp_cnt_tmp = 0;
    reg [DATA_WIDTH-1:0] acp_cnt_prev = 0;


    reg [DATA_WIDTH-1:0] trig_us_tmp = 0;
    reg [DATA_WIDTH-1:0] trig_us_prev = 0;


    assign CALIBRATED = (ARP_US > 0) && (ARP_US == arp_us_prev) 
        && (ACP_CNT > 0) && (ACP_CNT == acp_cnt_prev) 
        && (TRIG_US > 0) && (TRIG_US == trig_us_prev);

    // keep track of microseconds passed between ARPs
    always @(posedge S_AXIS_ACLK) begin
        if (ARP) begin
            arp_us_prev = ARP_US;
            ARP_US <= arp_us_tmp;
            
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1 
            if (USEC) begin
                arp_us_tmp <= 1;
            end else begin 
                arp_us_tmp <= 0;
            end
            
        end else if (USEC) begin
            arp_us_tmp <= arp_us_tmp + 1;
        end
    end
        
    
    // keep track of ACP counts between ARPs
    always @(posedge S_AXIS_ACLK) begin
        if (ARP) begin
            acp_cnt_prev <= ACP_CNT;
            ACP_CNT <= acp_cnt_tmp;
            
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1 
            if (ACP) begin
                acp_cnt_tmp <= 1;
            end else begin 
                acp_cnt_tmp <= 0;
            end
            
        end else if (ACP) begin
            acp_cnt_tmp <= acp_cnt_tmp + 1;
        end
    end
    

    // keep track of microseconds between TRIGs
    always @(posedge S_AXIS_ACLK) begin
        if (TRIG) begin
            trig_us_prev <= TRIG_US;
            TRIG_US <= trig_us_tmp;
            
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1 
            if (USEC) begin
                trig_us_tmp <= 1;
            end else begin 
                trig_us_tmp <= 0;
            end
            
        end else if (USEC) begin
            trig_us_tmp <= trig_us_tmp + 1;
        end
    end
        
endmodule
