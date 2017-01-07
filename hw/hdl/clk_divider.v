`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 12/29/2016 07:26:09 PM
// Design Name: 
// Module Name: clk_divider
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


module clk_divider #
    (
        parameter DIVIDER = 15
    )
    (
        input IN_SIG,
        output wire OUT_SIG
    );
    
    // function called clogb2 that returns an integer which has the 
    // value of the ceiling of the log base 2.
    function integer clogb2 (input integer bit_depth);
      begin
        for(clogb2=0; bit_depth>0; clogb2=clogb2+1)
          bit_depth = bit_depth >> 1;
      end
    endfunction
    
    localparam BITS = clogb2(DIVIDER-1);
    
    // higher and out of the range of [0, DIVIDER-1]
    localparam MAX = 1 << BITS;
    
    // how many counts to keep the out clock low
    localparam integer HIGH = DIVIDER / 2; 
    
    reg [BITS:0] counter = 0;
    
    always @(posedge IN_SIG)
    begin
        counter = counter + 1;
        if (counter >= DIVIDER) begin
            counter = 0;            
        end
    end
    
    assign OUT_SIG = (counter <= HIGH);
    
endmodule
