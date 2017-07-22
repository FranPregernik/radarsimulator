/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
#ifndef sim_TYPES_H
#define sim_TYPES_H

#include <iosfwd>

#include <thrift/Thrift.h>
#include <thrift/TApplicationException.h>
#include <thrift/protocol/TProtocol.h>
#include <thrift/transport/TTransport.h>

#include <thrift/cxxfunctional.h>


namespace hr { namespace franp { namespace rsim {

struct SubSystem {
  enum type {
    CLUTTER = 0,
    MOVING_TARGET = 1
  };
};

extern const std::map<int, const char*> _SubSystem_VALUES_TO_NAMES;

class SimState;

class RadarSignalNotCalibratedException;

class IncompatibleFileException;

class DmaNotInitializedException;

typedef struct _SimState__isset {
  _SimState__isset() : time(false), enabled(false), mtiEnabled(false), normEnabled(false), calibrated(false), arpUs(false), acpCnt(false), trigUs(false), simAcpIdx(false), currAcpIdx(false), loadedClutterAcpIndex(false), loadedTargetAcpIndex(false), loadedClutterAcp(false), loadedTargetAcp(false) {}
  bool time :1;
  bool enabled :1;
  bool mtiEnabled :1;
  bool normEnabled :1;
  bool calibrated :1;
  bool arpUs :1;
  bool acpCnt :1;
  bool trigUs :1;
  bool simAcpIdx :1;
  bool currAcpIdx :1;
  bool loadedClutterAcpIndex :1;
  bool loadedTargetAcpIndex :1;
  bool loadedClutterAcp :1;
  bool loadedTargetAcp :1;
} _SimState__isset;

class SimState {
 public:

  SimState(const SimState&);
  SimState& operator=(const SimState&);
  SimState() : time(0), enabled(0), mtiEnabled(0), normEnabled(0), calibrated(0), arpUs(0), acpCnt(0), trigUs(0), simAcpIdx(0), currAcpIdx(0), loadedClutterAcpIndex(0), loadedTargetAcpIndex(0), loadedClutterAcp(0), loadedTargetAcp(0) {
  }

  virtual ~SimState() throw();
  int32_t time;
  bool enabled;
  bool mtiEnabled;
  bool normEnabled;
  bool calibrated;
  int32_t arpUs;
  int32_t acpCnt;
  int32_t trigUs;
  int32_t simAcpIdx;
  int32_t currAcpIdx;
  int32_t loadedClutterAcpIndex;
  int32_t loadedTargetAcpIndex;
  int32_t loadedClutterAcp;
  int32_t loadedTargetAcp;

  _SimState__isset __isset;

  void __set_time(const int32_t val);

  void __set_enabled(const bool val);

  void __set_mtiEnabled(const bool val);

  void __set_normEnabled(const bool val);

  void __set_calibrated(const bool val);

  void __set_arpUs(const int32_t val);

  void __set_acpCnt(const int32_t val);

  void __set_trigUs(const int32_t val);

  void __set_simAcpIdx(const int32_t val);

  void __set_currAcpIdx(const int32_t val);

  void __set_loadedClutterAcpIndex(const int32_t val);

  void __set_loadedTargetAcpIndex(const int32_t val);

  void __set_loadedClutterAcp(const int32_t val);

  void __set_loadedTargetAcp(const int32_t val);

  bool operator == (const SimState & rhs) const
  {
    if (!(time == rhs.time))
      return false;
    if (!(enabled == rhs.enabled))
      return false;
    if (!(mtiEnabled == rhs.mtiEnabled))
      return false;
    if (!(normEnabled == rhs.normEnabled))
      return false;
    if (!(calibrated == rhs.calibrated))
      return false;
    if (!(arpUs == rhs.arpUs))
      return false;
    if (!(acpCnt == rhs.acpCnt))
      return false;
    if (!(trigUs == rhs.trigUs))
      return false;
    if (!(simAcpIdx == rhs.simAcpIdx))
      return false;
    if (!(currAcpIdx == rhs.currAcpIdx))
      return false;
    if (!(loadedClutterAcpIndex == rhs.loadedClutterAcpIndex))
      return false;
    if (!(loadedTargetAcpIndex == rhs.loadedTargetAcpIndex))
      return false;
    if (!(loadedClutterAcp == rhs.loadedClutterAcp))
      return false;
    if (!(loadedTargetAcp == rhs.loadedTargetAcp))
      return false;
    return true;
  }
  bool operator != (const SimState &rhs) const {
    return !(*this == rhs);
  }

  bool operator < (const SimState & ) const;

