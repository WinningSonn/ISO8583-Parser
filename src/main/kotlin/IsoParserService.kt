package org.example

import org.springframework.stereotype.Service
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

@Service
class Iso8583ParserService {

    fun parseISOMessage(isoMessage: String): String {
        val response = StringBuilder()
        response.append("Parsed ISO Message\n")
        parseISO8583isoString(isoMessage, response)
        return response.toString()
    }

    fun sanitizeIsoMessage(rawMessage: String): String {
        return rawMessage
            .lines()                           // Split into lines
            .map { it.trim() }                 // Trim each line to remove unnecessary spaces
            .filter { it.isNotEmpty() }        // Remove empty lines
            .joinToString("")                  // Combine into a single string
    }

    private fun parseISO8583isoString(isoString: String, response: StringBuilder) {
        val header = isoString.take(12)
        val mti = isoString.substring(12, 16)
        val primaryBitmapBinary = Fields().hexToBinary(isoString.substring(16, 32))

        val hasSecondaryBitmap = primaryBitmapBinary[0] == '1'
        val secondaryBitmapBinary = if (hasSecondaryBitmap) {
            Fields().hexToBinary(isoString.substring(32, 48))
        } else ""
        val bitmapBinary = primaryBitmapBinary + secondaryBitmapBinary
        val startPos = if (hasSecondaryBitmap) 48 else 32

        response.append("Header: $header\n")
        response.append("MTI: $mti\n")
        parseFields(bitmapBinary, isoString, startPos, response)
    }

    private fun parseFields(bitmapBinary: String, isoString: String, startIndex: Int, response: StringBuilder) {
        val fieldDetails = Fields().getFieldDetails()
        var currentPos = startIndex

        bitmapBinary.drop(1).forEachIndexed { index, bit ->
            if (bit == '1') {
                val fieldNumber = index + 2
                val fieldDetail = fieldDetails[fieldNumber]

                try {
                    if (fieldDetail != null) {
                        currentPos = processField(fieldDetail, isoString, currentPos, fieldNumber, response)
                    } else {
                        response.append("Unknown field: $fieldNumber\n")
                    }
                } catch (e: Exception) {
                    response.append("Error processing field $fieldNumber: ${e.message}\n")
                }
            }
        }
    }

    private fun processField(
        fieldDetail: Fields.FieldDetail,
        isoString: String,
        currentPos: Int,
        fieldNumber: Int,
        response: StringBuilder
    ): Int {
        val fieldDefinition = fieldDetail.definition
        return when {
            fieldDefinition.type.contains("LLLVAR") || fieldDefinition.type.contains("LLVAR") -> {
                val (value, nextPos) = extractVariableLengthField(fieldDefinition, isoString, currentPos)
                response.append("$fieldNumber: ${fieldDetail.label} (${fieldDefinition.type}) -> $value\n")
                nextPos
            }

            else -> {
                validateFieldLength(isoString, currentPos, fieldDefinition.length, fieldNumber)
                val fieldValue = isoString.substring(currentPos, currentPos + fieldDefinition.length)
                response.append("$fieldNumber:${fieldDetail.label} (${fieldDefinition.type}) -> $fieldValue\n")
                currentPos + fieldDefinition.length
            }
        }
    }

    private fun extractVariableLengthField(
        fieldDefinition: Fields.FieldDefinition,
        isoString: String,
        currentPos: Int
    ): Pair<String, Int> {
        val lengthIndicatorSize = if (fieldDefinition.type.startsWith("LLL")) 3 else 2
        val lengthIndicator = isoString.substring(currentPos, currentPos + lengthIndicatorSize)

        if (!lengthIndicator.all { it.isDigit() }) {
            throw IllegalArgumentException("Invalid length indicator: '$lengthIndicator'")
        }

        val fieldLength = lengthIndicator.toInt()
        validateFieldLength(isoString, currentPos + lengthIndicatorSize, fieldLength, -1)

        val fieldValue = isoString.substring(
            currentPos + lengthIndicatorSize,
            currentPos + lengthIndicatorSize + fieldLength
        )
        return fieldValue to (currentPos + lengthIndicatorSize + fieldLength)
    }

    private fun validateFieldLength(
        isoString: String,
        currentPos: Int,
        length: Int,
        fieldNumber: Int
    ) {
        if (currentPos + length > isoString.length) {
            val fieldInfo = if (fieldNumber >= 0) " for field $fieldNumber" else ""
            throw IllegalArgumentException("isoString too short to extract value$fieldInfo")
        }
    }
}