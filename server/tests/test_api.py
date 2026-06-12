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
    assert response.json()["schema_version"] == 2
    assert response.json()["transactions"] == []
    history = client.get(
        "/api/v1/portfolio/history",
        headers={"Authorization": "Bearer a-long-enough-test-token"},
    )
    assert history.status_code == 200
    assert len(history.json()["points"]) == 1
    app.dependency_overrides.clear()
