/*
 * main.cpp
 *
 *  Created on: Feb 25, 2017
 *      Author: fpregernik
 */

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <csignal>
#include <thread>
#include <chrono>
using namespace std;

#include "inc/cxxopts.hpp"
#include "radar_simulator.hpp"

RadarSimulator rsim;

std::chrono::seconds sleepDuration(1);

void signalHandler(int signum) {
    cout << "INTR=" << signum << endl;
    rsim.disable();
    cout << "DISABLE_SIM" << endl;

    exit(signum);
}

int main(int argc, char* argv[]) {

    // register signal SIGINT and signal handler
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);

    // ensure simulator is not running
    rsim.disable();

    // setup CLI option handling
    cxxopts::Options options(argv[0], " - example command line options");
    options.add_option("", "ct", "clear-target", "Clear target map", cxxopts::value<bool>(), "");
    options.add_option("", "cc", "clear-clutter", "Clear clutter map", cxxopts::value<bool>(), "");
    options.add_option("", "ltf", "load-target-file", "Load target file", cxxopts::value<string>(), "FILE");
    options.add_option("", "lcf", "load-clutter-file", "Load target file", cxxopts::value<string>(), "FILE");
    options.add_option("", "r", "run", "Start simulation", cxxopts::value<bool>(), "");
    options.add_option("", "t", "test", "Start test signal generation", cxxopts::value<bool>(), "");
    options.add_option("", "c", "cal", "Calibrate", cxxopts::value<bool>(), "");
    options.add_option("", "h", "help", "Print help", cxxopts::value<bool>(), "");

    try {
        options.parse(argc, argv);
    } catch (const cxxopts::OptionException& e) {
        cout << "error parsing options: " << e.what() << endl;
        exit(1);
    }

    if (options.count("help")) {
        cout << options.help( { "" }) << endl;
        exit(0);
    }

    if (options.count("ct")) {
        rsim.clearTargetMap();
        cout << "CLR_MT_MAP" << endl;
    }

    if (options.count("ct")) {
        rsim.clearClutterMap();
        cout << "CLR_CL_MAP" << endl;
    }

    // get status
    auto status = rsim.getStatus();

    // perform calibration
    cout << "CAL_BEGIN" << endl;
    while (!rsim.calibrate()) {
        status = rsim.getStatus();
        cout << "SIM_ARP_US=" << dec << status.arpUs << endl;
        cout << "SIM_ACP_CNT=" << dec << status.acpCnt << endl;
        cout << "SIM_TRIG_US=" << dec << status.trigUs << endl;
        this_thread::sleep_for(sleepDuration);
    };
    cout << "SIM_ARP_US=" << dec << rsim.getCalArpUs() << endl;
    cout << "SIM_ACP_CNT=" << dec << rsim.getCalAcpCnt() << endl;
    cout << "SIM_TRIG_US=" << dec << rsim.getCalTrigUs() << endl;
    cout << "SIM_CAL=" << 1 << endl;
    cout << "CAL_COMPLETE" << endl;

    if (options.count("r")) {

        if (options.count("ltf")) {
            auto& ff = options["ltf"].as<string>();

            ifstream mtFile(ff, ios_base::in | ios_base::binary);
            if (!mtFile || !mtFile.is_open()) {
                cerr << "ERR=Unable to open file " << ff << endl;
                exit(2);
            }
            rsim.initTargetMap(mtFile);

            cout << "INIT_MT_FILE" << endl;
        }

        if (options.count("lcf")) {
            auto& ff = options["lcf"].as<string>();

            ifstream clFile(ff, ios_base::in | ios_base::binary);
            if (!clFile || !clFile.is_open()) {
                cerr << "ERR=Unable to open file " << ff << endl;
                exit(3);
            }
            rsim.initClutterMap(clFile);

            cout << "INIT_CL_FILE" << endl;
        }

        rsim.enable();
        cout << "ENABLE_SIM" << endl;

        auto startTime = chrono::steady_clock::now();
        chrono::milliseconds timeSinceEpoch = chrono::duration_cast < chrono::milliseconds > (startTime.time_since_epoch());

        cout << "SIM_EN=" << status.enabled << endl;
        cout << "SIM_MTI_EN=" << status.mtiEnabled << endl;
        cout << "SIM_NORM_EN=" << status.normEnabled << endl;
        cout << "SIM_CAL=" << status.calibrated << endl;
        cout << "SIM_ARP_US=" << dec << status.arpUs << endl;
        cout << "SIM_ACP_CNT=" << dec << status.acpCnt << endl;
        cout << "SIM_TRIG_US=" << dec << status.trigUs << endl;
        cout << "SIM_MT_FIFO_CNT=" << dec << status.targetFifoCnt << endl;
        cout << "SIM_CL_FIFO_CNT=" << dec << status.clutterFifoCnt << endl;
        cout << "SIM_ACP_IDX=" << dec << status.simAcpIdx << "/" << timeSinceEpoch.count() << endl;
        cout << "SIM_CURR_ACP=" << dec << status.currAcpIdx << "/" << timeSinceEpoch.count() << endl;

        // notify caller of progress
        while (!rsim.isScenarioFinished()) {

            status = rsim.getStatus();
            timeSinceEpoch = chrono::duration_cast < chrono::milliseconds > (chrono::steady_clock::now().time_since_epoch());
            cout << "SIM_ACP_IDX=" << dec << status.simAcpIdx << "/" << timeSinceEpoch.count() << endl;

            // NOT IMPLEMENTED
            // cout << "SIM_CURR_ACP=" << dec << status.currAcpIdx << "/" << timeSinceEpoch.count() << endl;

            // check if we can load more moving target data
            if (options.count("ltf")) {
                auto& ff = options["ltf"].as<string>();
                ifstream mtFile(ff, ios_base::in | ios_base::binary);
                if (!mtFile || !mtFile.is_open()) {
                    cerr << "Unable to open file: " << ff << endl;
                    exit(2);
                }
                rsim.loadNextTargetMaps(mtFile);
            }

            this_thread::sleep_for(sleepDuration);
        }

        // disable simulator
        rsim.disable();
        cout << "END_SIM" << endl;

    } else if (options.count("t")) {

        rsim.initTestClutterMap();
        rsim.initTestTargetMap();

        rsim.enable();
        cout << "ENABLE_SIM" << endl;

        auto startTime = chrono::steady_clock::now();
        chrono::milliseconds timeSinceEpoch = chrono::duration_cast < chrono::milliseconds > (startTime.time_since_epoch());

        cout << "SIM_EN=" << status.enabled << endl;
        cout << "SIM_MTI_EN=" << status.mtiEnabled << endl;
        cout << "SIM_NORM_EN=" << status.normEnabled << endl;
        cout << "SIM_CAL=" << status.calibrated << endl;
        cout << "SIM_ARP_US=" << dec << status.arpUs << endl;
        cout << "SIM_ACP_CNT=" << dec << status.acpCnt << endl;
        cout << "SIM_TRIG_US=" << dec << status.trigUs << endl;
        cout << "SIM_MT_FIFO_CNT=" << dec << status.targetFifoCnt << endl;
        cout << "SIM_CL_FIFO_CNT=" << dec << status.clutterFifoCnt << endl;
        cout << "SIM_ACP_IDX=" << dec << status.simAcpIdx << "/" << timeSinceEpoch.count() << endl;
        cout << "SIM_CURR_ACP=" << dec << status.currAcpIdx << "/" << timeSinceEpoch.count() << endl;

        // notify caller of progress
        while (true) {

            status = rsim.getStatus();
            timeSinceEpoch = chrono::duration_cast < chrono::milliseconds > (chrono::steady_clock::now().time_since_epoch());
            cout << "SIM_ACP_IDX=" << dec << status.simAcpIdx << "/" << timeSinceEpoch.count() << endl;

            this_thread::sleep_for(sleepDuration);
        }

        // disable simulator
        rsim.disable();
        cout << "END_SIM" << endl;

    }

    return 0;
}

