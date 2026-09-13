package com.adipginting.lecturo

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import com.adipginting.lecturo.saved.SavedScreen
import com.adipginting.lecturo.chat.ChatScreen
import com.adipginting.lecturo.chat.ConversationListScreen
import com.adipginting.lecturo.chat.DraftChatScreen
import com.adipginting.lecturo.library.LibraryScreen
import com.adipginting.lecturo.reader.ReaderScreen
import com.adipginting.lecturo.settings.SettingsScreen
import com.adipginting.lecturo.ui.theme.LecturoTheme

class MainActivity : FragmentActivity() {

    /** Document URL shared into the app via ACTION_SEND; consumed once. */
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium builds its EPUB navigator through a FragmentFactory and does
        // not support being restored by Android, so a process killed mid-book
        // crashed the relaunch before the reader could even be drawn. The dummy
        // factory lets the restore finish, and the restored navigators are
        // dropped below, leaving the reader screen to open its book again from
        // the position saved in the library.
        val restoring = savedInstanceState != null
        if (restoring) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        if (restoring) {
            supportFragmentManager.commitNow {
                supportFragmentManager.fragments.forEach { remove(it) }
            }
        }
        enableEdgeToEdge()
        // Unpacked EPUBs from the retired WebView reader. Nothing writes this
        // directory any more, so it is dead bytes on any device that ran it.
        Thread { java.io.File(filesDir, "epub").deleteRecursively() }.start()
        sharedUrl = extractSharedUrl(intent)
        setContent {
            LecturoTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(
                            onOpenDocument = { id -> navController.navigate("reader/$id") },
                            onOpenSaved = { navController.navigate("saved") },
                            onOpenSettings = { navController.navigate("settings") },
                            sharedUrl = sharedUrl,
                            onSharedUrlConsumed = { sharedUrl = null },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("saved") {
                        SavedScreen(
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
