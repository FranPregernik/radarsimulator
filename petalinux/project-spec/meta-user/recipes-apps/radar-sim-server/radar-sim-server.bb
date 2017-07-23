#
# This file is the radar-sim-server recipe.
#

SUMMARY = "Radar simulator server"
SECTION = "PETALINUX/apps"
LICENSE = "CLOSED"

DEPENDS = "bzip2 zlib boost thrift"

SRC_URI = "file://src \
           file://CMakeLists.txt \
	"

S = "${WORKDIR}"

inherit pkgconfig cmake
