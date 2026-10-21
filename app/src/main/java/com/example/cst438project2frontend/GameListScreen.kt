package com.example.cst438project2frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@Composable
fun GameListScreen(
    modifier: Modifier = Modifier,
    accessTokenProvider: () -> String? = { null },
    onGameSelected: (Game) -> Unit = {}
) {
    val api = remember(accessTokenProvider) {
        GameApi(accessTokenProvider = accessTokenProvider)
    }
    var requestedPage by rememberSaveable { mutableIntStateOf(0) }
    var retryCount by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<GamePage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestedPage, retryCount) {
        loading = true
        error = null

        try {
            result = api.getGames(page = requestedPage)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            error = "Could not load games. Check your connection and try again."
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
        Text(
            text = "Games",
            style = MaterialTheme.typography.headlineMedium
        )
        Text("Choose a game to view its reviews.")

        when {
            loading -> {
                CircularProgressIndicator()
                Text("Loading games…")
            }

            error != null -> {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { retryCount++ }) {
                    Text("Try again")
                }
                if (requestedPage > 0) {
                    TextButton(onClick = { requestedPage-- }) {
                        Text("Previous page")
                    }
                }
            }

            else -> {
                val currentPage = result
                if (currentPage != null) {
                    if (currentPage.games.isEmpty()) {
                        Text("No games on this page.")
                        Spacer(Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = currentPage.games,
                                key = { it.id }
                            ) { game ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement =
                                            Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = game.name,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text("Publisher: ${game.publisher}")
                                        Text("Category: ${game.category}")
                                        Text("Price: ${game.price.toPlainString()}")

                                        Button(
                                            onClick = { onGameSelected(game) }
                                        ) {
                                            Text("View reviews")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { requestedPage-- },
                            enabled = requestedPage > 0
                        ) {
                            Text("Previous")
                        }

                        OutlinedButton(
                            onClick = { requestedPage++ },
                            enabled = currentPage.pageNumber + 1 <
                                    currentPage.totalPages
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}
