#!/system/bin/sh
# Techmodware Tester
exec 2>&1

log() { echo "[OK] $1"; }
try() { "$@" >/dev/null 2>&1; }

SDK=$(getprop ro.build.version.sdk)
echo "Android SDK: $SDK | $(getprop ro.product.brand) $(getprop ro.product.model)"

do_hz() {
  MAXHZ=$(dumpsys display 2>/dev/null | grep -o 'fps=[0-9.]*' | cut -d= -f2 | sort -nr | head -1)
  [ -z "$MAXHZ" ] && MAXHZ=$(dumpsys display 2>/dev/null | grep -o 'refreshRate=[0-9.]*' | cut -d= -f2 | sort -nr | head -1)
  [ -z "$MAXHZ" ] && MAXHZ=60
  HZ=$(echo "$MAXHZ" | awk '{printf "%d", $1+0.5}')
  echo "Hz maksimal terdeteksi: ${HZ}Hz"
  try settings put system min_refresh_rate "$HZ.0"
  try settings put system peak_refresh_rate "$HZ.0"
  try settings put system user_refresh_rate "$HZ"
  try settings put system refresh_rate_mode 2
  try settings put secure refresh_rate_mode 2
  try settings put system miui_refresh_rate "$HZ"
  try settings put global miui_refresh_rate "$HZ"
  try settings put system oplus_customize_screen_refresh_rate "$HZ"
  log "Refresh rate dikunci di ${HZ}Hz"
}

do_perf() {
  try cmd power set-fixed-performance-mode-enabled true
  try settings put global low_power 0
  try settings put global low_power_sticky 0
  try settings put global adaptive_battery_management_enabled 0
  try settings put global window_animation_scale 0.5
  try settings put global transition_animation_scale 0.5
  try settings put global animator_duration_scale 0.5
  log "Mode performa tinggi aktif"
}

do_ppa() {
  if [ "$SDK" -ge 31 ]; then
    try device_config set_sync_disabled_for_tests persistent
    try device_config put activity_manager max_phantom_processes 2147483647
    try device_config put activity_manager settings_enable_monitor_phantom_procs false
    try settings put global settings_enable_monitor_phantom_procs false
    log "Phantom Process Killer dimatikan"
  else
    echo "PPA hanya ada di Android 12+, dilewati."
  fi
}

do_batt() {
  PKGS="com.dts.freefireth com.dts.freefiremax com.spotify.music"
  for KW in ggmouse redcorner gamespace ggspace sensix nebula; do
    FOUND=$(pm list packages 2>/dev/null | cut -d: -f2 | grep -i "$KW")
    PKGS="$PKGS $FOUND"
  done
  for P in $PKGS; do
    pm list packages 2>/dev/null | grep -q "package:$P$" || continue
    try dumpsys deviceidle whitelist +"$P"
    try cmd appops set "$P" RUN_IN_BACKGROUND allow
    try cmd appops set "$P" RUN_ANY_IN_BACKGROUND allow
    try cmd appops set "$P" WAKE_LOCK allow
    try cmd appops set "$P" START_FOREGROUND allow
    try am set-standby-bucket "$P" active
    try cmd game mode performance "$P"
    log "Batasan baterai dibuka: $P"
  done
}

do_otg() {
  try settings put global otg_enable 1
  try settings put global otg_auto_disable 0
  try settings put secure otg_enabled 1
  try settings put global usb_otg_enable 1
  log "Pengaturan OTG dikirim (hasil tergantung ROM)"
}

do_clean() {
  try am kill-all
  log "Proses background (cache) dibersihkan"
}

do_revert() {
  for K in min_refresh_rate peak_refresh_rate user_refresh_rate refresh_rate_mode miui_refresh_rate oplus_customize_screen_refresh_rate; do
    try settings delete system "$K"
  done
  try settings delete secure refresh_rate_mode
  try settings delete global miui_refresh_rate
  try cmd power set-fixed-performance-mode-enabled false
  try settings put global window_animation_scale 1.0
  try settings put global transition_animation_scale 1.0
  try settings put global animator_duration_scale 1.0
  try settings put global adaptive_battery_management_enabled 1
  if [ "$SDK" -ge 31 ]; then
    try device_config put activity_manager max_phantom_processes 32
    try device_config put activity_manager settings_enable_monitor_phantom_procs true
    try settings put global settings_enable_monitor_phantom_procs true
    try device_config set_sync_disabled_for_tests none
  fi
  wm size reset
  wm density reset
  log "Semua pengaturan dikembalikan ke awal"
}

do_launch() {
  P="$1"
  if pm list packages 2>/dev/null | grep -q "package:$P$"; then
    try monkey -p "$P" -c android.intent.category.LAUNCHER 1
    log "Membuka $P"
  else
    echo "Aplikasi $P belum terinstall"
  fi
}

do_res() {
  TARGET="$1"
  [ -z "$TARGET" ] && { echo "Target resolusi kosong"; return; }
  PHYS=$(wm size | grep -i physical | grep -o '[0-9]*x[0-9]*')
  PDEN=$(wm density | grep -i physical | grep -o '[0-9]*$')
  [ -z "$PHYS" ] && { echo "Gagal membaca ukuran layar fisik"; return; }
  PW=${PHYS%x*}; PH=${PHYS#*x}
  if [ "$PW" -lt "$PH" ]; then SHORT=$PW; LONG=$PH; else SHORT=$PH; LONG=$PW; fi
  NL=$(awk -v l="$LONG" -v t="$TARGET" -v s="$SHORT" 'BEGIN{v=int(l*t/s); v=v-(v%2); print v}')
  ND=$(awk -v d="${PDEN:-420}" -v t="$TARGET" -v s="$SHORT" 'BEGIN{printf "%d", d*t/s}')
  echo "Layar fisik: ${PW}x${PH} | Target: ${TARGET}x${NL} | Density: $ND"
  if [ "$PW" -lt "$PH" ]; then wm size "${TARGET}x${NL}"; else wm size "${NL}x${TARGET}"; fi
  wm density "$ND"
  log "Resolusi diubah ke ${TARGET}p"
}

do_resreset() {
  wm size reset
  wm density reset
  log "Resolusi dan density dikembalikan ke awal"
}

[ $# -eq 0 ] && set -- all
while [ $# -gt 0 ]; do
  case "$1" in
    all) do_hz; do_perf; do_ppa; do_batt; do_otg ;;
    hz) do_hz ;;
    perf) do_perf ;;
    ppa) do_ppa ;;
    batt) do_batt ;;
    otg) do_otg ;;
    clean) do_clean ;;
    revert) do_revert ;;
    launch) shift; do_launch "$1" ;;
    res) shift; do_res "$1" ;;
    resreset) do_resreset ;;
    *) echo "Fitur tidak dikenal: $1" ;;
  esac
  shift
done
echo "Selesai."
