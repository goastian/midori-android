package org.midorinext.android.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "sync_local_state")
internal data class SyncRevision(
    @PrimaryKey val id: Int = 1,
    val epoch: String,
    val revision: Long = 0,
    val localRevision: Long = 0,
    val remote: Int = 0
)

@Entity(tableName = "sync_local_changes", primaryKeys = ["kind", "key"], indices = [Index(value = ["localRevision"])])
internal data class SyncLocalChange(val kind: String, val key: String, val revision: Long, val localRevision: Long)

@Entity(tableName = "sync_record_index", primaryKeys = ["scope", "id"],
    indices = [Index(value = ["scope", "nativeKey", "dayStart"]), Index(value = ["scope", "nativeKey", "id"])])
internal data class SyncRecordIndex(
    val scope: String, val id: String, val nativeKey: String,
    val dayStart: Long = -1, val windowStart: Long = -1, val windowEnd: Long = -1
)

@Dao
internal interface SyncJournalDao {
    @Query("SELECT * FROM sync_local_state WHERE id=1")
    suspend fun revision(): SyncRevision

    @Query("SELECT localRevision FROM sync_local_state WHERE id=1")
    fun localRevisions(): Flow<Long>

    @Query("SELECT * FROM sync_local_changes WHERE localRevision > :after AND localRevision <= :through ORDER BY localRevision LIMIT :limit")
    suspend fun changes(after: Long, through: Long, limit: Int): List<SyncLocalChange>

    @Query("UPDATE sync_local_state SET revision=revision+1, localRevision=revision+1 WHERE id=1")
    suspend fun bumpLocal()

    @androidx.room.Upsert
    suspend fun putLocal(change: SyncLocalChange)

    @androidx.room.Transaction
    suspend fun markLocal(kind: String, key: String) {
        bumpLocal()
        val next = revision()
        putLocal(SyncLocalChange(kind, key, next.revision, next.localRevision))
    }

    @Query("DELETE FROM sync_local_changes WHERE revision <= :through")
    suspend fun compact(through: Long)

