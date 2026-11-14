package com.example.data.local

import java.util.Locale

object CondoUnitsHelper {
    const val UNITS_PER_BLOCK = 144
    val BLOCKS = listOf("A", "B")

    /**
     * Retorna a lista exata das 144 unidades formatadas com 3 dígitos ("001" até "144").
     */
    fun getAllUnits(): List<String> {
        return (1..UNITS_PER_BLOCK).map { num ->
            String.format(Locale.US, "%03d", num)
        }
    }

    /**
     * Retorna os identificadores completos para um bloco (ex.: "A-001" até "A-144").
     */
    fun getAllUnitIdentifiers(block: String): List<String> {
        val cleanBlock = block.trim().uppercase()
        return getAllUnits().map { "$cleanBlock-$it" }
    }

    /**
     * Formata um número inteiro para o padrão de 3 dígitos (ex: 25 -> "025").
     */
    fun formatUnitNumber(num: Int): String {
        return String.format(Locale.US, "%03d", num.coerceIn(1, UNITS_PER_BLOCK))
    }

    /**
     * Normaliza uma entrada de unidade digitada pelo porteiro (ex: "134" -> "134", "25" -> "025").
     */
    fun parseUnitNumber(rawInput: String): String? {
        val clean = rawInput.trim().uppercase().replace("A-", "").replace("B-", "").replace("A", "").replace("B", "")
        val num = clean.toIntOrNull() ?: return null
        return if (num in 1..UNITS_PER_BLOCK) {
            formatUnitNumber(num)
        } else {
            null
        }
    }
}
