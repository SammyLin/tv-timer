# 看電視計時(Android TV / Google TV)

給家裡電視用的「看電視計時」App,裝在 Google TV / Android TV(例如 Sony BRAVIA)上。

- 電視一打開就是鎖定畫面:選誰要看(最多兩人)、看多久(快速按鈕 / 自訂),按「開始」才能用電視
- 計時中右上角小小顯示剩幾分,快到時間變成倒數;時間到播 10 秒音效、讓後面的影片暫停,再鎖起來
- 電視待機自動暫停,下次開機可以「繼續上次」
- 「不限制」要爸媽手機掃 QR code、輸入密碼同意
- 每人每天上限(預設 120 分,可改,0 = 不限)
- 爸媽手機頁 `http://電視IP:8765/`:同意不限制、加時間、停止、改名字與設定、試聽音效;可以加到手機主畫面(PWA)

## 結構

| 位置 | 內容 |
| --- | --- |
| `app/` | Android TV App(Kotlin、Jetpack Compose)。鎖定畫面是蓋在所有 App 上面的 overlay,背景服務計時,內建小型 HTTP 伺服器 |
| `web/` | 爸媽手機頁(React + Vite + Tailwind + shadcn/ui),打包到 `app/src/main/assets/web`(打包結果有進 git) |

電視端 API:`GET /api/state`、`POST /api/do`(JSON,要爸媽密碼;錯 5 次鎖 10 分鐘)。

## 建置

```sh
# 爸媽手機頁(改了 web/ 才需要)
cd web && npm install && npm run build
TV=http://電視IP:8765 npm run dev        # 開發時把 /api 轉到電視

# Android App(JDK 17、Android SDK)
./gradlew assembleRelease               # app/build/outputs/apk/release/app-release.apk
```

## 安裝到電視

1. 電視:設定 → 系統 → 關於 →「Android TV 作業系統版本」連按 7 下,開啟開發人員模式
2. 設定 → 系統 → 開發人員選項 → 開啟「USB 偵錯」
3. 電腦:
   ```sh
   adb connect 電視IP                    # 電視上勾「一律允許」再按「允許」
   adb install -r app/build/outputs/apk/release/app-release.apk
   adb shell appops set ai.threemi.tvtimer SYSTEM_ALERT_WINDOW allow
   adb shell am start -n ai.threemi.tvtimer/.MainActivity
   ```
4. 電視上會顯示爸媽手機頁網址和初始密碼,用手機打開把密碼和小孩名字改掉

## 用 Tailscale 從外面連(選用)

電視本身不用裝 Tailscale。家裡任何一台開著 Tailscale 的電腦,用 `tailscale serve` 把電視轉出去:

```sh
tailscale serve --bg --https=8765 http://電視IP:8765
# 手機(同一個 tailnet)打開 https://<這台電腦>.<tailnet>.ts.net:8765/
```

這個網址是 HTTPS,Android Chrome 可以直接一鍵安裝成 App;不在家也能用。關掉:`tailscale serve --https=8765 off`。

## 限制

- 計時中如果小孩會進「設定 → 應用程式」強制停止,就擋不住(鎖定畫面出現時進不去設定)
- 電視重開機後會回到鎖定畫面;「不限制」不會跨重開機保留
- iPhone 沒有一鍵安裝 PWA,要用「分享 → 加入主畫面」
