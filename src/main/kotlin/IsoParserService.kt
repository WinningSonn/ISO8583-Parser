package org.example

import org.springframework.stereotype.Service

@Service
class Iso8583ParserService {

    data class ParsedIsoMessage(
        val header: String,
        val mti: String,
        val primaryBitmap: String,
        val secondaryBitmap: String? = null,
        val fields: List<ParsedField>
    )

    data class ParsedField(
        val fieldNumber: Int,
        val label: String?,
        val type: String,
        val value: String,
        val length: Int
    )

    fun parseIsoMessage(isoMessage: String): ParsedIsoMessage {
        val sanitizedMessage = sanitizeIsoMessage(isoMessage)
        return parseISO8583isoString(sanitizedMessage)
    }

    private fun sanitizeIsoMessage(rawMessage: String): String {
        // Sanitize the input ISO 8583 message
        return rawMessage
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("")
    }

    fun parseISO8583isoString(isoString: String): ParsedIsoMessage {
        val header = isoString.take(12)
        val mti = isoString.substring(12, 16)
        val primaryBitmapBinary = Fields().hexToBinary(isoString.substring(16, 32))

        val hasSecondaryBitmap = primaryBitmapBinary[0] == '1'
        val secondaryBitmapBinary: String? = if (hasSecondaryBitmap) {
            Fields().hexToBinary(isoString.substring(32, 48))
        } else {
            null
        }

        // Combine primary and secondary bitmaps
        val bitmapBinary = primaryBitmapBinary + (secondaryBitmapBinary ?: "")
        val startPos = if (hasSecondaryBitmap) 48 else 32

        // Parse the fields based on the bitmap
        val fields = parseFields(bitmapBinary, isoString, startPos)

        return ParsedIsoMessage(
            header = header,
            mti = mti,
            primaryBitmap = primaryBitmapBinary,
            secondaryBitmap = secondaryBitmapBinary,
            fields = fields
        )
    }

    private fun parseFields(bitmapBinary: String, isoString: String, startIndex: Int): List<ParsedField> {
        val fieldDetails = Fields().getFieldDetails()
        val parsedFields = mutableListOf<ParsedField>()
        var currentPos = startIndex

        bitmapBinary.drop(1).forEachIndexed { index, bit ->
            if (bit == '1') {
                val fieldNumber = index + 2
                val fieldDetail = fieldDetails[fieldNumber]

                try {
                    if (fieldDetail != null) {
                        // Process the field using metadata and add to the result
                        currentPos = processField(fieldDetail, isoString, currentPos, fieldNumber, parsedFields)
                    } else {
                        parsedFields.add(
                            ParsedField(
                                fieldNumber = fieldNumber,
                                label = null,
                                type = "UNKNOWN",
                                value = "Unknown field",
                                length = 0
                            )
                        )
                    }
                } catch (e: Exception) {
                    parsedFields.add(
                        ParsedField(
                            fieldNumber = fieldNumber,
                            label = null,
                            type = "ERROR",
                            value = "Error: ${e.message}",
                            length = 0
                        )
                    )
                }
            }
        }

        return parsedFields
    }

    // Process a single field
    private fun processField(
        fieldDetail: Fields.FieldDetail,
        isoString: String,
        currentPos: Int,
        fieldNumber: Int,
        parsedFields: MutableList<ParsedField>
    ): Int {
        val fieldDefinition = fieldDetail.definition
        return when {
            // Handle variable-length fields (LLVAR or LLLVAR)
            fieldDefinition.type.contains("LLLVAR") || fieldDefinition.type.contains("LLVAR") -> {
                val (value, nextPos) = extractVariableLengthField(fieldDefinition, isoString, currentPos)
                parsedFields.add(
                    ParsedField(
                        fieldNumber = fieldNumber,
                        label = fieldDetail.label,
                        type = fieldDefinition.type,
                        length = value.length,
                        value = value
                    )
                )
                nextPos
            }

            // Handle fixed length fields
            else -> {
                validateFieldLength(isoString, currentPos, fieldDefinition.length, fieldNumber)
                val fieldValue = isoString.substring(currentPos, currentPos + fieldDefinition.length)
                parsedFields.add(
                    ParsedField(
                        fieldNumber = fieldNumber,
                        label = fieldDetail.label,
                        type = fieldDefinition.type,
                        length = fieldValue.length,
                        value = fieldValue
                    )
                )
                currentPos + fieldDefinition.length
            }
        }
    }

    // Extract variable length fields (LLVAR or LLLVAR)
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

    // Ensure the field data does not exceed the ISO string length
    private fun validateFieldLength(
        isoString: String,
        currentPos: Int,
        length: Int,
        fieldNumber: Int
    ) {
        if (currentPos + length > isoString.length) {
            val fieldInfo = if (fieldNumber >= 0) " for field $fieldNumber" else ""
            throw IllegalArgumentException("ISO String too short to extract value$fieldInfo")
        }
    }
}