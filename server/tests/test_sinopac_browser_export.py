from app.sinopac_browser_export import _is_official_url


def test_only_accepts_official_sinopac_hosts() -> None:
    assert _is_official_url("https://mma.sinopac.com/")
    assert _is_official_url("https://bank.sinopac.com/something")
    assert not _is_official_url("https://sinopac.example.com/")
    assert not _is_official_url("https://sinopac.com.evil.example/")
