#include <cctype>
#include <chrono>
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <mutex>
#include <set>

#include <wayland-client.h>

#include "dpms-client-protocol.h"
#include "httplib.h"
#include "nlohmann/json.hpp"

template <typename T> T *bindWlInterface(wl_display *display, const wl_interface *interface) {
  using Data = std::pair<T *, const wl_interface *>;
  const wl_registry_listener listener = {
      .global =
          [](void *data, wl_registry *registry, const uint32_t id, const char *name, uint32_t) {
            if (auto object = static_cast<Data *>(data); std::strcmp(name, object->second->name) == 0) {
              object->first = static_cast<T *>(wl_registry_bind(registry, id, object->second, object->second->version));
            }
          },
      .global_remove = nullptr};
  Data data{{}, interface};
  // listener and data are stack-local; destroy the registry so no later event reaches the dangling listener
  wl_registry *registry = wl_display_get_registry(display);
  wl_registry_add_listener(registry, &listener, &data);
  wl_display_roundtrip(display);
  wl_registry_destroy(registry);

  if (!data.first) {
    std::cerr << "Wayland protocol " << data.second->name << " not found!" << std::endl;
    wl_display_disconnect(display);
    std::exit(EXIT_FAILURE);
  }
  return data.first;
}

struct Config {
  std::string host = "0.0.0.0";
  int port = 9000;
  std::string powerWPath{}; // takes precedence over powerHwmonName when set
  std::string powerHwmonName{};
  std::string powerSensor = "power1_input";
  std::vector<std::string> ifNames{};
  std::vector<std::string> ifMacs{};
  NLOHMANN_DEFINE_TYPE_INTRUSIVE_WITH_DEFAULT(Config, host, port, powerWPath, powerHwmonName, powerSensor, ifNames,
                                              ifMacs);
  friend std::ostream &operator<<(std::ostream &os, const Config &config) {
    os << "Config("
       << "host: " << config.host << ", "
       << "port: " << config.port << ", "
       << "powerWPath: " << (config.powerWPath.empty() ? "N/A" : config.powerWPath) << ", "
       << "powerHwmonName: " << (config.powerHwmonName.empty() ? "N/A" : config.powerHwmonName) << ", "
       << "powerSensor: " << config.powerSensor << ", "
       << "ifNames: [";
    for (size_t i = 0; i < config.ifNames.size(); ++i) {
      os << config.ifNames[i];
      if (i < config.ifNames.size() - 1) os << ", ";
    }
    os << "], ifMacs: [";
    for (size_t i = 0; i < config.ifMacs.size(); ++i) {
      os << config.ifMacs[i];
      if (i < config.ifMacs.size() - 1) os << ", ";
    }
    return os << "])";
  }
};

// hwmonN index is not stable across reboots, so match on the driver name file instead.
static std::string resolveHwmonPath(const std::string &name, const std::string &sensor) {
  std::error_code ec;
  for (const auto &entry : std::filesystem::directory_iterator("/sys/class/hwmon", ec)) {
    std::ifstream nameFile(entry.path() / "name");
    std::string n;
    if (nameFile && std::getline(nameFile, n) && n == name) {
      auto path = entry.path() / sensor;
      if (std::filesystem::exists(path)) return path.string();
    }
  }
  return {};
}

// ifname can change but the MAC is stable, so resolve configured MACs to current names.
static std::set<std::string> resolveIfMacs(const std::vector<std::string> &macs) {
  auto lower = [](std::string s) {
    for (auto &c : s) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return s;
  };
  std::set<std::string> want;
  for (const auto &m : macs) want.insert(lower(m));
  std::set<std::string> names;
  std::error_code ec;
  for (const auto &entry : std::filesystem::directory_iterator("/sys/class/net", ec)) {
    std::ifstream addrFile(entry.path() / "address");
    std::string mac;
    if (addrFile && std::getline(addrFile, mac) && want.count(lower(mac)))
      names.insert(entry.path().filename().string());
  }
  return names;
}
struct NetworkStat {
  size_t inetTxTotalBytes{}, inetRxTotalBytes{};
  NLOHMANN_DEFINE_TYPE_INTRUSIVE(NetworkStat, inetTxTotalBytes, inetRxTotalBytes);
};

struct CPUStat {
  int64_t ordinal{};
  int64_t coreId{};
  int64_t frequencyKHz{};
  float utilisation{};
  NLOHMANN_DEFINE_TYPE_INTRUSIVE(CPUStat, ordinal, coreId, frequencyKHz, utilisation);
};

struct DisplayStat {
  bool displayOn{};
  NLOHMANN_DEFINE_TYPE_INTRUSIVE(DisplayStat, displayOn);
};

struct NodeStat {
  bool displayOn{};
  float powerW{}; // /sys/class/hwmon/hwmon4/power1_input divide by 1000000
  std::unordered_map<std::string, CPUStat> cpus{};
  std::unordered_map<std::string, NetworkStat> networks{};
  float loadAvg1m{}, loadAvg5m{}, loadAvg15m{};
  size_t memoryTotalBytes{}, memoryCachedBytes{}, memoryBufferedBytes{}, memoryFreeBytes{};

