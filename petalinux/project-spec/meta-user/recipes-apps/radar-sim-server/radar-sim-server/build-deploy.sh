#!/usr/bin/env bash

petalinux-build -c radar-sim-server -x do_install
scp ../../../../../build/tmp/work/cortexa9hf-neon-xilinx-linux-gnueabi/radar-sim-server/1.0-r0/image/usr/bin/radar_sim_server $1

