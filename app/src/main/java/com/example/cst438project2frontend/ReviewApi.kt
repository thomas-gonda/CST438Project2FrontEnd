package com.example.cst438project2frontend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Review(
    val id: Long,
    val comment: String,
    val rating: String,
    val reviewDate: String,
    val userId: Long
)

class ReviewApi(
    private val baseUrl: String = "http://10.0.2.2:8080"
) {
    suspend fun getReviews(gameId: Long): List<Review> =
        withContext(Dispatchers.IO) {
            val url = URL(
                "${baseUrl.trimEnd('/')}/api/v1/games/$gameId/reviews"
            )
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")

                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) {
                    throw IOException("Could not load reviews (HTTP $status).")
                }

                val body = connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val reviews = JSONArray(body)

                buildList {
                    for (index in 0 until reviews.length()) {
                        val item = reviews.getJSONObject(index)
                        add(
                            Review(
                                id = item.getLong("id"),
                                comment = if (item.isNull("comment")) {
                                    ""
                                } else {
                                    item.getString("comment")
                                },
                                rating = if (item.isNull("rating")) {
                                    "Not rated"
                                } else {
                                    item.get("rating").toString()
                                },
                                reviewDate = item.getString("reviewDate"),
                                userId = item.getLong("userId")
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
}