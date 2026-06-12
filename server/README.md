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

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
```

## CSV Sources

Place standardized CSV files in `data/imports`. The server reads every `.csv`
file on each request, so replacing a file does not require a restart.

Do not use duplicate account/symbol rows across files. A sample format is in
`..\samples\holdings-template.csv`.

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

If the phone cannot connect, change the active Wi-Fi network profile to
`Private`, then run:

```powershell
.\allow-firewall.cmd
```

Accept the Windows administrator prompt. This bypasses the PowerShell script
policy for this process only and does not change the machine-wide policy.

Alternatively, from Administrator PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\allow-firewall.ps1
```

The firewall rule only permits TCP 8787 from the local subnet.