  float readMs{};
  int64_t epochMs{};

  NLOHMANN_DEFINE_TYPE_INTRUSIVE(NodeStat, displayOn, powerW, cpus, networks, loadAvg1m, loadAvg5m, loadAvg15m,
                                 memoryTotalBytes, memoryCachedBytes, memoryBufferedBytes, memoryFreeBytes, readMs,
                                 epochMs);

  friend std::ostream &operator<<(std::ostream &os, const NodeStat &stats) {
    os << std::fixed << std::setprecision(2);
    os << "Display On: " << std::boolalpha << stats.displayOn << "\n";
    os << "Power (W): " << stats.powerW << "\n";
    os << "CPUs:";
    for (const auto &[id, cpu] : stats.cpus) {
      os << id                                                               //
         << "(" << cpu.coreId << ") " << (cpu.frequencyKHz / 1000) << "Mhz " //
         << " " << (cpu.utilisation * 100) << "%\n";
    }
    os << "\n";

    os << "Networks:";
    for (const auto &[name, net] : stats.networks) {
      os << name                                                 //
         << " Tx (MB): " << (net.inetTxTotalBytes / 1000 / 1000) //
         << " Rx (MB): " << (net.inetRxTotalBytes / 1000 / 1000) << "\n";
    }
    os << "\n";
    os << "Load Averages: " << stats.loadAvg1m << " (1m), " << stats.loadAvg5m << " (5m), " << stats.loadAvg15m
       << " (15m)\n";
    os << "Memory Total (MB): " << (stats.memoryTotalBytes / 1000 / 1000) << "\n";
    os << "Memory Free (MB): " << (stats.memoryFreeBytes / 1000 / 1000) << "\n";
    os << "Memory Cached (MB): " << (stats.memoryCachedBytes / 1000 / 1000) << "\n";
    os << "Memory Buffered (MB): " << (stats.memoryBufferedBytes / 1000 / 1000) << "\n";

    os << "Elapsed (ms): " << (stats.readMs) << "\n";
    return os;
  }

  static NodeStat collect(const Config &config, bool displayOn) {
    NodeStat stats;
    stats.displayOn = displayOn;
    auto start = std::chrono::high_resolution_clock::now();
    std::string powerPath = config.powerWPath;
    if (powerPath.empty() && !config.powerHwmonName.empty())
      powerPath = resolveHwmonPath(config.powerHwmonName, config.powerSensor);
    if (!powerPath.empty()) {
      if (std::ifstream powerFile(powerPath); powerFile) {
        double powerMicroW;
        powerFile >> powerMicroW;
        stats.powerW = static_cast<float>(powerMicroW / 1000000.0);
      }
    }

    static int64_t numCores = sysconf(_SC_NPROCESSORS_ONLN);
    static std::mutex cpuMutex; // collect runs on httplib's thread pool; serialise the shared counters
    static std::vector<size_t> prevTotal(numCores, 0);
    static std::vector<size_t> prevIdle(numCores, 0);
    std::lock_guard<std::mutex> cpuLock(cpuMutex);

    stats.cpus.reserve(numCores);
    for (int64_t i = 0; i < numCores; ++i) {
      auto id = std::to_string(i);
      std::string currentFreqPath = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/scaling_cur_freq";
      if (std::ifstream cpuFreqFile(currentFreqPath); cpuFreqFile) {
        cpuFreqFile >> stats.cpus[id].frequencyKHz;
      }
      std::string coreIdPath = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/topology/core_id";
      if (std::ifstream coreIdFile(coreIdPath); coreIdFile) {
        coreIdFile >> stats.cpus[id].coreId;
      }
    }

    if (std::ifstream statFile("/proc/stat"); statFile) {
      std::string line;
      while (std::getline(statFile, line)) {
        std::istringstream ss(line);
        std::string label;
        ss >> label;
        if (label.find("cpu") != 0 || label.size() <= 3) continue;
        size_t user{}, nice{}, system{}, idle{}, iowait{}, irq{}, softirq{};
        ss >> user >> nice >> system >> idle >> iowait >> irq >> softirq;
        size_t totalIdle = idle + iowait;
        size_t totalNonIdle = user + nice + system + irq + softirq;
        size_t total = totalIdle + totalNonIdle;

        auto id = label.substr(3);
        if (auto ordinal = std::stol(id); ordinal >= numCores)
          std::cerr << "CPU label ordinal out of bounds: " << id << " (max=" << numCores << ")" << std::endl;
        else {
          size_t totalDiff = total - prevTotal[ordinal];
          size_t idleDiff = totalIdle - prevIdle[ordinal];
          if (totalDiff > 0) stats.cpus[id].utilisation = 1.0f - static_cast<float>(idleDiff) / static_cast<float>(totalDiff);
          stats.cpus[id].ordinal = ordinal;
          prevTotal[ordinal] = total;
          prevIdle[ordinal] = totalIdle;
        }
      }
    }

    if (std::ifstream loadavgFile("/proc/loadavg"); loadavgFile) {
      loadavgFile >> stats.loadAvg1m >> stats.loadAvg5m >> stats.loadAvg15m;
    }

    if (std::ifstream meminfoFile("/proc/meminfo"); meminfoFile) {
      std::string key;
      size_t value;
      std::string unit;
      while (meminfoFile >> key >> value >> unit) {
        if (key == "MemTotal:") stats.memoryTotalBytes = value * 1024;
        else if (key == "MemFree:")
          stats.memoryFreeBytes = value * 1024;
        else if (key == "Buffers:")
          stats.memoryBufferedBytes = value * 1024;
        else if (key == "Cached:")
          stats.memoryCachedBytes = value * 1024;
      }
    }

    std::set<std::string> ifNames(config.ifNames.begin(), config.ifNames.end());
    for (auto &name : resolveIfMacs(config.ifMacs))
      ifNames.insert(name);
    if (std::ifstream netFile("/proc/net/dev"); netFile) {
      std::string line;
      while (std::getline(netFile, line)) {
        for (auto &ifName : ifNames) {
          if (line.find(ifName + ":") == std::string::npos) continue;
          std::istringstream ss(line);
          std::string iface;
          NetworkStat stat;
          ss >> iface >> stat.inetRxTotalBytes;
          for (int i = 0; i < 8; ++i)
            ss >> stat.inetTxTotalBytes;
          stats.networks.emplace(ifName, stat);
        }
      }
    }
    auto end = std::chrono::high_resolution_clock::now();
    stats.readMs = (std::chrono::duration<float, std::milli>(end - start)).count();
    stats.epochMs =
        std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch())
            .count();
    return stats;
  }
};

