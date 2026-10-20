package com.techmodware.tester

object Keys {
    private val data = listOf(
        "OWNER:96fb6028a949",
        "COOWNER:ec9551ee33a4"
    )
    val table: Map<String, String> = data.associate { it.substringAfter(":") to it.substringBefore(":") }
}
