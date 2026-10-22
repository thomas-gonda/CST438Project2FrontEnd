package com.example.cst438project2frontend

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private data class ActionForm(val action: EndpointAction, val values: Map<String, String>)
private data class RecordDetail(val action: EndpointAction, val values: Map<String, String>)

private fun endpoint(key: String) = endpointActions.first { it.key == key }

private fun recordValues(record: JSONObject): Map<String, String> = buildMap {
    record.keys().forEach { key ->
        if (!record.isNull(key)) put(key, record.get(key).toString())
    }
    val id = record.optString("id")
    when {
        record.has("publisher") -> put("gameId", id)
        record.has("rating") -> put("reviewId", id)
        record.has("email") -> put("userId", id)
    }
}

private fun responseError(response: EndpointResponse): String = when (response.status) {
    400 -> "Some details were not accepted. Check your entries and try again."
    401 -> "The server could not verify your account. Sign in again. If this continues, the API login connection needs attention."
    403 -> "Your account does not have permission to do that."
    404 -> "This item is no longer available. Refresh the list."
    409 -> "This change conflicts with an existing record. Refresh and try again."
    in 500..599 -> "The server could not complete this request. Please try again later."
    else -> "The request could not be completed (HTTP ${response.status})."
}

private fun connectionError(error: Exception): String = when (error) {
    is org.json.JSONException -> "The server returned data the app could not read. Open View API response for details."
    is UnknownHostException -> "The server address could not be reached. Check your internet connection."
    is SocketTimeoutException -> "The server took too long to respond. It may be starting up; please try again."
    is SSLException -> "A secure connection to the server could not be established."
    else -> "We could not load this information. Check your connection and try again."
}

