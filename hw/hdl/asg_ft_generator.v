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


module asg_ft_generator #
    (
        parameter SIZE = 3200
    )
    (
        input wire TRIG,

        // Declare the attributes above the port declaration
        (* X_INTERFACE_INFO = "xilinx.com:signal:clock:1.0 SYS_CLK CLK" *)
        // Supported parameters: ASSOCIATED_CLKEN, ASSOCIATED_RESET, ASSOCIATED_ASYNC_RESET, ASSOCIATED_BUSIF, CLK_DOMAIN, PHASE, FREQ_HZ
        // Output clocks will require FREQ_HZ to be set (note the value is in HZ and an integer is expected).
        (* X_INTERFACE_PARAMETER = "FREQ_HZ 100000000" *)
        input wire SYS_CLK,

        output wire GEN_SIGNAL
    );

    reg EN = 1;
    reg [SIZE-1:0] DATA = (1<<300) + (1<<700) + (1<<1100) + (1<<1500) + (1<<1900) + (1<<2300) + (1<<2700);

    wire USEC;
    clk_divider #(100) cd(
        .IN_SIG(SYS_CLK),
        .OUT_SIG(USEC)
    );

    azimuth_signal_generator #(SIZE) asg (
        .EN(EN),
        .TRIG(TRIG),
        .DATA(DATA),
        .CLK(USEC),
        .SYS_CLK(USEC),
        .GEN_SIGNAL(GEN_SIGNAL)
    );
    
endmodule
