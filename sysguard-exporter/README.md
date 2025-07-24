# sysguard-exporter

A small utility that exports Wayland DPMS state and device metrics as JSON on the local network.

## Prerequisites

* Wayland headers (wayland-devel)
* CMake 3.16+
* Any C++17 capable compiler

## Installing

```shell
cmake -H. -Bbuild -DCMAKE_BUILD_TYPE=Release  # generate a build
cmake --build build -j -- install             # install
```

A user systemd unit named `sysguard-exporter` us installed automatically