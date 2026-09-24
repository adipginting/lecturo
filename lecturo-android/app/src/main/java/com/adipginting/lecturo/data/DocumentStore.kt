package com.adipginting.lecturo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** File name inside filesDir/documents. */
    val fileName: String,
    /** "pdf" or "epub". */
    val format: String,
    /** PDF: page number as string. EPUB: root-relative spine href. */
    val lastLocator: String? = null,
    /** "calibre" or "zotero" when the file was downloaded from a remote library. */
    val source: String? = null,
    /** The remote item's own id, so the list can mark what is already local. */
    val remoteId: String? = null,
    /** The URL the file was fetched from, so a missing file can be fetched again. */
    val remoteUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: DocumentEntity)

    @Query("UPDATE documents SET lastLocator = :locator, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLocator(id: String, locator: String, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val providerId: String,
    /** Saved prompt chosen at conversation start; locked thereafter. */
    val promptId: Long? = null,
    /** One-off prompt typed at conversation start, when no saved prompt is used. */
    val customPrompt: String? = null,
    /** Excerpt fired from the saved text or the reader to seed this conversation's context. */
    val contextText: String? = null,
    /** Screenshot frozen as context, as a ChatImages file path; sent with the first user message. */
    val contextImagePath: String? = null,
    /** documents.id this chat belongs to; null when started without a document. */
    val docId: String? = null,
    /** Document title and locator (page/href) captured when the draft was sent. */
    val docTitle: String? = null,
    val docLocator: String? = null,
    /** Usage the API reported for the last reply; null until one reports it. */
    val lastPromptTokens: Int? = null,
    val lastCompletionTokens: Int? = null,
    /** When the last reply landed; messages newer than this were not in its payload. */
    val lastCallAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Null while this is still a draft — a conversation that has not been sent. */
    val sentAt: Long? = null,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    /** "user" or "assistant". */
    val role: String,
    val text: String,
    /** Image attached to this message, as a ChatImages file path; null for plain text. */
    val imagePath: String? = null,
    /** A compaction summary standing in for the messages marked superseded. */
    val isSummary: Boolean = false,
    /** Replaced by a compaction summary; kept for display, never sent again. */
    val superseded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
)

/**
 * An installed skill: a declarative prompt pack the reader can attach to a book
 * and run. It is text only — nothing installed here executes anything but a
 * model call.
 */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val author: String? = null,
    val version: String = "",
    /** "message" runs on demand, "conversation" seeds a chat, "book" runs over the book. */
    val scope: String,
    /** "manual" or "on-save". */
    val trigger: String,
    /** Prompt text; {{book}} and {{excerpt}} are filled in when it runs. */
    val template: String,
    /** Reserved for the marketplace; everything is free while this is null. */
    val price: String? = null,
    /** Where it was installed from; null for the built-in skills. */
    val catalogUrl: String? = null,
    val installedAt: Long = System.currentTimeMillis(),
)

/** A skill applied to one book: application is book-level, not per excerpt. */
@Entity(tableName = "book_skills", primaryKeys = ["docId", "skillId"])
data class BookSkillEntity(
    val docId: String,
    val skillId: String,
    val appliedAt: Long = System.currentTimeMillis(),
)

