`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: 
// Engineer: 
// 
// Create Date: 01/07/2017 08:59:22 AM
// Design Name: 
// Module Name: mux_sim
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


module mux_sim();

    // inputs
    reg SEL;
    reg IN_0;
    reg IN_1;
    
    // outputs
    wire OUT;
    
    // dut
    mux_2_1 dut (
        .M_SEL(SEL),
        .M_IN_0(IN_0),
        .M_IN_1(IN_1),
        .M_OUT(OUT)
    );
    
    initial begin
        IN_0 <= 0;
        IN_1 <= 0;
        SEL <= 0;
        #100 IN_1 <= 1;
        #100 IN_0 <= 1;
        #100 IN_1 <= 0;
        #100 IN_0 <= 0;
        
        #300 SEL <= 1;
        #100 IN_1 <= 1;
        #100 IN_0 <= 1;
        #100 IN_1 <= 0;
        #100 IN_0 <= 0;
         
        
    end
    
endmodule
