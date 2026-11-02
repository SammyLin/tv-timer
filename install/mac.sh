#!/bin/bash
# 看電視計時:Mac 一鍵安裝到 Google TV
# 用法(貼到「終端機」):
#   curl -fsSL https://raw.githubusercontent.com/SammyLin/tv-timer/main/install/mac.sh | bash
# 可以:安裝或更新 App、忘記爸媽密碼時重設、強制移除 App
set -u

APK_URL="${TVTIMER_APK_URL:-https://github.com/SammyLin/tv-timer/releases/latest/download/tv-timer.apk}"
TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
PKG="ai.threemi.tvtimer"
DIR="$HOME/.tv-timer-installer"
ADB="$DIR/platform-tools/adb"
TARGET=""

say()  { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m%s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31m✗ %s\033[0m\n' "$*"; exit 1; }
# 從鍵盤讀(用 curl | bash 跑時 stdin 是程式本身)
ask()  { local a; read -r -p "$1" a </dev/tty; printf '%s' "$a"; }
# adb 偶爾會卡住,最多等幾秒。
# </dev/null:用「curl | bash」跑的時候程式本身從 stdin 來,adb shell 會把後面的程式吃掉
adb_t() { local t=$1; shift; perl -e 'alarm shift; exec @ARGV' "$t" "$ADB" "$@" </dev/null; }

# 中文輸入法打出來的全形數字、句號都轉成半形,空白拿掉
normalize() {
  printf '%s' "$1" | sed -e 's/０/0/g;s/１/1/g;s/２/2/g;s/３/3/g;s/４/4/g;s/５/5/g;s/６/6/g;s/７/7/g;s/８/8/g;s/９/9/g' \
    -e 's/。/./g;s/．/./g;s/,/./g;s/，/./g;s/：/:/g' | tr -d '[:space:]'
}
valid_ip() {
  [[ "$1" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})(:[0-9]{1,5})?$ ]] || return 1
  local n; for n in "${BASH_REMATCH[@]:1:4}"; do [ "$n" -le 255 ] || return 1; done
}

# 掃這台電腦所在的網段,找開著 adb(5555)的裝置
scan_tvs() {
  local iface ip prefix i
  iface=$(route -n get default 2>/dev/null | awk '/interface:/{print $2}')
  ip=$(ipconfig getifaddr "${iface:-en0}" 2>/dev/null || ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null)
  [ -n "$ip" ] || return 0
  prefix=${ip%.*}
  for i in $(seq 1 254); do
    [ "$prefix.$i" = "$ip" ] && continue
    ( nc -z -G 1 -w 1 "$prefix.$i" 5555 >/dev/null 2>&1 && echo "$prefix.$i" ) &
  done
  wait
}

# 選電視:先自動找,找不到或不是要的就讓人打 IP。結果放在 TARGET
pick_tv() {
  local found=() line a n
  printf '正在家裡網路上找電視(約 3 秒)…\n'
  while IFS= read -r line; do [ -n "$line" ] && found+=("$line"); done < <(scan_tvs | sort -t . -k 4 -n)
  if [ ${#found[@]} -eq 1 ]; then
    printf '找到一台:\033[1m%s\033[0m\n' "${found[0]}"
    a=$(ask "是這台的話直接按 Enter;不是的話請輸入電視的 IP:")
    [ -z "$a" ] && a=${found[0]}
  elif [ ${#found[@]} -gt 1 ]; then
    printf '找到好幾台:\n'
    for n in "${!found[@]}"; do printf '  %d) %s\n' $((n + 1)) "${found[$n]}"; done
    a=$(ask "請輸入編號(1~${#found[@]}),或直接輸入電視的 IP:")
    a=$(normalize "$a")
    if [[ "$a" =~ ^[0-9]+$ ]] && [ "$a" -ge 1 ] && [ "$a" -le ${#found[@]} ]; then a=${found[$((a - 1))]}; fi
  else
    warn "沒有自動找到電視。常見原因:電視的「USB 偵錯」沒開、電視和電腦不在同一個網路,或電視在別的網段。"
    a=$(ask "請輸入電視的 IP 位址(例如 192.168.1.23;直接按 Enter 重新找一次):")
    [ -z "$a" ] && return 1
  fi
  a=$(normalize "$a")
  if ! valid_ip "$a"; then
    warn "「${a}」不是正確的 IP。應該像 192.168.1.23 這樣:四組 0~255 的數字,中間用點隔開。"
    return 1
  fi
  case "$a" in *:*) TARGET="$a" ;; *) TARGET="$a:5555" ;; esac
}

# 連上 TARGET 並等電視按「允許」;失敗時說明原因並回傳 1
connect_tv() {
  local out state limit asked=0
  printf '連線中…\n'
  out=$(adb_t 12 connect "$TARGET" 2>&1)
  case "$out" in
    *refused*)
      warn "這個 IP 有裝置,但它沒有開「USB 偵錯」(或它不是電視)。
  → 電視:設定 → 系統 → 開發人員選項 → 打開「USB 偵錯」,再試一次。"
      return 1 ;;
    *"connected to"*) ;;
    *)
      warn "這個 IP 沒有回應($TARGET)。
  → IP 可能打錯了:電視「設定 → 網路和網際網路 → 點目前的網路」看 IP 位址
  → 或電視和電腦不在同一個網路(訪客 Wi-Fi 不算)"
      return 1 ;;
  esac
  # 連上了,等電視授權:沒反應最多等 1 分鐘;電視在等人按「允許」就等到 3 分鐘
  limit=60; SECONDS=0
  while [ "$SECONDS" -lt "$limit" ]; do
    state=$(adb_t 5 -s "$TARGET" get-state 2>&1)
    case "$state" in
      device) return 0 ;;
      *unauthorized*)
        limit=180
        if [ "$asked" = 0 ]; then
          printf '\n👉 請看電視:畫面上應該跳出「要允許 USB 偵錯嗎?」\n   勾選「一律允許這部電腦」,再按「允許」。等你按完會自動繼續…\n'
          printf '   (如果電視正顯示「看電視計時」的鎖定畫面,這個視窗可能被蓋住:先用爸媽手機頁按「改成不限制」)\n'
          asked=1
        fi ;;
      *) adb_t 10 connect "$TARGET" >/dev/null 2>&1 ;;
    esac
    sleep 3
  done
  if [ "$asked" = 1 ]; then warn "等太久了,電視上一直沒有按「允許」。"
  else warn "連上之後電視沒有回應,請把電視重開機再試一次。"
  fi
  adb_t 5 disconnect "$TARGET" >/dev/null 2>&1
  return 1
}

