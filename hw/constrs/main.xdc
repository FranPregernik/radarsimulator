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

# JA1
set_property PACKAGE_PIN Y11 [get_ports ARP_OUT]
# JA2
set_property PACKAGE_PIN AA11 [get_ports ACP_OUT]
# JA3
set_property PACKAGE_PIN Y10 [get_ports TRIG_OUT]
# JA4
set_property PACKAGE_PIN AA9 [get_ports USEC_CLK]
# JA7
set_property PACKAGE_PIN AB11 [get_ports TEST_FT_SIG]
# JA8
set_property PACKAGE_PIN AB10 [get_ports TEST_MT_SIG]


set_property IOSTANDARD LVCMOS33 [get_ports RADAR_TRIG]
set_property IOSTANDARD LVCMOS33 [get_ports RADAR_ACP]
set_property IOSTANDARD LVCMOS33 [get_ports RADAR_ARP]

set_property IOSTANDARD LVCMOS33 [get_ports TRIG_OUT]
set_property IOSTANDARD LVCMOS33 [get_ports ACP_OUT]
set_property IOSTANDARD LVCMOS33 [get_ports ARP_OUT]
set_property IOSTANDARD LVCMOS33 [get_ports USEC_CLK]

set_property IOSTANDARD LVCMOS33 [get_ports SIM_FT_SIG]
set_property IOSTANDARD LVCMOS33 [get_ports SIM_MT_SIG]

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

