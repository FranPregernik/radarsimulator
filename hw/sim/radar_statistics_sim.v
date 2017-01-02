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
    
    // Inputs
    reg in_clk;
    integer i;
    
    reg arp;  
    reg acp;    
    reg trig;
    
    // Outputs
    wire [DATA_WIDTH-1:0] ARP_US;
    wire [DATA_WIDTH-1:0] ACP_CNT;
    wire [DATA_WIDTH-1:0] TRIG_CNT;
    wire CALIBRATED;
    
    // Instantiate the Unit Under Test (UUT)
    radar_statistics #(DATA_WIDTH) uut(
        .ARP(arp),
        .ACP(acp),
        .TRIG(trig),
        .US_CLK(in_clk),
        .CALIBRATED(CALIBRATED),
        .ARP_US(ARP_US),
        .ACP_CNT(ACP_CNT),
        .TRIG_CNT(TRIG_CNT)
    );
 
    initial
    begin
        i = 0;
        in_clk = 0;
        arp = 0;
        acp = 0;
        trig = 0;
        forever
        #500 in_clk = ~in_clk;
    end
    
    always @(posedge in_clk)
    begin
        if (i > 0 && i % 2_048_000 == 0)
            arp <= ~arp;

        if ((i-500) > 0 && (i-500) % 500 == 0)
            acp <= ~acp;      

        if ((i-50) > 0 && (i-50) % 50 == 0)
            trig <= ~trig;    
            
        i <= i + 1;       
    end
 
endmodule
