package com.example.cst438project2frontend

internal fun authorizationHeader(accessToken: String?): String? {
    val token = accessToken?.trim().orEmpty()
    return token.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }
}
