# ZedBoard Pin Assignments

############################
# JB PMOD                 #
############################
# JB1
set_property PACKAGE_PIN W12 [get_ports RADAR_ARP]
# JB2
set_property PACKAGE_PIN W11 [get_ports RADAR_ACP]
# JB3
set_property PACKAGE_PIN V10 [get_ports RADAR_TRIG]
# JB7
set_property PACKAGE_PIN V12 [get_ports SIM_FT_SIG]
# JB8
set_property PACKAGE_PIN W10 [get_ports SIM_MT_SIG]
# JB9
set_property PACKAGE_PIN V9 [get_ports TEST_FT_SIG]
# JB10
set_property PACKAGE_PIN V8 [get_ports TEST_MT_SIG]

set_property IOSTANDARD LVCMOS33 [get_ports RADAR_TRIG]
set_property IOSTANDARD LVCMOS33 [get_ports SIM_FT_SIG]
set_property IOSTANDARD LVCMOS33 [get_ports SIM_MT_SIG]
set_property IOSTANDARD LVCMOS33 [get_ports RADAR_ACP]
set_property IOSTANDARD LVCMOS33 [get_ports RADAR_ARP]
set_property IOSTANDARD LVCMOS33 [get_ports TEST_FT_SIG]
set_property IOSTANDARD LVCMOS33 [get_ports TEST_MT_SIG]

############################
# On-board LED             #
############################
set_property PACKAGE_PIN T22 [get_ports {LEDS[0]}]
set_property IOSTANDARD LVCMOS33 [get_ports {LEDS[0]}]
set_property PACKAGE_PIN T21 [get_ports {LEDS[1]}]
set_property IOSTANDARD LVCMOS33 [get_ports {LEDS[1]}]
set_property PACKAGE_PIN U22 [get_ports {LEDS[2]}]
set_property IOSTANDARD LVCMOS33 [get_ports {LEDS[2]}]
set_property PACKAGE_PIN U21 [get_ports {LEDS[3]}]
set_property IOSTANDARD LVCMOS33 [get_ports {LEDS[3]}]
set_property PACKAGE_PIN V22 [get_ports {LEDS[4]}]
set_property IOSTANDARD LVCMOS33 [get_ports {LEDS[4]}]
set_property PACKAGE_PIN W22 [get_ports {LEDS[5]}]
set_property IOSTANDARD LVCMOS33 [get_ports {LEDS[5]}]
set_property PACKAGE_PIN U19 [get_ports {LEDS[6]}]
set_property IOSTANDARD LVCMOS33 [get_ports {LEDS[6]}]
set_property PACKAGE_PIN U14 [get_ports {LEDS[7]}]
set_property IOSTANDARD LVCMOS33 [get_ports {LEDS[7]}]



############################
# On-board Slide Switches  #
############################
set_property PACKAGE_PIN M15 [get_ports SW_7]
set_property IOSTANDARD LVCMOS33 [get_ports SW_7]



############################
# DEBUG                    #
############################

create_debug_core u_ila_0 ila
set_property ALL_PROBE_SAME_MU true [get_debug_cores u_ila_0]
set_property ALL_PROBE_SAME_MU_CNT 1 [get_debug_cores u_ila_0]
set_property C_ADV_TRIGGER false [get_debug_cores u_ila_0]
set_property C_DATA_DEPTH 1024 [get_debug_cores u_ila_0]
set_property C_EN_STRG_QUAL false [get_debug_cores u_ila_0]
set_property C_INPUT_PIPE_STAGES 0 [get_debug_cores u_ila_0]
set_property C_TRIGIN_EN false [get_debug_cores u_ila_0]
set_property C_TRIGOUT_EN false [get_debug_cores u_ila_0]
set_property port_width 1 [get_debug_ports u_ila_0/clk]
connect_debug_port u_ila_0/clk [get_nets [list design_1_i/processing_system7_0/inst/FCLK_CLK0]]
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe0]
set_property port_width 32 [get_debug_ports u_ila_0/probe0]
connect_debug_port u_ila_0/probe0 [get_nets [list {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[0]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[1]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[2]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[3]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[4]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[5]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[6]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[7]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[8]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[9]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[10]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[11]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[12]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[13]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[14]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[15]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[16]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[17]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[18]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[19]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[20]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[21]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[22]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[23]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[24]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[25]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[26]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[27]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[28]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[29]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[30]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP_US[31]}]]
create_debug_port u_ila_0 probe
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe1]
set_property port_width 32 [get_debug_ports u_ila_0/probe1]
connect_debug_port u_ila_0/probe1 [get_nets [list {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[0]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[1]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[2]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[3]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[4]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[5]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[6]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[7]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[8]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[9]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[10]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[11]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[12]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[13]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[14]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[15]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[16]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[17]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[18]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[19]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[20]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[21]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[22]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[23]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[24]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[25]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[26]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[27]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[28]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[29]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[30]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP_CNT[31]}]]
create_debug_port u_ila_0 probe
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe2]
set_property port_width 32 [get_debug_ports u_ila_0/probe2]
connect_debug_port u_ila_0/probe2 [get_nets [list {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[0]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[1]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[2]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[3]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[4]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[5]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[6]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[7]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[8]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[9]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[10]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[11]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[12]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[13]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[14]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[15]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[16]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[17]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[18]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[19]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[20]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[21]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[22]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[23]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[24]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[25]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[26]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[27]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[28]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[29]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[30]} {design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG_US[31]}]]
create_debug_port u_ila_0 probe
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe3]
set_property port_width 1 [get_debug_ports u_ila_0/probe3]
connect_debug_port u_ila_0/probe3 [get_nets [list design_1_i/radar_simulator_1/radar_statistics_0/inst/ACP]]
create_debug_port u_ila_0 probe
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe4]
set_property port_width 1 [get_debug_ports u_ila_0/probe4]
connect_debug_port u_ila_0/probe4 [get_nets [list design_1_i/radar_simulator_1/radar_statistics_0/inst/ARP]]
create_debug_port u_ila_0 probe
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe5]
set_property port_width 1 [get_debug_ports u_ila_0/probe5]
connect_debug_port u_ila_0/probe5 [get_nets [list design_1_i/radar_signal_selector/trig_mux/inst/M_SEL]]
create_debug_port u_ila_0 probe
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe6]
set_property port_width 1 [get_debug_ports u_ila_0/probe6]
connect_debug_port u_ila_0/probe6 [get_nets [list design_1_i/radar_signal_selector/acp_mux/inst/M_SEL]]
create_debug_port u_ila_0 probe
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe7]
set_property port_width 1 [get_debug_ports u_ila_0/probe7]
connect_debug_port u_ila_0/probe7 [get_nets [list design_1_i/radar_signal_selector/arp_mux/inst/M_SEL]]
create_debug_port u_ila_0 probe
set_property PROBE_TYPE DATA [get_debug_ports u_ila_0/probe8]
set_property port_width 1 [get_debug_ports u_ila_0/probe8]
connect_debug_port u_ila_0/probe8 [get_nets [list design_1_i/radar_simulator_1/radar_statistics_0/inst/TRIG]]
set_property C_CLK_INPUT_FREQ_HZ 300000000 [get_debug_cores dbg_hub]
set_property C_ENABLE_CLK_DIVIDER false [get_debug_cores dbg_hub]
set_property C_USER_SCAN_CHAIN 1 [get_debug_cores dbg_hub]
connect_debug_port dbg_hub/clk [get_nets u_ila_0_FCLK_CLK0]
