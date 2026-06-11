# AssetScope

AssetScope 是一個本機優先的 Android 資產分析 App，用來整合 Firstrade、永豐證券與
永豐銀行資產，並以新台幣呈現跨境投資組合。

GitHub: <https://github.com/Kennythecat5566/AssetScope>

## 安裝簽章衝突

若 Android 顯示「應用程式套件與現有套件衝突」，代表手機曾安裝
Android Studio 產生的舊 debug APK。請先移除手機上的舊 AssetScope，
再安裝 GitHub Release 的正式 APK。這是一次性遷移；之後的正式版本
都使用相同簽章，可直接透過 App 內更新覆蓋安裝。

新版 debug App 使用獨立的 `tw.kensuke.assetscope.debug` 套件名稱與
`AssetScope Dev` 名稱，因此可與正式版並存，不會再干擾正式更新。

## 目前功能

- Jetpack Compose 繁中資產總覽
- 台幣與美元持倉換算
- 機構資產配置
- 成本、淨值、未實現損益與報酬率
- 已實現損益、股息收入與總投資報酬
- Firstrade 買入／賣出／股息交易時間軸
- CSV 匯入及本機持久化
- 可由其他 App「分享」CSV 至 AssetScope，或直接以 AssetScope 開啟 CSV
- Android SAF 授權資料夾與每 12 小時背景同步
- 電腦端 FastAPI 唯讀伺服器與 Token 驗證
- Android 區網伺服器同步與背景更新
- GitHub Release 自動更新檢查與 APK 安裝流程
- 純 Kotlin 資產計算與 CSV parser 單元測試

## 開發環境

- Android Studio（建議使用內建 JDK 17 或 21）
- Android SDK 35
- Kotlin 2.1.20
- Gradle Wrapper

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## CSV 格式

範例位於 `samples/holdings-template.csv`。必要欄位：

```text
institution,account,symbol,name,type,currency,quantity,average_cost,market_price
```

`institution` 可使用 `firstrade`、`永豐證券` 或 `永豐銀行`。`type` 支援
`STOCK`、`ETF`、`CASH`、`DEPOSIT`；`currency` 支援 `TWD` 與 `USD`。

## 架構

```text
app/
  data/       Repository、本機儲存、CSV parser
  domain/     資產模型與純計算邏輯
  ui/         Compose 畫面與 ViewModel
server/
  app/        FastAPI、資料彙整與來源連接器
  data/       電腦端匯入資料
  tests/      後端測試
```

App 不保存金融機構密碼，也不以 WebView 或網頁爬蟲模擬登入。後續串接永豐證券
Shioaji 時，應由獨立後端保管 API 憑證並提供唯讀同步。

## App 更新

App 啟動時會檢查 GitHub 最新 Release。發現新版本後顯示更新卡，下載完成會開啟
Android 系統安裝確認畫面。Android 不允許一般 App 靜默安裝 APK；首次更新時可能
需要允許 AssetScope「安裝未知應用程式」。

推送版本標籤即可建立 Release：

```powershell
git tag v0.2.0
git push origin v0.2.0
```

## 自動同步限制

Android App 沙箱禁止 AssetScope 直接讀取 Firstrade、永豐證券或永豐銀行 App 的
私有資料。現在的自動同步會讀取使用者透過系統檔案選擇器授權的資料夾，並選擇其中
最後更新的 CSV。若要做到完全免匯出的同步：

- 永豐證券：透過自有後端串接官方 Shioaji，手機端只取得唯讀彙總資料。
- 永豐銀行：須使用銀行正式 Open Banking/TSP 授權流程。
- Firstrade：在官方未提供個人帳戶 API 時，維持官方 CSV 匯出方式。

## 電腦伺服器同步

完整說明位於 `server/README.md`。首次啟動：

```powershell
cd D:\AppDev\server
.\setup.cmd
.\start.cmd
```

設定 Windows 登入時自動啟動：

```powershell
D:\AppDev\server\install-startup-task.cmd
```

`start.cmd` 可以重複執行；若伺服器已在 `8787` 執行，會直接顯示已啟動，不會再建立第二份程序。

Firstrade 瀏覽器輔助匯出：

```powershell
D:\AppDev\server\capture-firstrade-normal-browser.cmd
```

若自動化瀏覽器顯示參考代碼 `2192`，請勿反覆嘗試。新版流程會使用你平常的瀏覽器登入，
程式只監控 Windows 下載資料夾的新 CSV，不會讀取帳密、Cookie 或 MFA。

登入成功後仍需在網頁中操作：

```text
我的帳戶 → 稅務中心（Tax Center）
→ Download Account Information → Excel CSV Files → Download
```

終端停在「正在監控」是正常狀態，下載完成後才會繼續。

下載後會自動產生 `server/data/imports/firstrade.csv`。由於 Firstrade 交易歷史檔
不包含即時行情，目前市價暫用最近成交價；手機需在「PC SERVER」按一次「立即同步」。

Firstrade 交易歷史會重建移動平均成本、已實現損益、未實現損益與股息收入。App
顯示最近 20 筆活動，包含交易日期、數量、成交價、現金金額及賣出損益。

目前電腦區網位址為 `192.168.0.102`，Android App 中可設定：

```text
http://192.168.0.102:8787
```

API Token 必須與 `server/.env` 的 `ASSETSCOPE_API_TOKEN` 相同。手機與電腦需在
同一個 Wi-Fi。HTTP 只允許私人區網；若未來從外網存取，必須架設 HTTPS。

若 PowerShell 顯示「已停用指令碼執行」，請執行：

```powershell
D:\AppDev\server\allow-firewall.cmd
```

並接受 Windows 系統管理員權限提示。這不會永久修改系統的指令碼執行政策。

## 建議匯入流程

### 分享至 AssetScope

1. 在金融 App 或瀏覽器使用官方「下載／匯出 CSV」。
2. 點選系統「分享」並選擇 AssetScope。
3. AssetScope 會立即解析並更新資產資料。

也可以在檔案管理器點選 CSV，選擇以 AssetScope 開啟。匯入檔案上限為 5 MB。

### 資料夾自動同步

1. 將官方匯出的標準化 CSV 固定存入同一個共享資料夾。
2. 在 AssetScope 選擇該資料夾並授權唯讀存取。
3. App 每 12 小時讀取最後更新的 CSV，也可手動按「立即同步」。

AssetScope 無法主動按下其他 App 內的「匯出」按鈕。若來源 App 沒有 API、排程匯出、
分享 Intent 或共享檔案功能，匯出動作仍須由使用者在來源 App 中觸發。
