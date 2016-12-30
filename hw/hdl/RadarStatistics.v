`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: 
// Engineer: 
// 
// Create Date: 12/29/2016 08:04:28 PM
// Design Name: 
// Module Name: RadarStatistics
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


module RadarStatistics #
    (
        parameter DATA_WIDTH = 32
    )
    (
        input ARP,
        input ACP,
        input TRIG,
        input US_CLK,
        output CALIBRATED,
        output reg [DATA_WIDTH-1:0] ARP_US = 0,
        output reg [DATA_WIDTH-1:0] ACP_CNT = 0,
        output reg [DATA_WIDTH-1:0] TRIG_CNT = 0
    );
    
    localparam MAX = (1<<DATA_WIDTH) - 1;
    
    reg [DATA_WIDTH-1:0] arp_us_tmp = 0;
    reg [DATA_WIDTH-1:0] arp_us_prev = MAX;
    
    reg [DATA_WIDTH-1:0] acp_cnt_tmp = 0;
    reg [DATA_WIDTH-1:0] acp_cnt_prev = MAX;

    reg [DATA_WIDTH-1:0] trig_cnt_tmp = 0;
    reg [DATA_WIDTH-1:0] trig_cnt_prev = MAX;
    
    assign CALIBRATED = (ARP_US == arp_us_prev) 
        && (ACP_CNT == acp_cnt_prev) 
        && (TRIG_CNT == trig_cnt_prev);

    // keep track of us passed between
    always @(posedge US_CLK) begin
        arp_us_tmp <= arp_us_tmp + 1;            
    end
    
    always @(posedge ARP) begin
        arp_us_prev = ARP_US;
        ARP_US = arp_us_tmp;
        arp_us_tmp = 0;
    end
        
    
    // keep track of ACP counts between ARPs
    always @(posedge ACP) begin
        acp_cnt_tmp <= acp_cnt_tmp + 1;            
    end
    
    always @(posedge ARP) begin
        acp_cnt_prev = ACP_CNT;
        ACP_CNT = acp_cnt_tmp;
        acp_cnt_tmp = 0;
    end
    
    
    // keep track of trig counts between ACPs
    always @(posedge TRIG) begin
        trig_cnt_tmp <= trig_cnt_tmp + 1;            
    end
        
    always @(posedge ACP) begin
        trig_cnt_prev = TRIG_CNT;
        TRIG_CNT = trig_cnt_tmp;
        trig_cnt_tmp = 0;
    end
        
endmodule
