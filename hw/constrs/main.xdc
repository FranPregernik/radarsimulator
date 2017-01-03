############################
# JB1 PMOD                 #
############################
set_property PACKAGE_PIN W11 [get_ports RADAR_ACP]
set_property PACKAGE_PIN W12 [get_ports RADAR_ARP]
set_property PACKAGE_PIN V10 [get_ports RADAR_TRIG]
set_property PACKAGE_PIN W8 [get_ports RADAR_CLK]
set_property PACKAGE_PIN V9 [get_ports SIM_FT_SIG]
set_property PACKAGE_PIN V8 [get_ports SIM_MT_SIG]

set_property IOSTANDARD LVCMOS33 [get_ports RADAR_TRIG]
set_property IOSTANDARD LVCMOS33 [get_ports SIM_FT_SIG]
set_property IOSTANDARD LVCMOS33 [get_ports SIM_MT_SIG]
set_property IOSTANDARD LVCMOS33 [get_ports RADAR_CLK]
set_property IOSTANDARD LVCMOS33 [get_ports RADAR_ACP]
set_property IOSTANDARD LVCMOS33 [get_ports RADAR_ARP]

############################
# On-board led             #
############################
set_property PACKAGE_PIN T22 [get_ports LEDS[0]]
set_property IOSTANDARD LVCMOS33 [get_ports LEDS[0]]
set_property PACKAGE_PIN T21 [get_ports LEDS[1]]
set_property IOSTANDARD LVCMOS33 [get_ports LEDS[1]]
set_property PACKAGE_PIN U22 [get_ports LEDS[2]]
set_property IOSTANDARD LVCMOS33 [get_ports LEDS[2]]
set_property PACKAGE_PIN U21 [get_ports LEDS[3]]
set_property IOSTANDARD LVCMOS33 [get_ports LEDS[3]]
set_property PACKAGE_PIN V22 [get_ports LEDS[4]]
set_property IOSTANDARD LVCMOS33 [get_ports LEDS[4]]
set_property PACKAGE_PIN W22 [get_ports LEDS[5]]
set_property IOSTANDARD LVCMOS33 [get_ports LEDS[5]]
set_property PACKAGE_PIN U19 [get_ports LEDS[6]]
set_property IOSTANDARD LVCMOS33 [get_ports LEDS[6]]
set_property PACKAGE_PIN U14 [get_ports LEDS[7]]
set_property IOSTANDARD LVCMOS33 [get_ports LEDS[7]]