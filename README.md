# ASysGuard

Host system metrics for Linux, displayed on Android tablets and on the Linux desktop.

## Components

- `sysguard-exporter` - C++ user systemd service that exports Wayland DPMS state and host metrics
  (CPU, memory, network, power, processes) as JSON over HTTP.
- `shared` - Kotlin Multiplatform module holding the Compose UI and data layer; targets Android and
  desktop JVM. Not built on its own.
- `app` - Android application; consumes `shared`.
- `desktopApp` - Compose Desktop application; consumes `shared`.

The clients poll the exporter's HTTP endpoints (`/metrics.json`, `/display.json`, `/processes.json`).

## Prerequisites

- JDK 17+
- Android SDK for the `app` module - set `sdk.dir` in `local.properties`.
- For `sysguard-exporter`: CMake 3.16+, C++20 compiler, and Wayland client headers
  (`wayland-devel` / `libwayland-dev`).

## Configuration

Both client apps read `apis.properties` from the repository root (gitignored). Create it with:

```properties
SYSGUARD_EXPORTER_HOST=http://<exporter-host>:9000
OUTLOOK_ICS_URL=https://outlook.office365.com/owa/calendar/.../calendar.ics
LOCATION_MAIN_LAT=51.4545
LOCATION_MAIN_LON=-2.5879
LOCATION_MAIN_TIMEZONE=Europe/London
LOCATION_ALT_LAT=24.1469
LOCATION_ALT_LON=120.6839
LOCATION_ALT_TIMEZONE=Asia/Taipei
```

The Android build bakes these into `BuildConfig`; the desktop app reads the file from its working
directory (or any ancestor) at startup, so run it from somewhere inside the checkout.

## Build

### Exporter

See [sysguard-exporter](sysguard-exporter) for detail. In short:

```bash
cmake -S sysguard-exporter -B sysguard-exporter/build -DCMAKE_BUILD_TYPE=Release
cmake --build sysguard-exporter/build --target install
```

This installs and enables a per-user systemd unit `sysguard-exporter`, configured via
`~/.local/share/sysguard-exporter/config.json`.

### Android

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # minified release APK
```

APKs land under `app/build/outputs/apk/`. `minSdk` is 23. Double-tap the screen to cycle between the
node, info, and process boards.

### Desktop (Linux, Wayland or X11)

```bash
./gradlew :desktopApp:run                              # run from inside the checkout
./gradlew :desktopApp:packageDistributionForCurrentOS  # native bundle (deb / AppImage)
```

The desktop build shows all panels at once. F11 or double-click toggles fullscreen. Screen-wake uses
the `org.freedesktop.ScreenSaver` D-Bus interface (KDE/GNOME); with no session bus it is a no-op.
