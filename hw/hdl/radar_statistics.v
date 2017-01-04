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
        input ARP,

        // RADAR ACP signal - LSB of the rotation encoder
        input ACP,

        // RADAR TRIG signal - posedge signals the antenna transmission start
        input TRIG,

        // Steady 1MHz clock
        (* X_INTERFACE_PARAMETER = "FREQ_HZ 1000000" *)
        input US_CLK,

        // PL system clock
        (* X_INTERFACE_PARAMETER = "FREQ_HZ 100000000" *)
        input SYS_CLK,

        // Signals that the measurements are stable
        output CALIBRATED,

        // ARP signal period
        output reg [DATA_WIDTH-1:0] ARP_US = 0,

        // Number of ACPs between two ARPs
        output reg [DATA_WIDTH-1:0] ACP_CNT = 0,

        // TRIG signal period
        output reg [DATA_WIDTH-1:0] TRIG_US = 0
    );
    
       
    reg [DATA_WIDTH-1:0] arp_us_tmp = 0;
    reg [DATA_WIDTH-1:0] arp_us_prev = 0;
    
    reg [DATA_WIDTH-1:0] acp_cnt_tmp = 0;
    reg [DATA_WIDTH-1:0] acp_cnt_prev = 0;


    reg [DATA_WIDTH-1:0] trig_us_tmp = 0;
    reg [DATA_WIDTH-1:0] trig_us_prev = 0;

    
    assign CALIBRATED = (ARP_US == arp_us_prev) 
        && (ACP_CNT == acp_cnt_prev) 
        && (TRIG_US == trig_us_prev);


    wire arp_posedge;
    edge_detect arp_ed(
        .async_sig(ARP),
        .clk(SYS_CLK),
        .rise(arp_posedge)
    );
    
    wire acp_posedge;
    edge_detect acp_ed(
       .async_sig(ACP),
       .clk(SYS_CLK),
       .rise(acp_posedge)
    );

    wire trig_posedge;
    edge_detect trig_ed(
       .async_sig(TRIG),
       .clk(SYS_CLK),
       .rise(trig_posedge)
    );
    
    wire us_clk_posedge;
    edge_detect us_clk_ed(
       .async_sig(US_CLK),
       .clk(SYS_CLK),
       .rise(us_clk_posedge)
    );    

    // keep track of microseconds passed between ARPs
    always @(posedge SYS_CLK) begin
        if (arp_posedge) begin
            arp_us_prev = ARP_US;
            ARP_US <= arp_us_tmp;
            
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1 
            if (us_clk_posedge) begin
                arp_us_tmp <= 1;
            end else begin 
                arp_us_tmp <= 0;
            end
            
        end else if (us_clk_posedge) begin
            arp_us_tmp <= arp_us_tmp + 1;
        end
    end
        
    
    // keep track of ACP counts between ARPs
    always @(posedge SYS_CLK) begin
        if (arp_posedge) begin
            acp_cnt_prev <= ACP_CNT;
            ACP_CNT <= acp_cnt_tmp;
            
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1 
            if (acp_posedge) begin
                acp_cnt_tmp <= 1;
            end else begin 
                acp_cnt_tmp <= 0;
            end
            
        end else if (acp_posedge) begin
            acp_cnt_tmp <= acp_cnt_tmp + 1;
        end
    end
    

    // keep track of microseconds between TRIGs
    always @(posedge SYS_CLK) begin
        if (trig_posedge) begin
            trig_us_prev <= TRIG_US;
            TRIG_US <= trig_us_tmp;
            
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1 
            if (us_clk_posedge) begin
                trig_us_tmp <= 1;
            end else begin 
                trig_us_tmp <= 0;
            end
            
        end else if (us_clk_posedge) begin
            trig_us_tmp <= trig_us_tmp + 1;
        end
    end
        
endmodule
