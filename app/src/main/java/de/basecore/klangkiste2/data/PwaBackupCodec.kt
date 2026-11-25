package de.basecore.klangkiste2.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class PwaTrack(
    val tagId:String, val name:String, val description:String, val age:Int?, val genre:String,
    val runtime:Int?, val tags:List<String>, val audioNames:List<String>, val imageName:String?,
    val lastIndex:Int, val lastTimeSeconds:Double, val completed:Boolean, val hidden:Boolean,
    val lastPlayed:Long, val cueJson:String?, val remoteAudioUrl:String?
)
data class PwaStat(val timestamp:Long,val type:String,val tagId:String?,val duration:Double,val details:String,val legacyId:Long?)
data class PwaBackup(val tracks:List<PwaTrack>,val stats:List<PwaStat>,val settings:JSONObject?,val full:Boolean)

object PwaBackupCodec {
    fun parse(text:String):PwaBackup {
        val rootValue=JSONTokener(text).nextValue()
        val root=rootValue as? JSONObject
        val full=root!=null && root.optString("type").startsWith("FULL_BACKUP")
        val entries=if(full) root?.optJSONArray("tracks") ?: JSONArray() else rootValue as? JSONArray ?: error("Unbekanntes KlangKiste-JSON-Format")
        val settings=if(full) root?.optJSONObject("systemSettings") else null
        val hiddenIds=mutableSetOf<String>()
        settings?.optJSONArray("hiddenIDs")?.let { a -> for(i in 0 until a.length()) hiddenIds += a.optString(i) }
        val tracks=(0 until entries.length()).mapNotNull { i ->
            val row=entries.optJSONObject(i) ?: return@mapNotNull null
            val id=row.optString("tagId").ifBlank { "legacy-$i" }
            val meta=row.optJSONObject("meta") ?: JSONObject()
            val names=row.optJSONArray("playlistFileNames") ?: JSONArray()
            val tagsArray=row.optJSONArray("tags")
            val tags=if(tagsArray!=null) (0 until tagsArray.length()).map { tagsArray.optString(it) }.filter { it.isNotBlank() }
                else row.optString("tags").split(',').map { it.trim() }.filter { it.isNotBlank() }
            val age=meta.optInt("age_recommendation",0).takeIf { it>0 }
            PwaTrack(
                tagId=id,
                name=row.optString("name","Unbenannt"),
                description=meta.optString("description",""),
                age=age,
                genre=meta.optString("genre","Hörspiel").ifBlank { "Hörspiel" },
                runtime=meta.optInt("runtime",0).takeIf { it>0 },
                tags=tags,
                audioNames=(0 until names.length()).map { names.optString(it) }.filter { it.isNotBlank() },
                imageName=row.optString("imageFileName").takeIf { it.isNotBlank() && it!="null" },
                lastIndex=row.optInt("lastTrackIndex",0).coerceAtLeast(0),
                lastTimeSeconds=row.optDouble("lastTime",0.0).coerceAtLeast(0.0),
                completed=row.optBoolean("completed",false),
                hidden=row.optBoolean("hiddenInLib",false) || id in hiddenIds,
                lastPlayed=row.optLong("lastPlayedTimestamp",0L),
                cueJson=row.optJSONArray("cueTracks")?.toString(),
                remoteAudioUrl=meta.optString("streamingUrl").takeIf { it.startsWith("https://") }
            )
        }
        val statsArray=if(full) root?.optJSONArray("stats") ?: JSONArray() else JSONArray()
        val stats=(0 until statsArray.length()).mapNotNull { i ->
            val row=statsArray.optJSONObject(i) ?: return@mapNotNull null
            PwaStat(row.optLong("timestamp",System.currentTimeMillis()),row.optString("type","imported"),row.optString("tagId").takeIf { it.isNotBlank() && it!="null" },row.optDouble("durationSec",0.0).coerceAtLeast(0.0),row.optString("details",""),row.optLong("id",-1L).takeIf { it>=0L })
        }
        return PwaBackup(tracks,stats,settings,full)
    }

