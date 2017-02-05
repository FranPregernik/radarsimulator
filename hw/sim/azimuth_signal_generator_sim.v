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
    
    // Inputs
    reg TRIG;
    reg EN;
    wire US_CLK;
    reg [SIZE-1:0] DATA = {HLOW, HHIGH, HLOW, HHIGH};
        
    // output
    wire GEN_SIGNAL;
    
    wire clk_rise, trig_rise;
  
    initial begin
        TRIG = 0;      
                    
        #1000 TRIG <= 1;
        #1010 TRIG <= 0;
    end
    
    initial begin
        EN = 1;        
                    
        #800 EN <= 1;
        #1500_000 EN <= 0;
    end

    reg CLK;
    parameter PERIOD = 10;
    always begin
       CLK = 1'b0;
       #(PERIOD/2) CLK = 1'b1;
       #(PERIOD/2);
    end
    
    clk_divider #(100) us_clk(
        .IN_SIG(CLK),
        .OUT_SIG(US_CLK)
    );
    
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
