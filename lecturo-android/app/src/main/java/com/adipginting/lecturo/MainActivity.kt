package com.adipginting.lecturo

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import com.adipginting.lecturo.chat.ChatScreen
import com.adipginting.lecturo.chat.ChatSettings
import com.adipginting.lecturo.chat.ConversationListScreen
import com.adipginting.lecturo.library.LibraryScreen
import com.adipginting.lecturo.reader.ReaderScreen
import com.adipginting.lecturo.skills.DocumentSkillsScreen
import com.adipginting.lecturo.settings.SettingsScreen
import com.adipginting.lecturo.skills.SkillsScreen
import com.adipginting.lecturo.ui.theme.LecturoTheme
import com.adipginting.lecturo.util.MathSetting
import kotlinx.coroutines.flow.collect

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
                // The math renderer reads one mirrored setting rather than every
                // host threading the DataStore value down to each message.
                val chatSettings = remember { ChatSettings(applicationContext) }
                LaunchedEffect(chatSettings) {
                    chatSettings.mathRendering.collect { MathSetting.enabled = it }
                }
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(
                            onOpenDocument = { id -> navController.navigate("reader/$id") },
                            onOpenChats = { navController.navigate("conversations") },
                            onOpenSettings = { navController.navigate("settings") },
                            sharedUrl = sharedUrl,
                            onSharedUrlConsumed = { sharedUrl = null },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenSkills = { navController.navigate("skills") },
                        )
                    }
                    composable("skills") {
                        SkillsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("conversations") {
                        ConversationListScreen(
                            onBack = { navController.popBackStack() },
                            onOpenConversation = { id -> navController.navigate("chat/$id") },
                        )
                    }
                    composable("chat/{conversationId}") { entry ->
                        val id = entry.arguments?.getString("conversationId")
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
                            onOpenSkills = { navController.navigate("doc-skills/$docId") },
                        )
                    }
                    composable("doc-skills/{docId}") { entry ->
                        val docId = entry.arguments?.getString("docId") ?: return@composable
                        DocumentSkillsScreen(
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
