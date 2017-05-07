#
# This file is the radar-sim-test recipe.
#

SUMMARY = "Simple radar-sim-test application"
SECTION = "PETALINUX/apps"
LICENSE = "APACHE"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"
DEPENDS = "bzip2 zlib boost thrift"

SRC_URI = "file://xilinx \
           file://inc \
           file://radar_simulator.hpp \
           file://radar_simulator.cpp \
           file://main.cpp \
           file://Makefile \
		  "

S = "${WORKDIR}"

do_compile() {
	     oe_runmake
}

do_install() {
	     install -d ${D}${bindir}
	     install -m 0755 radar-sim-test ${D}${bindir}
}
