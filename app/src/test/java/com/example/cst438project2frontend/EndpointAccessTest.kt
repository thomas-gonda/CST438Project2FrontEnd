package com.example.cst438project2frontend

import org.junit.Assert.*
import org.junit.Test

class EndpointAccessTest {

    @Test
    fun guestsOnlySeePublicReads() {
        val available = endpointActions.filter {
            it.allowed(false, UserRole.USER)
        }

        assertEquals(
            setOf("games", "game", "reviews", "review"),
            available.map { it.key }.toSet()
        )

        assertTrue(
            available.all { it.method == "GET" }
        )
    }

    @Test
    fun regularUsersCanSubmitAndReadTheirReviewsButCannotAdministerRecords() {
        val available = endpointActions
            .filter {
                it.allowed(true, UserRole.USER)
            }
            .map { it.key }
            .toSet()

        assertEquals(
            setOf(
                "games",
                "game",
                "reviews",
                "review",
                "mine",
                "createReview"
            ),
            available
        )
    }

    @Test
    fun adminRoleWithoutLoginDoesNotUnlockWrites() {
        val available = endpointActions.filter {
            it.allowed(false, UserRole.ADMIN)
        }

        assertTrue(
            available.all {
                it.access == EndpointAccess.PUBLIC
            }
        )
    }

    @Test
    fun signedInAdminCanAccessAllDefinedActions() {
        assertTrue(
            endpointActions.all {
                it.allowed(true, UserRole.ADMIN)
            }
        )

        assertEquals(17, endpointActions.size)
    }
}