/** One background run of a skill over a book, and what it produced. */
@Entity(tableName = "skill_runs")
data class SkillRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: String,
    val skillId: String,
    /** "running", "done" or "failed". */
    val status: String,
    val result: String? = null,
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    /** Drafts: conversations whose first message has not been sent. */
    @Query("SELECT * FROM conversations WHERE sentAt IS NULL ORDER BY updatedAt DESC")
    fun observeDrafts(): Flow<List<ConversationEntity>>

    /** Conversations that have been sent; drafts are not among them. */
    @Query("SELECT * FROM conversations WHERE sentAt IS NOT NULL ORDER BY updatedAt DESC")
    fun observeSent(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET sentAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun markSent(id: Long, at: Long)

    @Query("SELECT * FROM conversations WHERE docId = :docId")
    fun observeForDoc(docId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET providerId = :providerId WHERE id = :id")
    suspend fun setProvider(id: Long, providerId: String)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query(
        "UPDATE conversations SET lastPromptTokens = :promptTokens, " +
            "lastCompletionTokens = :completionTokens, lastCallAt = :callAt WHERE id = :id",
    )
    suspend fun recordUsage(id: Long, promptTokens: Int?, completionTokens: Int?, callAt: Long)

    @Query(
        "UPDATE conversations SET lastPromptTokens = NULL, " +
            "lastCompletionTokens = NULL, lastCallAt = NULL WHERE id = :id",
    )
    suspend fun clearUsage(id: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, id")
    fun observeFor(conversationId: Long): Flow<List<MessageEntity>>

    /** The payload-eligible messages: everything compaction has not superseded. */
    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId " +
            "AND superseded = 0 ORDER BY createdAt, id",
    )
    suspend fun getActiveFor(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET superseded = 1 WHERE conversationId = :conversationId AND superseded = 0")
    suspend fun supersedeActive(conversationId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteFor(conversationId: Long)

    /** Image files this conversation's rows point at, for cleanup on delete. */
    @Query(
        "SELECT imagePath FROM messages WHERE conversationId = :conversationId " +
            "AND imagePath IS NOT NULL",
    )
    suspend fun imagePathsFor(conversationId: Long): List<String>
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY title")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun get(id: Long): PromptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prompt: PromptEntity): Long

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY name")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun get(id: String): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: SkillEntity)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BookSkillDao {
    @Query("SELECT * FROM book_skills WHERE docId = :docId ORDER BY appliedAt")
    fun observeForDoc(docId: String): Flow<List<BookSkillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(applied: BookSkillEntity)

    @Query("DELETE FROM book_skills WHERE docId = :docId AND skillId = :skillId")
    suspend fun delete(docId: String, skillId: String)

    @Query("DELETE FROM book_skills WHERE docId = :docId")
    suspend fun deleteForDoc(docId: String)
}

@Dao
interface SkillRunDao {
    @Query("SELECT * FROM skill_runs WHERE docId = :docId ORDER BY startedAt DESC, id DESC")
    fun observeForDoc(docId: String): Flow<List<SkillRunEntity>>

    @Query("DELETE FROM skill_runs WHERE docId = :docId")
    suspend fun deleteForDoc(docId: String)

    @Insert
    suspend fun insert(run: SkillRunEntity): Long

    @Query(
        "UPDATE skill_runs SET status = :status, result = :result, error = :error, " +
            "finishedAt = :finishedAt WHERE id = :id",
    )
    suspend fun finish(id: Long, status: String, result: String?, error: String?, finishedAt: Long)
}

@Database(
    entities = [
        DocumentEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        PromptEntity::class,
        SkillEntity::class,
        BookSkillEntity::class,
        SkillRunEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
abstract class LecturoDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun promptDao(): PromptDao
    abstract fun skillDao(): SkillDao
    abstract fun bookSkillDao(): BookSkillDao
    abstract fun skillRunDao(): SkillRunDao

    companion object {
        @Volatile
        private var instance: LecturoDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `basket_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`docId` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`locator` TEXT, `createdAt` INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `providerId` TEXT NOT NULL, " +
                        "`promptId` INTEGER, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`conversationId` INTEGER NOT NULL, `role` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prompts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `body` TEXT NOT NULL)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `customPrompt` TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `contextText` TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `docTitle` TEXT")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `docLocator` TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `docId` TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `lastPromptTokens` INTEGER")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `lastCompletionTokens` INTEGER")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `lastCallAt` INTEGER")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `isSummary` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `superseded` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `contextImagePath` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `imagePath` TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `source` TEXT")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `remoteId` TEXT")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `remoteUrl` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skills` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`author` TEXT, `version` TEXT NOT NULL, `scope` TEXT NOT NULL, " +
                        "`trigger` TEXT NOT NULL, `template` TEXT NOT NULL, `price` TEXT, " +
                        "`catalogUrl` TEXT, `installedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_skills` (" +
                        "`docId` TEXT NOT NULL, `skillId` TEXT NOT NULL, " +
                        "`appliedAt` INTEGER NOT NULL, PRIMARY KEY(`docId`, `skillId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skill_runs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `docId` TEXT NOT NULL, " +
                        "`skillId` TEXT NOT NULL, `status` TEXT NOT NULL, `result` TEXT, " +
                        "`error` TEXT, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER)",
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `sentAt` INTEGER")
                // Saved excerpts were a queue of things to ask about. A draft is
                // that same thing said properly: a conversation not yet sent.
                db.execSQL(
                    "INSERT INTO `conversations` " +
                        "(`title`, `providerId`, `promptId`, `customPrompt`, `contextText`, " +
                        "`docId`, `docTitle`, `docLocator`, `createdAt`, `updatedAt`, `sentAt`) " +
                        "SELECT 'New conversation', 'openai', NULL, NULL, b.`text`, b.`docId`, " +
                        "d.`title`, b.`locator`, b.`createdAt`, b.`createdAt`, NULL " +
                        "FROM `basket_items` b LEFT JOIN `documents` d ON d.`id` = b.`docId`",
                )
            }
        }

        /**
         * 11 → 12: the move to drafts was applied to the wrong half of the table.
         * Turning saved items into drafts set `sentAt` null for them — correct — but
         * every conversation that already existed kept a null `sentAt` too, so chats
         * that had been answered for weeks were listed under Drafts.
         *
         * A conversation that has messages was sent; there is no other way for one to
         * come to exist. A draft that never got a reply has none, and stays a draft.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE `conversations` SET `sentAt` = " +
                        "CASE WHEN `updatedAt` = 0 THEN `createdAt` ELSE `updatedAt` END " +
                        "WHERE `sentAt` IS NULL AND `id` IN " +
                        "(SELECT `conversationId` FROM `messages`)",
                )
            }
        }

        /**
         * 12 → 13: the saved queue is gone. A draft is a conversation that has not
         * been sent, and every saved excerpt became one when `sentAt` arrived, so
         * the table has had no writer since — it is dropped rather than left
         * behind as a lie in the schema.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `basket_items`")
            }
        }

        fun get(context: Context): LecturoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LecturoDatabase::class.java,
                    "lecturo.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                )
                    .build().also { instance = it }
            }
    }
}
