#!/bin/bash
# 看電視計時:Mac 一鍵安裝到 Google TV
# 用法(貼到「終端機」):
#   curl -fsSL https://raw.githubusercontent.com/SammyLin/tv-timer/main/install/mac.sh | bash
# 會做的事:下載 Google 官方的 adb 工具 → 下載最新版 App → 連到電視 → 安裝 → 開權限 → 打開 App
set -u

APK_URL="${TVTIMER_APK_URL:-https://github.com/SammyLin/tv-timer/releases/latest/download/tv-timer.apk}"
TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
PKG="ai.threemi.tvtimer"
DIR="$HOME/.tv-timer-installer"
ADB="$DIR/platform-tools/adb"

say()  { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31m✗ %s\033[0m\n' "$*"; exit 1; }
ask()  { local a; read -r -p "$1" a </dev/tty; printf '%s' "$a"; }
# adb 偶爾會卡住,最多等幾秒。
# </dev/null:用「curl | bash」跑的時候程式本身從 stdin 來,adb shell 會把後面的程式吃掉
adb_t() { local t=$1; shift; perl -e 'alarm shift; exec @ARGV' "$t" "$ADB" "$@" </dev/null; }

printf '\n\033[1m📺 看電視計時 · 安裝到電視\033[0m\n'
printf '開始前請確認:電視已開「開發人員選項」和「USB 偵錯」,而且電視和這台電腦連同一個網路。\n'

mkdir -p "$DIR" || fail "沒辦法建立資料夾 $DIR"

say "1/5 下載 Google 官方工具(adb)"
if [ -x "$ADB" ]; then ok "已經下載過了"
else
  curl -fL --progress-bar -o "$DIR/tools.zip" "$TOOLS_URL" || fail "下載失敗,請確認電腦有連上網路再試一次"
  unzip -q -o "$DIR/tools.zip" -d "$DIR" && rm -f "$DIR/tools.zip" || fail "解壓縮失敗"
  ok "下載好了"
fi

say "2/5 下載最新版 App"
if [ -n "${TVTIMER_APK:-}" ]; then cp "$TVTIMER_APK" "$DIR/tv-timer.apk" || fail "找不到 $TVTIMER_APK"
else curl -fL --progress-bar -o "$DIR/tv-timer.apk" "$APK_URL" || fail "下載失敗,請確認電腦有連上網路再試一次"
fi
ok "下載好了"

say "3/5 連到電視"
"$ADB" start-server </dev/null >/dev/null 2>&1
while :; do
  IP=$(ask "請輸入電視的 IP 位址(例如 192.168.1.23),然後按 Enter:" | tr -d ' ')
  case "$IP" in *:*) TARGET="$IP" ;; *) TARGET="$IP:5555" ;; esac
  if [[ "$IP" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]+)?$ ]]; then break; fi
  printf '格式不對,應該像 192.168.1.23 這樣(四組數字,中間用點隔開)\n'
done
printf '連線中…\n'
adb_t 10 connect "$TARGET" >/dev/null 2>&1
# 連不上最多等 1 分鐘;電視在等人按「允許」就等到 3 分鐘
STATE="" ; LIMIT=60 ; ASKED=0 ; SECONDS=0
while [ "$SECONDS" -lt "$LIMIT" ]; do
  STATE=$(adb_t 5 -s "$TARGET" get-state 2>&1)
  case "$STATE" in
    device) break ;;
    *unauthorized*)
      LIMIT=180
      if [ "$ASKED" = 0 ]; then
        printf '\n👉 請看電視:畫面上應該跳出「要允許 USB 偵錯嗎?」\n   勾選「一律允許這部電腦」,再按「允許」。等你按完會自動繼續…\n'
        ASKED=1
      fi ;;
    *) adb_t 10 connect "$TARGET" >/dev/null 2>&1 ;;
  esac
  sleep 3
done
[ "$STATE" = device ] || fail "連不到電視($TARGET)。請檢查:
  • IP 有沒有打錯(電視:設定 → 網路和網際網路 → 點目前的網路 → 看 IP 位址)
  • 電視的「USB 偵錯」有沒有打開
  • 電視和電腦是不是連同一個網路
  • 電視上的「允許 USB 偵錯」有沒有按允許
檢查完再貼一次安裝指令就可以。"
ok "連上電視了"

say "4/5 安裝 App(大約 10~30 秒)"
OUT=$(adb_t 120 -s "$TARGET" install -r "$DIR/tv-timer.apk" 2>&1)
if printf '%s' "$OUT" | grep -q "UPDATE_INCOMPATIBLE"; then
  printf '\n電視上已經有一個「看電視計時」,但它是用別的方式裝的,沒辦法直接更新。\n'
  printf '要先刪掉舊的再裝新的。注意:舊的設定(爸媽密碼、小孩名字、看了多久)會一起清掉。\n'
  A=$(ask "要刪掉舊的、裝新的嗎?請輸入 y 再按 Enter(其他鍵取消):")
  [ "$A" = y ] || [ "$A" = Y ] || fail "已取消,電視上的舊版沒有動"
  adb_t 60 -s "$TARGET" uninstall "$PKG" >/dev/null 2>&1
  OUT=$(adb_t 120 -s "$TARGET" install "$DIR/tv-timer.apk" 2>&1)
fi
printf '%s' "$OUT" | grep -q "^Success" || fail "安裝失敗:
$OUT"
ok "裝好了"

say "5/5 打開權限、啟動 App"
adb_t 10 -s "$TARGET" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || fail "開權限失敗,請再貼一次安裝指令"
adb_t 10 -s "$TARGET" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
adb_t 5 disconnect "$TARGET" >/dev/null 2>&1
ok "完成了!"

printf '\n\033[1m🎉 安裝完成\033[0m\n'
printf '電視畫面最下面一行有「爸媽手機網址」和「初始爸媽密碼」,請拍照或記下來。\n'
printf '用手機(連家裡 Wi-Fi)打開那個網址,第一次進去請先改掉密碼。\n'
printf '之後要更新 App,再貼一次同一行安裝指令就好。\n\n'
