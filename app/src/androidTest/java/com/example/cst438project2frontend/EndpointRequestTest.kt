package com.example.cst438project2frontend

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on Android because request creation uses Android's JSON library.
 */
@RunWith(AndroidJUnit4::class)
class EndpointRequestTest {

    private fun action(key: String): EndpointAction {
        return endpointActions.first { it.key == key }
    }

    @Test
    fun reviewSubmissionDoesNotSendClientIdentityOrDate() {
        val request = prepareEndpointRequest(
            action("createReview"),
            mapOf(
                "gameId" to "12",
                "comment" to "A \"great\" game!",
                "rating" to "4.5",
                "userId" to "999",
                "reviewDate" to "2000-01-01"
            )
        )

        assertEquals("POST", request.method)
        assertEquals(
            "/api/v1/games/12/reviews",
            request.path
        )

        val body = JSONObject(request.body!!)

        assertEquals(2, body.length())
        assertEquals(
            "A \"great\" game!",
            body.getString("comment")
        )
        assertEquals(
            4.5,
            body.getDouble("rating"),
            0.001
        )
        assertFalse(body.has("userId"))
        assertFalse(body.has("reviewDate"))
    }

    @Test
    fun reviewPaginationUsesTheSelectedGameAndPage() {
        val request = prepareEndpointRequest(
            action("reviews"),
            mapOf(
                "gameId" to "12",
                "page" to "2",
                "size" to "20"
            )
        )

        assertEquals(
            "/api/v1/games/12/reviews?page=2&size=20",
            request.path
        )

        assertNull(request.body)
    }

    @Test
    fun patchOmitsBlankFields() {
        val request = prepareEndpointRequest(
            action("patchReview"),
            mapOf(
                "reviewId" to "7",
                "comment" to "Updated",
                "rating" to ""
            )
        )

        val body = JSONObject(request.body!!)

        assertEquals(1, body.length())
        assertEquals(
            "Updated",
            body.getString("comment")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPatchIsRejected() {
        prepareEndpointRequest(
            action("patchReview"),
            mapOf("reviewId" to "7")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteRatingIsRejected() {
        prepareEndpointRequest(
            action("createReview"),
            mapOf(
                "gameId" to "1",
                "comment" to "Test",
                "rating" to "NaN"
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedPageIsRejected() {
        prepareEndpointRequest(
            action("games"),
            mapOf(
                "page" to "0",
                "size" to "101"
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePriceIsRejected() {
        prepareEndpointRequest(
            action("patchGame"),
            mapOf(
                "gameId" to "1",
                "price" to "-1"
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedCommentIsRejected() {
        prepareEndpointRequest(
            action("createReview"),
            mapOf(
                "gameId" to "1",
                "comment" to "a".repeat(1001),
                "rating" to "4"
            )
        )
    }
}