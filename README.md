# AssetScope

AssetScope 是一套個人資產管理系統，由 Android App 與 Windows 電腦上的
FastAPI 伺服器組成。它可以彙整 Firstrade、永豐證券、銀行存款與信用卡消費
資料，並提供資產配置、投資績效、交易明細、K 線、資產趨勢與模擬交易機器人。

專案網址：<https://github.com/Kennythecat5566/AssetScope>

iOS 版本正在 `feature/ios-app` 分支開發，macOS/Xcode 建置方式請參考
[ios/README.md](ios/README.md)。

> [!IMPORTANT]
> AssetScope 是個人分析工具，不是券商或銀行的官方 App。內建交易機器人只會
> 進行模擬交易，伺服器沒有真實自動下單端點。

## 主要功能

- 彙整 TWD 與 USD 資產，支援 App 內切換顯示幣別
- 自動更新 USD/TWD 匯率
- 顯示資產分布、總資產趨勢、投資績效與股息
- 顯示持股清單、走勢縮圖及日線、月線、季線、年線 K 線
- 顯示 Firstrade 與永豐證券交易明細
- 匯入永豐信用卡消費 CSV，進行分類與支出分析
- 透過電腦伺服器同步手機資料
- 支援區域網路與 Tailscale 私有網路連線
- 從 GitHub Releases 檢查並下載 App 新版本
- 三個持續運行的虛擬交易機器人與績效比較
- 中文、英文介面

## 系統架構

```text
Android App
    |
    | Bearer Token / HTTP（區域網路或 Tailscale）
    v
FastAPI Server
    |
    +-- 標準化 CSV
    +-- Firstrade 官方匯出 CSV
    +-- Shioaji 永豐證券 API
    +-- 永豐信用卡 CSV
    +-- Yahoo Finance 行情與匯率
    +-- 本機模擬交易狀態
```

主要目錄：

```text
app/
  src/main/java/tw/kensuke/assetscope/
    data/       API、CSV、Repository 與資料模型
    domain/     資產計算與商業邏輯
    ui/         Jetpack Compose 畫面與 ViewModel
server/
  app/          FastAPI、資料解析、行情與券商連接器
  data/         本機匯入、快取與模擬交易資料
  tests/        Python 測試
samples/        CSV 範例
.github/
  workflows/    GitHub Actions 發版流程
```

## 技術需求

AssetScope 目前以 Windows 10/11 作為電腦伺服器與主要開發環境。

| 工具 | 需求 |
| --- | --- |
| Git | 最新穩定版 |
| Python | 3.11 以上 |
| Android Studio | 可安裝 Android SDK 35 的穩定版 |
| JDK | 17 或 21 |
| Android SDK | API 35、Build Tools 35 |
| Android 手機 | Android 8.0（API 26）以上 |
| 網路 | 手機與電腦同一區域網路，或使用 Tailscale |

官方安裝文件：

