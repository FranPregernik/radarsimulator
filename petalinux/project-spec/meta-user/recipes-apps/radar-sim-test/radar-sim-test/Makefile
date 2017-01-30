APP = radar-sim-test
CPPFLAGS += -std=c++11

# Add any other object files to this list below
APP_OBJS = radar-sim-test.o

all: build

build: $(APP)

clean:
	$(RM) radar-sim-test

$(APP): $(APP_OBJS)
	$(CXX) $(LDFLAGS) -o $@ $(APP_OBJS) $(LDLIBS)
