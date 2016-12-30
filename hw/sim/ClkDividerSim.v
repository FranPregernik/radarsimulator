`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 12/29/2016 07:30:24 PM
// Design Name: 
// Module Name: ClkDividerSim
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


module ClkDividerSim;

    // Inputs
    reg in_clk;
 
    // Outputs
    wire out_clk;
 
    // Instantiate the Unit Under Test (UUT)
    ClkDivider #(15) uut (
        .IN_CLK(in_clk), 
        .OUT_CLK(out_clk)
    );
 
    initial
    begin
        in_clk = 0;
        forever
        #50 in_clk = ~in_clk;
    end
 
endmodule