  uint32_t read(::apache::thrift::protocol::TProtocol* iprot);
  uint32_t write(::apache::thrift::protocol::TProtocol* oprot) const;

  virtual void printTo(std::ostream& out) const;
};

void swap(SimState &a, SimState &b);

inline std::ostream& operator<<(std::ostream& out, const SimState& obj)
{
  obj.printTo(out);
  return out;
}


class RadarSignalNotCalibratedException : public ::apache::thrift::TException {
 public:

  RadarSignalNotCalibratedException(const RadarSignalNotCalibratedException&);
  RadarSignalNotCalibratedException& operator=(const RadarSignalNotCalibratedException&);
  RadarSignalNotCalibratedException() {
  }

  virtual ~RadarSignalNotCalibratedException() throw();

  bool operator == (const RadarSignalNotCalibratedException & /* rhs */) const
  {
    return true;
  }
  bool operator != (const RadarSignalNotCalibratedException &rhs) const {
    return !(*this == rhs);
  }

  bool operator < (const RadarSignalNotCalibratedException & ) const;

  uint32_t read(::apache::thrift::protocol::TProtocol* iprot);
  uint32_t write(::apache::thrift::protocol::TProtocol* oprot) const;

  virtual void printTo(std::ostream& out) const;
  mutable std::string thriftTExceptionMessageHolder_;
  const char* what() const throw();
};

void swap(RadarSignalNotCalibratedException &a, RadarSignalNotCalibratedException &b);

inline std::ostream& operator<<(std::ostream& out, const RadarSignalNotCalibratedException& obj)
{
  obj.printTo(out);
  return out;
}

typedef struct _IncompatibleFileException__isset {
  _IncompatibleFileException__isset() : subSystem(false) {}
  bool subSystem :1;
} _IncompatibleFileException__isset;

class IncompatibleFileException : public ::apache::thrift::TException {
 public:

  IncompatibleFileException(const IncompatibleFileException&);
  IncompatibleFileException& operator=(const IncompatibleFileException&);
  IncompatibleFileException() : subSystem((SubSystem::type)0) {
  }

  virtual ~IncompatibleFileException() throw();
  SubSystem::type subSystem;

  _IncompatibleFileException__isset __isset;

  void __set_subSystem(const SubSystem::type val);

  bool operator == (const IncompatibleFileException & rhs) const
  {
    if (!(subSystem == rhs.subSystem))
      return false;
    return true;
  }
  bool operator != (const IncompatibleFileException &rhs) const {
    return !(*this == rhs);
  }

  bool operator < (const IncompatibleFileException & ) const;

  uint32_t read(::apache::thrift::protocol::TProtocol* iprot);
  uint32_t write(::apache::thrift::protocol::TProtocol* oprot) const;

  virtual void printTo(std::ostream& out) const;
  mutable std::string thriftTExceptionMessageHolder_;
  const char* what() const throw();
};

void swap(IncompatibleFileException &a, IncompatibleFileException &b);

inline std::ostream& operator<<(std::ostream& out, const IncompatibleFileException& obj)
{
  obj.printTo(out);
  return out;
}

typedef struct _DmaNotInitializedException__isset {
  _DmaNotInitializedException__isset() : subSystem(false) {}
  bool subSystem :1;
} _DmaNotInitializedException__isset;

class DmaNotInitializedException : public ::apache::thrift::TException {
 public:

  DmaNotInitializedException(const DmaNotInitializedException&);
  DmaNotInitializedException& operator=(const DmaNotInitializedException&);
  DmaNotInitializedException() : subSystem((SubSystem::type)0) {
  }

  virtual ~DmaNotInitializedException() throw();
  SubSystem::type subSystem;

  _DmaNotInitializedException__isset __isset;

  void __set_subSystem(const SubSystem::type val);

  bool operator == (const DmaNotInitializedException & rhs) const
  {
    if (!(subSystem == rhs.subSystem))
      return false;
    return true;
  }
  bool operator != (const DmaNotInitializedException &rhs) const {
    return !(*this == rhs);
  }

  bool operator < (const DmaNotInitializedException & ) const;

  uint32_t read(::apache::thrift::protocol::TProtocol* iprot);
  uint32_t write(::apache::thrift::protocol::TProtocol* oprot) const;

  virtual void printTo(std::ostream& out) const;
  mutable std::string thriftTExceptionMessageHolder_;
  const char* what() const throw();
};

void swap(DmaNotInitializedException &a, DmaNotInitializedException &b);

inline std::ostream& operator<<(std::ostream& out, const DmaNotInitializedException& obj)
{
  obj.printTo(out);
  return out;
}

}}} // namespace

#endif
