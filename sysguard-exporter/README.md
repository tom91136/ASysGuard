# sysguard-exporter

A small utility that exports Wayland DPMS state and host metrics as JSON on the local network.

## Prerequisites

* Wayland client headers (`wayland-devel` / `libwayland-dev`)
* CMake 3.16+
* C++20 compiler

## Installing

```shell
cmake -H. -Bbuild -DCMAKE_BUILD_TYPE=Release  # generate a build
cmake --build build -j -- install             # install
```

A user systemd unit named `sysguard-exporter` is installed and enabled automatically. It starts with
the graphical session (it needs `WAYLAND_DISPLAY` for DPMS monitoring) and runs at idle scheduling
priority pinned to one core.

## Configuration

Edit `~/.local/share/sysguard-exporter/config.json`:

```json
{
  "host": "0.0.0.0",
  "port": 9000,
  "powerHwmonName": "corsairpsu",
  "powerSensor": "power1_input",
  "ifMacs": ["1a:4b:24:c6:dc:88"]
}
```

The power sensor is matched by hwmon driver `name` (the `hwmonN` index is not stable across reboots),
and interfaces by MAC (names can change). `powerWPath` (an explicit sysfs path) and `ifNames` are also
accepted.

## Endpoints

* `GET /metrics.json` - CPU, memory, network, power, load, display state.
* `GET /display.json` - just the DPMS display-on state.
* `GET /processes.json?n=<N>&sort=cpu|mem` - top-N processes plus task/load/uptime summary.
