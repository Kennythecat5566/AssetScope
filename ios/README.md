# AssetScope iOS

AssetScope iOS 是使用 SwiftUI 與 Swift Charts 開發的原生 iPhone/iPad App，共用
根目錄 `server/` 的 FastAPI 後端。API Token 儲存在 iOS Keychain，資產快取則
保存在 App 的 Application Support 目錄。

## 功能

- 總資產、損益、報酬率、匯率與金融機構配置
- 資產歷史趨勢
- 持股搜尋、行情縮圖與 K 線
- 左右滑動切換日線、月線、季線、年線
- Firstrade 與永豐證券交易紀錄
- 永豐信用卡消費紀錄
- 三個模擬交易機器人、績效指數比較與交易紀錄
- TWD/USD 與繁體中文/英文切換
- 區域網路或 Tailscale 伺服器同步
- 伺服器暫時離線時顯示最近一次成功同步的資產

## 開發需求

- macOS 14 以上
- Xcode 16 以上
- XcodeGen
- iOS 17 以上的模擬器或實體裝置
- 已啟動的 AssetScope FastAPI Server

Windows 可以編輯 Swift 原始碼，但 Apple 的 iOS SDK、模擬器、程式簽章與實機安裝
只能在 macOS 的 Xcode 完成。

## 從零開始建置

### 1. 安裝 Xcode

從 Mac App Store 安裝 Xcode，第一次啟動時完成額外元件安裝，再執行：

```bash
xcode-select --install
xcodebuild -version
```

### 2. 安裝 XcodeGen

已安裝 Homebrew 時：

```bash
brew install xcodegen
xcodegen --version
```

XcodeGen 專案：<https://github.com/yonaskolb/XcodeGen>

### 3. 下載專案

```bash
git clone https://github.com/Kennythecat5566/AssetScope.git
cd AssetScope
git switch feature/ios-app
```

### 4. 產生 Xcode Project

```bash
cd ios
xcodegen generate
open AssetScopeIOS.xcodeproj
```

`.xcodeproj` 是由 `project.yml` 產生的本機檔案，不提交 Git。新增 Swift 檔案後只要
重新執行 `xcodegen generate`，所有 `AssetScopeIOS/` 下的來源都會自動加入 Target。

### 5. 設定簽章

在 Xcode：

1. 選擇 `AssetScopeIOS` Project
2. 選擇 `AssetScopeIOS` Target
3. 開啟 `Signing & Capabilities`
4. 選擇自己的 Apple Developer Team
5. 若 Bundle Identifier 已被使用，將
   `tw.kensuke.assetscope.ios` 改成自己的唯一識別碼

個人 Apple ID 可以安裝到自己的 iPhone 測試，但免費簽章有期限與能力限制。
TestFlight 或 App Store 發佈需要 Apple Developer Program。

### 6. 啟動後端

在 Windows 電腦：

```powershell
cd D:\AppDev\server
.\start.cmd
Invoke-RestMethod http://127.0.0.1:8787/health
```

Mac/iPhone 與 Windows 在同一 Wi-Fi 時，使用 Windows 的區網 IP，例如：

```text
http://192.168.0.102:8787
```

外出連線時，在 Windows 與 iPhone 安裝並登入同一個 Tailscale 帳號，使用：

```text
http://100.x.x.x:8787
```

### 7. 執行 App

1. 在 Xcode Scheme 選擇 `AssetScopeIOS`
2. 選擇 iOS 17 以上的模擬器或已信任的 iPhone
3. 按下 `Cmd+R`
4. 到 App 的「同步」頁
5. 輸入伺服器 URL
6. 輸入 `server/.env` 的 `ASSETSCOPE_API_TOKEN`
7. 點擊「連接並同步」

請只輸入 Token 本體，不要加上 `Bearer`。

## 命令列建置

先產生 Project：

```bash
cd ios
xcodegen generate
```

列出可用 Simulator：

```bash
xcrun simctl list devices available
```

建置：

```bash
xcodebuild \
  -project AssetScopeIOS.xcodeproj \
  -scheme AssetScopeIOS \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

若本機沒有 `iPhone 16` Simulator，改成 `simctl` 顯示的裝置名稱。

## 網路安全

`Info.plist` 開放 App Transport Security 的 HTTP 載入，是為了讓 App 能連線到動態
區網 IP 與 Tailscale IP。`APIClient` 仍會拒絕公開 IP/網域的 HTTP，只允許：

- `10.0.0.0/8`
- `172.16.0.0/12`
- `192.168.0.0/16`
- `100.64.0.0/10`（Tailscale/CGNAT）
- localhost 與 `.local`

公開伺服器必須使用 HTTPS。若準備提交 App Store，建議替後端建立固定 HTTPS
網域後移除 `NSAllowsArbitraryLoads`。

## App 更新

iOS 不允許一般 App 自行下載 IPA 並靜默覆蓋安裝。正式更新應使用：

- Xcode 實機重新安裝：個人開發測試
- TestFlight：內部或外部測試
- App Store：正式發佈

因此 Android 的 GitHub APK 自動更新流程不會直接移植到 iOS。

## 專案結構

```text
ios/
  project.yml
  AssetScopeIOS/
    App/              App 進入點
    Domain/           API 資料模型與資產計算
    Infrastructure/   URLSession、Keychain、離線快取
    Store/            App 狀態與同步流程
    UI/               SwiftUI 畫面與圖表
    Assets.xcassets/
    Info.plist
```

## 已知限制

- Windows 無法執行 Xcode 編譯與 iOS Simulator
- 尚未加入 XCTest Target；第一輪以共用 API 契約與手動 UI 測試為主
- 背景同步受 iOS 排程與省電政策限制，不保證固定時間執行
- App 只提供模擬交易檢視，不提供真實下單
