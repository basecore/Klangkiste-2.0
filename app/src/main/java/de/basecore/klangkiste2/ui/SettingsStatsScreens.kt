package de.basecore.klangkiste2.ui

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
import de.basecore.klangkiste2.data.AppSettingsEntity
import de.basecore.klangkiste2.data.PlaybackEventEntity
import de.basecore.klangkiste2.data.TrackWithAudio
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(vm:KlangKisteViewModel,settings:AppSettingsEntity,onPinApp:()->Unit){
 val context=LocalContext.current;val tts by vm.ttsAvailable.collectAsState()
 val bgPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){runCatching{context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)};vm.updateSettings{it.copy(backgroundMode="image",backgroundUri=uri.toString())}}}
 LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  item{Text("Einstellungen",fontSize=23.sp)}
  item{Text("Maximale Lautstärke: ${(settings.maxVolume*100).toInt()}%")}
  item{Slider(settings.maxVolume,{v->vm.updateSettings{it.copy(maxVolume=v.coerceIn(0f,1f))}})}
  item{Button(onClick=vm::playTestTone){Text("Testton (Lautstärkebegrenzung)")}}
  item{Text("Schlaf-Timer")}
  item{Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(0.0,0.5,10.0).forEach{m->Button(onClick={vm.setSleepTimer(m)}){Text(if(m==0.0)"Aus" else if(m==0.5)"30 Sek." else "${m.toInt()} Min.")}}}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(20.0,30.0,60.0).forEach{m->Button(onClick={vm.setSleepTimer(m)}){Text(if(m==60.0)"1 Std." else "${m.toInt()} Min.")}}}}
  item{SettingSwitch("Beim Start direkt in den Kindermodus",settings.startMode=="kids"){v->vm.setStartInKids(v)}}
  item{SettingSwitch("Bibliothek im Kindermodus",settings.libraryEnabled){v->vm.updateSettings{old->old.copy(libraryEnabled=v)}}}
  item{SettingSwitch("Display während der Wiedergabe anlassen",settings.keepScreenOn){v->vm.updateSettings{old->old.copy(keepScreenOn=v)}}}
  item{SettingSwitch("Face-down-Energiespar-Overlay",settings.ecoMode){v->vm.updateSettings{old->old.copy(ecoMode=v)}}}
  item{SettingSwitch("Bunte Kindersteuerung",settings.colorfulControls){v->vm.updateSettings{old->old.copy(colorfulControls=v)}}}
  item{Text("Globaler Altersfilter: ${if(settings.maxAge>=99)"Alle" else "bis ${settings.maxAge} Jahre"}")}
  item{Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){Button(onClick={vm.updateSettings{it.copy(maxAge=99)}}){Text("Alle")};Button(onClick={vm.updateSettings{it.copy(maxAge=0)}}){Text("0 / unbekannt")};Button(onClick={vm.updateSettings{it.copy(maxAge=4)}}){Text("Bis 4")};Button(onClick={vm.updateSettings{it.copy(maxAge=12)}}){Text("Bis 12")}}}
  item{Text("Kindermodus-Hintergrund")}
  item{Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){Button(onClick={vm.updateSettings{it.copy(backgroundMode="default",backgroundUri=null)}}){Text("KlangKiste-Original")};Button(onClick={vm.updateSettings{it.copy(backgroundMode="color")}}){Text("Farbe")};Button(onClick={bgPicker.launch(arrayOf("image/*"))}){Text("Bild wählen")}}}
  item{OutlinedTextField(value=settings.backgroundColor,onValueChange={v->vm.updateSettings{it.copy(backgroundColor=v)}},label={Text("Hintergrundfarbe, z. B. #FFFFFF")},singleLine=true)}
  item{SettingSwitch("Medien in App-Speicher kopieren",settings.storageMode=="copy"){v->vm.updateSettings{it.copy(storageMode=if(v)"copy" else "reference")}}}
  item{Text(if(tts)"Deutsche TTS-Stimme verfügbar." else "Keine deutsche Stimme bestätigt.")}
  item{Button(onClick={vm.speakTitle("Guten Abend")}){Text("Deutsche Stimme testen")}}
  item{Button(onClick=onPinApp){Text("Android-App-Pinning starten")}}
  item{Text("App-Pinning ist eine Systemfunktion mit Nutzerbestätigung. Eine normale App kann Systemgesten nicht vollständig blockieren; echter Lock-Task/Kioskmodus kann Device-Owner-Konfiguration erfordern.")}
  item{Text("Dateireferenz benötigt die SAF-Zugriffsfreigabe. Kopien liegen im App-Speicher und werden bei Deinstallation entfernt.")}
 }
}

@Composable private fun SettingSwitch(title:String,checked:Boolean,onChanged:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(title,Modifier.weight(1f));Switch(checked,onChanged)}}

@Composable
fun StatsScreen(vm:KlangKisteViewModel,tracks:List<TrackWithAudio>){
 val events by vm.events.collectAsState();var scope by remember{mutableStateOf("day")};val now=System.currentTimeMillis()
 val cutoff=when(scope){"day"->java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis;"week"->now-7L*24*60*60*1000;else->0L}
 val filtered=events.filter{it.timestamp>=cutoff};val sessions=filtered.filter{it.type=="stop"||it.type=="app_close"};val duration=sessions.sumOf{it.durationSec};val names=tracks.associate{it.track.id to it.track.title};val complete=tracks.count{it.track.completed}
 val top=sessions.groupBy{it.trackId?:""}.mapValues{it.value.sumOf{e->e.durationSec}}.toList().sortedByDescending{it.second}.take(5)
 Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Text("Nutzung & Statistik",fontSize=23.sp)
  Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("day" to "Heute","week" to "7 Tage","all" to "Gesamt").forEach{(key,label)->FilterChip(scope==key,{scope=key},{Text(label)})}}
  Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("Hördauer: ${formatDuration(duration)}");Text("Ø Session: ${if(sessions.isEmpty())"0m" else formatDuration(duration/sessions.size)}");Text("Vollständig gehört: $complete")}}
  Text("Lieblings-Hörspiele");top.forEach{(id,sec)->Text("${names[id]?:"Unbekannt"} · ${formatDuration(sec)}")}
  Text("Timeline · letzte 50 Ereignisse")
  LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)){items(filtered.take(50),key={it.id}){TimelineRow(it,names[it.trackId])}}
 }
}
@Composable private fun TimelineRow(event:PlaybackEventEntity,title:String?){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(9.dp)){Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(event.timestamp))} · ${event.type}");if(!title.isNullOrBlank())Text(title);if(event.details.isNotBlank())Text(event.details,fontSize=12.sp)}}}
private fun formatDuration(seconds:Double):String{val total=(seconds/60).toInt().coerceAtLeast(0);val h=total/60;val m=total%60;return if(h>0)"${h}h ${m}m" else "${m}m"}
