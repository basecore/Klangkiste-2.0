package de.basecore.klangkiste2.ui

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.core.ImportFilter
import de.basecore.klangkiste2.data.ImportCandidate
import de.basecore.klangkiste2.data.TrackEntity
import de.basecore.klangkiste2.data.TrackWithAudio

@Composable
fun AdminScreen(vm:KlangKisteViewModel,tracks:List<TrackWithAudio>,onPinApp:()->Unit){
 val candidates by vm.candidates.collectAsState();val settings by vm.settings.collectAsState();val context=LocalContext.current
 val smart=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let{uri->vm.scanFolder(uri,true)}}
 val flat=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let{uri->vm.scanFolder(uri,false)}}
 val repair=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let{uri->vm.relinkFolder(uri,settings.storageMode=="copy")}}
 val loose=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty())vm.scanSelectedFiles(uris)}
 val openJson=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importBackup)}
 val saveJson=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportBackup)}
 var editing by remember{mutableStateOf<TrackWithAudio?>(null)};var confirmClear by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("Elternbereich · Verwaltung",fontSize=22.sp);Text("${tracks.size} Einträge · Speicherung lokal auf diesem Gerät.")
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={smart.launch(null)}){Text("Ordner-Struktur")};Button(onClick={flat.launch(null)}){Text("Massenimport")};Button(onClick={loose.launch(arrayOf("audio/*","image/*","application/json","text/plain"))}){Text("Dateien wählen")}}
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={openJson.launch(arrayOf("application/json","text/plain","*/*"))}){Text("Backup laden")};Button(onClick={saveJson.launch("klangkiste_full_backup.json")}){Text("Backup exportieren")}}
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={repair.launch(null)}){Text("Dateien verknüpfen")};Button(onClick=vm::installOhrkaCatalog){Text("OHRKA")};Button(onClick=vm::loadOnlineExamples){Text("Online-Beispiele")}}
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={vm.setAllHidden(true)}){Text("Alle verstecken")};Button(onClick={vm.setAllHidden(false)}){Text("Alle anzeigen")};Button(onClick=onPinApp){Text("App-Pinning")};TextButton(onClick={confirmClear=true}){Text("Leeren")}}
  Text("Gespeicherte Inhalte",fontSize=18.sp)
  LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)){items(tracks,key={it.track.id}){item->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(item.track.title);Text("${item.audioParts.size} Datei(en) · ${item.track.genre}",fontSize=12.sp)};TextButton(onClick={editing=item}){Text("Bearbeiten")};TextButton(onClick={vm.beginNfcPairing(item.track.id)}){Text("Tag")};TextButton(onClick={vm.toggleHidden(item.track)}){Text(if(item.track.hiddenInLibrary)"Einblenden" else "Verstecken")};TextButton(onClick={vm.deleteTrack(item.track)}){Text("Löschen")};if(item.audioParts.isEmpty()&&!item.track.remoteAudioUrl.isNullOrBlank())TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(item.track.remoteAudioUrl)))}){Text("Download")}}}}}
 }
 if(candidates.isNotEmpty())ImportPreviewDialog(candidates,settings.storageMode=="copy",vm::clearCandidates,vm::toggleCandidate){items,copy->vm.importCandidates(items,copy)}
 editing?.let{item->TrackEditDialog(item.track,{editing=null}){vm.saveEditedTrack(it);editing=null}}
 if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},title={Text("Datenbank leeren?")},text={Text("Tracks, NFC-Zuordnungen und Verlauf werden gelöscht.")},confirmButton={TextButton(onClick={vm.clearAll();confirmClear=false}){Text("Leeren")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Abbrechen")}})
}

@Composable private fun ImportPreviewDialog(candidates:List<ImportCandidate>,defaultCopy:Boolean,onDismiss:()->Unit,onToggle:(String)->Unit,onImport:(List<ImportCandidate>,Boolean)->Unit){
 var query by remember{mutableStateOf("")};var minText by remember{mutableStateOf("")};var maxText by remember{mutableStateOf("")};var copy by remember(candidates){mutableStateOf(defaultCopy)}
 val visible=candidates.filter{ImportFilter.matches("${it.title} ${it.description} ${it.ageRecommendation?:""}",it.ageRecommendation,query,minText.toIntOrNull(),maxText.toIntOrNull())}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Import-Auswahl · ${candidates.size}")},text={Column(Modifier.fillMaxWidth().heightIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
  OutlinedTextField(query,{query=it},label={Text("Suche; -begriff schließt aus")},singleLine=true)
  Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){OutlinedTextField(minText,{minText=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Ab J.")},singleLine=true);OutlinedTextField(maxText,{maxText=it.filter(Char::isDigit)},Modifier.weight(1f),label={Text("Bis J.")},singleLine=true)}
  Row{TextButton(onClick={visible.filter{!it.selected}.forEach{onToggle(it.id)}}){Text("Alle sichtbaren")};TextButton(onClick={visible.filter{it.selected}.forEach{onToggle(it.id)}}){Text("Sichtbare abwählen")}}
  LazyColumn(Modifier.weight(1f)){items(candidates,key={it.id}){item->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(item.selected,{onToggle(item.id)});Column{Text(item.title);Text("${item.audioFiles.size.takeIf{it>0}?:item.expectedAudioNames.size} Datei(en) · ${item.ageRecommendation?.let{"Ab $it J."}?:"Alter unbekannt"}",fontSize=12.sp)}}}}
  Row(verticalAlignment=Alignment.CenterVertically){Switch(copy,{copy=it});Text(if(copy)"Dateien in KlangKiste kopieren" else "SAF-Dateien referenzieren")}
 }},confirmButton={TextButton(onClick={onImport(candidates.filter{it.selected},copy)}){Text("Importieren")}},dismissButton={TextButton(onClick=onDismiss){Text("Abbrechen")}})
}

@Composable private fun TrackEditDialog(track:TrackEntity,onDismiss:()->Unit,onSave:(TrackEntity)->Unit){
 var title by remember(track.id){mutableStateOf(track.title)};var description by remember(track.id){mutableStateOf(track.description)};var age by remember(track.id){mutableStateOf(track.ageRecommendation?.toString()?:"")};var genre by remember(track.id){mutableStateOf(track.genre)}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Eintrag bearbeiten")},text={Column(verticalArrangement=Arrangement.spacedBy(5.dp)){OutlinedTextField(title,{title=it},label={Text("Titel")});OutlinedTextField(description,{description=it},label={Text("Beschreibung")});OutlinedTextField(age,{age=it.filter(Char::isDigit)},label={Text("Alter")});OutlinedTextField(genre,{genre=it},label={Text("Genre")})}},confirmButton={TextButton(onClick={onSave(track.copy(title=title,description=description,ageRecommendation=age.toIntOrNull(),genre=genre))}){Text("Speichern")}},dismissButton={TextButton(onClick=onDismiss){Text("Abbrechen")}})
}
