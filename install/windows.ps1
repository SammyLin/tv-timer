# 看電視計時:Windows 一鍵安裝到 Google TV
# 用法(貼到 PowerShell):
#   irm https://raw.githubusercontent.com/SammyLin/tv-timer/main/install/windows.ps1 | iex
# 會做的事:下載 Google 官方的 adb 工具 → 下載最新版 App → 連到電視 → 安裝 → 開權限 → 打開 App

& {
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # 下載進度條在舊版 PowerShell 會讓下載慢很多
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ApkUrl   = if ($env:TVTIMER_APK_URL) { $env:TVTIMER_APK_URL } else { "https://github.com/SammyLin/tv-timer/releases/latest/download/tv-timer.apk" }
$ToolsUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
$Pkg      = "ai.threemi.tvtimer"
$Dir      = Join-Path $env:LOCALAPPDATA "tv-timer-installer"
$Adb      = Join-Path $Dir "platform-tools\adb.exe"
$Apk      = Join-Path $Dir "tv-timer.apk"

function Say($m)  { Write-Host ""; Write-Host "▶ $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "✓ $m" -ForegroundColor Green }
function Fail($m) { Write-Host ""; Write-Host "✗ $m" -ForegroundColor Red; throw "STOP" }
# adb 常把一般訊息寫到 stderr;Windows PowerShell 5.1 在 Stop 模式下會把它當錯誤中斷,這裡要放寬
function AdbRun { $ErrorActionPreference = "Continue"; $o = (& $Adb @args 2>&1 | ForEach-Object { "$_" }) -join "`n"; return $o.Trim() }

try {
  Write-Host ""
  Write-Host "📺 看電視計時 · 安裝到電視" -ForegroundColor White
  Write-Host "開始前請確認:電視已開「開發人員選項」和「USB 偵錯」,而且電視和這台電腦連同一個網路。"
  New-Item -ItemType Directory -Force -Path $Dir | Out-Null

  Say "1/5 下載 Google 官方工具(adb)"
  if (Test-Path $Adb) { Ok "已經下載過了" }
  else {
    $zip = Join-Path $Dir "tools.zip"
    try { Invoke-WebRequest -Uri $ToolsUrl -OutFile $zip -UseBasicParsing } catch { Fail "下載失敗,請確認電腦有連上網路再試一次" }
    Expand-Archive -Path $zip -DestinationPath $Dir -Force
    Remove-Item $zip
    Ok "下載好了"
  }

  Say "2/5 下載最新版 App"
  if ($env:TVTIMER_APK) { Copy-Item $env:TVTIMER_APK $Apk -Force }
  else { try { Invoke-WebRequest -Uri $ApkUrl -OutFile $Apk -UseBasicParsing } catch { Fail "下載失敗,請確認電腦有連上網路再試一次" } }
  Ok "下載好了"

  Say "3/5 連到電視"
  Write-Host "(如果 Windows 跳出「防火牆」視窗問要不要允許 adb,請按「允許」)"
  AdbRun start-server | Out-Null
  while ($true) {
    $ip = (Read-Host "請輸入電視的 IP 位址(例如 192.168.1.23),然後按 Enter").Trim()
    if ($ip -match '^\d{1,3}(\.\d{1,3}){3}(:\d+)?$') { break }
    Write-Host "格式不對,應該像 192.168.1.23 這樣(四組數字,中間用點隔開)"
  }
  $target = if ($ip -like "*:*") { $ip } else { "${ip}:5555" }
  Write-Host "連線中…"
  AdbRun connect $target | Out-Null
  # 連不上最多等 1 分鐘;電視在等人按「允許」就等到 3 分鐘
  $limit = 60; $asked = $false; $state = ""
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt $limit) {
    $state = AdbRun -s $target get-state
    if ($state -eq "device") { break }
    if ($state -match "unauthorized") {
      $limit = 180
      if (-not $asked) {
        Write-Host ""
        Write-Host "👉 請看電視:畫面上應該跳出「要允許 USB 偵錯嗎?」" -ForegroundColor Yellow
        Write-Host "   勾選「一律允許這部電腦」,再按「允許」。等你按完會自動繼續…" -ForegroundColor Yellow
        $asked = $true
      }
    } else { AdbRun connect $target | Out-Null }
    Start-Sleep -Seconds 3
  }
  if ($state -ne "device") {
    Fail ("連不到電視($target)。請檢查:`n" +
      "  • IP 有沒有打錯(電視:設定 → 網路和網際網路 → 點目前的網路 → 看 IP 位址)`n" +
      "  • 電視的「USB 偵錯」有沒有打開`n" +
      "  • 電視和電腦是不是連同一個網路`n" +
      "  • 電視上的「允許 USB 偵錯」有沒有按允許`n" +
      "檢查完再貼一次安裝指令就可以。")
  }
  Ok "連上電視了"

  Say "4/5 安裝 App(大約 10~30 秒)"
  $out = AdbRun -s $target install -r $Apk
  if ($out -match "UPDATE_INCOMPATIBLE") {
    Write-Host ""
    Write-Host "電視上已經有一個「看電視計時」,但它是用別的方式裝的,沒辦法直接更新。"
    Write-Host "要先刪掉舊的再裝新的。注意:舊的設定(爸媽密碼、小孩名字、看了多久)會一起清掉。"
    $a = Read-Host "要刪掉舊的、裝新的嗎?請輸入 y 再按 Enter(其他鍵取消)"
    if ($a -ne "y" -and $a -ne "Y") { Fail "已取消,電視上的舊版沒有動" }
    AdbRun -s $target uninstall $Pkg | Out-Null
    $out = AdbRun -s $target install $Apk
  }
  if ($out -notmatch "(?m)^Success") { Fail "安裝失敗:`n$out" }
  Ok "裝好了"

  Say "5/5 打開權限、啟動 App"
  AdbRun -s $target shell appops set $Pkg SYSTEM_ALERT_WINDOW allow | Out-Null
  AdbRun -s $target shell am start -n "$Pkg/.MainActivity" | Out-Null
  AdbRun disconnect $target | Out-Null
  Ok "完成了!"

  Write-Host ""
  Write-Host "🎉 安裝完成" -ForegroundColor White
  Write-Host "電視上現在會顯示「爸媽手機網址」和「初始爸媽密碼」,請拍照或記下來。"
  Write-Host "用手機(連家裡 Wi-Fi)打開那個網址,第一次進去請先改掉密碼。"
  Write-Host "之後要更新 App,再貼一次同一行安裝指令就好。"
  Write-Host ""
} catch {
  if ("$_" -ne "STOP") { Write-Host ""; Write-Host "✗ 發生錯誤:$_" -ForegroundColor Red }
}
}
