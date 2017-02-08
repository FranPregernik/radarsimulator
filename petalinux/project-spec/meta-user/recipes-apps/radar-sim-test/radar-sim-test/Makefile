APP = radar-sim-test
CPPFLAGS += -std=c++11

# Add any other object files to this list below
APP_OBJS = xilinx/xaxidma_bd.o xilinx/xaxidma_bdring.o xilinx/xaxidma_g.o xilinx/xaxidma_sinit.o xilinx/xaxidma.o radar-sim-test.o

all: build

build: $(APP)

clean:
	$(RM) radar-sim-test

$(APP): $(APP_OBJS)
	$(CXX) $(LDFLAGS) -o $@ $(APP_OBJS) $(LDLIBS)
