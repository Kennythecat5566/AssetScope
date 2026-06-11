package tw.kensuke.assetscope.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortfolioApiClientTest {
    @Test
    fun `handles plain text internal server error`() {
        assertEquals(
            "伺服器內部錯誤（HTTP 500），請檢查電腦伺服器",
            PortfolioApiClient.errorMessage(500, "Internal Server Error"),
        )
    }

    @Test
    fun `trims copied token line endings`() {
        assertEquals(
            "assetscope-local-2026-change-this-token",
            PortfolioApiClient.normalizeApiToken(
                " assetscope-local-2026-change-this-token\r\n",
            ),
        )
    }

    @Test
    fun `rejects control characters inside token`() {
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioApiClient.normalizeApiToken(
                "assetscope-local-\r-2026-change-this-token",
            )
        }
    }
}
