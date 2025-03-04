package org.example

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class Iso8583Response(
    val status: String,
    val data: Iso8583ParserService.ParsedIsoMessage? = null,
    val message: String? = null
)

@RestController
@RequestMapping("/api/iso8583")

class Iso8583Controller(private val parserService: Iso8583ParserService) {

    @PostMapping("/parse")
    fun parseIsoMessage(@RequestBody rawMessage: Map<String, String>): ResponseEntity<Iso8583Response> {
        return try {
            val isoMessage = rawMessage["isoMessage"]
                ?: throw IllegalArgumentException("The field 'isoMessage' is required.")
            val parsedResult = parserService.parseIsoMessage(isoMessage)

            ResponseEntity.ok(
                Iso8583Response(
                    status = "success",
                    data = parsedResult
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                Iso8583Response(
                    status = "error",
                    message = e.message ?: "Bad Request please check the payload structure and try again!"
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                Iso8583Response(
                    status = "error",
                    message = "An unexpected error occurred: ${e.message} please try again!"
                )
            )
        }
    }
}