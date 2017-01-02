`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 12/30/2016 10:48:02 AM
// Design Name: 
// Module Name: azimuth_signal_generator
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


module azimuth_signal_generator #
    (
        parameter SIZE = 3200
    )
    (
        input wire EN,
        input wire TRIG,
        input wire [SIZE-1:0] DATA,
        input wire CLK,
        
        output wire SIGNAL
    );
    
    // function called clogb2 that returns an integer which has the 
    // value of the ceiling of the log base 2.
    function integer clogb2 (input integer bit_depth);
      begin
        for(clogb2=0; bit_depth>0; clogb2=clogb2+1)
          bit_depth = bit_depth >> 1;
      end
    endfunction
        
    localparam BITS = clogb2(SIZE-1);
        
    reg [BITS-1:0] clk_idx = 0;
    
    always @(posedge TRIG) begin
        clk_idx = 0;
    end 
    
    always @(posedge CLK) begin
        if (clk_idx < SIZE) begin
                clk_idx = clk_idx + 1;
        end 
        
        if (clk_idx > SIZE) begin
            clk_idx = SIZE;
        end
    end
    
    // generates the signal (0/1) based on the memory register for the current time (clk_idx)
    assign SIGNAL = EN && (clk_idx < SIZE) && (DATA[clk_idx] == 1'b1);
    
endmodule
