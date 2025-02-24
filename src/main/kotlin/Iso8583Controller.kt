package org.example

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/iso8583")
class Iso8583Controller(private val parserService: Iso8583ParserService) {

    @PostMapping("/parse")
    fun parseISOMessage(@RequestBody isoMessage: String): String {
        return try {
            val sanitizedMessage = parserService.sanitizeIsoMessage(isoMessage)
            parserService.parseISOMessage(sanitizedMessage)
        } catch (e: Exception) {
            "Error parsing ISO8583 message: ${e.message}"
        }
    }
}