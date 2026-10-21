package com.example.cst438project2frontend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL

/** A server response, including unsuccessful HTTP responses. */
data class EndpointResponse(
    val method: String,
    val path: String,
    val status: Int,
    val body: String
)

enum class EndpointAccess {
    PUBLIC,
    SIGNED_IN,
    ADMIN
}

enum class InputKind {
    TEXT,
    ID,
    PAGE,
    SIZE,
    RATING,
    PRICE,
    ROLE
}

data class EndpointField(
    val name: String,
    val label: String,
    val kind: InputKind = InputKind.TEXT,
    val optional: Boolean = false,
    val initial: String = ""
)

/** Describes one supported API action. */
data class EndpointAction(
    val key: String,
    val title: String,
    val method: String,
    val path: String,
    val access: EndpointAccess = EndpointAccess.PUBLIC,
    val fields: List<EndpointField> = emptyList()
) {
    fun allowed(signedIn: Boolean, role: UserRole): Boolean {
        return when (access) {
            EndpointAccess.PUBLIC -> true
            EndpointAccess.SIGNED_IN -> signedIn
            EndpointAccess.ADMIN ->
                signedIn && role == UserRole.ADMIN
        }
    }
}

private val pageFields = listOf(
    EndpointField(
        "page",
        "Page (starts at 0)",
        InputKind.PAGE,
        initial = "0"
    ),
    EndpointField(
        "size",
        "Items per page (1–100)",
        InputKind.SIZE,
        initial = "20"
    )
)

private fun id(name: String) =
    EndpointField(name, name, InputKind.ID)

private fun reviewFields(optional: Boolean = false) = listOf(
    EndpointField(
        "comment",
        "Comment (up to 1000 characters)",
        optional = optional
    ),
    EndpointField(
        "rating",
        "Rating",
        InputKind.RATING,
        optional
    )
)

private fun gameFields(optional: Boolean = false) = listOf(
    EndpointField(
        "name",
        "Game name",
        optional = optional
    ),
    EndpointField(
        "publisher",
        "Publisher",
        optional = optional
    ),
    EndpointField(
        "category",
        "Category",
        optional = optional
    ),
    EndpointField(
        "price",
        "Price",
        InputKind.PRICE,
        optional
    )
)

private fun userFields(optional: Boolean = false) = listOf(
    EndpointField(
        "displayName",
        "Display name",
        optional = optional
    ),
    EndpointField(
        "email",
        "Email",
        optional = optional
    ),
    EndpointField(
        "role",
        "Role (USER or ADMIN)",
        InputKind.ROLE,
        optional
    )
)

/** Actions match the supplied backend controllers. */
val endpointActions = listOf(
    EndpointAction(
        "games",
        "Browse games",
        "GET",
        "/api/v1/games",
        fields = pageFields
    ),
    EndpointAction(
        "game",
        "Game details",
        "GET",
        "/api/v1/games/{gameId}",
        fields = listOf(id("gameId"))
    ),
    EndpointAction(
        "reviews",
        "Game reviews",
        "GET",
        "/api/v1/games/{gameId}/reviews",
        fields = listOf(id("gameId")) + pageFields
    ),
    EndpointAction(
        "review",
        "Review details",
        "GET",
        "/api/v1/reviews/{reviewId}",
        fields = listOf(id("reviewId"))
    ),
    EndpointAction(
        "mine",
        "My reviews",
        "GET",
        "/api/v1/users/me/reviews",
        EndpointAccess.SIGNED_IN,
        pageFields
    ),
    EndpointAction(
        "createReview",
        "Write a review",
        "POST",
        "/api/v1/games/{gameId}/reviews",
        EndpointAccess.SIGNED_IN,
        listOf(id("gameId")) + reviewFields()
    ),
    EndpointAction(
        "patchReview",
        "Edit review fields",
        "PATCH",
        "/api/v1/reviews/{reviewId}",
        EndpointAccess.ADMIN,
        listOf(id("reviewId")) + reviewFields(true)
    ),
    EndpointAction(
        "replaceReview",
        "Replace review text and rating",
        "PUT",
        "/api/v1/reviews/{reviewId}",
        EndpointAccess.ADMIN,
        listOf(id("reviewId")) + reviewFields()
    ),
    EndpointAction(
        "deleteReview",
        "Delete review",
        "DELETE",
        "/api/v1/reviews/{reviewId}",
        EndpointAccess.ADMIN,
        listOf(id("reviewId"))
    ),
    EndpointAction(
        "createGame",
        "Add game",
        "POST",
        "/api/v1/games",
        EndpointAccess.ADMIN,
        gameFields()
    ),
    EndpointAction(
        "patchGame",
        "Edit game",
        "PATCH",
        "/api/v1/games/{gameId}",
        EndpointAccess.ADMIN,
        listOf(id("gameId")) + gameFields(true)
    ),
    EndpointAction(
        "deleteGame",
        "Delete game",
        "DELETE",
        "/api/v1/games/{gameId}",
        EndpointAccess.ADMIN,
        listOf(id("gameId"))
    ),
    EndpointAction(
        "users",
        "List users",
        "GET",
        "/api/v1/users",
        EndpointAccess.ADMIN
    ),
    EndpointAction(
        "user",
        "User details",
        "GET",
        "/api/v1/users/{userId}",
        EndpointAccess.ADMIN,
        listOf(id("userId"))
    ),
    EndpointAction(
        "createUser",
        "Add database user",
        "POST",
        "/api/v1/users",
        EndpointAccess.ADMIN,
        userFields()
    ),
    EndpointAction(
        "patchUser",
        "Edit database user",
        "PATCH",
        "/api/v1/users/{userId}",
        EndpointAccess.ADMIN,
        listOf(id("userId")) + userFields(true)
    ),
    EndpointAction(
        "deleteUser",
        "Delete database user",
        "DELETE",
        "/api/v1/users/{userId}",
        EndpointAccess.ADMIN,
        listOf(id("userId"))
    )
)

