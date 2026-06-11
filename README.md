# AssetScope

AssetScope 是一個本機優先的 Android 資產分析 App，用來整合 Firstrade、永豐證券與
永豐銀行資產，並以新台幣呈現跨境投資組合。

## 目前功能

- Jetpack Compose 繁中資產總覽
- 台幣與美元持倉換算
- 機構資產配置
- 成本、淨值、未實現損益與報酬率
- CSV 匯入及本機持久化
- Android SAF 授權資料夾與每 12 小時背景同步
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
