# AssetScope Server

Read-only personal asset API that runs on the Windows PC and serves normalized
portfolio data to the Android app.

## Setup

```powershell
cd D:\AppDev\server
.\setup.cmd
```

Edit `.env` and replace `ASSETSCOPE_API_TOKEN` with a long random value. Then:

```powershell
.\start.cmd
```

`start.cmd` is safe to run repeatedly. If AssetScope is already listening on
port `8787`, it reports that the server is running and exits successfully.

The API listens on `http://0.0.0.0:8787`. On the current network, the Android
app should use `http://192.168.0.102:8787`.

USD/TWD is refreshed automatically and cached for six hours. If the quote
service is temporarily unavailable, the server keeps using the latest cached
rate, then falls back to `ASSETSCOPE_USD_TO_TWD`.

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
```

## CSV Sources

Place standardized CSV files in `data/imports`. The server reads every `.csv`
file on each request, so replacing a file does not require a restart.

Do not use duplicate account/symbol rows across files. A sample format is in
`..\samples\holdings-template.csv`.

## SinoPac Credit Card Expenses

AssetScope can analyze an official SinoPac credit-card CSV without storing
your online-banking password. Save the exported file under `data/imports` with
a name beginning with `sinopac-card`, for example:

```text
data/imports/sinopac-card-2026-06.csv
```

The supported template is `..\samples\sinopac-card-template.csv`. Required
columns are transaction date, merchant, and amount. The importer accepts the
English template headers and common Chinese headers such as `消費日`,
`消費明細`, and `新台幣金額`. ROC dates such as `115/06/09` are supported.

Categories can be supplied in the CSV or inferred from merchant keywords.
Supported categories include dining, transport, shopping, groceries,
entertainment, subscriptions, travel, health, utilities, and other. Negative
amounts are treated as refunds.

After replacing the CSV, tap `連接並同步` or `立即同步` in the Android app.
The `消費` page shows the latest month's total, category chart, and itemized
card activity.

### Browser-assisted SinoPac export

To try a browser-assisted download, run:

```powershell
D:\AppDev\server\capture-sinopac-card.cmd
```

The tool opens the official SinoPac MMA site in a dedicated Microsoft Edge
profile. Enter the username, password, captcha, and MFA only in the official
browser window. AssetScope does not read or store them. After login, it makes
a best-effort attempt to open the read-only `信用卡` and `近期帳單` sections,
then waits for a CSV download. If the site layout has changed, navigate to the
bill page and click its CSV/export control yourself.

Only downloads from an official `sinopac.com` or `sinopac.com.tw` page are
accepted. The automation does not click payment, transfer, cash advance, or
trading controls and does not bypass captcha, MFA, or device verification.
The accepted CSV is copied to `data/imports` automatically.

SinoPac does not guarantee that every personal credit-card page offers CSV.
If a page provides only PDF, use the official CSV option on another statement
or continue with the manual template until PDF parsing is added.

## Browser-Assisted Firstrade Export

Firstrade may reject an automated browser as a new or suspicious device. If
you see verification reference code `2192`, stop retrying and use the normal
browser workflow:

```powershell
.\capture-firstrade-normal-browser.cmd
```

This opens Firstrade in your regular default browser and watches the Windows
Downloads folder for the next CSV. Sign in normally, complete MFA, navigate to
`Accounts > Tax Center > Download Account Information`, choose `Excel CSV
Files`, and click `Download`.

The watcher only reads the newly downloaded CSV. It cannot see passwords,
cookies, page contents, or MFA codes.

After login, the watcher intentionally does not click inside Firstrade. Use:

```text
我的帳戶 > 稅務中心 (Tax Center)
> Download Account Information > Excel CSV Files > Download
```

Keep the terminal open while downloading. It detects both newly created CSV
files and an existing CSV that the browser overwrites.

After download, the transaction history is automatically converted to
`data/imports/firstrade.csv`. Current quantity and moving-average cost are
rebuilt from BUY/SELL records. Because this export does not contain live
quotes, `market_price` temporarily uses the latest transaction price.

The converter also writes `firstrade.activity.json` with BUY, SELL, and
Dividend events plus realized profit, unrealized profit, dividend income, and
total return. The API exposes this data using schema version 2.

Raw files are stored in `data/raw/firstrade`, which is excluded from Git.
Never send account passwords in chat or place them in `.env`.

The raw Firstrade CSV is not copied directly to `data/imports`, because its
columns differ from AssetScope's normalized holdings schema. After capturing
one export, a Firstrade-specific converter can transform it automatically.

The Playwright-based `capture-firstrade.cmd` remains available for sites that
accept a dedicated browser profile, but the normal-browser workflow is the
recommended Firstrade option.

## Shioaji

Install the optional package:

```powershell
.\.venv\Scripts\python.exe -m pip install -e ".[shioaji]"
```

Then update `.env`:

```text
ASSETSCOPE_SHIOAJI_ENABLED=true
ASSETSCOPE_SHIOAJI_API_KEY=...
ASSETSCOPE_SHIOAJI_SECRET_KEY=...
```

You can store the credentials without showing them in the terminal:

```powershell
.\configure-shioaji.cmd
```

The setup tool avoids PowerShell secure-paste issues by reading one credential
at a time from the clipboard. Copy the requested value and press Enter without
pasting it into the terminal. The clipboard is cleared immediately after each
read, and only the character count is displayed.

Create the API Key and Secret Key in the official SinoPac API management page.
Enable `Account` and `Production Environment`; trading permission is not
required. Restrict the key to this PC's IP when practical. Do not enter your
banking password in this project or send it through chat.

The connector reads Taiwan stock positions in share units, including odd lots,
and the linked SinoPac stock settlement account balance. It does not call any
order endpoint. Shioaji cannot read unrelated SinoPac savings or time-deposit
accounts; those still need an authorized Open Banking provider or a CSV import.

The portfolio response also includes open-position acquisition lots from
`list_position_detail` and realized sales returned by `list_profit_loss`.
Shioaji does not expose every historical filled order indefinitely, so the
server reports the records available from these official read-only endpoints
without inventing missing history.

If the server reports that the stock account is not API-signed, complete the
official SinoPac API agreement and stock API test. A successful API Key login
alone is not sufficient; `api.list_accounts()` must report `signed=True` for
the stock account before position and balance queries are accepted.

On a business day during SinoPac's test service hours, run:

```powershell
.\test-shioaji-api.cmd
```

The script is hard-coded to `simulation=True` and follows SinoPac's official
stock test example. It submits one simulated limit order for the test symbol
2890 using that day's reference price; it cannot place a production order.
Wait at least five minutes after a successful result before checking the
production account's `signed` status.
The API Key must have the `Trading` permission enabled for SinoPac to accept
and record the simulated order test.

## Windows Automation

Run this once to start the API at Windows sign-in:

```powershell
.\install-startup-task.cmd
```

The `.cmd` launchers use `ExecutionPolicy Bypass` for their own process only.
They do not change the machine-wide PowerShell policy.

The phone and PC must be connected to the same home router. A phone using 5G
or another hotspot cannot reach a PC address such as `192.168.0.102`.

If the phone cannot connect, run:

```powershell
.\allow-firewall.cmd
```

Accept the Windows administrator prompt. On an active network with a private
gateway, the script changes the Windows network category to `Private` and
allows TCP 8787 from `LocalSubnet` only. It does not expose the server to the
public Internet.

Alternatively, from Administrator PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\allow-firewall.ps1
```

