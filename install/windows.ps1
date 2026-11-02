# 看電視計時:Windows 一鍵安裝到 Google TV
# 用法(貼到 PowerShell):
#   irm https://raw.githubusercontent.com/SammyLin/tv-timer/main/install/windows.ps1 | iex
# 可以:安裝或更新 App、忘記爸媽密碼時重設、強制移除 App

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
$script:Target = ""

function Say($m)  { Write-Host ""; Write-Host "▶ $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "✓ $m" -ForegroundColor Green }
function Warn($m) { Write-Host $m -ForegroundColor Yellow }
function Fail($m) { Write-Host ""; Write-Host "✗ $m" -ForegroundColor Red; throw "STOP" }
function Done()   { throw "DONE" }
# adb 常把一般訊息寫到 stderr;Windows PowerShell 5.1 在 Stop 模式下會把它當錯誤中斷,這裡要放寬
function AdbRun { $ErrorActionPreference = "Continue"; $o = (& $Adb @args 2>&1 | ForEach-Object { "$_" }) -join "`n"; return $o.Trim() }
function IsYes($a) { return ($a -eq "y" -or $a -eq "Y" -or $a -eq "ｙ" -or $a -eq "Ｙ") }

# 中文輸入法打出來的全形數字、句號都轉成半形,空白拿掉
function Normalize($s) {
  if ($null -eq $s) { return "" }
  $s = $s -replace '\s', ''
  $chars = $s.ToCharArray() | ForEach-Object { if ($_ -ge [char]0xFF10 -and $_ -le [char]0xFF19) { [char]([int]$_ - 0xFF10 + 48) } else { $_ } }
  $s = -join $chars
  return ($s -replace '[。．，,]', '.' -replace '：', ':')
}
function ValidIp($s) {
  if ($s -notmatch '^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(:\d{1,5})?$') { return $false }
  foreach ($n in $Matches[1..4]) { if ([int]$n -gt 255) { return $false } }
  return $true
}

# 掃這台電腦所在的網段,找開著 adb(5555)的裝置
function ScanTvs {
  $ip = $null
  try {
    $cfg = Get-NetIPConfiguration -ErrorAction Stop | Where-Object { $_.IPv4DefaultGateway } | Select-Object -First 1
    if ($cfg) { $ip = @($cfg.IPv4Address)[0].IPAddress }
  } catch { }
  if (-not $ip) {
    $ip = [Net.Dns]::GetHostAddresses($env:COMPUTERNAME) | Where-Object { $_.AddressFamily -eq "InterNetwork" -and "$_" -notlike "169.254.*" -and "$_" -ne "127.0.0.1" } | Select-Object -First 1 | ForEach-Object { "$_" }
  }
  if (-not $ip) { return @() }
  $prefix = $ip -replace '\.\d+$', ''
  $probes = foreach ($i in 1..254) {
    $addr = "$prefix.$i"
    if ($addr -eq $ip) { continue }
    $c = New-Object Net.Sockets.TcpClient
    [pscustomobject]@{ Ip = $addr; Client = $c; Task = $c.ConnectAsync($addr, 5555) }
  }
  Start-Sleep -Milliseconds 2000
  $found = @($probes | Where-Object { $_.Task.Status -eq "RanToCompletion" -and $_.Client.Connected } | ForEach-Object { $_.Ip })
  $probes | ForEach-Object { try { $_.Client.Close() } catch { } }
  return $found
}

# 選電視:先自動找,找不到或不是要的就讓人打 IP。成功回傳 $true,結果放在 $script:Target
function PickTv {
  Write-Host "正在家裡網路上找電視(約 3 秒)…"
  $found = @(ScanTvs)
  if ($found.Count -eq 1) {
    Write-Host "找到一台:$($found[0])" -ForegroundColor White
    $a = Read-Host "是這台的話直接按 Enter;不是的話請輸入電視的 IP"
    if (-not $a) { $a = $found[0] }
  } elseif ($found.Count -gt 1) {
    Write-Host "找到好幾台:"
    for ($n = 0; $n -lt $found.Count; $n++) { Write-Host "  $($n + 1)) $($found[$n])" }
    $a = Normalize (Read-Host "請輸入編號(1~$($found.Count)),或直接輸入電視的 IP")
    if ($a -match '^\d+$' -and [int]$a -ge 1 -and [int]$a -le $found.Count) { $a = $found[[int]$a - 1] }
  } else {
    Warn "沒有自動找到電視。常見原因:電視的「USB 偵錯」沒開、電視和電腦不在同一個網路,或電視在別的網段。"
    $a = Read-Host "請輸入電視的 IP 位址(例如 192.168.1.23;直接按 Enter 重新找一次)"
    if (-not $a) { return $false }
  }
  $a = Normalize $a
  if (-not (ValidIp $a)) {
    Warn "「${a}」不是正確的 IP。應該像 192.168.1.23 這樣:四組 0~255 的數字,中間用點隔開。"
    return $false
  }
  $script:Target = if ($a -like "*:*") { $a } else { "${a}:5555" }
  return $true
}

# 連上電視並等電視按「允許」;失敗時說明原因並回傳 $false
function ConnectTv {
  $t = $script:Target
  Write-Host "連線中…"
  $out = AdbRun connect $t
  if ($out -match "refused") {
    Warn "這個 IP 有裝置,但它沒有開「USB 偵錯」(或它不是電視)。`n  → 電視:設定 → 系統 → 開發人員選項 → 打開「USB 偵錯」,再試一次。"
    return $false
  }
  if ($out -notmatch "connected to") {
    Warn "這個 IP 沒有回應($t)。`n  → IP 可能打錯了:電視「設定 → 網路和網際網路 → 點目前的網路」看 IP 位址`n  → 或電視和電腦不在同一個網路(訪客 Wi-Fi 不算)"
    return $false
  }
  # 連上了,等電視授權:沒反應最多等 1 分鐘;電視在等人按「允許」就等到 3 分鐘
  $limit = 60; $asked = $false
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt $limit) {
    $state = AdbRun -s $t get-state
    if ($state -eq "device") { return $true }
    if ($state -match "unauthorized") {
      $limit = 180
      if (-not $asked) {
        Write-Host ""
        Write-Host "👉 請看電視:畫面上應該跳出「要允許 USB 偵錯嗎?」" -ForegroundColor Yellow
        Write-Host "   勾選「一律允許這部電腦」,再按「允許」。等你按完會自動繼續…" -ForegroundColor Yellow
        Write-Host "   (如果電視正顯示「看電視計時」的鎖定畫面,這個視窗可能被蓋住:先用爸媽手機頁按「改成不限制」)"
        $asked = $true
      }
    } else { AdbRun connect $t | Out-Null }
    Start-Sleep -Seconds 3
  }
  if ($asked) { Warn "等太久了,電視上一直沒有按「允許」。" } else { Warn "連上之後電視沒有回應,請把電視重開機再試一次。" }
  AdbRun disconnect $t | Out-Null
  return $false
}

