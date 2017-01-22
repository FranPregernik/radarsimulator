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
        
        // constant microseconds clock
        input wire USEC,
        
        // Declare the attributes above the port declaration
        (* X_INTERFACE_INFO = "xilinx.com:signal:clock:1.0 SYS_CLK CLK" *)
        // Supported parameters: ASSOCIATED_CLKEN, ASSOCIATED_RESET, ASSOCIATED_ASYNC_RESET, ASSOCIATED_BUSIF, CLK_DOMAIN, PHASE, FREQ_HZ
        // Output clocks will require FREQ_HZ to be set (note the value is in HZ and an integer is expected).
        (* X_INTERFACE_PARAMETER = "FREQ_HZ 100000000" *)
        input wire SYS_CLK,

        output wire GEN_SIGNAL
    );
    
    reg EN = 1;
    reg [SIZE-1:0] DATA = 0;
    
    always @* begin
        // 3 microsecond pulses
        DATA[102:100] <= 3'b111;
        DATA[502:500] <= 3'b111;
        DATA[902:900] <= 3'b111;
        DATA[1302:1300] <= 3'b111;
        DATA[1702:1700] <= 3'b111;
        DATA[2102:2100] <= 3'b111;
        DATA[2502:2500] <= 3'b111;
        DATA[2902:2900] <= 3'b111;
    end

    azimuth_signal_generator #(SIZE) asg (
        .EN(EN),
        .TRIG(TRIG),
        .DATA(DATA),
        .CLK(USEC),
        .SYS_CLK(SYS_CLK),
        .GEN_SIGNAL(GEN_SIGNAL)
    );
    
endmodule