- [Git for Windows](https://git-scm.com/download/win)
- [Python on Windows](https://docs.python.org/3/using/windows.html)
- [安裝 Android Studio](https://developer.android.com/studio/install)
- [在實體 Android 裝置執行 App](https://developer.android.com/studio/run/device)
- [Tailscale 下載](https://tailscale.com/download)
- [Shioaji 官方文件](https://sinotrade.github.io/)

## 從零開始建置

以下指令均在 PowerShell 執行。

### 1. 安裝開發工具

安裝 Git、Python 3.11 以上與 Android Studio。Android Studio 第一次啟動時，
依照 Setup Wizard 安裝 Android SDK，並在 SDK Manager 確認已安裝：

- Android SDK Platform 35
- Android SDK Build-Tools 35
- Android SDK Platform-Tools

確認命令列工具：

```powershell
git --version
python --version
java -version
```

`java -version` 必須顯示 17 或 21。若 PowerShell 仍使用舊版 Java，可暫時指定
Android Studio 內附的 JDK：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

若 Android Studio 安裝在其他位置，請改成實際的 `jbr` 或 JDK 路徑。

### 2. 下載專案

```powershell
git clone https://github.com/Kennythecat5566/AssetScope.git
cd AssetScope
```

若專案已存在：

```powershell
cd D:\AppDev
git pull --ff-only
```

請勿將不同裝置的未提交修改直接互相覆蓋。切換裝置前先完成
`git add`、`git commit` 與 `git push`，另一台裝置再執行 `git pull --ff-only`。

### 3. 建立電腦伺服器環境

```powershell
cd server
.\setup.cmd
```

這個指令會：

1. 建立 `server\.venv` Python 虛擬環境
2. 安裝 FastAPI、測試工具與瀏覽器輔助套件
3. 從 `.env.example` 建立本機專用的 `.env`

若 `python` 指令不存在，重新開啟 PowerShell；仍無法使用時，重新安裝 Python
並勾選「Add Python to PATH」。

### 4. 設定伺服器 Token

產生一組隨機 Token：

```powershell
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

開啟 `server\.env`，把輸出的單行文字填入：

```dotenv
ASSETSCOPE_API_TOKEN=請替換成剛才產生的隨機Token
ASSETSCOPE_IMPORT_DIR=data/imports
ASSETSCOPE_USD_TO_TWD=32.4

ASSETSCOPE_SHIOAJI_ENABLED=false
ASSETSCOPE_SHIOAJI_API_KEY=
ASSETSCOPE_SHIOAJI_SECRET_KEY=
```

注意事項：

- Token 至少使用 16 個字元，建議保留完整的隨機輸出
- 不要在 App 欄位加入 `Bearer` 前綴
- 不要複製換行、引號或多餘空白
- `.env`、`api_key.txt` 與 API 金鑰不可提交到 Git

可選設定：

```dotenv
ASSETSCOPE_EXCHANGE_RATE_AUTO_UPDATE=true
ASSETSCOPE_EXCHANGE_RATE_CACHE_HOURS=6
ASSETSCOPE_SHIOAJI_HISTORY_DAYS=365
ASSETSCOPE_PAPER_TRADING_ENABLED=true
ASSETSCOPE_PAPER_TRADING_INTERVAL_MINUTES=60
ASSETSCOPE_PAPER_TRADING_INITIAL_CASH_TWD=1000000
```

### 5. 啟動伺服器

```powershell
cd D:\AppDev\server
.\start.cmd
```

測試伺服器：

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
```

正常情況會回傳 `status` 為 `ok`。伺服器預設監聽：

```text
http://0.0.0.0:8787
```

`0.0.0.0` 只代表監聽所有網路介面，不能填入手機。手機必須使用電腦的實際
區域網路 IP，例如 `http://192.168.0.102:8787`。

執行以下指令尋找電腦 IPv4：

```powershell
ipconfig
```

找到目前 Wi-Fi 或乙太網路介面的 `IPv4 Address`。

### 6. 開放 Windows 防火牆

以系統管理員身分執行：

```powershell
cd D:\AppDev\server
.\allow-firewall.cmd
```

規則只開放 TCP 8787 給本機子網路。不要直接把 8787 Port Forward 到公開
網際網路。

### 7. 建置 Android App

回到專案根目錄：

```powershell
cd D:\AppDev
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

建置完成的 APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

也可以在 Android Studio：

1. 選擇 `File > Open`，開啟專案根目錄
2. 將 Gradle JDK 設為 17 或 21
3. 等待 Gradle Sync 完成
4. 開啟手機的開發人員選項與 USB 偵錯
5. 選擇 `app` Run Configuration
6. 按下 Run 安裝 `AssetScope Dev`

命令列安裝：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Debug App 的套件名稱為 `tw.kensuke.assetscope.debug`，顯示名稱為
`AssetScope Dev`，可以和正式版同時安裝。

### 8. 連接手機與伺服器

1. 確認手機與電腦連到同一個 Wi-Fi
2. 確認 `server\start.cmd` 正在運行
3. 開啟 AssetScope 的「同步」頁面
4. 伺服器網址填入 `http://電腦IPv4:8787`
5. API Token 填入 `server\.env` 的 `ASSETSCOPE_API_TOKEN`
6. 點擊「連接並同步」

不要使用：

- `http://127.0.0.1:8787`：在手機上代表手機自己
- `http://0.0.0.0:8787`：這不是可連線的目的位址
- 公開 IP 的純 HTTP：App 只允許私有網路 HTTP，公開網址必須使用 HTTPS

## 從外部網路連回家中電腦

建議使用 Tailscale，不需要路由器 Port Forward，也不必把伺服器公開到網際網路。

### 電腦端

安裝並登入 Tailscale，或執行：

```powershell
cd D:\AppDev\server
.\setup-tailscale.cmd
```

記下電腦的 Tailscale IPv4，通常是 `100.x.x.x`。

### 手機端

1. 安裝 Tailscale Android App
2. 使用和電腦相同的 Tailscale 帳號登入
3. 開啟 Tailscale 連線
4. AssetScope 伺服器網址填入 `http://100.x.x.x:8787`

Tailscale 在 Android 上會顯示為 VPN，這是建立私人加密網路的正常方式。若不想
使用 VPN 介面，需另外部署具備有效 TLS 憑證的 HTTPS 反向代理；不建議直接
公開 FastAPI 的 8787 Port。

## 資料匯入

### 標準持股 CSV

範例位於 `samples\holdings-template.csv`：

```csv
institution,account,symbol,name,type,currency,quantity,average_cost,market_price
firstrade,brokerage,VOO,Vanguard S&P 500 ETF,ETF,USD,10,450.00,500.00
sinopac,stock,2330,台積電,STOCK,TWD,1000,800.00,900.00
sinopac,bank,TWD,新台幣存款,CASH,TWD,1,300000,300000
```

欄位：

| 欄位 | 說明 |
| --- | --- |
| `institution` | 金融機構，例如 `firstrade`、`sinopac` |
| `account` | 帳戶識別名稱 |
| `symbol` | 股票代號或幣別 |
| `name` | 顯示名稱 |
| `type` | `STOCK`、`ETF`、`CASH`、`DEPOSIT` |
| `currency` | `TWD` 或 `USD` |
| `quantity` | 股數或單位數 |
| `average_cost` | 單位平均成本 |
| `market_price` | 單位市價 |

將 CSV 放到：

```text
server\data\imports\
```

再到 App 點擊同步。伺服器匯入資料與原始金融檔案都已由 `.gitignore` 排除。

### Firstrade

Firstrade 目前使用官方網站匯出的 CSV。啟動下載監看器：

```powershell
cd D:\AppDev\server
.\capture-firstrade-normal-browser.cmd
```

接著在一般瀏覽器：

1. 登入 Firstrade 官方網站並完成 MFA
2. 前往 Tax Center 或帳戶紀錄
3. 使用官方下載功能匯出 CSV
4. 等待終端機偵測並轉換檔案

處理後的資料會寫入 `server\data\imports\firstrade.csv`。請勿把 Firstrade
密碼寫入腳本或 `.env`。遇到驗證代碼或裝置認證時，應在官方瀏覽器頁面完成。

### 永豐證券 Shioaji

Shioaji 可以讀取台股持倉、交割帳戶餘額與 API 可取得的交易紀錄。先安裝選用套件：

```powershell
cd D:\AppDev\server
.\.venv\Scripts\python.exe -m pip install -e ".[shioaji]"
```

設定 API Key 與 Secret Key：

```powershell
server\configure-shioaji.cmd
```

依終端機指示，先把 Key 複製到 Windows 剪貼簿，再按 Enter。腳本會把資料保存到
`server\.env`，不需要將密鑰貼到終端機或提交到 Git。

永豐 API 必須先完成官方簽署與 Python API 模擬測試：

```powershell
server\test-shioaji-api.cmd
```

此專案的測試腳本使用模擬模式；執行前仍應核對終端機顯示的環境。官方 Token、
憑證及測試規範請以 [Shioaji 文件](https://sinotrade.github.io/tutor/prepare/token/)
為準。

啟用連接器：

```dotenv
ASSETSCOPE_SHIOAJI_ENABLED=true
ASSETSCOPE_SHIOAJI_API_KEY=你的APIKey
ASSETSCOPE_SHIOAJI_SECRET_KEY=你的SecretKey
```

修改 `.env` 後重新執行 `server\start.cmd`。若 API 狀態仍為未啟用、帳號
`signed=False`，或出現 `Please sign ... first`，需回到永豐官方頁面完成簽署與
API 測試，並等待官方審核。

Shioaji 的交割帳戶不是完整的網銀 Open Banking API；一般活存或其他銀行帳戶
仍需標準 CSV 或銀行正式授權介面。

### 永豐信用卡消費

範例位於 `samples\sinopac-card-template.csv`。可將永豐官方匯出的信用卡 CSV
放到 `server\data\imports\`，檔名建議以 `sinopac-card` 開頭。

瀏覽器輔助流程：

```powershell
cd D:\AppDev\server
.\capture-sinopac-card.cmd
```

登入、MFA、驗證碼與下載動作仍由使用者在永豐官方頁面完成。不要把網銀密碼存入
Playwright 腳本、環境變數或 Git。

## 自動啟動伺服器

設定 Windows 登入後自動啟動：

```powershell
cd D:\AppDev\server
.\install-startup-task.cmd
```

若不再需要：

```powershell
Unregister-ScheduledTask -TaskName "AssetScope Server" -Confirm:$false
```

排程只負責啟動服務。電腦必須開機、網路正常，手機才能同步。

## 測試

### Android

```powershell
cd D:\AppDev
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

### Server

```powershell
cd D:\AppDev\server
.\.venv\Scripts\python.exe -m pytest -q
```

### 正式版完整檢查

```powershell
cd D:\AppDev
.\gradlew.bat test lintRelease assembleRelease
```

正式 APK 必須使用固定簽章。若沒有 `release-signing.properties`，不可把產物當成
可覆蓋既有正式版的更新。

## 正式版簽章

首次建立自己的發行版時，建立並妥善備份 keystore：

```powershell
keytool -genkeypair -v `
  -keystore assetscope-release.jks `
  -alias assetscope `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

在專案根目錄建立 `release-signing.properties`：

```properties
storeFile=assetscope-release.jks
storePassword=你的Store密碼
keyAlias=assetscope
keyPassword=你的Key密碼
```

以下檔案已被 Git 忽略，仍應另外加密備份：

- `assetscope-release.jks`
- `release-signing.properties`
- `server\.env`

遺失正式版 keystore 後，Android 不允許用另一把金鑰直接覆蓋安裝現有 App。

## GitHub Actions 與 App 自動更新

推送 `v*` Tag 時，`.github\workflows\release.yml` 會執行測試、Lint、正式版建置、
簽章驗證，並建立 GitHub Release。

Repository 必須設定以下 Actions Secrets：

| Secret | 內容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore 的 Base64 |
| `ANDROID_STORE_PASSWORD` | keystore 密碼 |
| `ANDROID_KEY_ALIAS` | Key Alias |
| `ANDROID_KEY_PASSWORD` | Key 密碼 |

Windows 產生 Base64：

```powershell
[Convert]::ToBase64String(
    [IO.File]::ReadAllBytes("assetscope-release.jks")
) | Set-Clipboard
```

發版前先更新 `app\build.gradle.kts` 的 `versionCode` 與 `versionName`，再執行：

```powershell
git add .
git commit -m "Prepare vX.Y.Z"
git push origin main
git tag vX.Y.Z
git push origin vX.Y.Z
```

App 會查詢 GitHub Releases。找到較新的 `versionCode` 後可下載 APK，但 Android
基於安全限制仍會要求使用者確認安裝，不能完全靜默更新。

Fork 此專案時還需修改：

1. `app\build.gradle.kts` 中用於更新檢查的 GitHub Repository
2. `.github\workflows\release.yml` 中預期的簽章 SHA-256

取得 APK 簽章摘要：

```powershell
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat"
& $apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk
```

## 常見問題

### PowerShell 顯示「已停用指令碼執行」

優先執行專案提供的 `.cmd` 包裝器，例如：

```powershell
.\server\start.cmd
.\server\allow-firewall.cmd
```

需要直接執行 `.ps1` 時：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\server\allow-firewall.ps1
```

這只對該次程序套用 Bypass，不必永久降低全系統執行原則。

### Port 8787 出現 WinError 10048

代表已有程序正在監聽 8787：

```powershell
Get-NetTCPConnection -LocalPort 8787 -State Listen |
    Select-Object LocalAddress,LocalPort,OwningProcess
```

查看程序：

```powershell
Get-Process -Id 上一步的OwningProcess
```

若是 AssetScope，直接使用既有伺服器；`server\start.cmd` 也會偵測並提示。不要在
不確認程序用途的情況下強制終止。

### 手機顯示 failed to connect

依序確認：

1. `Invoke-RestMethod http://127.0.0.1:8787/health` 是否成功
2. 手機是否使用電腦實際 IP，而不是 `127.0.0.1`
3. 手機與電腦是否在同一 Wi-Fi，或兩端 Tailscale 是否已連線
4. Windows 防火牆是否已執行 `allow-firewall.cmd`
5. Wi-Fi 是否啟用 AP Isolation，阻擋裝置互連
6. 電腦休眠後伺服器會無法連線

### App 顯示「HTTP 僅允許私人區網位址」

區域網路可使用：

```text
http://192.168.x.x:8787
http://10.x.x.x:8787
http://100.x.x.x:8787
```

公開網域或公開 IP 必須使用 HTTPS。

### Token 出現 header value、0x0d 或 JSON 解析錯誤

- App 只填 Token 本體，不要填 `Bearer`
- 確認 Token 是單行文字，沒有 CR/LF
- 重新從 `server\.env` 複製值，不要連同變數名稱一起複製
- 確認手機連到目前這份專案啟動的伺服器
- 先在電腦測試 `/health`，再重新啟動 App 與伺服器

### Gradle 顯示需要 Java 17

目前終端機可能仍使用 Java 8。設定正確的 `JAVA_HOME` 後重開 PowerShell：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
```

### `gh` 無法辨識

GitHub CLI 不是建置 App 的必要工具。需要時可安裝：

```powershell
winget install --id GitHub.cli
```

重新開啟終端機後：

```powershell
gh auth login --hostname github.com --git-protocol https --web
```

若 PATH 尚未更新：

```powershell
& "C:\Program Files\GitHub CLI\gh.exe" auth status
```

### App 顯示套件衝突，無法安裝

代表手機上已有相同套件名稱但簽章不同的版本。正式版必須持續使用同一把 keystore。
若是早期測試版且不需保留其本機資料，先解除安裝舊版再安裝正式版。Debug 版目前
使用獨立套件名稱，不會和正式版衝突。

### Firstrade 登入後監看器沒有動作

監看器只等待新的 CSV 出現在 Windows Downloads，不會自動替你按下下載。請在
Firstrade 官方網站完成登入、MFA 並手動使用官方 CSV 匯出功能。

### Shioaji 顯示 `Please sign ... first`

這是券商帳號尚未完成 API 簽署或 Python API 測試，不是 AssetScope Token 問題。
請在永豐官方頁面確認帳號為已簽署，於官方開放時段完成模擬測試，等待審核後再同步。

## 安全原則

- 不要將券商、銀行密碼交給 AssetScope 或寫入自動化腳本
- 不要提交 `.env`、CSV、API Key、Secret Key、keystore 或瀏覽器 Profile
- 每台伺服器使用獨立且足夠長的 API Token
- HTTP 只用於可信任的區域網路或 Tailscale 私有網路
- 對外服務必須使用 HTTPS、存取控制與定期更新
- GitHub Release APK 必須驗證固定簽章
- 真實交易前應另行設計額度、標的、時段、人工確認與緊急停止機制

目前 `.gitignore` 已排除：

```text
server/.env
server/.venv/
server/data/imports/*.csv
server/data/imports/*.json
server/data/raw/
server/data/cache/
server/data/paper-trading.json
server/browser-profiles/
release-signing.properties
*.jks
*.keystore
api_key.txt
```

提交前仍應執行：

```powershell
git status
git diff --cached
```

確認沒有個資、持股資料或憑證被加入版本控制。

## 開發規範

請閱讀 [CONTRIBUTING.md](CONTRIBUTING.md)。修改時應遵守：

- 優先沿用既有 Compose、Repository 與 FastAPI 模組邊界
- UI 文字必須同時維護中文與英文資源
- 新資料來源先轉換為標準模型，不在 UI 直接解析原始格式
- 金額、匯率與報酬計算需補單元測試
- 不提交產生檔、帳戶資料或本機設定
- 發版前完成 Android 測試、Lint、Server 測試與簽章檢查

## 授權與責任

本專案處理的是高敏感度金融資料。使用者應自行確認金融機構服務條款、資料下載
規則與 API 權限。行情、匯率、成本與報酬僅供個人分析，可能存在延遲或估算誤差，
不構成投資建議。
