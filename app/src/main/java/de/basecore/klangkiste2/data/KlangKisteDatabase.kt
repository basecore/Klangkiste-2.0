package de.basecore.klangkiste2.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val ageRecommendation: Int? = null,
    val genre: String = "Hörspiel",
    val runtimeMinutes: Int? = null,
    val tagsCsv: String = "",
    val coverUri: String? = null,
    val coverFileName: String? = null,
    val expectedAudioNamesJson: String = "[]",
    val cueJson: String? = null,
    val remoteAudioUrl: String? = null,
    val hiddenInLibrary: Boolean = false,
    val completed: Boolean = false,
    val lastTrackIndex: Int = 0,
    val lastPositionMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val legacyTagId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audio_parts",
    indices = [Index(value = ["trackId", "position"], unique = true), Index(value = ["trackId"])],
    foreignKeys = [ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)]
)
data class AudioPartEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val position: Int,
    val uriString: String,
    val displayName: String,
    val managedCopy: Boolean = false
)

@Entity(tableName = "nfc_tags", foreignKeys = [ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)])
data class NfcTagEntity(@PrimaryKey val uid: String, val trackId: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "playback_events", indices = [Index("timestamp"), Index("trackId"), Index(value = ["legacyId"], unique = true)])
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val type: String,
    val trackId: String? = null,
    val durationSec: Double = 0.0,
    val details: String = "",
    val legacyId: Long? = null
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val startMode: String = "parent",
    val maxVolume: Float = 1f,
    val sleepTimerMinutes: Double = 0.0,
    val colorfulControls: Boolean = true,
    val libraryEnabled: Boolean = true,
    val maxAge: Int = 99,
    val backgroundMode: String = "default",
    val backgroundColor: String = "#FFFFFF",
    val backgroundUri: String? = null,
    val keepScreenOn: Boolean = true,
    val ecoMode: Boolean = false,
    val adminListView: Boolean = true,
    val storageMode: String = "reference",
    val ttsEnabled: Boolean = false,
    val lastTrackId: String? = null
)

data class TrackWithAudio(
    @Embedded val track: TrackEntity,
    @Relation(parentColumn = "id", entityColumn = "trackId") val audioParts: List<AudioPartEntity>
)

@Dao interface TrackDao {
    @Transaction @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC") fun observeAll(): Flow<List<TrackWithAudio>>
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC") suspend fun getAll(): List<TrackEntity>
    @Query("SELECT * FROM tracks WHERE id=:id LIMIT 1") suspend fun get(id: String): TrackEntity?
    @Upsert suspend fun upsert(track: TrackEntity)
    @Query("UPDATE tracks SET hiddenInLibrary=:hidden") suspend fun setAllHidden(hidden: Boolean)
    @Query("DELETE FROM tracks WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM tracks") suspend fun deleteAll()
}

@Dao interface AudioPartDao {
    @Query("SELECT * FROM audio_parts WHERE trackId=:trackId ORDER BY position ASC") suspend fun getForTrack(trackId: String): List<AudioPartEntity>
    @Upsert suspend fun upsertAll(parts: List<AudioPartEntity>)
    @Query("DELETE FROM audio_parts WHERE trackId=:trackId") suspend fun deleteForTrack(trackId: String)
}

@Dao interface NfcTagDao {
    @Query("SELECT * FROM nfc_tags WHERE uid=:uid LIMIT 1") suspend fun get(uid: String): NfcTagEntity?
    @Upsert suspend fun upsert(tag: NfcTagEntity)
    @Query("DELETE FROM nfc_tags") suspend fun clear()
}

@Dao interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id=1 LIMIT 1") fun observe(): Flow<AppSettingsEntity?>
    @Query("SELECT * FROM app_settings WHERE id=1 LIMIT 1") suspend fun get(): AppSettingsEntity?
    @Upsert suspend fun upsert(settings: AppSettingsEntity)
}

@Dao interface StatsDao {
    @Query("SELECT * FROM playback_events ORDER BY timestamp DESC") fun observeAll(): Flow<List<PlaybackEventEntity>>
    @Query("SELECT * FROM playback_events ORDER BY timestamp DESC") suspend fun getAll(): List<PlaybackEventEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(event: PlaybackEventEntity)
    @Query("DELETE FROM playback_events") suspend fun clear()
}

@Database(entities = [TrackEntity::class, AudioPartEntity::class, NfcTagEntity::class, PlaybackEventEntity::class, AppSettingsEntity::class], version = 1, exportSchema = false)
abstract class KlangKisteDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun audioPartDao(): AudioPartDao
    abstract fun nfcTagDao(): NfcTagDao
    abstract fun settingsDao(): SettingsDao
    abstract fun statsDao(): StatsDao
    companion object {
        @Volatile private var instance: KlangKisteDatabase? = null
        fun get(context: Context): KlangKisteDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, KlangKisteDatabase::class.java, "klangkiste-2.db").build().also { instance = it }
        }
    }
}