data class PreparedEndpointRequest(
    val method: String,
    val path: String,
    val body: String?
)

/** Validates input and constructs the request safely. */
fun prepareEndpointRequest(
    action: EndpointAction,
    values: Map<String, String>
): PreparedEndpointRequest {
    var path = action.path
    val body = JSONObject()
    val query = mutableListOf<String>()

    for (field in action.fields) {
        val value = values[field.name].orEmpty().trim()

        if (value.isEmpty() && field.optional) {
            continue
        }

        require(value.isNotEmpty()) {
            "${field.label} is required."
        }

        val converted: Any = when (field.kind) {
            InputKind.ID ->
                value.toLongOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException(
                        "${field.label} must be a positive whole number."
                    )

            InputKind.PAGE ->
                value.toIntOrNull()?.takeIf { it >= 0 }
                    ?: throw IllegalArgumentException(
                        "Page must be zero or greater."
                    )

            InputKind.SIZE ->
                value.toIntOrNull()?.takeIf { it in 1..100 }
                    ?: throw IllegalArgumentException(
                        "Items per page must be between 1 and 100."
                    )

            InputKind.RATING ->
                value.toFloatOrNull()?.takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException(
                        "Rating must be a finite number."
                    )

            InputKind.PRICE ->
                value.toBigDecimalOrNull()
                    ?.takeIf { it >= BigDecimal.ZERO }
                    ?: throw IllegalArgumentException(
                        "Price must be zero or greater."
                    )

            InputKind.ROLE -> value.uppercase().also {
                require(it in listOf("USER", "ADMIN")) {
                    "Role must be USER or ADMIN."
                }
            }

            InputKind.TEXT -> value.also {
                if (field.name == "comment") {
                    require(it.length <= 1000) {
                        "Comment must be 1000 characters or fewer."
                    }
                }
            }
        }

        when {
            path.contains("{${field.name}}") -> {
                path = path.replace(
                    "{${field.name}}",
                    converted.toString()
                )
            }

            field.kind == InputKind.PAGE ||
                    field.kind == InputKind.SIZE -> {
                query.add("${field.name}=$converted")
            }

            else -> {
                body.put(field.name, converted)
            }
        }
    }

    if (query.isNotEmpty()) {
        path += "?" + query.joinToString("&")
    }

    if (action.method == "PATCH") {
        require(body.length() > 0) {
            "Enter at least one field to change."
        }
    }

    return PreparedEndpointRequest(
        method = action.method,
        path = path,
        body = if (action.method in listOf("POST", "PATCH", "PUT")) {
            body.toString()
        } else {
            null
        }
    )
}

/**
 * Sends API requests with the current access token.
 * The backend remains responsible for authorization.
 */
class EndpointApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    suspend fun execute(
        request: PreparedEndpointRequest,
        token: String?
    ): EndpointResponse = withContext(Dispatchers.IO) {
        val connection = URL(
            baseUrl.trimEnd('/') + request.path
        ).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            authorizationHeader(token)?.let {
                connection.setRequestProperty(
                    "Authorization",
                    it
                )
            }

            request.body?.let { requestBody ->
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                connection.outputStream.use {
                    it.write(
                        requestBody.toByteArray(Charsets.UTF_8)
                    )
                }
            }

            val status = connection.responseCode

            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            EndpointResponse(
                method = request.method,
                path = request.path,
                status = status,
                body = responseBody
            )
        } finally {
            connection.disconnect()
        }
    }
}