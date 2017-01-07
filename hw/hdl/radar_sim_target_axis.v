`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
//
// Create Date: 12/29/2016 08:04:28 PM
// Design Name:
// Module Name: radar_sim_target_axis
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

module radar_sim_target_axis #
	(
		// Users to add parameters here

		// User parameters ends
		// Do not modify the parameters beyond this line

		// AXI4Stream sink: Data Width
		parameter integer C_S_AXIS_TDATA_WIDTH	= 3200
	)
	(
		// Users to add ports here

		// is the simulator enabled
		(* X_INTERFACE_PARAMETER = "POLARITY ACTIVE_HIGH" *)
		input SIM_EN,

		// radar antenna angle change
		input ACP,

		// radar antenna transmission start
		input TRIG,

		// time based signal that specifies a target is present or not
        output GEN_SIGNAL,

		// User ports ends
		// Do not modify the ports beyond this line

		// AXI4Stream sink: Clock
		input wire  S_AXIS_ACLK,
		// AXI4Stream sink: Reset
		input wire  S_AXIS_ARESETN,
		// Ready to accept data in
		output wire  S_AXIS_TREADY,
		// Data in
		input wire [C_S_AXIS_TDATA_WIDTH-1 : 0] S_AXIS_TDATA,
		// Indicates boundary of last packet
		input wire  S_AXIS_TLAST,
		// Data is in valid
		input wire  S_AXIS_TVALID
	);
	
	// temporary register to store the current azimuth radar response data
	reg [C_S_AXIS_TDATA_WIDTH-1:0] bank;

    wire USEC;
    clk_divider #(100) cd(
        .IN_SIG(S_AXIS_ACLK),
        .OUT_SIG(USEC)
    );

    // create synchronous ACP posedge signal
    wire acp_posedge;
    edge_detect acp_ed(
       .async_sig(ACP),
       .clk(S_AXIS_ACLK),
       .rise(acp_posedge)
    );

    // initialize the radar signal response generator
    azimuth_signal_generator #(C_S_AXIS_TDATA_WIDTH) asg(
        .EN(SIM_EN),
        .TRIG(TRIG),
        .DATA(bank),
        .CLK(USEC),
        .SYS_CLK(S_AXIS_ACLK),
        
        .GEN_SIGNAL(GEN_SIGNAL)
    );

    // ready to receive if the simulation is enabled and the next ACP happens
	assign S_AXIS_TREADY = SIM_EN && acp_posedge;
	
    always @(posedge S_AXIS_ACLK) begin  
        if (!S_AXIS_ARESETN) begin
            // Synchronous reset (active low)
            bank <= 0;
	    end else if (acp_posedge) begin
            // on next ACP load fresh data
            bank <= S_AXIS_TDATA;
       end
	end

endmodule
