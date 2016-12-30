`timescale 1 ns / 1 ps

//////////////////////////////////////////////////////////////////////////////////
// Company: franp.com
// Engineer: Fran Pregernik <fran.pregernik@gmail.com>
// 
// Create Date: 12/30/2016 10:48:02 AM
// Design Name: 
// Module Name: radar_sim_axi_v1_0
// Project Name: Radar simulator
// Target Devices: Zedboard
// Tool Versions: 
// Description: AXI wrapper for radar simulation components
// 
// Dependencies: 
// 
// Revision:
// Revision 0.01 - File Created
// Additional Comments:
// 
//////////////////////////////////////////////////////////////////////////////////

module radar_sim_axi #
	(
		// Users to add parameters here
        parameter DATA_WIDTH = 32,
        parameter ADDR_WIDTH = 6
		// User parameters ends
	)
	(
		// Users to add ports here
        input wire RADAR_ARP,
        input wire RADAR_ACP,
        input wire RADAR_TRIG,
        input wire RADAR_CLK,
        
        // status leds
        output wire [7:0] leds,
        
        // moving target simulated signal
        output reg SIM_MT_SIG,
        
        // fixed target simulated signal
        output reg SIM_FT_SIG,
        
		// User ports ends
		// Do not modify the ports beyond this line


		// Ports of Axi Slave Bus Interface S_CONTROL_AXI
		input wire  S_CTRL_AXI_ACLK,
		input wire  S_CTRL_AXI_ARESETN,
		input wire [ADDR_WIDTH-1 : 0] S_CTRL_AXI_AWADDR,
		input wire [2 : 0] S_CTRL_AXI_AWPROT,
		input wire  S_CTRL_AXI_AWVALID,
		output wire  S_CTRL_AXI_AWREADY,
		input wire [DATA_WIDTH-1 : 0] S_CTRL_AXI_WDATA,
		input wire [(DATA_WIDTH/8)-1 : 0] S_CTRL_AXI_WSTRB,
		input wire  S_CTRL_AXI_WVALID,
		output wire  S_CTRL_AXI_WREADY,
		output wire [1 : 0] S_CTRL_AXI_BRESP,
		output wire  S_CTRL_AXI_BVALID,
		input wire  S_CTRL_AXI_BREADY,
		input wire [ADDR_WIDTH-1 : 0] S_CTRL_AXI_ARADDR,
		input wire [2 : 0] S_CTRL_AXI_ARPROT,
		input wire  S_CTRL_AXI_ARVALID,
		output wire  S_CTRL_AXI_ARREADY,
		output wire [DATA_WIDTH-1 : 0] S_CTRL_AXI_RDATA,
		output wire [1 : 0] S_CTRL_AXI_RRESP,
		output wire  S_CTRL_AXI_RVALID,
		input wire  S_CTRL_AXI_RREADY,

		// Ports of Axi Slave Bus Interface S_MOVING_TARGET_AXIS
		input wire  S_MT_AXIS_ACLK,
		input wire  S_MT_AXIS_ARESETN,
		output wire  S_MT_AXIS_TREADY,
		input wire [DATA_WIDTH-1 : 0] S_MT_AXIS_TDATA,
		input wire  S_MT_AXIS_TLAST,
		input wire  S_MT_AXIS_TVALID,

		// Ports of Axi Slave Bus Interface S_FIXED_TARGET_AXIS
		input wire  S_FT_AXIS_ACLK,
		input wire  S_FT_AXIS_ARESETN,
		output wire  S_FT_AXIS_TREADY,
		input wire [DATA_WIDTH-1 : 0] S_FT_AXIS_TDATA,
		input wire  S_FT_AXIS_TLAST,
		input wire  S_FT_AXIS_TVALID
	);
    
	localparam LED_EN = 7;
	localparam LED_CAL = 6;
	localparam LED_ARP = 5;
	localparam LED_ACP = 4;
	localparam LED_TRIG = 3;
    
    // internal radar RESET signal from PS
    wire RADAR_RST;
    
    // internal radar ENABLE signal from PS
    wire RADAR_EN;
        
    // Microseconds clock derived from the 15 MHz radar clock 
    wire US_CLK;
    
    // is radar calibrated
    wire RADAR_CAL;
    
    wire [DATA_WIDTH-1:0] ARP_US;
    wire [DATA_WIDTH-1:0] ACP_CNT;
    wire [DATA_WIDTH-1:0] TRIG_CNT;
    wire [DATA_WIDTH-1:0] SWEEP_IDX;
    
    // Instantiation of Axi Bus Interface S_CTRL_AXI
	radar_sim_ctrl_axi # ( 
		.C_S_AXI_DATA_WIDTH(DATA_WIDTH),
		.C_S_AXI_ADDR_WIDTH(ADDR_WIDTH)
	) radar_sim_ctrl_axi_inst (
	
	    // RADAR SIGNALS
        .RADAR_EN(RADAR_EN),
        .RADAR_CAL(RADAR_CAL),
        .RADAR_ARP_US(ARP_US),
        .RADAR_ACP_CNT(ACP_CNT),
        .RADAR_TRIG_CNT(TRIG_CNT),
        .RADAR_SWEEP_IDX(SWEEP_IDX),
	   
        // AXI lite signals
		.S_AXI_ACLK(S_CTRL_AXI_ACLK),
		.S_AXI_ARESETN(S_CTRL_AXI_ARESETN),
		.S_AXI_AWADDR(S_CTRL_AXI_AWADDR),
		.S_AXI_AWPROT(S_CTRL_AXI_AWPROT),
		.S_AXI_AWVALID(S_CTRL_AXI_AWVALID),
		.S_AXI_AWREADY(S_CTRL_AXI_AWREADY),
		.S_AXI_WDATA(S_CTRL_AXI_AWDATA),
		.S_AXI_WSTRB(S_CTRL_AXI_WSTRB),
		.S_AXI_WVALID(S_CTRL_AXI_WVALID),
		.S_AXI_WREADY(S_CTRL_AXI_WREADY),
		.S_AXI_BRESP(S_CTRL_AXI_BRESP),
		.S_AXI_BVALID(S_CTRL_AXI_BVALID),
		.S_AXI_BREADY(S_CTRL_AXI_BREADY),
		.S_AXI_ARADDR(S_CTRL_AXI_ARADDR),
		.S_AXI_ARPROT(S_CTRL_AXI_ARPROT),
		.S_AXI_ARVALID(S_CTRL_AXI_ARVALID),
		.S_AXI_ARREADY(S_CTRL_AXI_ARREADY),
		.S_AXI_RDATA(S_CTRL_AXI_RDATA),
		.S_AXI_RRESP(S_CTRL_AXI_RRESP),
		.S_AXI_RVALID(S_CTRL_AXI_RVALID),
		.S_AXI_RREADY(S_CTRL_AXI_RREADY)
	);

    // Instantiation of Axi Bus Interface S_MT_AXIS
	radar_sim_target_axis # ( 
		.C_S_AXIS_TDATA_WIDTH(DATA_WIDTH)
	) radar_sim_mt_axis_inst (
		.S_AXIS_ACLK(S_MT_AXIS_ACLK),
		.S_AXIS_ARESETN(S_MT_AXIS_ARESETN),
		.S_AXIS_TREADY(S_MT_AXIS_TREADY),
		.S_AXIS_TDATA(S_MT_AXIS_TDATA),
		.S_AXIS_TLAST(S_MT_AXIS_TLAST),
		.S_AXIS_TVALID(S_MT_AXIS_TVALID)
	);

    // Instantiation of Axi Bus Interface S_FT_AXIS
	radar_sim_target_axis # ( 
		.C_S_AXIS_TDATA_WIDTH(DATA_WIDTH)
	) radar_sim_ft_axis_inst (
		.S_AXIS_ACLK(S_FT_AXIS_ACLK),
		.S_AXIS_ARESETN(S_FT_AXIS_ARESETN),
		.S_AXIS_TREADY(S_FT_AXIS_TREADY),
		.S_AXIS_TDATA(S_FT_AXIS_TDATA),
		.S_AXIS_TLAST(S_FT_AXIS_TLAST),
		.S_AXIS_TVALID(S_FT_AXIS_TVALID)
	);

	// Add user logic here
	
	// display LED "is simulator enabled"
	assign leds[LED_EN] = (RADAR_EN == 1);
	
	// display CALIBRATED signal on LED
	assign leds[LED_CAL] = (RADAR_CAL == 1);

	// display ARP signal on LED
	assign leds[LED_ARP] = (RADAR_ARP == 1);
	
	// display ACP signal on LED
	assign leds[LED_ACP] = (RADAR_ARP == 1);
	
    // display ACP signal on LED
    assign leds[LED_TRIG] = (RADAR_TRIG == 1);
    
    // always LOW
    assign leds[2:0] = 3'b0;
	
	// setup radar 15Mhz clock divider to get the 1Mhz microseconds clock
	ClkDivider #(15) usClk (
	   .IN_CLK(RADAR_CLK),
	   .RST(RADAR_RST),
	   .OUT_CLK(US_CLK)
	);
	
	RadarStatistics #(DATA_WIDTH) stats(
	   .ARP(RADAR_ARP),
	   .ACP(RADAR_ACP),
	   .TRIG(RADAR_TRIG),
	   .US_CLK(US_CLK),
	   .CALIBRATED(RADAR_CAL),
	   .ARP_US(ARP_US),
       .ACP_CNT(ACP_CNT),
       .TRIG_CNT(TRIG_CNT)
	);

	// User logic ends

endmodule
