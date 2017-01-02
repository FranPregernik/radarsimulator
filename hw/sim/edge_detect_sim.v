`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: 
// Engineer: 
// 
// Create Date: 01/02/2017 11:05:01 PM
// Design Name: 
// Module Name: edge_detect_sim
// Project Name: 
// Target Devices: 
// Tool Versions: 
// Description: 
// 
// Dependencies: 
// 
// Revision:
// Revision 0.01 - File Created
// Additional Comments: from http://www.doulos.com/knowhow/fpga/synchronisation/downloads/edge_detect.v
// 
//////////////////////////////////////////////////////////////////////////////////


module edge_detect_sim;

    reg clk, async;
    wire rise, fall;

    edge_detect uut (
        .async_sig(async),
        .clk(clk),
        .rise(rise),
        .fall(fall)
    );

    initial begin
      clk = 0;
      async = 0;
      while ($time < 1000) begin
        clk <= ~clk;
        #5; // ns
      end
    end

    // Produce a randomly-changing async signal.

    integer seed;
    time delay;

    initial
    begin
        while ($time < 1000) begin
            @(negedge clk);
            // wait for a random number of ns
            delay = $dist_uniform(seed, 50, 100);
            #delay;
            async = ~ async;
        end
    end

endmodule

