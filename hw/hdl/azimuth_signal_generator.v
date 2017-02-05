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

    reg [SIZE-1:0] clk_mask = 0;
    
    always @(posedge SYS_CLK) begin
        if (EN) begin
            if (TRIG) begin
                clk_mask = 1;    
            end else if (CLK_PE && (clk_mask != 0)) begin
                clk_mask = clk_mask << 1;
            end
        end else begin
            clk_mask = 0;
        end
    end
       
    // generates the signal (0/1) based on the memory register for the current time (clk_idx in int_data)
    assign GEN_SIGNAL = (|(DATA & clk_mask)) && EN;

endmodule
