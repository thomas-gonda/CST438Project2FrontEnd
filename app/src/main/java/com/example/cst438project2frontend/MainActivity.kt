package com.example.cst438project2frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cst438project2frontend.ui.theme.CST438Project2FrontEndTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CST438Project2FrontEndTheme {
                val authManager = remember { AuthManager(applicationContext) }
                var signedIn by remember { mutableStateOf(false) }
                var role by remember { mutableStateOf(UserRole.USER) }
                var authBusy by remember { mutableStateOf(true) }
                var authError by remember { mutableStateOf<String?>(null) }
                val tokenProvider = remember(authManager) { { authManager.accessToken() } }

                LaunchedEffect(authManager) {
                    try {
                        signedIn = authManager.restoreSession()
                        role = authManager.userRole()
                    } finally {
                        authBusy = false
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Surface(tonalElevation = 1.dp) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Game Review", style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        if (!signedIn) "Discover. Play. Share."
                                        else if (role == UserRole.ADMIN) "Administrator"
                                        else "Player account",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (authBusy) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else if (signedIn) {
                                    TextButton(onClick = {
                                        authBusy = true
                                        authError = null
                                        authManager.logout(this@MainActivity,
                                            onSuccess = {
                                                signedIn = false
                                                role = UserRole.USER
                                                authBusy = false
                                            },
                                            onFailure = {
                                                authError = "Sign-out wasn't completed. Please try again."
                                                authBusy = false
                                            })
                                    }) { Text("Log out") }
                                } else {
                                    FilledTonalButton(onClick = {
                                        authBusy = true
                                        authError = null
                                        authManager.login(this@MainActivity,
                                            onSuccess = {
                                                signedIn = true
                                                role = authManager.userRole()
                                                authBusy = false
                                            },
                                            onFailure = {
                                                authError = "Sign-in wasn't completed. You can try again or keep browsing."
                                                authBusy = false
                                            })
                                    }) { Text("Sign in") }
                                }
                            }
                        }
                        authError?.let { message ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { authError = null }) { Text("Dismiss") }
                                }
                            }
                        }
                        // Changing accounts removes old private data and forms.
                        if (!authBusy) {
                            key(signedIn, role, authManager.accessToken()) {
                                GameReviewPage(
                                    signedIn = signedIn,
                                    role = role,
                                    accessTokenProvider = tokenProvider,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Connecting your account...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}