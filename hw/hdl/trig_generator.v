`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 01/06/2017 05:35:32 AM
// Design Name: 
// Module Name: clk_divider_15_1_mhz
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


module trig_generator(
    
        // Declare the attributes above the port declaration
        (* X_INTERFACE_INFO = "xilinx.com:signal:clock:1.0 IN_CLK CLK" *)
        // Supported parameters: ASSOCIATED_CLKEN, ASSOCIATED_RESET, ASSOCIATED_ASYNC_RESET, ASSOCIATED_BUSIF, CLK_DOMAIN, PHASE, FREQ_HZ
        // Output clocks will require FREQ_HZ to be set (note the value is in HZ and an integer is expected).
        (* X_INTERFACE_PARAMETER = "FREQ_HZ 100000000" *)
        input IN_CLK, //  (required)

        output RADAR_TRIG //  (required)
                        
    );
    
    clk_divider #(300300) cd(
        .IN_SIG(IN_CLK),
        .OUT_SIG(RADAR_TRIG)
    ); 

endmodule
