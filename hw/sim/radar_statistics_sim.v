`timescale 1ns / 1ns
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 12/29/2016 07:30:24 PM
// Design Name: 
// Module Name: radar_statistics_sim
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


module radar_statistics_sim;

    localparam DATA_WIDTH = 32;
    localparam DEL = 5;
    
    localparam US_RATIO = 100;
    localparam TRIG_RATIO = US_RATIO * 5;
    localparam ACP_RATIO = TRIG_RATIO * 5;
    localparam ARP_RATIO = ACP_RATIO * 5;


    
    // Inputs
    reg in_clk;
    wire us_clk;
    wire arp;  
    wire acp;    
    wire trig;
    
    // Outputs
    wire [DATA_WIDTH-1:0] ARP_US;
    wire [DATA_WIDTH-1:0] ACP_CNT;
    wire [DATA_WIDTH-1:0] TRIG_US;
    wire CALIBRATED;
    
    // Instantiate the Unit Under Test (UUT)
    radar_statistics #(DATA_WIDTH) uut(
        .ARP(arp),
        .ACP(acp),
        .TRIG(trig),
        .US_CLK(us_clk),
        .SYS_CLK(in_clk),
        .CALIBRATED(CALIBRATED),
        .ARP_US(ARP_US),
        .ACP_CNT(ACP_CNT),
        .TRIG_US(TRIG_US)
    );
    
    clk_divider #(US_RATIO) c1 (in_clk, us_clk);
    clk_divider #(ARP_RATIO) c2 (in_clk, arp);
    clk_divider #(ACP_RATIO) c3 (in_clk, acp);
    clk_divider #(TRIG_RATIO) c4 (in_clk, trig);
 
    initial
    begin
        in_clk = 0;
        forever
            #DEL in_clk = ~in_clk;
    end
 
endmodule
