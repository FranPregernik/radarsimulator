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
    integer i;
    integer j;
    
    // Inputs
    reg CLK;
    reg TRIG;
    reg [SIZE-1:0] DATA;
    reg EN;
        
    // output
    wire SIGNAL;
    
    azimuth_signal_generator #(SIZE) uut (
        .EN(EN),
        .TRIG(TRIG),
        .CLK(CLK),
        .DATA(DATA),
        .SIGNAL(SIGNAL)
    );
    
    initial
    begin
        TRIG = 0;
        CLK = 0;
        EN = 0;
        DATA = 0;
        j = 0;

        for (i = 0; i < SIZE/2; i = i + 1) begin
            DATA[i] = 1;
        end
            
        forever
            #500 CLK = ~CLK;
    end
    
    initial begin
        #4000000 EN = 0;
        #200 EN = 1;
        #100 TRIG = 1;
        #500 TRIG = 0;
    end; 

    initial begin
        #9000000 EN = 0;
        #100 DATA = ~DATA;
        #200 EN = 1;
        #100 TRIG = 1;
        #500 TRIG = 0;
    end;           
    
endmodule
