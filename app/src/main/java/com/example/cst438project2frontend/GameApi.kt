package com.example.cst438project2frontend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL

data class Game(
    val id: Long,
    val name: String,
    val publisher: String,
    val category: String,
    val price: BigDecimal
)

data class GamePage(
    val games: List<Game>,
    val pageNumber: Int,
    val totalPages: Int
)

class GameApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val accessTokenProvider: () -> String? = { null }
) {
    suspend fun getGames(page: Int = 0, size: Int = 20): GamePage =
        withContext(Dispatchers.IO) {
            require(page >= 0) { "Page must be zero or greater." }
            require(size in 1..100) { "Size must be between 1 and 100." }

            val url = URL(
                "${baseUrl.trimEnd('/')}/api/v1/games?page=$page&size=$size"
            )
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")
                authorizationHeader(accessTokenProvider())?.let { authorization ->
                    connection.setRequestProperty("Authorization", authorization)
                }

                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) {
                    throw IOException("Could not load games (HTTP $status).")
                }

                val body = connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val response = JSONObject(body)
                val content = response.getJSONArray("content")
                val metadata = response.getJSONObject("page")

                val games = buildList {
                    for (index in 0 until content.length()) {
                        val item = content.getJSONObject(index)

                        add(
                            Game(
                                id = item.getLong("id"),
                                name = item.getString("name"),
                                publisher = item.getString("publisher"),
                                category = item.getString("category"),
                                price = BigDecimal(item.get("price").toString())
                            )
                        )
                    }
                }

                GamePage(
                    games = games,
                    pageNumber = metadata.getInt("number"),
                    totalPages = metadata.getInt("totalPages")
                )
            } finally {
                connection.disconnect()
            }
        }
}
