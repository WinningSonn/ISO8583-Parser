package org.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class Iso8583ParserApplication

fun main(args: Array<String>) {
    runApplication<Iso8583ParserApplication>(*args)
}