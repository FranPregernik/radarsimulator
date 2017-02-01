`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
//
// Create Date: 01/04/2017 09:47:07 PM
// Design Name:
// Module Name: mux_2_1
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


module mux_2_1(

        (* X_INTERFACE_PARAMETER = "POLARITY ACTIVE_HIGH" *)
        input M_SEL,

        input M_IN_0,

        input M_IN_1,

        output M_OUT
    );
    assign M_OUT = M_SEL ? M_IN_1 : M_IN_0;
endmodule
