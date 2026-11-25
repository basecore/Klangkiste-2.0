package de.basecore.klangkiste2

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import de.basecore.klangkiste2.core.CueParser
import de.basecore.klangkiste2.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

class ImportCoordinator(context:Context,private val db:KlangKisteDatabase,private val scope:CoroutineScope,private val message:(String)->Unit){
 private val app=context.applicationContext
 private val _candidates=MutableStateFlow<List<ImportCandidate>>(emptyList())
 val candidates=_candidates.asStateFlow()
 private val audioExt=setOf("mp3","m4a","ogg","wav","flac","aac")
 private val imageExt=setOf("jpg","jpeg","png","webp")

 fun clearCandidates(){_candidates.value=emptyList()}
 fun toggleCandidate(id:String){_candidates.value=_candidates.value.map{if(it.id==id)it.copy(selected=!it.selected)else it}}

 fun scanFolder(treeUri:Uri,smart:Boolean){scope.launch{
  message("Ordner wird eingelesen …")
  runCatching{app.contentResolver.takePersistableUriPermission(treeUri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
  val docs=withContext(Dispatchers.IO){walkTree(treeUri)}
  val audio=docs.filter{it.name.substringAfterLast('.',"").lowercase() in audioExt}
  if(audio.isEmpty()){message("Keine unterstützten Audiodateien gefunden.");return@launch}
  val metadataDoc=docs.firstOrNull{it.name.equals("klangkiste.json",true)||it.name.lowercase().startsWith("klangkiste")&&it.name.endsWith(".json",true)}
  val metadata=metadataDoc?.let{runCatching{PwaBackupCodec.parse(readText(Uri.parse(it.uriString)))}.getOrNull()}
  val groups=audio.groupBy{f->val parent=f.relativePath.substringBeforeLast('/',"");if(smart)parent.ifBlank{"root_${f.baseName()}"}else parent+"/"+f.baseName()}
  _candidates.value=groups.map{(key,group)->
   val ordered=group.sortedBy{it.name.lowercase()}
   val parent=ordered.first().relativePath.substringBeforeLast('/',"")
   val title=if(smart&&parent.isNotBlank())parent.substringAfterLast('/') else ordered.first().baseName().replace('_',' ')
   val cover=docs.firstOrNull{it.relativePath.substringBeforeLast('/',"")==parent&&it.name.substringAfterLast('.',"").lowercase() in imageExt}
   val cue=docs.firstOrNull{it.relativePath.substringBeforeLast('/',"")==parent&&it.name.substringAfterLast('.',"").equals("cue",true)}
   val old=metadata?.tracks?.firstOrNull{it.name.equals(title,true)}
   ImportCandidate(title=title,audioFiles=ordered,cover=cover,cue=cue,description=old?.description?:"",ageRecommendation=old?.age,genre=old?.genre?:"Hörspiel",runtimeMinutes=old?.runtime,tags=old?.tags?:emptyList(),expectedAudioNames=ordered.map{it.name},coverFileName=cover?.name,legacyTagId=old?.tagId)
  }
  message("${_candidates.value.size} Importkandidaten gefunden.")
 }}

 fun scanSelectedFiles(uris:List<Uri>){scope.launch{
  val docs=withContext(Dispatchers.IO){uris.mapNotNull{uri->runCatching{app.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};val name=displayName(uri)?:return@mapNotNull null;PickedDocument(uri.toString(),name,name)}}
  val audio=docs.filter{it.name.substringAfterLast('.',"").lowercase() in audioExt}
  if(audio.isEmpty()){message("Keine Audiodatei ausgewählt.");return@launch}
  val metadataFile=docs.firstOrNull{it.name.startsWith("klangkiste",true)&&it.name.endsWith(".json",true)}
  val metadata=metadataFile?.let{runCatching{PwaBackupCodec.parse(readText(Uri.parse(it.uriString)))}.getOrNull()}
  _candidates.value=audio.groupBy{it.baseName().lowercase()}.map{(_,group)->
   val first=group.first();val base=first.baseName();val cover=docs.firstOrNull{it.name.substringBeforeLast('.',"").equals(base,true)&&it.name.substringAfterLast('.',"").lowercase() in imageExt}
   val cue=docs.firstOrNull{it.name.substringBeforeLast('.',"").equals(base,true)&&it.name.endsWith(".cue",true)}
   val old=metadata?.tracks?.firstOrNull{it.name.equals(base,true)}
   ImportCandidate(title=old?.name?:base.replace('_',' '),audioFiles=group,cover=cover,cue=cue,description=old?.description?:"",ageRecommendation=old?.age,genre=old?.genre?:"Hörspiel",runtimeMinutes=old?.runtime,tags=old?.tags?:emptyList(),expectedAudioNames=group.map{it.name},coverFileName=cover?.name,legacyTagId=old?.tagId)
  }
  message("${_candidates.value.size} Audioeinträge zur Vorschau bereit.")
 }}

 fun importCandidates(selected:List<ImportCandidate>,copyToManaged:Boolean){scope.launch{
  if(selected.isEmpty()){message("Nichts ausgewählt.");return@launch}
  var count=0
  selected.forEachIndexed{index,c->
   message("Importiere ${index+1}/${selected.size}: ${c.title}")
   val id=c.legacyTagId?.takeIf{it.isNotBlank()}?:UUID.randomUUID().toString()
   val parts=c.audioFiles.mapIndexed{i,file->AudioPartEntity(trackId=id,position=i,uriString=storePicked(file,copyToManaged,"audio"),displayName=file.name,managedCopy=copyToManaged)}
   var coverUri=c.cover?.let{storePicked(it,copyToManaged,"covers")}
   if(coverUri==null&&c.remoteCoverUrl?.startsWith("https://")==true)coverUri=runCatching{downloadToManaged(c.remoteCoverUrl,c.coverFileName?:"cover.jpg","covers")}.getOrNull()
   val cueText=c.cue?.let{runCatching{readText(Uri.parse(it.uriString))}.getOrNull()}?:c.remoteCueUrl?.takeIf{it.startsWith("https://")}?.let{runCatching{fetchText(it)}.getOrNull()}
   val cueJson=cueText?.let{JSONArray(CueParser.parse(it).map{ch->JSONObject().put("title",ch.title).put("time",ch.timeSeconds)}).toString()}
   val expected=c.expectedAudioNames.ifEmpty{c.audioFiles.map{it.name}}
   val track=TrackEntity(id=id,title=c.title,description=c.description,ageRecommendation=c.ageRecommendation,genre=c.genre,runtimeMinutes=c.runtimeMinutes,tagsCsv=c.tags.joinToString("|"),coverUri=coverUri,coverFileName=c.coverFileName?:c.cover?.name,expectedAudioNamesJson=JSONArray(expected).toString(),cueJson=cueJson,remoteAudioUrl=c.remoteAudioUrl,legacyTagId=c.legacyTagId)
   db.withTransaction{db.trackDao().upsert(track);if(parts.isNotEmpty()){db.audioPartDao().deleteForTrack(id);db.audioPartDao().upsertAll(parts)};c.legacyTagId?.takeIf{isRealTagId(it)}?.let{db.nfcTagDao().upsert(NfcTagEntity(it,id))}}
   count++
  }
  _candidates.value=emptyList();message("$count Hörspiele importiert.")
 }}

 fun importBackup(uri:Uri,currentSettings:()->AppSettingsEntity){scope.launch{
  runCatching{
   val backup=PwaBackupCodec.parse(readText(uri))
   db.withTransaction{
    backup.tracks.forEach{e->
     val old=db.trackDao().get(e.tagId)
     db.trackDao().upsert(TrackEntity(id=e.tagId,title=e.name,description=e.description,ageRecommendation=e.age,genre=e.genre,runtimeMinutes=e.runtime,tagsCsv=e.tags.joinToString("|"),coverUri=old?.coverUri,coverFileName=e.imageName,expectedAudioNamesJson=JSONArray(e.audioNames).toString(),cueJson=e.cueJson?:old?.cueJson,remoteAudioUrl=e.remoteAudioUrl,hiddenInLibrary=e.hidden,completed=e.completed,lastTrackIndex=e.lastIndex,lastPositionMs=(e.lastTimeSeconds*1000).toLong(),lastPlayedAt=e.lastPlayed,legacyTagId=e.tagId,createdAt=old?.createdAt?:System.currentTimeMillis()))
     if(isRealTagId(e.tagId))db.nfcTagDao().upsert(NfcTagEntity(normalizeUid(e.tagId),e.tagId))
    }
    backup.stats.forEach{e->db.statsDao().insert(PlaybackEventEntity(timestamp=e.timestamp,type=e.type,trackId=e.tagId,durationSec=e.duration,details=e.details,legacyId=e.legacyId))}
    backup.settings?.let{db.settingsDao().upsert(PwaBackupCodec.settingsFromPwa(it,currentSettings()))}
   }
   message("Backup importiert: ${backup.tracks.size} Einträge, ${backup.stats.size} Statistikereignisse. Fehlende Dateien anschließend verknüpfen.")
  }.onFailure{message("Backup-Importfehler: ${it.message}")}
 }}

 fun exportBackup(uri:Uri,tracks:List<TrackWithAudio>,events:List<PlaybackEventEntity>,settings:AppSettingsEntity){scope.launch{
  runCatching{val text=PwaBackupCodec.export(tracks,events,settings);withContext(Dispatchers.IO){app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(text)}?:error("Ausgabedatei nicht verfügbar")};message("Backup erstellt. Medienbytes sind nicht im JSON enthalten.")}.onFailure{message("Exportfehler: ${it.message}")}
 }}

 fun relinkFolder(treeUri:Uri,copy:Boolean){scope.launch{
  runCatching{app.contentResolver.takePersistableUriPermission(treeUri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
  val docs=withContext(Dispatchers.IO){walkTree(treeUri)};val byName=docs.groupBy{it.name};var fixed=0
  db.trackDao().getAll().forEach{track->
   val names=runCatching{JSONArray(track.expectedAudioNamesJson)}.getOrElse{JSONArray()}
   if(db.audioPartDao().getForTrack(track.id).isEmpty()&&names.length()>0){
    val parts=(0 until names.length()).mapNotNull{i->val name=names.optString(i);val doc=byName[name]?.firstOrNull()?:return@mapNotNull null;AudioPartEntity(trackId=track.id,position=i,uriString=storePicked(doc,copy,"audio"),displayName=doc.name,managedCopy=copy)}
    if(parts.isNotEmpty()){db.audioPartDao().upsertAll(parts);fixed++}
   }
   if(track.coverUri.isNullOrBlank()&&!track.coverFileName.isNullOrBlank()){byName[track.coverFileName]?.firstOrNull()?.let{db.trackDao().upsert(track.copy(coverUri=storePicked(it,copy,"covers")))}}
  }
  message(if(fixed>0)"$fixed Einträge verknüpft." else "Keine fehlenden Dateien anhand der Dateinamen gefunden.")
 }}

 fun installOhrkaCatalog(){scope.launch{
  runCatching{
   val base="https://raw.githubusercontent.com/basecore/klangkiste/main/example/ohrka/"
   val catalog=JSONArray(fetchText(base+"klangkiste_ohrka.json"))
   _candidates.value=(0 until catalog.length()).mapNotNull{i->
    val row=catalog.optJSONObject(i)?:return@mapNotNull null
    val url=row.optString("downloadUrl");if(!url.startsWith("https://"))return@mapNotNull null
    val name=runCatching{URLDecoder.decode(url.substringAfterLast('/'),"UTF-8")}.getOrDefault("audio.mp3")
    val meta=row.optJSONObject("meta")?:JSONObject();val image=row.optString("imageFileName").takeIf{it.isNotBlank()&&it!="null"}
    val cue=row.optJSONArray("playlistFileNames")?.optString(0)?.substringBeforeLast('.',"")?.plus(".cue")
    ImportCandidate(title=row.optString("name","OHRKA-Hörbuch"),expectedAudioNames=listOf(name),description=meta.optString("description",""),ageRecommendation=meta.optInt("age_recommendation",0).takeIf{it>0},genre=meta.optString("genre","Hörbuch"),runtimeMinutes=meta.optInt("runtime",0).takeIf{it>0},tags=listOf("OHRKA"),coverFileName=image,legacyTagId=row.optString("tagId").ifBlank{"ohrka-${UUID.randomUUID()}"},remoteAudioUrl=url,remoteCoverUrl=image?.let{base+it},remoteCueUrl=cue?.let{base+it})
   }
   message("OHRKA-Katalog geladen: ${_candidates.value.size} Einträge. MP3-Dateien folgen separat für Offlinebetrieb.")
  }.onFailure{message("OHRKA nicht erreichbar: ${it.message}")}
 }}

 fun loadOnlineExamples(){scope.launch{
  runCatching{
   val base="https://raw.githubusercontent.com/basecore/klangkiste/main/example/ki_generiert/"
   val backup=PwaBackupCodec.parse(fetchText(base+"klangkiste_ki.json"));var count=0
   backup.tracks.forEachIndexed{idx,e->
    val id=e.tagId.ifBlank{"example-$idx-${UUID.randomUUID()}"}
    val parts=e.audioNames.mapIndexed{pos,name->val encoded=URLEncoder.encode(name,"UTF-8").replace("+","%20");AudioPartEntity(trackId=id,position=pos,uriString=downloadToManaged(base+encoded,name,"audio"),displayName=name,managedCopy=true)}
    val cover=e.imageName?.let{name->runCatching{downloadToManaged(base+URLEncoder.encode(name,"UTF-8").replace("+","%20"),name,"covers")}.getOrNull()}
    db.withTransaction{db.trackDao().upsert(TrackEntity(id=id,title=e.name,description=e.description,ageRecommendation=e.age,genre=e.genre,runtimeMinutes=e.runtime,tagsCsv=e.tags.joinToString("|"),coverUri=cover,coverFileName=e.imageName,expectedAudioNamesJson=JSONArray(e.audioNames).toString(),legacyTagId=e.tagId));if(parts.isNotEmpty()){db.audioPartDao().deleteForTrack(id);db.audioPartDao().upsertAll(parts)}}
    count++
   }
   message("$count Beispiel-Hörspiele lokal installiert.")
  }.onFailure{message("Online-Beispiele nicht geladen: ${it.message}")}
 }}

 private suspend fun walkTree(uri:Uri):List<PickedDocument> = withContext(Dispatchers.IO){
  val root=DocumentFile.fromTreeUri(app,uri)?:return@withContext emptyList();val out=mutableListOf<PickedDocument>()
  fun walk(dir:DocumentFile,parent:String){dir.listFiles().forEach{child->val name=child.name?:return@forEach;val path=if(parent.isBlank())name else "$parent/$name";if(child.isDirectory)walk(child,path)else out+=PickedDocument(child.uri.toString(),name,path)}}
  walk(root,"");out
 }
 private suspend fun storePicked(doc:PickedDocument,copy:Boolean,folder:String):String{
  if(!copy)return doc.uriString
  return withContext(Dispatchers.IO){val source=app.contentResolver.openInputStream(Uri.parse(doc.uriString))?:error("Datei kann nicht geöffnet werden");val target=managedFile(folder,doc.name);source.use{input->target.outputStream().use{output->input.copyTo(output)}};Uri.fromFile(target).toString()}
 }
 private fun managedFile(folder:String,name:String):File{val clean=name.replace(Regex("[^A-Za-z0-9._-]"),"_").take(100).ifBlank{"media.bin"};val dir=File(app.filesDir,"klangkiste/$folder").apply{mkdirs()};return File(dir,"${UUID.randomUUID()}_$clean")}
 private suspend fun downloadToManaged(url:String,name:String,folder:String):String=withContext(Dispatchers.IO){require(url.startsWith("https://"));val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=30000;try{c.connect();if(c.responseCode !in 200..299)error("HTTP ${c.responseCode}");val target=managedFile(folder,name);c.inputStream.use{input->target.outputStream().use{it2->input.copyTo(it2)}};Uri.fromFile(target).toString()}finally{c.disconnect()}}
 private suspend fun fetchText(url:String):String=withContext(Dispatchers.IO){require(url.startsWith("https://"));val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=30000;try{c.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText()}}finally{c.disconnect()}}
 private suspend fun readText(uri:Uri):String=withContext(Dispatchers.IO){app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:error("Datei konnte nicht gelesen werden")}
 private fun PickedDocument.baseName():String=name.substringBeforeLast('.',name)
 private fun isRealTagId(id:String):Boolean=!id.startsWith("manual_")&&!id.startsWith("folder_")&&!id.startsWith("ohrka-")&&!id.startsWith("example-")&&!id.startsWith("system_")
 private fun normalizeUid(id:String)=id.replace(Regex("[:\\-\\s]"),"").uppercase()
 private fun displayName(uri:Uri):String?=app.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else null}
}
