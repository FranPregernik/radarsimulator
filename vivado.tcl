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
 "[file normalize "$origin_dir/hw/hdl/RadarStatistics.v"]"\
 "[file normalize "$origin_dir/hw/hdl/ClkDivider.v"]"\
 "[file normalize "$origin_dir/hw/hdl/radar_sim_target_axis.v"]"\
 "[file normalize "$origin_dir/hw/hdl/radar_sim_ctrl_axi.v"]"\
 "[file normalize "$origin_dir/hw/hdl/radar_sim_axi.v"]"\
 "[file normalize "$origin_dir/hw/hdl/AzimuthSignalGenerator.v"]"\
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

# Create 'sim_1' fileset (if not found)
if {[string equal [get_filesets -quiet sim_1] ""]} {
  create_fileset -simset sim_1
}

# Set 'sim_1' fileset object
set obj [get_filesets sim_1]
set files [list \
 "[file normalize "$origin_dir/hw/hdl/AzimuthSignalGenerator.v"]"\
 "[file normalize "$origin_dir/hw/hdl/RadarStatistics.v"]"\
 "[file normalize "$origin_dir/hw/hdl/ClkDivider.v"]"\
 "[file normalize "$origin_dir/hw/sim/AzimuthSignalGeneratorSim.v"]"\
 "[file normalize "$origin_dir/hw/sim/RadarStatisticsSim.v"]"\
 "[file normalize "$origin_dir/hw/sim/ClkDividerSim.v"]"\
]
add_files -norecurse -fileset $obj $files

# Create block design
source $origin_dir/hw/bd/design_1.tcl

# Generate the wrapper
set design_name [get_bd_designs]
make_wrapper -files [get_files $design_name.bd] -top -import

puts "INFO: Project created:radar_simulator"
