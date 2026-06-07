#!/bin/sh
# deploy.sh server        rebuild exporter, install to ~/.local/bin, restart unit
# deploy.sh <client-host> rebuild dashboard uber jar, push to <host>:~/asysguard/, restart its dashboard
set -eu
cd "$(dirname "$0")"

case "${1:?usage: deploy.sh server|<client-host>}" in
server)
  cmake --build sysguard-exporter/cmake-build-release -j
  cp sysguard-exporter/cmake-build-release/sysguard-exporter "$HOME/.local/bin/sysguard-exporter"
  systemctl --user restart sysguard-exporter
  systemctl --user is-active sysguard-exporter
  ;;
*)
  host=$1
  ./gradlew :desktopApp:packageUberJarForCurrentOS
  jar=$(ls desktopApp/build/compose/jars/ASysGuard-linux-x64-*.jar | tail -1)
  scp "$jar" "$host":asysguard/asysguard-desktop.jar
  # dashboard unit is transient; recreate it if the host rebooted since the last systemd-run
  ssh "$host" 'systemctl --user restart asysguard-dashboard 2>/dev/null ||
    systemd-run --user --unit=asysguard-dashboard /home/tom/asysguard/launch.sh'
  ssh "$host" 'systemctl --user is-active asysguard-dashboard'
  ;;
esac
