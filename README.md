# AssetScope

AssetScope 是一個本機優先的 Android 資產分析 App，用來整合 Firstrade、永豐證券與
永豐銀行資產，並以新台幣呈現跨境投資組合。

## 目前功能

- Jetpack Compose 繁中資產總覽
- 台幣與美元持倉換算
- 機構資產配置
- 成本、淨值、未實現損益與報酬率
- CSV 匯入及本機持久化
- 可由其他 App「分享」CSV 至 AssetScope，或直接以 AssetScope 開啟 CSV
- Android SAF 授權資料夾與每 12 小時背景同步
- 電腦端 FastAPI 唯讀伺服器與 Token 驗證
- Android 區網伺服器同步與背景更新
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
D:\AppDev\server\capture-firstrade.cmd
```

請只在程式開啟的 Firstrade 官方瀏覽器頁面輸入帳密與 MFA，不要將金融帳密貼到對話、
`.env` 或程式碼。登入狀態及原始下載檔只保存在本機且不納入 Git。

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
