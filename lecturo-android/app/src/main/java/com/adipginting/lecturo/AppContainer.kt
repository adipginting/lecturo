package com.adipginting.lecturo

import android.content.Context
import com.adipginting.lecturo.chat.ChatImages
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.data.RoomConversations
import com.adipginting.lecturo.data.RoomDocuments
import com.adipginting.lecturo.data.RoomPrompts
import com.adipginting.lecturo.data.RoomSkills
import com.adipginting.lecturo.domain.chat.Conversations
import com.adipginting.lecturo.domain.chat.Prompts
import com.adipginting.lecturo.domain.library.Documents
import com.adipginting.lecturo.domain.skills.Skills
import java.io.File

/**
 * The composition root: the one place that knows about Room and the chat-images
 * store, exposing them as domain ports. ViewModels ask it; Compose never does.
 */
object AppContainer {
    fun conversations(context: Context): Conversations =
        RoomConversations(
            LecturoDatabase.get(context),
            ChatImages(File(context.filesDir, "chat_images")),
        )

    fun prompts(context: Context): Prompts = RoomPrompts(LecturoDatabase.get(context))

    fun documents(context: Context): Documents =
        RoomDocuments(context.applicationContext, LecturoDatabase.get(context))

    fun skills(context: Context): Skills = RoomSkills(LecturoDatabase.get(context))
}