/** A game catalog with review and administration flows. */
@Composable
fun GameReviewPage(
    signedIn: Boolean,
    role: UserRole,
    accessTokenProvider: () -> String?,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf("games") }
    var selectedGame by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var form by remember { mutableStateOf<ActionForm?>(null) }
    var detail by remember { mutableStateOf<RecordDetail?>(null) }
    var responses by remember { mutableStateOf<List<EndpointResponse>>(emptyList()) }
    var showResponses by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val admin = signedIn && role == UserRole.ADMIN

    fun log(response: EndpointResponse) {
        responses = (listOf(response) + responses).take(8)
    }

    fun openForm(key: String, values: Map<String, String>) {
        val action = endpoint(key)
        if (action.allowed(signedIn, role)) form = ActionForm(action, values)
    }

    BackHandler(enabled = selectedGame != null && form == null && detail == null && !showResponses) {
        selectedGame = null
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        notice?.let { message ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { notice = null }) { Text("Dismiss") }
                }
            }
        }
        if (selectedGame != null) {
            val gameId = selectedGame!!
            key(gameId) {
                GameDetailsPage(
                    gameId, signedIn, admin, revision, accessTokenProvider, ::log,
                    onBack = { selectedGame = null },
                    onForm = ::openForm,
                    onReviewDetails = { review ->
                        detail = RecordDetail(endpoint("review"), recordValues(review))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            key(tab) {
                CollectionPage(
                    tab, signedIn, admin, revision, accessTokenProvider, ::log,
                    onGame = { selectedGame = it },
                    onForm = ::openForm,
                    onDetail = { key, record -> detail = RecordDetail(endpoint(key), recordValues(record)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        NavigationBar {
            NavigationBarItem(
                selected = tab == "games", onClick = { tab = "games"; selectedGame = null },
                icon = { AppNavIcon("games") }, label = { Text("Games") }
            )
            if (signedIn) {
                NavigationBarItem(
                    selected = tab == "mine", onClick = { tab = "mine"; selectedGame = null },
                    icon = { AppNavIcon("reviews") }, label = { Text("My reviews") }
                )
            }
            if (admin) {
                NavigationBarItem(
                    selected = tab == "users", onClick = { tab = "users"; selectedGame = null },
                    icon = { AppNavIcon("users") }, label = { Text("Users") }
                )
            }
        }
    }
    form?.let { target ->
        key(target) {
            ActionDialog(target, accessTokenProvider, ::log,
                onDismiss = { form = null },
                onSuccess = {
                    form = null
                    revision++
                    notice = when (target.action.key) {
                        "createReview" -> "Your review was posted."
                        "deleteReview" -> "Review deleted."
                        "deleteGame" -> "Game deleted."
                        "deleteUser" -> "User deleted."
                        else -> "Changes saved."
                    }
                    if (target.action.key == "deleteGame") selectedGame = null
                })
        }
    }
    detail?.let { target ->
        key(target) {
            DetailDialog(target, accessTokenProvider, ::log, onDismiss = { detail = null })
        }
    }
    if (showResponses) {
        ApiResponseDialog(responses, onDismiss = { showResponses = false })
    }
}

private data class CollectionResult(val records: List<JSONObject>, val page: Int, val pages: Int)

private fun parseCollection(response: EndpointResponse): CollectionResult {
    val body = response.body.trim()
    val root = if (body.startsWith("[")) null else JSONObject(body)
    val content = root?.getJSONArray("content") ?: JSONArray(body)
    val meta = root?.getJSONObject("page")
    return CollectionResult(
        (0 until content.length()).map { content.getJSONObject(it) },
        meta?.getInt("number") ?: 0,
        meta?.getInt("totalPages") ?: 1
    )
}

@Composable
private fun CollectionPage(
    tab: String,
    signedIn: Boolean,
    admin: Boolean,
    revision: Int,
    token: () -> String?,
    log: (EndpointResponse) -> Unit,
    onGame: (String) -> Unit,
    onForm: (String, Map<String, String>) -> Unit,
    onDetail: (String, JSONObject) -> Unit,
    modifier: Modifier
) {
    var requestedPage by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CollectionResult?>(null) }
    val api = remember { EndpointApi() }

    LaunchedEffect(tab, requestedPage, refresh, revision) {
        loading = true
        error = null
        try {
            val response = api.execute(prepareEndpointRequest(endpoint(tab), mapOf(
                "page" to requestedPage.toString(), "size" to "20"
            )), token())
            log(response)
            if (response.status in 200..299) {
                val parsed = parseCollection(response)
                if (parsed.records.isEmpty() && requestedPage > 0) {
                    requestedPage = (parsed.pages - 1).coerceAtLeast(0)
                } else result = parsed
            } else error = responseError(response)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = connectionError(exception)
        } finally { loading = false }
    }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            PageHeading(
                title = when (tab) { "mine" -> "Your reviews"; "users" -> "Community"; else -> "Find your next game" },
                subtitle = when (tab) {
                    "mine" -> "Your opinions, all in one place."
                    "users" -> "Manage the app's database users."
                    else -> "Explore the catalog. See what players think."
                }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(when (tab) { "mine" -> "My reviews"; "users" -> "Users"; else -> "Discover" },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = { refresh++ }, enabled = !loading) { Text("Refresh") }
                    if (admin && tab != "mine") {
                        FilledTonalButton(onClick = {
                            onForm(if (tab == "users") "createUser" else "createGame", emptyMap())
                        }) { Text(if (tab == "users") "Add user" else "Add game") }
                    }
                }
            }
        }
        when {
            loading -> item { LoadingCard() }
            error != null -> item { ErrorCard(error!!, onRetry = { refresh++ }) }
            result?.records.isNullOrEmpty() -> item {
                EmptyCard(
                    if (tab == "mine") "Your first review starts here" else "Nothing here yet",
                    if (tab == "mine") "Choose a game from the Games tab and share your thoughts." else "New entries will appear here when they are added."
                )
            }
            else -> {
                items(result!!.records, key = { it.getLong("id") }) { record ->
                    when (tab) {
                        "games" -> GameCard(record, token, revision, refresh, onClick = { onGame(record.getLong("id").toString()) })
                        "mine" -> ReviewCard(record, admin,
                            onDetails = { onDetail("review", record) }, onForm = onForm,
                            onGame = { onGame(record.getLong("gameId").toString()) })
                        "users" -> UserCard(record, onDetails = { onDetail("user", record) }, onForm = onForm)
                    }
                }
                if (tab != "users") item {
                    PageControls(result!!.page, result!!.pages, onPage = { requestedPage = it })
                }
            }
        }
        if (!signedIn && tab == "games") item {
            Text("Sign in above to share a review.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun GameDetailsPage(
    gameId: String,
    signedIn: Boolean,
    admin: Boolean,
    revision: Int,
    token: () -> String?,
    log: (EndpointResponse) -> Unit,
    onBack: () -> Unit,
    onForm: (String, Map<String, String>) -> Unit,
    onReviewDetails: (JSONObject) -> Unit,
    modifier: Modifier
) {
    val api = remember { EndpointApi() }
    var game by remember { mutableStateOf<JSONObject?>(null) }
    var gameError by remember { mutableStateOf<String?>(null) }
    var gameLoading by remember { mutableStateOf(true) }
    var reviews by remember { mutableStateOf<CollectionResult?>(null) }
    var reviewError by remember { mutableStateOf<String?>(null) }
    var reviewLoading by remember { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(gameId, revision, refresh) {
        gameLoading = true
        gameError = null
        try {
            val response = api.execute(prepareEndpointRequest(endpoint("game"), mapOf("gameId" to gameId)), token())
            log(response)
            if (response.status in 200..299) game = JSONObject(response.body)
            else gameError = responseError(response)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) { gameError = connectionError(exception) }
        finally { gameLoading = false }
    }
    LaunchedEffect(gameId, page, revision, refresh) {
        reviewLoading = true
        reviewError = null
        try {
            val response = api.execute(prepareEndpointRequest(endpoint("reviews"), mapOf(
                "gameId" to gameId, "page" to page.toString(), "size" to "20"
            )), token())
            log(response)
            if (response.status in 200..299) {
                val parsed = parseCollection(response)
                if (parsed.records.isEmpty() && page > 0) page = (parsed.pages - 1).coerceAtLeast(0)
                else reviews = parsed
            } else reviewError = responseError(response)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) { reviewError = connectionError(exception) }
        finally { reviewLoading = false }
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = onBack) { Text("‹ Back") } }
        when {
            gameLoading -> item { LoadingCard() }
            gameError != null -> item { ErrorCard(gameError!!, onRetry = { refresh++ }) }
            game != null -> item {
                val currentGame = game!!
                GameHero(currentGame, token, revision, refresh)
                if (admin) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onForm("patchGame", recordValues(currentGame)) }) { Text("Edit game") }
                        TextButton(onClick = { onForm("deleteGame", recordValues(currentGame)) }) { Text("Delete game") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Player reviews", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (signedIn && game != null && gameError == null) {
                    Button(onClick = { onForm("createReview", mapOf("gameId" to gameId)) }) { Text("Write a review") }
                }
            }
            if (!signedIn) Text("Sign in above to add your review.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            reviewLoading -> item { LoadingCard() }
            reviewError != null -> item { ErrorCard(reviewError!!, onRetry = { refresh++ }) }
            reviews?.records.isNullOrEmpty() -> item {
                EmptyCard("Be the first to review", "Share what you liked and help other players choose.")
            }
            else -> {
                items(reviews!!.records, key = { it.getLong("id") }) { review ->
                    ReviewCard(review, admin, onDetails = { onReviewDetails(review) }, onForm = onForm)
                }
                item { PageControls(reviews!!.page, reviews!!.pages, onPage = { page = it }) }
            }
        }
        item { TextButton(onClick = { refresh++ }, enabled = !reviewLoading && !gameLoading) { Text("Refresh reviews") } }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GameCard(
    game: JSONObject,
    token: () -> String?,
    revision: Int,
    refresh: Int,
    onClick: () -> Unit
) {
    var rating by remember { mutableStateOf<GameRatingSummary?>(null) }
    var ratingLoading by remember { mutableStateOf(true) }
    var ratingFailed by remember { mutableStateOf(false) }
    val gameId = game.getLong("id").toString()

    LaunchedEffect(gameId, revision, refresh) {
        ratingLoading = true
        ratingFailed = false
        rating = null
        try {
            rating = ratingRequests.withPermit {
                loadGameRating(gameId, token)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            ratingFailed = true
        } finally {
            ratingLoading = false
        }
    }
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Text(game.optString("name").take(2).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.optString("name"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(game.optString("publisher"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    val average = rating?.average
                    Text(
                        text = when {
                            ratingLoading -> "Loading..."
                            ratingFailed -> "Unavailable"
                            average == null -> "No ratings"
                            else -> "★ " + String.format(java.util.Locale.getDefault(), "%.1f", average)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!ratingLoading && !ratingFailed && average != null) {
                        val count = rating!!.count
                        Text(
                            "$count ${if (count == 1) "rating" else "ratings"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(game.optString("category"), Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
                Text("Price: \$${game.optString("price")}", style = MaterialTheme.typography.labelLarge)
            }
            Text("Explore reviews  →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GameHero(
    game: JSONObject,
    token: () -> String?,
    revision: Int,
    refresh: Int
) {
    val colors = MaterialTheme.colorScheme
    val gameId = game.getLong("id").toString()

    var rating by remember {
        mutableStateOf<GameRatingSummary?>(null)
    }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(gameId, revision, refresh) {
        loading = true
        failed = false
        rating = null

        try {
            rating = ratingRequests.withPermit {
                loadGameRating(gameId, token)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primaryContainer,
                        colors.secondaryContainer
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = game.optString("category").uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    val average = rating?.average

                    Text(
                        text = when {
                            loading -> "Loading..."
                            failed -> "Rating unavailable"
                            average == null -> "No ratings"
                            else -> "★ " + String.format(
                                java.util.Locale.getDefault(),
                                "%.1f",
                                average
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )

                    if (!loading && !failed && average != null) {
                        val count = rating!!.count

                        Text(
                            text = "$count ${
                                if (count == 1) "rating" else "ratings"
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimaryContainer
                        )
                    }
                }
            }

            Text(
                game.optString("name"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                "By ${game.optString("publisher")}",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                "Price: \$${game.optString("price")}",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ReviewCard(
    review: JSONObject,
    admin: Boolean,
    onDetails: () -> Unit,
    onForm: (String, Map<String, String>) -> Unit,
    onGame: (() -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("★ ${review.optString("rating")}", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(review.optString("reviewDate"), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(review.optString("comment").ifBlank { "No written comment." }, style = MaterialTheme.typography.bodyLarge)
            Text("Player ${review.optString("userId")}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (onGame != null) TextButton(onClick = onGame) { Text("View game") }
                TextButton(onClick = onDetails) {
                    Text("Details")
                }
                if (admin) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) { Text("Manage") }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("patchReview" to "Edit review", "replaceReview" to "Replace review", "deleteReview" to "Delete review").forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    expanded = false
                                    onForm(key, recordValues(review))
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: JSONObject, onDetails: () -> Unit, onForm: (String, Map<String, String>) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(user.optString("displayName"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(user.optString("email"))
            Text(user.optString("role"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Row {
                TextButton(onClick = onDetails) { Text("Details") }
                TextButton(onClick = { onForm("patchUser", recordValues(user)) }) { Text("Edit") }
                TextButton(onClick = { onForm("deleteUser", recordValues(user)) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Row(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyCard(title: String, description: String) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(18.dp)) {
            Text(message)
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun PageControls(page: Int, pages: Int, onPage: (Int) -> Unit) {
    if (pages <= 1) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Text("Previous") }
        Text("${page + 1} / $pages", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { onPage(page + 1) }, enabled = page + 1 < pages) { Text("Next") }
    }
}

@Composable
private fun ActionDialog(
    target: ActionForm,
    token: () -> String?,
    log: (EndpointResponse) -> Unit,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val action = target.action
    val values = remember { mutableStateMapOf<String, String>().apply {
        action.fields.forEach { put(it.name, target.values[it.name] ?: it.initial) }
    } }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { EndpointApi() }
    val deleting = action.method == "DELETE"
    val title = when (action.key) {
        "createReview" -> "Write your review"
        "patchReview" -> "Edit review"
        "replaceReview" -> "Replace review"
        "patchGame" -> "Edit game"
        "patchUser" -> "Edit user"
        else -> action.title
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (deleting) {
                    val name = target.values["name"] ?: target.values["displayName"]
                    ?: target.values["comment"]?.take(80) ?: "this record"
                    Text("Delete $name? This cannot be undone.")
                } else {
                    if (action.key == "createReview") Text("What did you think? Share your experience.")
                    if (action.key.contains("User")) Text("This updates the app's database, not Auth0 login accounts or permissions.")
                    action.fields.filter { it.kind != InputKind.ID }.forEach { field ->
                        OutlinedTextField(
                            value = values[field.name].orEmpty(),
                            onValueChange = { values[field.name] = it },
                            label = { Text(when (field.name) { "comment" -> "Your review"; "role" -> "Role (USER or ADMIN)"; else -> field.label }) },
                            singleLine = field.name != "comment",
                            minLines = if (field.name == "comment") 4 else 1,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (action.method == "PATCH") Text("Blank fields keep their current values.", style = MaterialTheme.typography.bodySmall)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                error = null
                val request = try {
                    prepareEndpointRequest(action, values.toMap())
                } catch (exception: IllegalArgumentException) {
                    error = exception.message
                    null
                }
                if (request != null) {
                    busy = true
                    scope.launch {
                        try {
                            val currentToken = token()
                            if (currentToken.isNullOrBlank()) {
                                error = "Please sign in before saving changes."
                            } else {
                                val response = api.execute(request, currentToken)
                                log(response)
                                if (response.status in 200..299) onSuccess()
                                else error = responseError(response)
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (exception: Exception) {
                            error = connectionError(exception) + " Check the list before retrying; your change may have reached the server."
                        } finally { busy = false }
                    }
                }
            }) { Text(if (busy) "Saving..." else if (deleting) "Delete" else if (action.key == "createReview") "Post review" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
private fun DetailDialog(
    target: RecordDetail,
    token: () -> String?,
    log: (EndpointResponse) -> Unit,
    onDismiss: () -> Unit
) {
    var record by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        try {
            val response = EndpointApi().execute(prepareEndpointRequest(target.action, target.values), token())
            log(response)
            if (response.status in 200..299) record = JSONObject(response.body)
            else error = responseError(response)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) { error = connectionError(exception) }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(target.action.title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            record?.let { data ->
                val labels = if (target.action.key == "review") listOf("rating" to "Rating", "comment" to "Review", "reviewDate" to "Posted", "userId" to "Player", "gameId" to "Game")
                else listOf("displayName" to "Name", "email" to "Email", "role" to "Role")
                labels.forEach { (field, label) ->
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(data.optString(field), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun ApiResponseDialog(responses: List<EndpointResponse>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Recent API responses") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            responses.forEach { response ->
                Text("${response.method} ${response.path}", style = MaterialTheme.typography.labelMedium)
                Text("HTTP ${response.status}", fontWeight = FontWeight.Bold)
                val pretty = runCatching {
                    if (response.body.trimStart().startsWith("[")) JSONArray(response.body).toString(2)
                    else JSONObject(response.body).toString(2)
                }.getOrDefault(response.body.ifBlank { "No response body." })
                SelectionContainer { Text(pretty, style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

/** Small dependency-free icons for the three navigation destinations. */
@Composable
private fun AppNavIcon(kind: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(1.7f * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), strokeWidth = 1.7f * unit)
        }
        when (kind) {
            "games" -> {
                drawRoundRect(color, Offset(2f * unit, 5f * unit), Size(20f * unit, 14f * unit), CornerRadius(4f * unit), style = stroke)
                line(5f, 12f, 11f, 12f)
                line(8f, 9f, 8f, 15f)
                drawCircle(color, 1.2f * unit, Offset(16f * unit, 10f * unit))
                drawCircle(color, 1.2f * unit, Offset(19f * unit, 14f * unit))
            }
            "reviews" -> {
                drawRoundRect(color, Offset(3f * unit, 3f * unit), Size(18f * unit, 15f * unit), CornerRadius(3f * unit), style = stroke)
                line(6f, 18f, 6f, 22f)
                line(6f, 22f, 11f, 18f)
                line(7f, 8f, 17f, 8f)
                line(7f, 12f, 14f, 12f)
            }
            else -> {
                drawCircle(color, 3.5f * unit, Offset(9f * unit, 7f * unit), style = stroke)
                drawRoundRect(color, Offset(2f * unit, 14f * unit), Size(14f * unit, 7f * unit), CornerRadius(3f * unit), style = stroke)
                line(18f, 5f, 18f, 11f)
                line(15f, 8f, 21f, 8f)
                line(19f, 15f, 21f, 19f)
            }
        }
    }
}

private data class GameRatingSummary(val average: Double?, val count: Int)

// Limit background requests when several game cards are visible.
private val ratingRequests = Semaphore(4)

/** Includes every review page, rather than averaging only the visible page. */
private suspend fun loadGameRating(
    gameId: String,
    token: () -> String?
): GameRatingSummary {
    val api = EndpointApi()
    var page = 0
    var totalPages: Int
    var sum = 0.0
    var count = 0
    val seen = mutableSetOf<Long>()

    do {
        val response = api.execute(
            prepareEndpointRequest(
                endpoint("reviews"),
                mapOf("gameId" to gameId, "page" to page.toString(), "size" to "100")
            ),
            token()
        )
        check(response.status in 200..299) { "Rating request failed." }
        val body = JSONObject(response.body)
        val reviews = body.getJSONArray("content")
        totalPages = body.getJSONObject("page").getInt("totalPages")
        for (index in 0 until reviews.length()) {
            val review = reviews.getJSONObject(index)
            if (review.isNull("rating")) continue
            val value = review.getDouble("rating")
            if (value.isFinite() && seen.add(review.getLong("id"))) {
                sum += value
                count++
            }
        }
        page++
    } while (page < totalPages)

    return GameRatingSummary(if (count == 0) null else sum / count, count)
}
