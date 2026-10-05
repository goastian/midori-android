package org.midorinext.android.pwa

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_web_apps")
data class InstalledWebApp(
    @PrimaryKey val startUrl: String,
    val name: String,
    val scope: String,
    val displayMode: String,
    val installedAt: Long,
    val enabled: Boolean = true,
)

@Dao
interface InstalledWebAppDao {
    @Query("SELECT * FROM installed_web_apps ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<InstalledWebApp>>

    @Query("SELECT * FROM installed_web_apps WHERE startUrl = :startUrl LIMIT 1")
    suspend fun get(startUrl: String): InstalledWebApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(app: InstalledWebApp)

    @Query("UPDATE installed_web_apps SET enabled = :enabled WHERE startUrl = :startUrl")
    suspend fun setEnabled(startUrl: String, enabled: Boolean)

    @Query("DELETE FROM installed_web_apps WHERE startUrl = :startUrl")
    suspend fun delete(startUrl: String)
}

@Database(entities = [InstalledWebApp::class], version = 1, exportSchema = false)
abstract class InstalledWebAppDatabase : RoomDatabase() {
    abstract fun apps(): InstalledWebAppDao
}
