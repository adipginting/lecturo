package com.adipginting.lecturo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adipginting.lecturo.basket.BasketScreen
import com.adipginting.lecturo.chat.ChatScreen
import com.adipginting.lecturo.chat.ConversationListScreen
import com.adipginting.lecturo.chat.DraftChatScreen
import com.adipginting.lecturo.library.LibraryScreen
import com.adipginting.lecturo.reader.ReaderScreen
import com.adipginting.lecturo.settings.SettingsScreen
import com.adipginting.lecturo.ui.theme.LecturoTheme

class MainActivity : ComponentActivity() {

    /** Document URL shared into the app via ACTION_SEND; consumed once. */
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl = extractSharedUrl(intent)
        setContent {
            LecturoTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(
                            onOpenDocument = { id -> navController.navigate("reader/$id") },
                            onOpenBasket = { navController.navigate("basket") },
                            onOpenSettings = { navController.navigate("settings") },
                            sharedUrl = sharedUrl,
                            onSharedUrlConsumed = { sharedUrl = null },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("basket") {
                        BasketScreen(
                            onBack = { navController.popBackStack() },
                            onOpenChat = { navController.navigate("conversations") },
                            onOpenDraft = { navController.navigate("chat/new") },
                        )
                    }
                    composable("chat/new") {
                        DraftChatScreen(
                            onBack = { navController.popBackStack() },
                            onSent = { id ->
                                navController.navigate("chat/$id") {
                                    popUpTo("chat/new") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("conversations") {
                        ConversationListScreen(
                            onBack = { navController.popBackStack() },
                            onOpenConversation = { id -> navController.navigate("chat/$id") },
                        )
                    }
                    composable("chat/{conversationId}") { entry ->
                        val id = entry.arguments?.getString("conversationId")?.toLongOrNull()
                            ?: return@composable
                        ChatScreen(
                            conversationId = id,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("reader/{docId}") { entry ->
                        val docId = entry.arguments?.getString("docId") ?: return@composable
                        ReaderScreen(
                            docId = docId,
                            onBack = { navController.popBackStack() },
                            onOpenDraft = { navController.navigate("chat/new") },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedUrl(intent)?.let { sharedUrl = it }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return text.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
