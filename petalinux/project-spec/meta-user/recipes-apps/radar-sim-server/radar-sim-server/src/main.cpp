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

#include <boost/shared_ptr.hpp>
#include <thrift/protocol/TBinaryProtocol.h>
#include <thrift/server/TSimpleServer.h>
#include <thrift/transport/TServerSocket.h>
#include <thrift/transport/TBufferTransports.h>

using namespace std;

using namespace ::apache::thrift;
using namespace ::apache::thrift::protocol;
using namespace ::apache::thrift::transport;
using namespace ::apache::thrift::server;

#include "radar_simulator.hpp"

boost::shared_ptr<SimulatorHandler> handler(new SimulatorHandler());
boost::shared_ptr<TProcessor> processor(new SimulatorProcessor(handler));

void signalHandler(int signum) {
    handler->reset();
    cout << "DISABLE_SIM INTR=" << signum << endl;
    exit(signum);
}

int main(int argc, char *argv[]) {

    // register signal SIGINT and signal handler
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);

    int port = 9090;
    boost::shared_ptr<TServerTransport> serverTransport(new TServerSocket(port));
    boost::shared_ptr<TTransportFactory> transportFactory(new TBufferedTransportFactory());
    boost::shared_ptr<TProtocolFactory> protocolFactory(new TBinaryProtocolFactory());

    TSimpleServer server(processor, serverTransport, transportFactory, protocolFactory);
    server.serve();

    return 0;
}

