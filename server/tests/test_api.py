from pathlib import Path

from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import app


def test_portfolio_requires_token(tmp_path: Path) -> None:
    app.dependency_overrides[get_settings] = lambda: Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path,
        exchange_rate_auto_update=False,
        shioaji_enabled=False,
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
