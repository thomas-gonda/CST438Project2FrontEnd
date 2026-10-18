package com.example.cst438project2frontend

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@Composable
fun ReviewScreen(
    gameId: Long,
    gameName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val api = remember { ReviewApi() }
    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)

    LaunchedEffect(gameId, retryCount) {
        loading = true
        error = null

        try {
            reviews = api.getReviews(gameId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            error = "Could not load reviews. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("Back to games")
        }

        Text(
            text = gameName,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Reviews",
            style = MaterialTheme.typography.titleLarge
        )

        when {
            loading -> {
                CircularProgressIndicator()
                Text("Loading reviews…")
            }

            error != null -> {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { retryCount++ }) {
                    Text("Try again")
                }
            }

            reviews.isEmpty() -> {
                Text("No reviews yet for this game.")
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reviews, key = { it.id }) { review ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Rating: ${review.rating}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = review.comment.ifBlank {
                                        "No written comment."
                                    }
                                )
                                Text(
                                    text = "User ${review.userId} • ${review.reviewDate}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}