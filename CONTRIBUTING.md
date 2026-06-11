# Contributing

## Branches

- `main`: 隨時保持可建置
- `feature/<name>`: 新功能
- `fix/<name>`: 錯誤修正

## Commit style

採用 Conventional Commits，例如：

```text
feat: add Firstrade CSV import
fix: handle empty portfolio allocation
test: cover USD conversion
```

提交前請執行：

```powershell
.\gradlew.bat test assembleDebug
```

禁止提交金融帳密、API Key、實際對帳單與 `local.properties`。

