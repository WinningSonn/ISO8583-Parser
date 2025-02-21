import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

fun main() {
    val filePath = "src/main/resources/isoMessage.txt"
    val fileContent = readIsoMessages(filePath)

    val trimmedIsoMessages = fileContent.split("?")
        .map { it.trim().replace("\r", "").replace("\n", "") }
        .filterNot { it.isBlank() }

    trimmedIsoMessages.forEachIndexed { index, isoMessage ->
        println("------------------------- ISO Message ${index + 1} -------------------------")
        parseISO8583isoString(isoMessage)
    }
}

fun readIsoMessages(filePath: String): String {
    val path = Path(filePath)
    if (!path.exists()) throw IllegalArgumentException("File '$filePath' not found")
    return path.readText()
}

// Parse the full ISO8583 message
fun parseISO8583isoString(isoString: String) {
    val hasHeader = isoString.startsWith("ISO")
    val header = if (hasHeader) isoString.take(12) else "No Header"
    val mtiStartIndex = if (hasHeader) 12 else 0
    val mti = isoString.substring(mtiStartIndex, mtiStartIndex + 4)
    val primaryBitmapBinary = Fields().hexToBinary(isoString.substring(mtiStartIndex + 4, mtiStartIndex + 20))

    val hasSecondaryBitmap = primaryBitmapBinary[0] == '1'
    val secondaryBitmapBinary = if (hasSecondaryBitmap) {
        Fields().hexToBinary(isoString.substring(mtiStartIndex + 20, mtiStartIndex + 36))
    } else ""
    val bitmapBinary = primaryBitmapBinary + secondaryBitmapBinary
    val startPos = if (hasSecondaryBitmap) mtiStartIndex + 36 else mtiStartIndex + 20

    // Print the header and MTI
    println("Header: $header")
    println("MTI: $mti")
    parseFields(bitmapBinary, isoString, startPos)
}

// Parse fields based on the bitmap
fun parseFields(bitmapBinary: String, isoString: String, startIndex: Int) {
    val fieldDetails = Fields().getFieldDetails()
    var currentPos = startIndex

    bitmapBinary.drop(1).forEachIndexed { index, bit ->
        if (bit == '1') {
            val fieldNumber = index + 2
            val fieldDetail = fieldDetails[fieldNumber]

            try {
                if (fieldDetail != null) {
                    currentPos = processField(fieldDetail, isoString, currentPos, fieldNumber)
                } else {
                    println("Unknown field: $fieldNumber")
                }
            } catch (e: Exception) {
                println("Error processing field $fieldNumber: ${e.message}")
            }
        }
    }
}

// Process individual fields
fun processField(
    fieldDetail: Fields.FieldDetail,
    isoString: String,
    currentPos: Int,
    fieldNumber: Int
): Int {
    val fieldDefinition = fieldDetail.definition
    return when {
        fieldDefinition.type.contains("LLLVAR") || fieldDefinition.type.contains("LLVAR") -> {
             val (value, nextPos) = extractVariableLengthField(fieldDefinition, isoString, currentPos)
            println("${fieldDetail.label} (${fieldDefinition.type}) -> $value")
            nextPos
        }
        else -> {
            validateFieldLength(isoString, currentPos, fieldDefinition.length, fieldNumber)
            val fieldValue = isoString.substring(currentPos, currentPos + fieldDefinition.length)
            println("${fieldDetail.label} (${fieldDefinition.type}) -> $fieldValue")
            currentPos + fieldDefinition.length
        }
    }
}

// Extract fields with variable lengths
fun extractVariableLengthField(
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

    val fieldValue = isoString.substring(currentPos + lengthIndicatorSize, currentPos + lengthIndicatorSize + fieldLength)
    return fieldValue to (currentPos + lengthIndicatorSize + fieldLength)
}

// Validate field lengths during parsing
fun validateFieldLength(
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