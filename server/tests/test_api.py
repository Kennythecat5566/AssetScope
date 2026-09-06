from pathlib import Path

from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import app
from app import service


def test_portfolio_requires_token(tmp_path: Path) -> None:
    app.dependency_overrides[get_settings] = lambda: Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path,
        exchange_rate_auto_update=False,
        shioaji_enabled=False,
        firstrade_api_enabled=False,
        gmail_expenses_enabled=False,
    )
    client = TestClient(app)

    assert client.get("/api/v1/portfolio").status_code == 401
    response = client.get(
        "/api/v1/portfolio",
        headers={"Authorization": "Bearer a-long-enough-test-token"},
    )

    assert response.status_code == 200
    assert response.json()["schema_version"] == 3
    assert response.json()["transactions"] == []
    assert response.json()["expenses"] == []
    history = client.get(
        "/api/v1/portfolio/history",
        headers={"Authorization": "Bearer a-long-enough-test-token"},
    )
    assert history.status_code == 200
    assert len(history.json()["points"]) == 1
    paper = client.get(
        "/api/v1/paper-trading",
        headers={"Authorization": "Bearer a-long-enough-test-token"},
    )
    assert paper.status_code == 200
    assert paper.json()["paper_only"] is True
    assert len(paper.json()["bots"]) == 3
    bot = client.get(
        "/api/v1/paper-trading/aggressive",
        headers={"Authorization": "Bearer a-long-enough-test-token"},
    )
    assert bot.status_code == 200
    assert bot.json()["id"] == "aggressive"
    app.dependency_overrides.clear()


def test_portfolio_continues_when_gmail_import_fails(
    tmp_path: Path,
    monkeypatch,
) -> None:
    app.dependency_overrides[get_settings] = lambda: Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path,
        exchange_rate_auto_update=False,
        shioaji_enabled=False,
        firstrade_api_enabled=False,
        gmail_expenses_enabled=True,
    )

    def fail_gmail_import(settings):
        raise RuntimeError("quota exceeded")

    monkeypatch.setattr(service, "load_gmail_card_expenses", fail_gmail_import)
    client = TestClient(app)

    response = client.get(
        "/api/v1/portfolio",
        headers={"Authorization": "Bearer a-long-enough-test-token"},
    )

    assert response.status_code == 200
    assert response.json()["expenses"] == []
    app.dependency_overrides.clear()


def test_portfolio_response_is_cached(tmp_path: Path, monkeypatch) -> None:
    service.clear_portfolio_cache()
    app.dependency_overrides[get_settings] = lambda: Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path,
        exchange_rate_auto_update=False,
        shioaji_enabled=False,
        firstrade_api_enabled=False,
        gmail_expenses_enabled=False,
        portfolio_cache_seconds=30,
    )
    calls = 0

    original_load_csv_folder = service.load_csv_folder

    def counting_load_csv_folder(import_dir):
        nonlocal calls
        calls += 1
        return original_load_csv_folder(import_dir)

    monkeypatch.setattr(service, "load_csv_folder", counting_load_csv_folder)
    client = TestClient(app)
    headers = {"Authorization": "Bearer a-long-enough-test-token"}

    assert client.get("/api/v1/portfolio", headers=headers).status_code == 200
    assert client.get("/api/v1/portfolio", headers=headers).status_code == 200
    assert calls == 1

    service.clear_portfolio_cache()
    app.dependency_overrides.clear()
