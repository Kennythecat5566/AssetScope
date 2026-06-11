# AssetScope Server

Read-only personal asset API that runs on the Windows PC and serves normalized
portfolio data to the Android app.

## Setup

```powershell
cd D:\AppDev\server
.\setup.ps1
```

Edit `.env` and replace `ASSETSCOPE_API_TOKEN` with a long random value. Then:

```powershell
.\start.ps1
```

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

Run `install-startup-task.ps1` once to start the API at Windows sign-in.

If the phone cannot connect, change the active Wi-Fi network profile to
`Private`, then run `allow-firewall.ps1` once in Administrator PowerShell.
The firewall rule only permits TCP 8787 from the local subnet.