The firewall rule only permits TCP 8787 from the local subnet.

## Access Away From Home

Use Tailscale instead of router port forwarding. It creates a private encrypted
network between the PC and Android phone without exposing port 8787 publicly.

On the PC, run:

```powershell
.\setup-tailscale.cmd
```

Accept the administrator prompt and sign in to Tailscale. Install the official
Tailscale Android app and sign in with the same account. Enter the URL printed
by the setup script, for example `http://100.x.y.z:8787`, in AssetScope.

The separate Windows Firewall rule allows TCP 8787 only from Tailscale's
`100.64.0.0/10` address range. Do not configure router port forwarding.

## Price History

The authenticated history endpoint provides daily OHLCV candles for charting:

```text
GET /api/v1/history/{institution}/{symbol}?days=90
```

The Android holdings list also requests compact 30-session market summaries
in one authenticated call:

```text
POST /api/v1/market/summaries
```

Each successful item includes the latest close, previous-session change,
change rate, and closing prices used by the mini trend chart. A failed symbol
is omitted without preventing the other holdings from loading. Cash and
deposit holdings are not requested.

The portfolio endpoint records one net-worth snapshot per day. The app reads
the accumulated timeline from:

```text
GET /api/v1/portfolio/history?days=365
```

### AI 模擬交易實驗室

伺服器會在背景執行三個完全隔離於券商下單的策略代理：

- 美股自由型：不限制策略風格，只交易 Firstrade 美股個股。
- 台股自由型：不限制策略風格，只交易永豐台股個股。
- 全球自由型：在虛擬帳戶內不限制集中度與週轉率，可交易全部美股與台股個股。

每個代理預設使用獨立的 `NT$1,000,000` 虛擬資金。持倉、交易紀錄與每日淨值
保存在 `server/data/paper-trading.json`。此模組不包含 Shioaji 下單呼叫，也沒有
切換為實盤的設定。API 另提供以相同起始資金換算的台灣加權指數與 S&P 500
比較序列，Android 詳細頁會將機器人與兩項基準畫在同一張績效圖中。

```text
GET /api/v1/paper-trading
```

可在 `.env` 調整：

```dotenv
ASSETSCOPE_PAPER_TRADING_ENABLED=true
ASSETSCOPE_PAPER_TRADING_INTERVAL_MINUTES=60
ASSETSCOPE_PAPER_TRADING_INITIAL_CASH_TWD=1000000
```

History starts accumulating after this server version is installed. Snapshots
and exchange-rate caches are stored under `data/cache`, which is excluded from
Git.

Taiwan stock candles come from official Shioaji historical K-bars and are
aggregated into daily candles. Firstrade US symbols use Yahoo Finance daily
history. Results are cached on the PC for 30 minutes. Cash and deposit holdings
do not expose a chart action.