# 找到並連上電視,失敗就讓人重來
function EnsureTv {
  AdbRun start-server | Out-Null
  while ($true) {
    if ((PickTv) -and (ConnectTv)) { break }
    $a = Read-Host "要再試一次嗎?按 Enter 重來,輸入 q 離開"
    if ($a -eq "q" -or $a -eq "Q") { Fail "已離開,電視上沒有任何變動。檢查好再貼一次指令就可以。" }
  }
  $t = $script:Target
  $brand = AdbRun -s $t shell getprop ro.product.manufacturer
  $model = AdbRun -s $t shell getprop ro.product.model
  Ok "連上了:$brand $model($t)"
  if ((AdbRun -s $t shell pm has-feature android.software.leanback) -notmatch "true") {
    Warn "注意:這台看起來不是電視($brand $model)。"
    $a = Read-Host "確定要繼續嗎?輸入 y 繼續,其他鍵離開"
    if (-not (IsYes $a)) { AdbRun disconnect $t | Out-Null; Fail "已離開,這台裝置沒有任何變動。" }
  }
}

function Installed { return ((AdbRun -s $script:Target shell pm path $Pkg) -match "package:") }

function DoInstall {
  Say "下載最新版 App"
  if ($env:TVTIMER_APK) { Copy-Item $env:TVTIMER_APK $Apk -Force }
  else { try { Invoke-WebRequest -Uri $ApkUrl -OutFile $Apk -UseBasicParsing } catch { Fail "下載失敗,請確認電腦有連上網路再試一次" } }
  Ok "下載好了"

  Say "連到電視"
  EnsureTv
  $t = $script:Target

  Say "安裝 App(大約 10~30 秒)"
  # -i:把 App 登記成自己的安裝者,之後爸媽可以用手機按「更新」
  $out = AdbRun -s $t install -r -i $Pkg $Apk
  if ($out -match "UPDATE_INCOMPATIBLE") {
    Write-Host ""
    Write-Host "電視上已經有一個「看電視計時」,但它是用別的方式裝的,沒辦法直接更新。"
    Write-Host "要先刪掉舊的再裝新的。注意:舊的設定(爸媽密碼、小孩名字、看了多久)會一起清掉。"
    $a = Read-Host "要刪掉舊的、裝新的嗎?請輸入 y 再按 Enter(其他鍵取消)"
    if (-not (IsYes $a)) { Fail "已取消,電視上的舊版沒有動" }
    AdbRun -s $t uninstall $Pkg | Out-Null
    $out = AdbRun -s $t install -i $Pkg $Apk
  }
  if ($out -notmatch "(?m)^Success") {
    if ($out -match "INSUFFICIENT_STORAGE") { Fail "電視的空間不夠,請刪掉一些用不到的 App 再試一次" }
    Fail "安裝失敗:`n$out"
  }
  Ok "裝好了"

  Say "打開權限、啟動 App"
  # 全新安裝時 -i 不會生效,要再覆蓋裝一次才登記得上
  if ((AdbRun -s $t shell dumpsys package $Pkg) -notmatch "installerPackageName=$Pkg") { AdbRun -s $t install -r -i $Pkg $Apk | Out-Null }
  AdbRun -s $t shell appops set $Pkg SYSTEM_ALERT_WINDOW allow | Out-Null
  AdbRun -s $t shell appops set $Pkg REQUEST_INSTALL_PACKAGES allow | Out-Null
  AdbRun -s $t shell am start -n "$Pkg/.MainActivity" | Out-Null
  AdbRun disconnect $t | Out-Null
  Ok "完成了!"

  Write-Host ""
  Write-Host "🎉 安裝完成" -ForegroundColor White
  Write-Host "電視畫面最下面一行有「爸媽手機網址」和「初始爸媽密碼」,請拍照或記下來。"
  Write-Host "用手機(連家裡 Wi-Fi)打開那個網址,第一次進去請先改掉密碼。"
  Write-Host "之後要更新 App:爸媽手機頁「設定」裡按「檢查更新」,或再貼一次同一行安裝指令。"
  Write-Host ""
}

