`timescale 1ns / 1ns
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 12/30/2016 11:00:20 AM
// Design Name: 
// Module Name: azimuth_signal_generator_sim
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


module azimuth_signal_generator_sim;

    localparam SIZE = 3200;
    localparam HSIZE = SIZE / 2;
    localparam HHIGH = {HSIZE{1'b1}};
    localparam HLOW = {HSIZE{1'b0}};
    
    // Inputs
    reg CLK;
    reg TRIG;
    reg EN;
    wire US_CLK;
    reg [SIZE-1:0] DATA;
        
    // output
    wire GEN_SIGNAL;
    
    clk_divider #(100) us_clk(
        .IN_SIG(CLK),
        .OUT_SIG(US_CLK)
    );
    
    azimuth_signal_generator #(SIZE) uut (
        .EN(EN),
        .TRIG(TRIG),
        .CLK(US_CLK),
        .SYS_CLK(CLK),
        .DATA(DATA),
        .GEN_SIGNAL(GEN_SIGNAL)
    );
    
    initial
    begin
        TRIG = 0;
        CLK = 0;
        EN = 1;
        DATA =  {HLOW, HHIGH};
        
        forever
            #10 CLK <= ~CLK;
            
        #10000 TRIG <= 1;
        #100 TRIG <= 0;
    end
    
endmodule