int main(int argc, char *argv[]) {
  static httplib::Server s;
  static wl_display *display{};

  for (auto sig : {SIGINT, SIGTERM})
    std::signal(sig, [](int) {
      std::cerr << "Stopping..." << std::endl;
      if (display) {
        wl_display_sync(display);
        wl_display_flush(display);
        wl_display_disconnect(display);
      }
      s.stop();
    });

  std::atomic_bool displayOn{};
  std::thread dpmsMonitor([&]() {
    display = wl_display_connect(nullptr);
    if (!display) {
      std::cerr << "Failed to connect to the Wayland display" << std::endl;
      return;
    }
    auto *dpmsManager = bindWlInterface<org_kde_kwin_dpms_manager>(display, &org_kde_kwin_dpms_manager_interface);
    auto *output = bindWlInterface<wl_output>(display, &wl_output_interface);
    constexpr org_kde_kwin_dpms_listener listener{
        .supported =
            [](void *, struct org_kde_kwin_dpms *, const uint32_t supported) {
              if (!supported) {
                std::cerr << "Warning: KWin reports DPMS as unsupported" << std::endl;
              }
            },
        .mode =
            [](void *data, struct org_kde_kwin_dpms *, const uint32_t mode) {
              *static_cast<std::atomic_bool *>(data) = mode == ORG_KDE_KWIN_DPMS_MODE_ON;
              std::cout << "DPMS mode changed to " << mode << "\n";
            },

        .done = [](void *, struct org_kde_kwin_dpms *) {} //
    };

    const auto dpms = org_kde_kwin_dpms_manager_get(dpmsManager, output);
    org_kde_kwin_dpms_add_listener(dpms, &listener, &displayOn);
    wl_display_sync(display);
    std::cout << "Monitoring for KWin DPMS events on display " << display << std::endl;
    while (wl_display_dispatch(display) != -1) {
    }
    std::cout << "DPMS monitor stopped" << std::endl;
  });

  //  Config c{.host = "0.0.0.0", .port = 9000, .powerWPath = "/sys/class/hwmon/hwmon4/power1_input",
  //  .ifNames{"enp9s0"}};

  std::vector<std::string> args(argv + 1, argv + argc);

  if (args.size() > 1) {
    std::cerr << "More than one arg given, ignoring the rest" << std::endl;
  }
  std::string configPath = "./config.json";
  if (!args.empty()) configPath = args[0];

  Config config;
  if (std::ifstream configFile(configPath); configFile) {
    nlohmann::json json;
    configFile >> json;
    nlohmann::from_json(json, config);
    std::cout << "Found config.json" << std::endl;
  } else {
    std::ofstream out("./config.json");
    out << nlohmann::json(config);
    std::cout << "Cannot find config.json, generating default config" << std::endl;
  }
  std::cout << "Using config " << config << std::endl;

  std::thread server([&]() {
    s.Get("/metrics.json", [&](const httplib::Request &, httplib::Response &res) {
      const nlohmann::json json = NodeStat::collect(config, displayOn);
      res.set_content(json.dump(), "application/json");
    });

    s.Get("/display.json", [&](const httplib::Request &, httplib::Response &res) {
      const nlohmann::json json = DisplayStat{displayOn};
      res.set_content(json.dump(), "application/json");
    });
    std::cout << "Server listening on " << config.host << ":" << config.port << std::endl;
    s.listen(config.host, config.port);
  });

  server.join();
  dpmsMonitor.join();

  return EXIT_SUCCESS;
}
