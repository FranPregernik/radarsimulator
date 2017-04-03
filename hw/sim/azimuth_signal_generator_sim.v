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
    localparam FSIZE = SIZE / 4;
    localparam HHIGH = {FSIZE{1'b1}};
    localparam HLOW = {FSIZE{1'b0}};
    
    reg EN;
    reg CLK;
    parameter PERIOD = 10;
    parameter TRIG_PERIOD = 30000*PERIOD;
    parameter US_PERIOD = 10*PERIOD;
    
     // clock
     initial begin
       EN = 1'b0;
       CLK = 1'b0;
       repeat(4) #PERIOD CLK = ~CLK;
       EN = 1'b1;
       forever #PERIOD CLK = ~CLK; // generate a clock
     end
     
    // Inputs
    reg TRIG;
    reg US_CLK;
    reg [SIZE-1:0] DATA = {HLOW, HHIGH, HLOW, HHIGH};
        
    // output
    wire GEN_SIGNAL;
    
    wire clk_rise, trig_rise;
  
    initial begin
        TRIG = 0;
        @(posedge EN);
        TRIG = 1;
        @(posedge CLK);
        TRIG = 0;
        forever #TRIG_PERIOD TRIG = ~TRIG;
    end

    initial begin
        US_CLK = 0;
        forever #US_PERIOD US_CLK = ~US_CLK;
    end
    
    edge_detect ed_clk (
       .async_sig(US_CLK),
       .clk(CLK),
       .rise(clk_rise)
    );
    
    edge_detect ed_trig (
       .async_sig(TRIG),
       .clk(CLK),
       .rise(trig_rise)
    );
    
    azimuth_signal_generator #(SIZE) uut (
        .EN(EN),
        .TRIG(trig_rise),
        .CLK_PE(clk_rise),
        .SYS_CLK(CLK),
        .DATA(DATA),
        .GEN_SIGNAL(GEN_SIGNAL)
    );
        
endmodule
