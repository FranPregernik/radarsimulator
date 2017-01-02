`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 01/02/2017 11:01:02 PM
// Design Name: 
// Module Name: edge_detect
// Project Name: 
// Target Devices: 
// Tool Versions: 
// Description: Provides a system clock synchronized async signal edge detection
// 
// Dependencies: 
// 
// Revision:
// Revision 0.01 - File Created
// Additional Comments: source electronics.stackoverflow.com
// 
//////////////////////////////////////////////////////////////////////////////////


module edge_detect 
    (
        input async_sig,
        input clk,
        output rise,
        output fall
    );

    reg [1:0] resync;
    
    assign rise = (resync == 2'b01);
    assign fall = (resync == 2'b01);
    
    always @(posedge clk) begin
        resync <= {resync[0], async_sig};
    end

endmodule
