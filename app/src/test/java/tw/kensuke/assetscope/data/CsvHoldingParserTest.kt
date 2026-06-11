package tw.kensuke.assetscope.data

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kensuke.assetscope.domain.model.Institution

class CsvHoldingParserTest {
    @Test
    fun `parses quoted standardized holding csv`() {
        val csv = """
            institution,account,symbol,name,type,currency,quantity,average_cost,market_price
            firstrade,Main,VTI,"Vanguard Total Market, ETF",ETF,USD,2,250.5,290.0
            永豐銀行,DAWHO,TWD,新台幣活存,DEPOSIT,TWD,1,100000,100000
        """.trimIndent()

        val (holdings, skipped) = CsvHoldingParser.parse(csv)

        assertEquals(2, holdings.size)
        assertEquals(0, skipped)
        assertEquals("Vanguard Total Market, ETF", holdings.first().name)
        assertEquals(Institution.SINOPAC_BANK, holdings.last().institution)
    }
}

