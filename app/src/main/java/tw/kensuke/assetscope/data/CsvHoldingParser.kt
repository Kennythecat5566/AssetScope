package tw.kensuke.assetscope.data

import tw.kensuke.assetscope.domain.model.AssetType
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution
import java.util.UUID

object CsvHoldingParser {
    private val requiredColumns = setOf(
        "institution",
        "account",
        "symbol",
        "name",
        "type",
        "currency",
        "quantity",
        "average_cost",
        "market_price",
    )

    fun parse(content: String): Pair<List<Holding>, Int> {
        val rows = content.lineSequence()
            .filter(String::isNotBlank)
            .map(::parseRow)
            .toList()

        if (rows.isEmpty()) return emptyList<Holding>() to 0

        val headers = rows.first().map { it.trim().lowercase() }
        require(headers.containsAll(requiredColumns)) {
            "CSV 缺少必要欄位：${requiredColumns.minus(headers.toSet()).joinToString()}"
        }

        var skipped = 0
        val holdings = rows.drop(1).mapNotNull { values ->
            runCatching {
                val row = headers.zip(values).toMap()
                Holding(
                    id = UUID.randomUUID().toString(),
                    institution = parseInstitution(row.getValue("institution")),
                    accountName = row.getValue("account"),
                    symbol = row.getValue("symbol").uppercase(),
                    name = row.getValue("name"),
                    assetType = AssetType.valueOf(row.getValue("type").uppercase()),
                    currency = Currency.valueOf(row.getValue("currency").uppercase()),
                    quantity = row.getValue("quantity").toDouble(),
                    averageCost = row.getValue("average_cost").toDouble(),
                    marketPrice = row.getValue("market_price").toDouble(),
                )
            }.getOrElse {
                skipped += 1
                null
            }
        }
        return holdings to skipped
    }

    private fun parseInstitution(value: String): Institution = when (value.trim().lowercase()) {
        "firstrade" -> Institution.FIRSTRade
        "sinopac_securities", "永豐證券" -> Institution.SINOPAC_SECURITIES
        "sinopac_bank", "永豐銀行" -> Institution.SINOPAC_BANK
        else -> error("不支援的機構：$value")
    }

    private fun parseRow(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    values += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString().trim()
        return values
    }
}