function DoResetPin {
  Say "連到電視"
  EnsureTv
  $t = $script:Target
  if (-not (Installed)) { Fail "這台電視上沒有裝「看電視計時」。" }
  Say "重設爸媽密碼"
  $out = AdbRun -s $t shell am broadcast -a "$Pkg.RESET_PIN" -n "$Pkg/.AdminReceiver"
  AdbRun disconnect $t | Out-Null
  if ($out -notmatch 'data="(\d+)"') { Fail "重設失敗。電視上的 App 可能是舊版,請先選「1 安裝或更新」更新,再來重設。`n($out)" }
  $pin = $Matches[1]
  Write-Host ""
  Write-Host "🔑 新的爸媽密碼:$pin" -ForegroundColor Yellow
  Write-Host "電視鎖定畫面最下面也會顯示。小孩名字和看電視的紀錄都沒有變。"
  Write-Host "請用手機打開爸媽頁面,用這組密碼登入後改成你自己的密碼。"
  Write-Host ""
}

function DoUninstall {
  Say "連到電視"
  EnsureTv
  $t = $script:Target
  if (-not (Installed)) { AdbRun disconnect $t | Out-Null; Ok "這台電視上本來就沒有「看電視計時」,不用移除。"; Done }
  Write-Host ""
  Write-Host "要從電視移除「看電視計時」。所有設定(爸媽密碼、小孩名字、看了多久)都會清掉,電視會馬上解鎖。"
  $a = Read-Host "確定要移除嗎?請輸入 y 再按 Enter(其他鍵取消)"
  if (-not (IsYes $a)) { Fail "已取消,沒有移除" }
  Say "移除中"
  AdbRun -s $t shell am force-stop $Pkg | Out-Null
  $out = AdbRun -s $t uninstall $Pkg
  if ($out -notmatch "(?m)^Success") { $out = AdbRun -s $t shell pm uninstall --user 0 $Pkg }
  AdbRun disconnect $t | Out-Null
  if ($out -notmatch "Success") { Fail "移除失敗:`n$out" }
  Ok "已經從電視移除了"
  Write-Host "想再裝回來,貼同一行指令選「1」就可以。"
  Write-Host ""
}

try {
  Write-Host ""
  Write-Host "📺 看電視計時" -ForegroundColor White
  Write-Host "開始前請確認:電視已開「開發人員選項」和「USB 偵錯」,而且電視和這台電腦連同一個網路。"
  Write-Host ""
  Write-Host "要做什麼?"
  Write-Host "  1) 安裝或更新 App"
  Write-Host "  2) 忘記爸媽密碼(重設密碼,其他設定保留)"
  Write-Host "  3) 從電視移除 App(壞掉或不用了)"
  $choice = Normalize (Read-Host "請輸入 1、2 或 3,再按 Enter(直接按 Enter = 1)")
  if (-not $choice) { $choice = "1" }
  if ($choice -notin @("1", "2", "3")) { Fail "看不懂「${choice}」,請重新貼一次指令,輸入 1、2 或 3" }

  New-Item -ItemType Directory -Force -Path $Dir | Out-Null
  Say "準備 Google 官方工具(adb)"
  if (Test-Path $Adb) { Ok "已經下載過了" }
  else {
    $zip = Join-Path $Dir "tools.zip"
    try { Invoke-WebRequest -Uri $ToolsUrl -OutFile $zip -UseBasicParsing } catch { Fail "下載失敗,請確認電腦有連上網路再試一次" }
    Expand-Archive -Path $zip -DestinationPath $Dir -Force
    Remove-Item $zip
    Ok "下載好了"
  }
  Write-Host "(如果 Windows 跳出「防火牆」視窗問要不要允許 adb,請按「允許」)"

  switch ($choice) {
    "1" { DoInstall }
    "2" { DoResetPin }
    "3" { DoUninstall }
  }
} catch {
  if ("$_" -ne "STOP" -and "$_" -ne "DONE") { Write-Host ""; Write-Host "✗ 發生錯誤:$_" -ForegroundColor Red }
}
}
