package de.basecore.klangkiste2.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.data.AppSettingsEntity
import de.basecore.klangkiste2.data.PlaybackEventEntity
import de.basecore.klangkiste2.data.TrackWithAudio
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(vm:KlangKisteViewModel,settings:AppSettingsEntity,onPinApp:()->Unit,mode:DisplayMode,onMode:(DisplayMode)->Unit){
 val context=LocalContext.current
 val tts by vm.ttsAvailable.collectAsState()
 val remaining by vm.sleepSeconds.collectAsState()
 val storyLimit by vm.sleepStoryLimit.collectAsState()
 val storiesRemaining by vm.sleepStoriesRemaining.collectAsState()
 val playing by vm.isPlaying.collectAsState()
 var showRules by rememberSaveable { mutableStateOf(false) }
 var showStoryDialog by rememberSaveable { mutableStateOf(false) }
 var storyInput by rememberSaveable { mutableStateOf("2") }
 if(showStoryDialog){
  val count=storyInput.toIntOrNull()?.takeIf{it in 1..99}
  AlertDialog(
   onDismissRequest={showStoryDialog=false},
   title={Text("Nach Hörspielen")},
   text={OutlinedTextField(storyInput,{input->storyInput=input.filter{it in '0'..'9'}.take(2)},label={Text("Anzahl (1–99)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))},
   confirmButton={TextButton(enabled=count!=null,onClick={count?.let(vm::setSleepStoryLimit);showStoryDialog=false}){Text("Übernehmen")}},
   dismissButton={TextButton(onClick={showStoryDialog=false}){Text("Abbrechen")}}
  )
 }
 val bgPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};vm.updateSettings{it.copy(backgroundMode="image",backgroundUri=uri.toString())}}}
 val timers=listOf(0.0 to "Aus",0.5 to "30 Sek.",10.0 to "10 Min.",20.0 to "20 Min.",30.0 to "30 Min.",60.0 to "1 Std.")
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  item{Text("Einstellungen",fontSize=23.sp)}
  item{Button(onClick={showRules=!showRules}){Text(if(showRules) "Hörregeln ausblenden" else "Hörregeln öffnen")}}
  if(showRules){
   item(key="listening_rules"){ListeningRules()}
   item(key="listening_rules_sleep_timer"){
    Text("Schlaf-Timer")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     timers.forEach{(minutes,label)->Selection(label,storyLimit==0&&settings.sleepTimerMinutes==minutes){vm.setSleepTimer(minutes)}}
     Selection("Nach Hörspielen",storyLimit>0){storyInput=if(storyLimit>0)storyLimit.toString() else "2";showStoryDialog=true}
    }
    val status=when{
     storyLimit>0->when(storiesRemaining){0->"Schlafenszeit";1->"Noch 1 Hörspiel";else->"Noch $storiesRemaining Hörspiele"}
     settings.sleepTimerMinutes<=0.0->"Timer ausgeschaltet"
     remaining>0->if(playing)"Verbleibend: %d:%02d".format(remaining/60,remaining%60) else "Pausiert bei %d:%02d".format(remaining/60,remaining%60)
     else->"Startet mit der Wiedergabe"
    }
    Text(status,style=MaterialTheme.typography.bodySmall)
   }
   item(key="listening_rules_age_filter"){
    Text("Altersfilter: ${if(settings.maxAge>=99)"Alle" else "bis ${settings.maxAge} Jahre"}")
    Text("Gilt für alle Hörspiele.")
    Text("Titel ohne Altersempfehlung bleiben erlaubt.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     listOf(99 to "Alle",0 to "0 / unbekannt",4 to "Bis 4",12 to "Bis 12").forEach{(age,label)->
      Selection(label,if(age==99)settings.maxAge>=99 else settings.maxAge==age){vm.updateSettings{it.copy(maxAge=age)}}
     }
    }
   }
  }
  item{Text("Maximale Lautstärke: ${(settings.maxVolume*100).toInt()}%")}
  item{Slider(settings.maxVolume,{v->vm.updateSettings{it.copy(maxVolume=v.coerceIn(0f,1f))}})}
  item{Button(onClick=vm::playTestTone){Text("Testton (Lautstärkebegrenzung)")}}
  item{SettingSwitch("Beim Start direkt in den Kindermodus",settings.startMode=="kids",vm::setStartInKids)}
  item{SettingSwitch("Bibliothek im Kindermodus",settings.libraryEnabled){v->vm.updateSettings{it.copy(libraryEnabled=v)}}}
  item{
   Text("Display anlassen")
   FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    DisplayMode.entries.forEach{option->Selection(option.label,mode==option){onMode(option)}}
   }
   Text("Bei Wiedergabe gilt im Kindermodus. Immer an gilt auch bei Pause und im Elternbereich, solange KlangKiste im Vordergrund ist.",style=MaterialTheme.typography.bodySmall)
  }
  item{SettingSwitch("Face-down-Energiespar-Overlay",settings.ecoMode){v->vm.updateSettings{it.copy(ecoMode=v)}}}
  item{SettingSwitch("Bunte Kindersteuerung",settings.colorfulControls){v->vm.updateSettings{it.copy(colorfulControls=v)}}}
  item{
   Text("Kindermodus-Hintergrund")
   FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Selection("KlangKiste-Original",settings.backgroundMode=="default"){vm.updateSettings{it.copy(backgroundMode="default",backgroundUri=null)}}
    Selection("Farbe",settings.backgroundMode=="color"){vm.updateSettings{it.copy(backgroundMode="color")}}
    Selection("Bild wählen",settings.backgroundMode=="image"){bgPicker.launch(arrayOf("image/*"))}
   }
  }
  item{OutlinedTextField(value=settings.backgroundColor,onValueChange={v->vm.updateSettings{it.copy(backgroundColor=v)}},label={Text("Hintergrundfarbe, z. B. #FFFFFF")},singleLine=true)}
  item{SettingSwitch("Medien in App-Speicher kopieren",settings.storageMode=="copy"){v->vm.updateSettings{it.copy(storageMode=if(v)"copy" else "reference")}}}
  item{Text(if(tts)"Deutsche TTS-Stimme verfügbar." else "Keine deutsche Stimme bestätigt.")}
  item{Button(onClick={vm.speakTitle("Guten Abend")}){Text("Deutsche Stimme testen")}}
  item{Button(onClick=onPinApp){Text("Android-App-Pinning starten")}}
  item{Text("App-Pinning ist eine Systemfunktion mit Nutzerbestätigung. Eine normale App kann Systemgesten nicht vollständig blockieren; echter Lock-Task/Kioskmodus kann Device-Owner-Konfiguration erfordern.")}
  item{Text("Dateireferenz benötigt die SAF-Zugriffsfreigabe. Kopien liegen im App-Speicher und werden bei Deinstallation entfernt.")}
 }
}

@Composable private fun Selection(label:String,selected:Boolean,onClick:()->Unit){
 FilterChip(selected,onClick,{Text(if(selected)"✓ $label" else label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary))
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
  Card(Modifier.fillMaxWidth()){Column(Modifier.padding(9.dp)){Text("Hördauer: ${formatDuration(duration)}");Text("Ø Session: ${if(sessions.isEmpty())"0m" else formatDuration(duration/sessions.size)}");Text("Vollständig gehört: $complete")}}
  Text("Lieblings-Hörspiele");top.forEach{(id,sec)->Text("${names[id]?:"Unbekannt"} · ${formatDuration(sec)}")}
  Text("Timeline · letzte 50 Ereignisse")
  LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)){items(filtered.take(50),key={it.id}){TimelineRow(it,names[it.trackId])}}
 }
}
@Composable private fun TimelineRow(e:PlaybackEventEntity,title:String?){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(9.dp)){Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(e.timestamp))} · ${e.type}");if(!title.isNullOrBlank())Text(title);if(e.details.isNotBlank())Text(e.details,fontSize=12.sp)}}}
private fun formatDuration(seconds:Double):String{val total=(seconds/60).toInt().coerceAtLeast(0);return "${total/60}h ${total%60}m"}
