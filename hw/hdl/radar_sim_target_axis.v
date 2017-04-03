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

        parameter integer DATA_WIDTH = 32,

        // User parameters ends
        // Do not modify the parameters beyond this line

        // AXI4Stream sink: Data Width
        parameter integer C_S_AXIS_TDATA_WIDTH  = 3200
    )
    (
        // Users to add ports here

        // is the simulator enabled
        (* X_INTERFACE_PARAMETER = "POLARITY ACTIVE_HIGH" *)
        (* MARK_DEBUG="true" *)
        input wire EN,

        // radar antenna angle change
        (* MARK_DEBUG="true" *)
        input wire RADAR_ARP_PE,
        
        // radar antenna angle change
        (* MARK_DEBUG="true" *)
        input wire RADAR_ACP_PE,
        
        input wire [DATA_WIDTH-1:0] ACP_CNT_MAX,

        // is data stable
        output wire DATA_VALID,
               
        output reg [DATA_WIDTH-1:0] ACP_IDX = 0,
        
        output reg [DATA_WIDTH-1:0] ACP_POS = 0,
        
        output reg [C_S_AXIS_TDATA_WIDTH-1:0] BANK = 0,
        
        (* MARK_DEBUG="true" *)
        output wire DBG_READY,
        
        (* MARK_DEBUG="true" *)
        output wire DBG_VALID,
        
        (* MARK_DEBUG="true" *)
        output wire [DATA_WIDTH-1:0] DBG_ACP_CNT,

        // User ports ends
        // Do not modify the ports beyond this line

        // AXI4Stream sink: Clock
        input wire  S_AXIS_ACLK,
        // AXI4Stream sink: Reset
        input wire  S_AXIS_ARESETN,
        // Ready to accept data in
        output reg  S_AXIS_TREADY = 0,
        // Data in
        input wire [C_S_AXIS_TDATA_WIDTH-1 : 0] S_AXIS_TDATA,
        // Indicates boundary of last packet
        input wire  S_AXIS_TLAST,
        // Data is in valid
        input wire  S_AXIS_TVALID
    );

    localparam POS_BIT_CNT = 16;
    
    // SET on ARP, cleared on read of ACP data with idx 0 
    reg fast_fwd = 0;
    
    // ACP since ARP
    reg [DATA_WIDTH-1:0] acp_cnt = 0;
        
    assign DATA_VALID = EN && (acp_cnt == ACP_POS);

    assign DBG_READY = S_AXIS_TREADY;
    assign DBG_VALID = S_AXIS_TVALID;
    assign DBG_ACP_CNT = acp_cnt; 

    // keep track of ACP counts between ARPs
    always @(posedge S_AXIS_ACLK) begin
        if (RADAR_ARP_PE) begin
            // edge case handling when both signals appear at the same time
            // without this the count would be off by -1
            if (RADAR_ACP_PE) begin
                acp_cnt <= 1;
            end else begin
                acp_cnt <= 0;
            end
        end else if (RADAR_ACP_PE) begin
            if (acp_cnt < ACP_CNT_MAX - 1) begin
                acp_cnt <= acp_cnt + 1;
            end
        end
    end

    // data loading state machine
    always @(posedge S_AXIS_ACLK) begin        
    
        if (!S_AXIS_ARESETN || !EN) begin
        
            // Synchronous reset (active low)
            ACP_IDX <= 0;
            BANK <= 0;
            ACP_POS <= 0;
            fast_fwd <= 0;
            S_AXIS_TREADY <= 0;
            
        end else if (EN) begin
        
            if (RADAR_ARP_PE) begin
                // set fast forwad flag           
                fast_fwd <= 1;
            end
                           
            if (S_AXIS_TREADY && S_AXIS_TVALID) begin
            
                // load new data from AXIS bus
                ACP_IDX <= ACP_IDX + 1;
                BANK <= { S_AXIS_TDATA[C_S_AXIS_TDATA_WIDTH-1:POS_BIT_CNT], 16'b0 };
                ACP_POS <= S_AXIS_TDATA[POS_BIT_CNT-1:0];
                S_AXIS_TREADY <= 0;
    
                // hold reset flag untill we read data for IDX 0 
                if (fast_fwd && S_AXIS_TDATA[POS_BIT_CNT-1:0] == 0) begin
                    fast_fwd <= 0;
                end
           
           end else begin
           
               // ready to receive if the simulation is enabled and the next RADAR_ACP_PE happens
               S_AXIS_TREADY <= (acp_cnt != ACP_POS);

           end
           
       end
    end

endmodule
