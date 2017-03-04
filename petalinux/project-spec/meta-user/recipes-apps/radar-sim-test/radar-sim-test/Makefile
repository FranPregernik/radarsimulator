APP = radar-sim-test
CXXFLAGS += -std=c++11 -std=gnu++11

# Add any other object files to this list below
APP_OBJS = xilinx/xaxidma_bd.o xilinx/xaxidma_bdring.o xilinx/xaxidma_g.o xilinx/xaxidma_sinit.o xilinx/xaxidma.o radar_simulator.o main.o

all: build

build: $(APP)

clean:
	$(RM) -rf $(APP) *.gdb *.o *.elf

$(APP): $(APP_OBJS)
	$(CXX) $(LDFLAGS) -o $@ $(APP_OBJS) $(LDLIBS)
