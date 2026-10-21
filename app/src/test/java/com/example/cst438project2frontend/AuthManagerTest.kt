package com.example.cst438project2frontend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthManagerTest {
    @Test
    fun `admin permission claim identifies an admin token`() {
        assertTrue(hasAdminClaims("{\"permissions\":[\"admin\"]}"))
    }

    @Test
    fun `regular user token is not treated as admin`() {
        assertFalse(hasAdminClaims("{\"permissions\":[]}"))
        assertFalse(hasAdminClaims("{\"scope\":\"openid profile\"}"))
    }

    @Test
    fun `malformed or missing tokens are treated as regular users`() {
        assertFalse(hasAdminPermission(null))
        assertFalse(hasAdminPermission("not-a-jwt"))
        assertFalse(hasAdminPermission("header.invalid-payload.signature"))
    }
}
