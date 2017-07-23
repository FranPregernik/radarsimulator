cmake_minimum_required(VERSION 3.4)
project(radar_sim_server)

set(CMAKE_CXX_STANDARD 11)

file(GLOB_RECURSE source_list src/*.c*)

add_executable(radar_sim_server ${source_list})
target_link_libraries(radar_sim_server thrift)
target_link_libraries(radar_sim_server pthread)

install(TARGETS radar_sim_server DESTINATION bin)