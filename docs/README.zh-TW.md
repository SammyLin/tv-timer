<div align="center">
  <img src="logo.svg" alt="tv-timer logo" width="96" />
  <h1>tv-timer 看電視計時</h1>
  <p><b>家裡電視的看電視時間鎖。</b><br>小孩用遙控器選誰要看、看多久;其他的由爸媽在手機上同意。</p>
</div>

<p align="center">
  <a href="../README.md">English</a> | 繁體中文
</p>

![截圖](screenshot.png)

> 介面是繁體中文。可以裝在任何 Google TV / Android TV 上(開發和測試用的是 Sony BRAVIA)。

## 為什麼做這個

電視上的時間控制,通常是整台電視一組密碼,或要切換到兒童帳號。兩個小孩共用一台電視、爸媽常常在隔壁房間的客廳,這兩種都不好用。

tv-timer 把決定放在電視畫面上:

- **電視一打開就是鎖住的。** 小孩選誰要看(一個或兩個人)、看多久,按「開始」才能用。
- **時間跟著電視走。** 電視待機就暫停,下次打開按「繼續上次」,剩下的分鐘接著用。
- **每個小孩有每天的額度**,看完馬上再開一次也不會重算。
- **「不限制」要爸媽同意。** 電視顯示 QR code,爸媽用手機掃了、按同意才會開始。

## 功能

| | |
|---|---|
| **鎖定畫面** | 蓋在所有 App 上面的全螢幕畫面,按 Home、返回都跳不出去;全部用遙控器方向鍵操作 |
| **誰看、看多久** | 一次最多兩個人;快速按鈕(預設 30 / 60 分)和自訂分鐘 |
| **倒數** | 右上角半透明的小字「剩 25 分」,最後幾分鐘變成大的倒數 |
| **時間到** | 先讓後面正在播的影片暫停,播 10 秒音樂盒音效,再鎖起來 |
| **暫停與繼續** | 電視待機就暫停,剩下的分鐘保留;App 更新不會打斷正在看的 |
| **每日額度** | 每人每天(預設 120 分,0 = 不限);開始時最多只能選到剩下的額度 |
| **爸媽手機頁** | `http://電視IP:8765/`:同意不限制、加 15 / 30 分、停止、改名字、改上限和密碼、試聽音效 |
| **裝到手機** | 爸媽手機頁是 PWA,加到主畫面後像一般 App 全螢幕打開 |
| **密碼保護** | 每個動作都要爸媽密碼;錯 5 次鎖 10 分鐘。第一次輸入對之後,手機會記住 |
| **不用雲端** | 全部在電視上跑,手機頁也是電視自己送的;不用帳號、不用伺服器 |

## 截圖

| | |
|---|---|
| ![電視:繼續上次](tv-resume.png) | ![電視:不限制要爸媽掃 QR code](tv-approval.png) |
| **電視待機後再打開:** 剩下的分鐘接著用 | **「不限制」:** 爸媽掃 QR code 同意 |

<p align="center"><img src="phone-approve.png" alt="爸媽手機頁:同意不限制" width="300" />&nbsp;&nbsp;<img src="phone-watching.png" alt="爸媽手機頁:計時中還剩 25 分" width="300" /></p>
<p align="center"><b>爸媽手機頁:</b>同意或不同意;看的時候可以加時間</p>

> 截圖裡的名字都是範例資料。

## 架構

| 位置 | 內容 |
| --- | --- |
| `app/` | Android TV App(Kotlin、Jetpack Compose)。前景服務負責計時、畫鎖定畫面、跑一個小型 HTTP 伺服器 |
| `web/` | 爸媽手機頁(React、Vite、Tailwind、shadcn/ui),打包到 `app/src/main/assets/web`(打包結果有進 git) |

電視端 API:`GET /api/state`、`POST /api/do`(JSON,要爸媽密碼)。

## 安裝到電視

需要一台有 `adb` 的電腦,跟電視在同一個網路。

1. 電視:設定 → 系統 → 關於 →「Android TV 作業系統版本」連按 7 下,開啟開發人員模式
2. 設定 → 系統 → 開發人員選項 → 開啟「USB 偵錯」
3. 電腦:
   ```sh
   adb connect 電視IP            # 電視上勾「一律允許」再按「允許」
   adb install -r app-release.apk
   adb shell appops set ai.threemi.tvtimer SYSTEM_ALERT_WINDOW allow
   adb shell am start -n ai.threemi.tvtimer/.MainActivity
   ```
4. 電視會顯示爸媽手機頁網址和初始密碼,用手機打開,把密碼和小孩名字改掉

大部分電視沒有「顯示在其他應用程式上層」的設定畫面,所以 `appops` 那行一定要跑。

## 建置

```sh
# 爸媽手機頁(改了 web/ 才需要)
cd web && npm install && npm run build
TV=http://電視IP:8765 npm run dev    # 開發用,/api 轉到電視

# Android App(JDK 17、Android SDK)
./gradlew assembleRelease           # app/build/outputs/apk/release/app-release.apk
```

## 用 Tailscale 從外面連

電視不用裝 Tailscale。家裡任何一台一直開著、有裝 Tailscale 的電腦,都可以把電視轉出去:

```sh
tailscale serve --bg --https=8765 http://電視IP:8765
# 同一個 tailnet 的手機打開 https://<電腦名稱>.<tailnet>.ts.net:8765/
```

這個網址是 HTTPS,Android Chrome 可以一鍵安裝。關掉:`tailscale serve --https=8765 off`。

## 限制

- **計時中**小孩如果進「設定 → 應用程式 → 強制停止」,就停得掉(鎖定畫面出現時進不去設定)
- 電視整個重開機會回到鎖定畫面;「不限制」不會跨重開機保留
- iPhone 沒有一鍵安裝 PWA,要用「分享 → 加入主畫面」

## 授權

[MIT](../LICENSE)