# 找到並連上電視,失敗就讓人重來
ensure_tv() {
  local a brand model
  "$ADB" start-server </dev/null >/dev/null 2>&1
  while :; do
    if pick_tv && connect_tv; then break; fi
    a=$(ask "要再試一次嗎?按 Enter 重來,輸入 q 離開:")
    if [ "$a" = q ] || [ "$a" = Q ]; then fail "已離開,電視上沒有任何變動。檢查好再貼一次指令就可以。"; fi
  done
  brand=$(adb_t 5 -s "$TARGET" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r')
  model=$(adb_t 5 -s "$TARGET" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
  ok "連上了:$brand $model($TARGET)"
  if ! adb_t 5 -s "$TARGET" shell pm has-feature android.software.leanback 2>/dev/null | grep -q true; then
    warn "注意:這台看起來不是電視($brand $model)。"
    a=$(ask "確定要繼續嗎?輸入 y 繼續,其他鍵離開:")
    if [ "$a" != y ] && [ "$a" != Y ]; then
      adb_t 5 disconnect "$TARGET" >/dev/null 2>&1
      fail "已離開,這台裝置沒有任何變動。"
    fi
  fi
}

installed() { adb_t 10 -s "$TARGET" shell pm path "$PKG" 2>/dev/null | grep -q package:; }

do_install() {
  say "下載最新版 App"
  if [ -n "${TVTIMER_APK:-}" ]; then cp "$TVTIMER_APK" "$DIR/tv-timer.apk" || fail "找不到 $TVTIMER_APK"
  else curl -fL --progress-bar -o "$DIR/tv-timer.apk" "$APK_URL" || fail "下載失敗,請確認電腦有連上網路再試一次"
  fi
  ok "下載好了"

  say "連到電視"
  ensure_tv

  say "安裝 App(大約 10~30 秒)"
  local out a
  out=$(adb_t 120 -s "$TARGET" install -r "$DIR/tv-timer.apk" 2>&1)
  if printf '%s' "$out" | grep -q "UPDATE_INCOMPATIBLE"; then
    printf '\n電視上已經有一個「看電視計時」,但它是用別的方式裝的,沒辦法直接更新。\n'
    printf '要先刪掉舊的再裝新的。注意:舊的設定(爸媽密碼、小孩名字、看了多久)會一起清掉。\n'
    a=$(ask "要刪掉舊的、裝新的嗎?請輸入 y 再按 Enter(其他鍵取消):")
    if [ "$a" != y ] && [ "$a" != Y ]; then fail "已取消,電視上的舊版沒有動"; fi
    adb_t 60 -s "$TARGET" uninstall "$PKG" >/dev/null 2>&1
    out=$(adb_t 120 -s "$TARGET" install "$DIR/tv-timer.apk" 2>&1)
  fi
  if ! printf '%s' "$out" | grep -q "^Success"; then
    case "$out" in
      *INSUFFICIENT_STORAGE*) fail "電視的空間不夠,請刪掉一些用不到的 App 再試一次" ;;
      *) fail "安裝失敗:
$out" ;;
    esac
  fi
  ok "裝好了"

  say "打開權限、啟動 App"
  adb_t 10 -s "$TARGET" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || fail "開權限失敗,請再貼一次安裝指令"
  adb_t 10 -s "$TARGET" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
  adb_t 5 disconnect "$TARGET" >/dev/null 2>&1
  ok "完成了!"

  printf '\n\033[1m🎉 安裝完成\033[0m\n'
  printf '電視畫面最下面一行有「爸媽手機網址」和「初始爸媽密碼」,請拍照或記下來。\n'
  printf '用手機(連家裡 Wi-Fi)打開那個網址,第一次進去請先改掉密碼。\n'
  printf '之後要更新 App,再貼一次同一行安裝指令就好。\n\n'
}