    fun settingsFromPwa(old:JSONObject,base:AppSettingsEntity=AppSettingsEntity()):AppSettingsEntity=base.copy(
        startMode=old.optString("startMode",base.startMode),
        maxVolume=old.optDouble("maxVolume",base.maxVolume.toDouble()).toFloat().coerceIn(0f,1f),
        sleepTimerMinutes=old.optDouble("sleepTimerMinutes",base.sleepTimerMinutes).coerceAtLeast(0.0),
        colorfulControls=old.optBoolean("colorfulControls",base.colorfulControls),
        libraryEnabled=old.optBoolean("enableLibrary",base.libraryEnabled),
        maxAge=old.optInt("libMaxAge",base.maxAge),
        backgroundMode=old.optString("bgMode",base.backgroundMode),
        backgroundColor=old.optString("bgColor",base.backgroundColor),
        keepScreenOn=old.optBoolean("enableWakeLock",base.keepScreenOn),
        ecoMode=old.optBoolean("enableEcoMode",base.ecoMode)
    )

    fun export(tracks:List<TrackWithAudio>,stats:List<PlaybackEventEntity>,settings:AppSettingsEntity):String {
        val root=JSONObject().put("type","FULL_BACKUP_V1").put("timestamp",System.currentTimeMillis())
        val settingJson=JSONObject().put("startMode",settings.startMode).put("maxVolume",settings.maxVolume.toDouble())
            .put("sleepTimerMinutes",settings.sleepTimerMinutes).put("colorfulControls",settings.colorfulControls)
            .put("enableLibrary",settings.libraryEnabled).put("libMaxAge",settings.maxAge).put("bgMode",settings.backgroundMode)
            .put("bgColor",settings.backgroundColor).put("enableWakeLock",settings.keepScreenOn).put("enableEcoMode",settings.ecoMode)
            .put("hiddenIDs",JSONArray(tracks.filter { it.track.hiddenInLibrary }.map { it.track.legacyTagId ?: it.track.id }))
        root.put("systemSettings",settingJson)
        val trackArray=JSONArray()
        tracks.forEach { item ->
            val t=item.track
            val names=runCatching { JSONArray(t.expectedAudioNamesJson) }.getOrElse { JSONArray() }
            if(names.length()==0) item.audioParts.sortedBy { it.position }.forEach { names.put(it.displayName) }
            val meta=JSONObject().put("description",t.description).put("age_recommendation",t.ageRecommendation ?: 0).put("genre",t.genre).put("runtime",t.runtimeMinutes ?: 0)
            val row=JSONObject().put("tagId",t.legacyTagId ?: t.id).put("name",t.title).put("playlistFileNames",names)
                .put("imageFileName",t.coverFileName ?: JSONObject.NULL).put("meta",meta)
                .put("tags",JSONArray(t.tagsCsv.split('|').map { it.trim() }.filter { it.isNotBlank() }))
                .put("filter_age",t.ageRecommendation ?: 0).put("lastTrackIndex",t.lastTrackIndex)
                .put("lastTime",t.lastPositionMs/1000.0).put("completed",t.completed).put("hiddenInLib",t.hiddenInLibrary)
                .put("lastPlayedTimestamp",t.lastPlayedAt)
            t.cueJson?.let { cue -> row.put("cueTracks",runCatching { JSONArray(cue) }.getOrElse { JSONArray() }) }
            trackArray.put(row)
        }
        root.put("tracks",trackArray)
        val statArray=JSONArray()
        stats.forEach { e -> statArray.put(JSONObject().put("id",e.legacyId ?: e.id).put("timestamp",e.timestamp).put("type",e.type).put("tagId",e.trackId ?: JSONObject.NULL).put("durationSec",e.durationSec).put("details",e.details)) }
        root.put("stats",statArray)
        return root.toString(2)
    }
}
