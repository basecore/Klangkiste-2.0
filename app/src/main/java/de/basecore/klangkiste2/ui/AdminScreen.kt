package de.basecore.klangkiste2.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.core.ImportFilter
import de.basecore.klangkiste2.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(vm:KlangKisteViewModel,tracks:List<TrackWithAudio>,onPinApp:()->Unit){
 val candidates by vm.candidates.collectAsState()
 val settings by vm.settings.collectAsState()
 val downloads:AudioDownloadViewModel=viewModel()
 val busy by downloads.busy.collectAsStateWithLifecycle()
 val smart=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let{uri->vm.scanFolder(uri,true)}}
 val flat=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let{uri->vm.scanFolder(uri,false)}}
 val repair=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let{uri->vm.relinkFolder(uri,settings.storageMode=="copy")}}
 val loose=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty())vm.scanSelectedFiles(uris)}
 val openJson=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importBackup)}
 val saveJson=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportBackup)}
 var editing by remember{mutableStateOf<TrackWithAudio?>(null)}
 var confirmClear by remember{mutableStateOf(false)}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  item{Text("Elternbereich · Verwaltung",fontSize=22.sp);Text("${tracks.size} Einträge · Speicherung lokal auf diesem Gerät.")}
  item{
   Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text("Inhalte einfügen",fontSize=18.sp)
    OutlinedButton(onClick={smart.launch(null)}){Text("Kapitel je Unterordner bündeln")}
    Text("Beispiel: Grüffelo/01.mp3 und Grüffelo/02.mp3 ergeben zusammen „Grüffelo“. Dateien direkt im gewählten Ordner bleiben einzelne Titel.")
    OutlinedButton(onClick={flat.launch(null)}){Text("Dateien einzeln importieren")}
    Text("Beispiel: Grüffelo.mp3 und Pettersson.mp3 ergeben zwei Titel, auch im selben Ordner. Dateien mit gleichem Grundnamen im selben Ordner werden zusammengefasst.")
    Text("Beide Varianten durchsuchen auch Unterordner.",style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={loose.launch(arrayOf("audio/*","image/*","application/json","text/plain"))}){Text("Einzelne Dateien auswählen")}
   }
  }
  item{
   FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
    Button(onClick={openJson.launch(arrayOf("application/json","text/plain","*/*"))}){Text("Backup laden")}
    Button(onClick={saveJson.launch("klangkiste_full_backup.json")}){Text("Backup exportieren")}
    Button(onClick={repair.launch(null)}){Text("Dateien verknüpfen")}
    Button(onClick=vm::installOhrkaCatalog){Text("OHRKA")}
    Button(onClick=vm::loadOnlineExamples){Text("Online-Beispiele")}
    Button(onClick={vm.setAllHidden(true)}){Text("Alle verstecken")}
    Button(onClick={vm.setAllHidden(false)}){Text("Alle anzeigen")}
    Button(onClick=onPinApp){Text("App-Pinning")}
    TextButton(onClick={confirmClear=true}){Text("Leeren")}
   }
  }
  item{Text("Gespeicherte Inhalte",fontSize=18.sp)}
  items(tracks,key={it.track.id}){item->
   val track=item.track
   val downloading=track.id in busy
   Card(Modifier.fillMaxWidth()){
    Column(Modifier.padding(10.dp)){
     Text(track.title)
     Text("${item.audioParts.size} Datei(en) · ${track.genre}",fontSize=12.sp)
     if(item.audioParts.isEmpty()&&!track.remoteAudioUrl.isNullOrBlank()){
      Button(onClick={downloads.start(track.id,vm::showMessage)},enabled=!downloading){
       Text(if(downloading)"Audio wird heruntergeladen …" else "Audio herunterladen")
      }
     }
     FlowRow{
      TextButton(onClick={editing=item}){Text("Bearbeiten")}
      TextButton(onClick={vm.beginNfcPairing(track.id)}){Text("Tag")}
      TextButton(onClick={vm.toggleHidden(track)}){Text(if(track.hiddenInLibrary)"Einblenden" else "Verstecken")}
      TextButton(onClick={vm.deleteTrack(track)},enabled=!downloading){Text("Löschen")}
     }
    }
   }
  }
 }
 if(candidates.isNotEmpty())ImportPreviewDialog(candidates,settings.storageMode=="copy",vm::clearCandidates,vm::toggleCandidate){items,copy->vm.importCandidates(items,copy)}
 editing?.let{item->TrackEditDialog(item.track,{editing=null}){vm.saveEditedTrack(it);editing=null}}
 if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},title={Text("Datenbank leeren?")},text={Text("Tracks, NFC-Zuordnungen und Verlauf werden gelöscht.")},confirmButton={TextButton(onClick={vm.clearAll();confirmClear=false}){Text("Leeren")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Abbrechen")}})
}

internal class AudioDownloadViewModel(private val app:Application):AndroidViewModel(app){
 private val db=KlangKisteDatabase.get(app)
 private val _busy=MutableStateFlow<Set<String>>(emptySet())
 val busy=_busy.asStateFlow()

 fun start(id:String,message:(String)->Unit){viewModelScope.launch{
  if(id in _busy.value)return@launch
  _busy.value=_busy.value+id
  var target:File?=null
  var linked=false
  try{
   val track=db.trackDao().get(id)?:error("Eintrag nicht gefunden")
   if(db.audioPartDao().getForTrack(id).isNotEmpty())return@launch
   val url=track.remoteAudioUrl?.takeIf{it.startsWith("https://")}?:error("Kein HTTPS-Audiolink vorhanden")
   val expected=runCatching{JSONArray(track.expectedAudioNamesJson).optString(0)}.getOrDefault("")
   val name=expected.ifBlank{Uri.parse(url).lastPathSegment.orEmpty().ifBlank{"audio.mp3"}}
   val suffix="_"+name.replace(Regex("[^A-Za-z0-9._-]"),"_").takeLast(90)
   withContext(Dispatchers.IO){
    val dir=File(app.filesDir,"klangkiste/audio").apply{mkdirs()}
    val file=File.createTempFile("download_",suffix,dir)
    target=file
    val conn=URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout=15000
    conn.readTimeout=30000
    try{
     val status=conn.responseCode
     if(status !in 200..299||conn.url.protocol!="https")error("Unerwartete HTTP-Antwort: $status")
     val type=conn.contentType.orEmpty().substringBefore(';').trim().lowercase()
     if(type.startsWith("text/")||type=="application/json"||type=="application/xhtml+xml")error("Der Link liefert keine Audiodatei")
     conn.inputStream.use{input->file.outputStream().use{output->
      val buffer=ByteArray(64*1024)
      while(true){
       ensureActive()
       val n=input.read(buffer)
       if(n<0)break
       output.write(buffer,0,n)
      }
     }}
     val size=conn.getHeaderField("Content-Length")?.toLongOrNull()
     if(file.length()==0L||(size!=null&&size!=file.length()))error("Download unvollständig")
    }finally{conn.disconnect()}
   }
   val part=AudioPartEntity(trackId=id,position=0,uriString=Uri.fromFile(checkNotNull(target)).toString(),displayName=name,managedCopy=true)
   ensureActive()
   withContext(NonCancellable){
    linked=db.withTransaction{
     if(db.trackDao().get(id)?.remoteAudioUrl!=url||db.audioPartDao().getForTrack(id).isNotEmpty())false
     else{db.audioPartDao().upsertAll(listOf(part));true}
    }
   }
   message(if(linked)"„${track.title}“ ist jetzt offline verfügbar." else "Eintrag wurde geändert; Audio nicht hinzugefügt.")
  }catch(e:CancellationException){throw e}
  catch(e:Exception){message("Audio-Download fehlgeschlagen: ${e.localizedMessage?:"Unbekannter Fehler"}")}
  finally{
   if(!linked)withContext(NonCancellable+Dispatchers.IO){target?.delete()}
   _busy.value=_busy.value-id
  }
 }}
}

@Composable private fun ImportPreviewDialog(candidates:List<ImportCandidate>,defaultCopy:Boolean,onDismiss:()->Unit,onToggle:(String)->Unit,onImport:(List<ImportCandidate>,Boolean)->Unit){
 var query by remember{mutableStateOf("")};var minText by remember{mutableStateOf("")};var maxText by remember{mutableStateOf("")};var copy by remember(candidates){mutableStateOf(defaultCopy)}
 val visible=candidates.filter{ImportFilter.matches("${it.title} ${it.description} ${it.ageRecommendation?:""}",it.ageRecommendation,query,minText.toIntOrNull(),maxText.toIntOrNull())}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Import-Auswahl · ${candidates.size}")},text={Column(Modifier.fillMaxWidth().heightIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
  OutlinedTextField(query,{query=it},label={Text("Suche; -begriff schließt aus")},singleLine=true)
  Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){OutlinedTextField(minText,{minText=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Ab J.")},singleLine=true);OutlinedTextField(maxText,{maxText=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Bis J.")},singleLine=true)}
  Row{TextButton(onClick={visible.filter{!it.selected}.forEach{onToggle(it.id)}}){Text("Alle sichtbaren")};TextButton(onClick={visible.filter{it.selected}.forEach{onToggle(it.id)}}){Text("Sichtbare abwählen")}}
  LazyColumn(Modifier.weight(1f)){items(visible,key={it.id}){item->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(item.selected,{onToggle(item.id)});Column{Text(item.title);Text("${item.audioFiles.size.takeIf{it>0}?:item.expectedAudioNames.size} Datei(en) · ${item.ageRecommendation?.let{"Ab $it J."}?:"Alter unbekannt"}",fontSize=12.sp)}}}}
  Row(verticalAlignment=Alignment.CenterVertically){Switch(copy,{copy=it});Text(if(copy)"Dateien in KlangKiste kopieren" else "SAF-Dateien referenzieren")}
 }},confirmButton={TextButton(onClick={onImport(candidates.filter{it.selected},copy)}){Text("Importieren")}},dismissButton={TextButton(onClick=onDismiss){Text("Abbrechen")}})
}

@Composable private fun TrackEditDialog(track:TrackEntity,onDismiss:()->Unit,onSave:(TrackEntity)->Unit){
 var title by remember(track.id){mutableStateOf(track.title)};var description by remember(track.id){mutableStateOf(track.description)};var age by remember(track.id){mutableStateOf(track.ageRecommendation?.toString()?:"")};var genre by remember(track.id){mutableStateOf(track.genre)}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Eintrag bearbeiten")},text={Column(verticalArrangement=Arrangement.spacedBy(5.dp)){OutlinedTextField(title,{title=it},label={Text("Titel")});OutlinedTextField(description,{description=it},label={Text("Beschreibung")});OutlinedTextField(age,{age=it.filter(Char::isDigit)},label={Text("Alter")});OutlinedTextField(genre,{genre=it},label={Text("Genre")})}},confirmButton={TextButton(onClick={onSave(track.copy(title=title,description=description,ageRecommendation=age.toIntOrNull(),genre=genre))}){Text("Speichern")}},dismissButton={TextButton(onClick=onDismiss){Text("Abbrechen")}})
}