do_reset_pin() {
  say "連到電視"
  ensure_tv
  installed || fail "這台電視上沒有裝「看電視計時」。"
  say "重設爸媽密碼"
  local out pin
  out=$(adb_t 15 -s "$TARGET" shell am broadcast -a "$PKG.RESET_PIN" -n "$PKG/.AdminReceiver" 2>&1)
  pin=$(printf '%s' "$out" | sed -n 's/.*data="\([0-9]*\)".*/\1/p')
  adb_t 5 disconnect "$TARGET" >/dev/null 2>&1
  [ -n "$pin" ] || fail "重設失敗。電視上的 App 可能是舊版,請先選「1 安裝或更新」更新,再來重設。
($out)"
  printf '\n\033[1m🔑 新的爸媽密碼:\033[1;33m%s\033[0m\n' "$pin"
  printf '電視鎖定畫面最下面也會顯示。小孩名字和看電視的紀錄都沒有變。\n'
  printf '請用手機打開爸媽頁面,用這組密碼登入後改成你自己的密碼。\n\n'
}

do_uninstall() {
  say "連到電視"
  ensure_tv
  if ! installed; then
    adb_t 5 disconnect "$TARGET" >/dev/null 2>&1
    ok "這台電視上本來就沒有「看電視計時」,不用移除。"
    exit 0
  fi
  printf '\n要從電視移除「看電視計時」。所有設定(爸媽密碼、小孩名字、看了多久)都會清掉,電視會馬上解鎖。\n'
  local a out
  a=$(ask "確定要移除嗎?請輸入 y 再按 Enter(其他鍵取消):")
  if [ "$a" != y ] && [ "$a" != Y ]; then fail "已取消,沒有移除"; fi
  say "移除中"
  adb_t 10 -s "$TARGET" shell am force-stop "$PKG" >/dev/null 2>&1
  out=$(adb_t 60 -s "$TARGET" uninstall "$PKG" 2>&1)
  if ! printf '%s' "$out" | grep -q "^Success"; then
    out=$(adb_t 60 -s "$TARGET" shell pm uninstall --user 0 "$PKG" 2>&1)
  fi
  adb_t 5 disconnect "$TARGET" >/dev/null 2>&1
  printf '%s' "$out" | grep -q "Success" || fail "移除失敗:
$out"
  ok "已經從電視移除了"
  printf '想再裝回來,貼同一行指令選「1」就可以。\n\n'
}

printf '\n\033[1m📺 看電視計時\033[0m\n'
printf '開始前請確認:電視已開「開發人員選項」和「USB 偵錯」,而且電視和這台電腦連同一個網路。\n\n'
printf '要做什麼?\n'
printf '  1) 安裝或更新 App\n'
printf '  2) 忘記爸媽密碼(重設密碼,其他設定保留)\n'
printf '  3) 從電視移除 App(壞掉或不用了)\n'
CHOICE=$(normalize "$(ask "請輸入 1、2 或 3,再按 Enter(直接按 Enter = 1):")")
CHOICE=${CHOICE:-1}
case "$CHOICE" in 1|2|3) ;; *) fail "看不懂「${CHOICE}」,請重新貼一次指令,輸入 1、2 或 3" ;; esac

mkdir -p "$DIR" || fail "沒辦法建立資料夾 $DIR"
say "準備 Google 官方工具(adb)"
if [ -x "$ADB" ]; then ok "已經下載過了"
else
  curl -fL --progress-bar -o "$DIR/tools.zip" "$TOOLS_URL" || fail "下載失敗,請確認電腦有連上網路再試一次"
  unzip -q -o "$DIR/tools.zip" -d "$DIR" && rm -f "$DIR/tools.zip" || fail "解壓縮失敗"
  ok "下載好了"
fi

case "$CHOICE" in
  1) do_install ;;
  2) do_reset_pin ;;
  3) do_uninstall ;;
esac
