package com.example.cst438project2frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiAuthorizationTest {
    @Test
    fun `missing access token does not create an authorization header`() {
        assertNull(authorizationHeader(null))
        assertNull(authorizationHeader("   "))
    }

    @Test
    fun `access token creates bearer authorization header`() {
        assertEquals(
            "Bearer access-token",
            authorizationHeader("  access-token  ")
        )
    }
}
