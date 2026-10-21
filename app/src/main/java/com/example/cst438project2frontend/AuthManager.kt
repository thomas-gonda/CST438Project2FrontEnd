package com.example.cst438project2frontend

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials

enum class UserRole {
    ADMIN,
    USER
}

/**
 * Owns the Auth0 session and keeps credentials encrypted by the Auth0 SDK.
 *
 * The access token is only exposed through [accessToken] so API clients can add
 * it to requests without duplicating authentication logic in each screen.
 */
class AuthManager(context: Context) {
    companion object {
        const val apiAudience = "https://gamereview-api-gqbj.onrender.com"
        private const val scheme = "cst438project2"
        private const val requestedScope =
            "openid profile email offline_access admin"
    }

    private val applicationContext = context.applicationContext
    private val auth0 = Auth0.getInstance(applicationContext)
    private val credentialsManager = SecureCredentialsManager(
        applicationContext,
        auth0,
        SharedPreferencesStorage(applicationContext)
    )

    @Volatile
    private var currentAccessToken: String? = null

    @Volatile
    private var currentRole = UserRole.USER

    fun accessToken(): String? = currentAccessToken

    fun userRole(): UserRole = currentRole

    suspend fun restoreSession(): Boolean {
        if (!credentialsManager.hasValidCredentials()) {
            currentAccessToken = null
            currentRole = UserRole.USER
            return false
        }

        return runCatching {
            val credentials = credentialsManager.awaitCredentials()
            updateSession(credentials)
            true
        }.getOrElse {
            credentialsManager.clearCredentials()
            currentAccessToken = null
            currentRole = UserRole.USER
            false
        }
    }

    fun login(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (AuthenticationException) -> Unit
    ) {
        WebAuthProvider.login(auth0)
            .withScheme(scheme)
            .withAudience(apiAudience)
            .withScope(requestedScope)
            .start(
                activity,
                object : Callback<Credentials, AuthenticationException> {
                    override fun onSuccess(result: Credentials) {
                        runCatching {
                            credentialsManager.saveCredentials(result)
                            updateSession(result)
                        }.onSuccess {
                            onSuccess()
                        }.onFailure {
                            onFailure(
                                AuthenticationException(
                                    "a0.credentials_storage_failed",
                                    "The login succeeded, but the session could not be stored securely."
                                )
                            )
                        }
                    }

                    override fun onFailure(error: AuthenticationException) {
                        onFailure(error)
                    }
                }
            )
    }

    fun logout(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (AuthenticationException) -> Unit
    ) {
        WebAuthProvider.logout(auth0)
            .withScheme(scheme)
            .start(
                activity,
                object : Callback<Void?, AuthenticationException> {
                    override fun onSuccess(result: Void?) {
                        credentialsManager.clearCredentials()
                        currentAccessToken = null
                        currentRole = UserRole.USER
                        onSuccess()
                    }

                    override fun onFailure(error: AuthenticationException) {
                        onFailure(error)
                    }
                }
            )
    }

    private fun updateSession(credentials: Credentials) {
        currentAccessToken = credentials.accessToken
        currentRole = if (hasAdminPermission(credentials.accessToken)) {
            UserRole.ADMIN
        } else {
            UserRole.USER
        }
    }
}

internal fun hasAdminPermission(accessToken: String?): Boolean {
    val encodedPayload = accessToken?.split('.')?.getOrNull(1) ?: return false

    return runCatching {
        val payload = String(
            Base64.decode(
                encodedPayload,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            ),
            Charsets.UTF_8
        )
        hasAdminClaims(payload)
    }.getOrDefault(false)
}

internal fun hasAdminClaims(payload: String): Boolean {
    val permissions = Regex("\\\"permissions\\\"\\s*:\\s*\\[(.*?)]")
        .find(payload)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val hasPermissionClaim = Regex("\\\"admin\\\"").containsMatchIn(permissions)

    val scope = Regex("\\\"scope\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        .find(payload)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val hasScopeClaim = scope.split(Regex("\\s+")).contains("admin")

    return hasPermissionClaim || hasScopeClaim
}
