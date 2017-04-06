`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: 
// Engineer: 
// 
// Create Date: 03/29/2017 06:51:41 PM
// Design Name: 
// Module Name: radar_sim_target_axis_sim
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


module radar_sim_target_axis_sim;

  reg clk, en;
 
  reg arp, acp;  
  reg [31:0] data_in;
  
  wire [31:0] data_out;
  wire [31:0] acp_idx;
  
  wire tready;
  reg tvalid;
  
  // clock
  initial begin
    en = 1'b0;
    clk = 1'b0;
    repeat(4) #10 clk = ~clk;
    en = 1'b1;
    forever #10 clk = ~clk; // generate a clock
  end
  
  reg [31:0] mem [150:0];
  initial begin
    $readmemh("memory.list", mem);
  end
  
  integer idx = 0;
  always @(posedge tready) begin 
    idx = idx + 1;
    data_in = mem[idx];
  end
  
  radar_sim_target_axis #(32, 32) uut(
    .EN(en),
    .RADAR_ARP_PE(arp),
    .RADAR_ACP_PE(acp),
    .ACP_CNT_MAX(16),
    
    .ACP_IDX(acp_idx),
    
    .BANK(data_out),
    
    .S_AXIS_ACLK(clk),
    .S_AXIS_ARESETN(1),
    .S_AXIS_TREADY(tready),
    .S_AXIS_TDATA(data_in),
    .S_AXIS_TVALID(tvalid)
  );
  
  integer index;
  
  initial begin
  
    // initial values    
    arp = 0;
    acp = 0;
    tvalid = 1;
    
    // wait for reset
    @(posedge en);
    
    // begin simulation scenario
    
    // Scenario #1 - ARP and ACP are coordinated - 15 ACP per ARP, like 
    for (index=0; index < 16; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
    arp = 1;
    @(posedge clk);
    arp = 0;
    @(posedge clk);
    for (index=0; index < 16; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
    
    // reset counters
    arp = 1;
    @(posedge clk);
    arp = 0;
        
    en = 0;
    repeat(20) @(posedge clk);
    en = 1;
      
    // Scenario #2 - ARP before 15 ACPs - rest of data should be discarded until the next index 0
    for (index=0; index < 10; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
    arp = 1;
    @(posedge clk);
    arp = 0;
    repeat(10) @(posedge clk);
    for (index=0; index < 16; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
    
    // reset counters
    arp = 1;
    @(posedge clk);
    arp = 0;
    
    en = 0;
    repeat(20) @(posedge clk);
    en = 1;
        
    // Scenario #2 - ARP after 15 ACPs - data should halt at 15 until next ARP
    for (index=0; index < 30; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
    arp = 1;
    @(posedge clk);
    arp = 0;
    repeat(10) @(posedge clk);
    for (index=0; index < 16; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
    
    arp = 1;
    @(posedge clk);
    arp = 0;
    
    en = 0;
    repeat(20) @(posedge clk);
    //en = 1;    
    
    // Scenario #3 - Half way in the ARP (7) then enable
    en = 0;
    for (index=0; index < 7; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
    en = 1;
    for (index=6; index < 16; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
    arp = 1;
    @(posedge clk);
    arp = 0;
    repeat(10) @(posedge clk);
    for (index=0; index < 16; index = index + 1) begin
        acp = 1;
        @(posedge clk);
        acp = 0;
        repeat(10) @(posedge clk);
    end
        
    en = 0;
    repeat(20) @(posedge clk);
    en = 1;
        
    repeat(20) @(posedge clk);
        
    $finish;
  end
  
  
endmodule