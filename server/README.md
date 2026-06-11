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

Run:

```powershell
.\capture-firstrade.cmd
```

AssetScope opens a dedicated Microsoft Edge profile. Enter your Firstrade
credentials and MFA only inside that official browser window, then navigate to
`Accounts > Tax Center > Download Account Information`, choose `Excel CSV
Files`, and click `Download`.

The login profile is stored in `browser-profiles/firstrade`, and raw files are
stored in `data/raw/firstrade`. Both locations are excluded from Git. Never
send account passwords in chat or place them in `.env`.

The raw Firstrade CSV is not copied directly to `data/imports`, because its
columns differ from AssetScope's normalized holdings schema. After capturing
one export, a Firstrade-specific converter can transform it automatically.

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

Only the position query is used. No order endpoint is exposed by this server.

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
