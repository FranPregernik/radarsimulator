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
        (* X_INTERFACE_PARAMETER = "POLARITY ACTIVE_HIGH" *)
        input wire EN,

        input wire TRIG,

        input wire [SIZE-1:0] DATA,

        input wire CLK_PE,

        // Declare the attributes above the port declaration
        (* X_INTERFACE_INFO = "xilinx.com:signal:clock:1.0 SYS_CLK CLK" *)
        // Supported parameters: ASSOCIATED_CLKEN, ASSOCIATED_RESET, ASSOCIATED_ASYNC_RESET, ASSOCIATED_BUSIF, CLK_DOMAIN, PHASE, FREQ_HZ
        // Output clocks will require FREQ_HZ to be set (note the value is in HZ and an integer is expected).
        (* X_INTERFACE_PARAMETER = "FREQ_HZ 100000000" *)
        input wire SYS_CLK,

        output wire GEN_SIGNAL
    );

    // function called clogb2 that returns an integer which has the
    // value of the ceiling of the log base 2.
    function integer clogb2 (input integer bit_depth);
      begin
        for(clogb2=0; bit_depth>0; clogb2=clogb2+1)
          bit_depth = bit_depth >> 1;
      end
    endfunction

    localparam BITS = clogb2(SIZE);

    reg [BITS-1:0] clk_idx = 1'bx;

    always @(posedge SYS_CLK) begin
        if (EN) begin
            if (TRIG) begin
                clk_idx = 0;
            end else if (CLK_PE && (clk_idx >= 0)) begin
                if (clk_idx < SIZE) begin
                    clk_idx = clk_idx + 1;
                end
                if (clk_idx >= SIZE) begin
                    clk_idx = SIZE;
                end
            end
        end else begin
            clk_idx = 1'bx;
        end
    end

    // generates the signal (0/1) based on the memory register for the current time (clk_idx in DATA)
    assign GEN_SIGNAL = EN && (clk_idx < SIZE) && DATA[clk_idx];

endmodule
