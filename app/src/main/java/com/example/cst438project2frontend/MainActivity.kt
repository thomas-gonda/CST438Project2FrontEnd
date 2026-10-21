package com.example.cst438project2frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.cst438project2frontend.ui.theme.CST438Project2FrontEndTheme
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CST438Project2FrontEndTheme {
                val authManager = remember { AuthManager(applicationContext) }
                var isAuthenticated by rememberSaveable { mutableStateOf(false) }
                var userRole by rememberSaveable { mutableStateOf(UserRole.USER) }
                var authError by rememberSaveable { mutableStateOf<String?>(null) }
                var selectedGameId by rememberSaveable {
                    mutableStateOf<Long?>(null)
                }
                var selectedGameName by rememberSaveable {
                    mutableStateOf("")
                }
                val screenState = rememberSaveableStateHolder()

                LaunchedEffect(authManager) {
                    isAuthenticated = authManager.restoreSession()
                    userRole = authManager.userRole()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AuthControls(
                            isAuthenticated = isAuthenticated,
                            userRole = userRole,
                            errorMessage = authError,
                            onLogin = {
                                authError = null
                                authManager.login(
                                    activity = this@MainActivity,
                                    onSuccess = {
                                        isAuthenticated = true
                                        userRole = authManager.userRole()
                                    },
                                    onFailure = { error ->
                                        authError = error.getDescription()
                                            ?: "Login failed. Please try again."
                                    }
                                )
                            },
                            onLogout = {
                                authError = null
                                authManager.logout(
                                    activity = this@MainActivity,
                                    onSuccess = {
                                        isAuthenticated = false
                                        userRole = UserRole.USER
                                    },
                                    onFailure = { error ->
                                        authError = error.getDescription()
                                            ?: "Logout failed. Please try again."
                                    }
                                )
                            }
                        )

                        val gameId = selectedGameId
                        if (gameId == null) {
                            screenState.SaveableStateProvider("games") {
                                GameListScreen(
                                    modifier = Modifier.weight(1f),
                                    accessTokenProvider = authManager::accessToken,
                                    onGameSelected = { game ->
                                        selectedGameName = game.name
                                        selectedGameId = game.id
                                    }
                                )
                            }
                        } else {
                            ReviewScreen(
                                gameId = gameId,
                                gameName = selectedGameName,
                                modifier = Modifier.weight(1f),
                                accessTokenProvider = authManager::accessToken,
                                onBack = { selectedGameId = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthControls(
    isAuthenticated: Boolean,
    userRole: UserRole,
    errorMessage: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isAuthenticated) {
            Text("Signed in as ${userRole.name.lowercase().replaceFirstChar { it.uppercase() }}")
            OutlinedButton(onClick = onLogout) {
                Text("Log out")
            }
        } else {
            Button(onClick = onLogin) {
                Text("Log in with Auth0")
            }
        }

        if (errorMessage != null) {
            Text(errorMessage)
        }
    }
}
