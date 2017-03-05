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

    exit(signum);
}

int main(int argc, char* argv[]) {

    // register signal SIGINT and signal handler
    signal(SIGINT, signalHandler);

    // ensure simulator is not running
    rsim.disable();

    // setup CLI option handling
    cxxopts::Options options(argv[0], " - example command line options");
    options.add_option("", "ct", "clear-target", "Clear target map", cxxopts::value<bool>(), "");
    options.add_option("", "cc", "clear-clutter", "Clear clutter map", cxxopts::value<bool>(), "");
    options.add_option("", "ltf", "load-target-file", "Load target file", cxxopts::value<string>(), "FILE");
    options.add_option("", "lcf", "load-clutter-file", "Load target file", cxxopts::value<string>(), "FILE");
    options.add_option("", "r", "run", "Start simulation", cxxopts::value<bool>(), "");
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

    if (options.count("r")) {
        rsim.enable();
        cout << "EN_SIM" << endl;
    }

    auto startTime = chrono::steady_clock::now();
    auto lastStatTime = startTime;
    auto status = rsim.getStatus();
    chrono::milliseconds timeSinceEpoch = chrono::duration_cast < chrono::milliseconds > (startTime.time_since_epoch());

    cout << "SIM_EN=" << status.enabled << endl;
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

        auto secSinceFullStat = chrono::duration_cast < chrono::seconds > (chrono::steady_clock::now() - lastStatTime);
        if (secSinceFullStat.count() > 5 /* [s] */) {
            lastStatTime = chrono::steady_clock::now();
            cout << "SIM_EN=" << status.enabled << endl;
            cout << "SIM_CAL=" << status.calibrated << endl;
            cout << "SIM_ARP_US=" << dec << status.arpUs << endl;
            cout << "SIM_ACP_CNT=" << dec << status.acpCnt << endl;
            cout << "SIM_TRIG_US=" << dec << status.trigUs << endl;
            cout << "SIM_MT_FIFO_CNT=" << dec << status.targetFifoCnt << endl;
            cout << "SIM_CL_FIFO_CNT=" << dec << status.clutterFifoCnt << endl;
        }

        chrono::milliseconds timeSinceEpoch = chrono::duration_cast < chrono::milliseconds > (chrono::steady_clock::now().time_since_epoch());
        cout << "SIM_ACP_IDX=" << dec << status.simAcpIdx << "/" << timeSinceEpoch.count() << endl;
        cout << "SIM_CURR_ACP=" << dec << status.currAcpIdx << "/" << timeSinceEpoch.count() << endl;

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

    return 0;
}

