package com.example.cst438project2frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.cst438project2frontend.ui.theme.CST438Project2FrontEndTheme
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CST438Project2FrontEndTheme {
                var selectedGameId by rememberSaveable {
                    mutableStateOf<Long?>(null)
                }
                var selectedGameName by rememberSaveable {
                    mutableStateOf("")
                }
                val screenState = rememberSaveableStateHolder()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val gameId = selectedGameId

                    if (gameId == null) {
                        screenState.SaveableStateProvider("games") {
                            GameListScreen(
                                modifier = Modifier.padding(innerPadding),
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
                            modifier = Modifier.padding(innerPadding),
                            onBack = { selectedGameId = null }
                        )
                    }
                }
            }
        }
    }
}