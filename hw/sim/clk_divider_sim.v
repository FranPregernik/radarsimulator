`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 12/29/2016 07:30:24 PM
// Design Name: 
// Module Name: clk_divider_sim
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


module clk_divider_sim;

    // Inputs
    reg in_clk;
 
    // Outputs
    wire out_clk;
 
    // Instantiate the Unit Under Test (UUT)
    clk_divider #(15) uut (
        .IN_SIG(in_clk),
        .OUT_SIG(out_clk)
    );
 
    initial
    begin
        in_clk = 0;
        forever
            #50 in_clk = ~in_clk;
    end
 
endmodule
