#
# Vivado (TM) v2016.4 (64-bit)
#
#
#*****************************************************************************************

# Set the reference directory to where the script is
set origin_dir [file dirname [info script]]

# Use origin directory path location variable, if specified in the tcl shell
if { [info exists ::origin_dir_loc] } {
  set origin_dir $::origin_dir_loc
}

# Create project
create_project radar_simulator $origin_dir/vivado -part xc7z020clg484-1

# Set the directory path for the new project
set proj_dir [get_property directory [current_project]]

# Reconstruct message rules
# None

# Set project properties
set obj [get_projects radar_simulator]
set_property "board_part" "em.avnet.com:zed:part0:1.3" $obj
set_property "simulator_language" "Mixed" $obj

# Create 'sources_1' fileset (if not found)
if {[string equal [get_filesets -quiet sources_1] ""]} {
  create_fileset -srcset sources_1
}

# Rebuild user ip_repo's index before adding any source files
update_ip_catalog -rebuild

# Set 'sources_1' fileset object
set obj [get_filesets sources_1]
set files [list \
 "[file normalize "$origin_dir/hw/hdl/edge_detect.v"]"\
 "[file normalize "$origin_dir/hw/hdl/radar_statistics.v"]"\
 "[file normalize "$origin_dir/hw/hdl/clk_divider.v"]"\
 "[file normalize "$origin_dir/hw/hdl/radar_sim_target_axis.v"]"\
 "[file normalize "$origin_dir/hw/hdl/radar_sim_ctrl_axi.v"]"\
 "[file normalize "$origin_dir/hw/hdl/azimuth_signal_generator.v"]"\
 "[file normalize "$origin_dir/hw/hdl/mux_2_1.v"]"\
 "[file normalize "$origin_dir/hw/hdl/acp_generator.v"]"\
 "[file normalize "$origin_dir/hw/hdl/arp_generator.v"]"\
 "[file normalize "$origin_dir/hw/hdl/trig_generator.v"]"\
 "[file normalize "$origin_dir/hw/hdl/asg_ft_generator.v.v"]"\
 "[file normalize "$origin_dir/hw/hdl/asg_mt_generator.v.v"]"\
]
add_files -norecurse -fileset $obj $files

# Create 'constrs_1' fileset (if not found)
if {[string equal [get_filesets -quiet constrs_1] ""]} {
  create_fileset -constrset constrs_1
}

# Set 'constrs_1' fileset object
set obj [get_filesets constrs_1]

# Add/Import constrs file and set constrs file properties
set file "[file normalize "$origin_dir/hw/constrs/main.xdc"]"
set file_added [add_files -norecurse -fileset $obj $file]
set file "$origin_dir/hw/constrs/main.xdc"
set file [file normalize $file]
set file_obj [get_files -of_objects [get_filesets constrs_1] [list "*$file"]]
set_property "file_type" "XDC" $file_obj

# Set 'constrs_1' fileset properties
set obj [get_filesets constrs_1]
set_property "target_constrs_file" "$origin_dir/hw/constrs/main.xdc" $obj

# Create 'edge_detect' fileset (if not found)
if {[string equal [get_filesets -quiet edge_detect] ""]} {
  create_fileset -simset edge_detect
}

# Set 'edge_detect' fileset object
set obj [get_filesets edge_detect]
set files [list \
 "[file normalize "$origin_dir/hw/hdl/edge_detect.v"]"\
 "[file normalize "$origin_dir/hw/sim/edge_detect_sim.v"]"\
]
add_files -norecurse -fileset $obj $files
set_property top edge_detect_sim [get_filesets edge_detect]
set_property top_lib xil_defaultlib [get_filesets edge_detect]

# Create 'azimuth_signal_generator' fileset (if not found)
if {[string equal [get_filesets -quiet azimuth_signal_generator] ""]} {
  create_fileset -simset azimuth_signal_generator
}

# Set 'azimuth_signal_generator' fileset object
set obj [get_filesets azimuth_signal_generator]
set files [list \
 "[file normalize "$origin_dir/hw/hdl/azimuth_signal_generator.v"]"\
 "[file normalize "$origin_dir/hw/sim/azimuth_signal_generator_sim.v"]"\
]
add_files -norecurse -fileset $obj $files
set_property top azimuth_signal_generator_sim [get_filesets azimuth_signal_generator]
set_property top_lib xil_defaultlib [get_filesets azimuth_signal_generator]

# Create 'mux' fileset (if not found)
if {[string equal [get_filesets -quiet mux] ""]} {
  create_fileset -simset mux
}

# Set 'mux' fileset object
set obj [get_filesets mux]
set files [list \
 "[file normalize "$origin_dir/hw/hdl/mux_2_1.v"]"\
 "[file normalize "$origin_dir/hw/sim/mux_sim.v"]"\
]
add_files -norecurse -fileset $obj $files
set_property top mux_sim [get_filesets mux]
set_property top_lib xil_defaultlib [get_filesets mux]

# Create 'radar_statistics' fileset (if not found)
if {[string equal [get_filesets -quiet radar_statistics] ""]} {
  create_fileset -simset radar_statistics
}

# Set 'radar_statistics' fileset object
set obj [get_filesets radar_statistics]
set files [list \
 "[file normalize "$origin_dir/hw/hdl/radar_statistics.v"]"\
 "[file normalize "$origin_dir/hw/sim/radar_statistics_sim.v"]"\
]
add_files -norecurse -fileset $obj $files
set_property top radar_statistics_sim [get_filesets radar_statistics]
set_property top_lib xil_defaultlib [get_filesets radar_statistics]

# Create 'clk_divider' fileset (if not found)
if {[string equal [get_filesets -quiet clk_divider] ""]} {
  create_fileset -simset clk_divider
}

# Set 'clk_divider' fileset object
set obj [get_filesets clk_divider]
set files [list \
 "[file normalize "$origin_dir/hw/hdl/clk_divider.v"]"\
 "[file normalize "$origin_dir/hw/sim/clk_divider_sim.v"]"\
]
add_files -norecurse -fileset $obj $files
set_property top clk_divider_sim [get_filesets clk_divider]
set_property top_lib xil_defaultlib [get_filesets clk_divider]

# Create block design
source $origin_dir/hw/bd/design_1.tcl

# Generate the wrapper
set design_name [get_bd_designs]
make_wrapper -files [get_files $design_name.bd] -top -import
set_property top design_1_wrapper [current_fileset]

puts "INFO: Project created:radar_simulator"
