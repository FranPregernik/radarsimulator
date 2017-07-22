namespace java hr.franp.rsim
namespace cpp hr.franp.rsim

struct SimState {
    1: i32 time;
    3: bool enabled;
    4: bool mtiEnabled;
    5: bool normEnabled;
    6: bool calibrated;
    7: i32 arpUs;
    8: i32 acpCnt;
    9: i32 trigUs;
    10: i32 simAcpIdx;
    11: i32 currAcpIdx;
    12: i32 loadedClutterAcpIndex;
    13: i32 loadedTargetAcpIndex;
    14: i32 loadedClutterAcp;
    15: i32 loadedTargetAcp;
}

enum SubSystem {
    CLUTTER,       // 1
    MOVING_TARGET
}

exception RadarSignalNotCalibratedException {}

exception IncompatibleFileException {
    1: SubSystem subSystem;
}

exception DmaNotInitializedException {
    1: SubSystem subSystem;
}

service Simulator {

    /**
     * Resets the DMA and simulator hardware.
     **/
    void reset();

    /**
     * Ensure the HW is calibrated with the clock signals.
     **/
    void calibrate();

    /**
     * Enables the simulator output.
     **/
    void enable() throws (1: RadarSignalNotCalibratedException rsnc, 2: DmaNotInitializedException d);

    /**
     * Enables the MTI simulator output.
     **/
    void enableMti();

    /**
     * Enables the NORM simulator output.
     **/
    void enableNorm();

    /**
     * Disables the simulator output.
     **/
    void disable();

    /**
     * Disables the MTI simulator output.
     **/
    void disableMti();

    /**
     * Disables the NORM simulator output.
     **/
    void disableNorm();

    /**
     * Loads the clutter and target map data from the common location.
     **/
    void loadMap(1: i32 arpPosition) throws (1: IncompatibleFileException rsnc);

    /**
     * Returns the state of the simulator.
     **/
    SimState getState();

}