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

@Entity(tableName = "basket_items")
data class BasketItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: String,
    val text: String,
    /** PDF page / EPUB spine href where the text was selected. */
    val locator: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Basket item joined with its document's title for display. */
data class BasketRow(
    @androidx.room.Embedded val item: BasketItemEntity,
    val docTitle: String?,
)

@Dao
interface BasketDao {
    @Query(
        "SELECT basket_items.*, documents.title AS docTitle FROM basket_items " +
            "LEFT JOIN documents ON documents.id = basket_items.docId " +
            "ORDER BY basket_items.createdAt DESC",
    )
    fun observeAllWithTitles(): Flow<List<BasketRow>>

    @Insert
    suspend fun insert(item: BasketItemEntity)

    @Query("DELETE FROM basket_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM basket_items WHERE docId IN (:docIds)")
    suspend fun deleteByDocIds(docIds: List<String>)

    @Query("DELETE FROM basket_items")
    suspend fun clear()

    @Query(
        "SELECT basket_items.*, documents.title AS docTitle FROM basket_items " +
            "LEFT JOIN documents ON documents.id = basket_items.docId " +
            "ORDER BY basket_items.createdAt",
    )
    suspend fun allWithTitles(): List<BasketRow>
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
    /** Excerpt fired from the basket/reader to seed this conversation's context. */
    val contextText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    /** "user" or "assistant". */
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, id")
    fun observeFor(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, id")
    suspend fun getFor(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteFor(conversationId: Long)
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

@Database(
    entities = [
        DocumentEntity::class,
        BasketItemEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        PromptEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class LecturoDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun basketDao(): BasketDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun promptDao(): PromptDao

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

        fun get(context: Context): LecturoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LecturoDatabase::class.java,
                    "lecturo.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { instance = it }
            }
    }
}