    @Query("UPDATE sync_local_state SET remote=:remote WHERE id=1")
    suspend fun remote(remote: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun remember(record: SyncRecordIndex)

    @Query("SELECT * FROM sync_record_index WHERE scope=:scope AND id=:id")
    suspend fun identity(scope: String, id: String): SyncRecordIndex?

    @Query("SELECT * FROM sync_record_index WHERE scope=:scope AND nativeKey=:key ORDER BY id")
    suspend fun identities(scope: String, key: String): List<SyncRecordIndex>

    @Query("SELECT * FROM sync_record_index WHERE scope=:scope AND nativeKey=:key AND id>:after ORDER BY id LIMIT :limit")
    suspend fun identityPage(scope: String, key: String, after: String, limit: Int): List<SyncRecordIndex>

    @Query("SELECT * FROM sync_record_index WHERE scope=:scope AND nativeKey=:key AND dayStart=:day AND id>:after ORDER BY id LIMIT :limit")
    suspend fun dayIdentityPage(scope: String, key: String, day: Long, after: String, limit: Int): List<SyncRecordIndex>

    @Query("SELECT * FROM sync_record_index WHERE scope=:scope AND nativeKey=:key AND dayStart=:day ORDER BY windowStart,windowEnd")
    suspend fun windows(scope: String, key: String, day: Long): List<SyncRecordIndex>
}

internal object SyncJournalSchema {
    fun create(db: SupportSQLiteDatabase, history: Boolean) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_local_state (id INTEGER NOT NULL PRIMARY KEY, epoch TEXT NOT NULL, revision INTEGER NOT NULL, localRevision INTEGER NOT NULL, remote INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_local_changes (kind TEXT NOT NULL, `key` TEXT NOT NULL, revision INTEGER NOT NULL, localRevision INTEGER NOT NULL, PRIMARY KEY(kind,`key`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_local_changes_localRevision ON sync_local_changes(localRevision)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_record_index (scope TEXT NOT NULL, id TEXT NOT NULL, nativeKey TEXT NOT NULL, dayStart INTEGER NOT NULL, windowStart INTEGER NOT NULL, windowEnd INTEGER NOT NULL, PRIMARY KEY(scope,id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_record_index_scope_nativeKey_dayStart ON sync_record_index(scope,nativeKey,dayStart)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_record_index_scope_nativeKey_id ON sync_record_index(scope,nativeKey,id)")
        db.execSQL("INSERT OR IGNORE INTO sync_local_state VALUES(1,?,0,0,0)", arrayOf(UUID.randomUUID().toString()))
        if (history) historyTriggers(db) else bookmarkTriggers(db)
    }

    fun callback(history: Boolean) = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try { create(db, history); db.setTransactionSuccessful() } finally { db.endTransaction() }
        }
    }

    private fun dirty(kind: String, key: String) = """
        UPDATE sync_local_state SET revision=revision+1,
          localRevision=CASE WHEN remote=0 THEN revision+1 ELSE localRevision END WHERE id=1;
        INSERT INTO sync_local_changes(kind,`key`,revision,localRevision)
          SELECT '$kind',$key,revision,CASE WHEN remote=0 THEN revision ELSE 0 END FROM sync_local_state
          WHERE id=1 AND NOT EXISTS(SELECT 1 FROM sync_local_changes WHERE kind='$kind' AND `key`=$key);
        UPDATE sync_local_changes SET revision=(SELECT revision FROM sync_local_state WHERE id=1),
          localRevision=CASE WHEN (SELECT remote FROM sync_local_state WHERE id=1)=0
          THEN (SELECT revision FROM sync_local_state WHERE id=1) ELSE localRevision END
          WHERE kind='$kind' AND `key`=$key;
    """.trimIndent()

    private fun trigger(db: SupportSQLiteDatabase, name: String, event: String, body: String, condition: String = "") {
        // An outer UPDATE OR ABORT overrides a trigger's INSERT OR IGNORE policy.
        // The body avoids inserting an existing key; refresh our own definitions
        // atomically when opening databases created by an earlier debug build.
        db.execSQL("DROP TRIGGER IF EXISTS $name")
        db.execSQL("CREATE TRIGGER $name AFTER $event $condition BEGIN $body END")
    }

    private fun dayKey(row: String) = "$row.pageUri || char(31) || CAST(($row.timestampUsec / 86400000000) * 86400000000 AS TEXT)"

    private fun historyTriggers(db: SupportSQLiteDatabase) {
        trigger(db, "sync_visit_insert", "INSERT ON visits", dirty("day", dayKey("NEW")))
        trigger(db, "sync_visit_delete", "DELETE ON visits", dirty("day", dayKey("OLD")))
        trigger(db, "sync_visit_update", "UPDATE ON visits", dirty("day", dayKey("OLD")) + dirty("day", dayKey("NEW")),
            "WHEN OLD.pageUri IS NOT NEW.pageUri OR OLD.timestampUsec IS NOT NEW.timestampUsec OR OLD.type IS NOT NEW.type OR OLD.count IS NOT NEW.count")
        trigger(db, "sync_page_title", "UPDATE ON pages", dirty("url", "NEW.uri"),
            "WHEN OLD.title IS NOT NEW.title AND EXISTS(SELECT 1 FROM visits WHERE pageUri=NEW.uri)")
        trigger(db, "sync_page_delete", "DELETE ON pages", dirty("url", "OLD.uri"))
        trigger(db, "sync_history_removal", "INSERT ON sync_history_removals", dirty("removal", "CAST(NEW.id AS TEXT)"))
    }

    private fun bookmarkTriggers(db: SupportSQLiteDatabase) {
        trigger(db, "sync_bookmark_insert", "INSERT ON bookmark_node", dirty("node", "NEW.guid"))
        trigger(db, "sync_bookmark_delete", "DELETE ON bookmark_node", dirty("node", "OLD.guid"))
        trigger(db, "sync_bookmark_update", "UPDATE ON bookmark_node", dirty("node", "NEW.guid"),
            "WHEN OLD.type IS NOT NEW.type OR OLD.parentGuid IS NOT NEW.parentGuid OR OLD.position IS NOT NEW.position OR OLD.title IS NOT NEW.title OR OLD.url IS NOT NEW.url OR OLD.dateAdded IS NOT NEW.dateAdded")
        trigger(db, "sync_bookmark_insert_order", "INSERT ON bookmark_node", dirty("folder", "COALESCE(NEW.parentGuid,'bookmarks_root')"), "WHEN NEW.position IS NULL")
        trigger(db, "sync_bookmark_delete_order", "DELETE ON bookmark_node", dirty("folder", "COALESCE(OLD.parentGuid,'bookmarks_root')"), "WHEN OLD.position IS NULL")
        trigger(db, "sync_bookmark_old_order", "UPDATE ON bookmark_node", dirty("folder", "COALESCE(OLD.parentGuid,'bookmarks_root')"),
            "WHEN OLD.position IS NULL AND (OLD.parentGuid IS NOT NEW.parentGuid OR OLD.position IS NOT NEW.position OR OLD.dateAdded IS NOT NEW.dateAdded)")
        trigger(db, "sync_bookmark_new_order", "UPDATE ON bookmark_node", dirty("folder", "COALESCE(NEW.parentGuid,'bookmarks_root')"),
            "WHEN NEW.position IS NULL AND (OLD.parentGuid IS NOT NEW.parentGuid OR OLD.position IS NOT NEW.position OR OLD.dateAdded IS NOT NEW.dateAdded)")
    }
}

internal suspend fun <T> RoomDatabase.remoteSyncWrite(journal: SyncJournalDao, block: suspend () -> T): T = withTransaction {
    journal.remote(1)
    try { block() } finally { journal.remote(0) }
}
