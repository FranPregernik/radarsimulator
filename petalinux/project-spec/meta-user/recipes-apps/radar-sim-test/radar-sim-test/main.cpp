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

auto rsim = RadarSimulator();

std::chrono::seconds sleepDuration(2);

void signalHandler(int signum) {
    cout << "Interrupt signal (" << signum << ") received.\n";
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
        cout << "Cleared the target map." << endl;
    }

    if (options.count("ct")) {
        rsim.clearClutterMap();
        cout << "Cleared the clutter map." << endl;
    }

    if (options.count("ltf")) {
        auto& ff = options["ltf"].as<string>();
        cout << "Moving target file: " << ff << " ... ";

        ifstream mtFile(ff, ios_base::in | ios_base::binary);
        rsim.initTargetMap(mtFile);

        cout << " initialized " << endl;
    }

    ifstream clFile;
    if (options.count("lcf")) {
        auto& ff = options["lcf"].as<string>();
        cout << "Clutter file: " << ff << " ... ";

        ifstream clFile(ff, ios_base::in | ios_base::binary);
        rsim.initClutterMap(clFile);

        cout << " initialized " << endl;
    }

    if (options.count("r")) {
        rsim.enable();
        cout << "Enabled the simulator." << endl;
    }

    auto lastStatTime = chrono::steady_clock::now();
    while (true) {

        chrono::duration<double> diff = chrono::steady_clock::now() - lastStatTime;
        if (diff.count() > 5 /* [s] */) {
            lastStatTime = chrono::steady_clock::now();
            Simulator status = rsim.getStatus();
            cout << "Simulator enabled: " << status.enabled << endl;
            cout << "Simulator calibrated: " << status.calibrated << endl;
            cout << "Simulator ARP [us]: " << dec << status.arpUs << endl;
            cout << "Simulator ACP cnt: " << dec << status.acpCnt << endl;
            cout << "Simulator TRIG [us]: " << dec << status.trigUs << endl;
            cout << "Simulator target FIFO count: " << dec << status.targetFifoCnt << endl;
            cout << "Simulator clutter FIFO count: " << dec << status.clutterFifoCnt << endl;
            cout << "Simulator ACP index: " << dec << status.simAcpIdx << endl;
            cout << "Simulator ACP: " << dec << status.currAcpIdx << endl;
        }

        // check if we can load more moving target data
        if (options.count("ltf")) {
            auto& ff = options["ltf"].as<string>();
            cout << "Moving target file: " << ff << " ... ";

            ifstream mtFile(ff, ios_base::in | ios_base::binary);
            rsim.loadNextTargetMaps(mtFile);
        }

        this_thread::sleep_for(sleepDuration);
    }

    // disable simulator
    //rsim.disable();

    return 0;
}

