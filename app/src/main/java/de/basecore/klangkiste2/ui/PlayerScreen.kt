package de.basecore.klangkiste2.ui

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.R
import de.basecore.klangkiste2.data.AppSettingsEntity
import de.basecore.klangkiste2.data.TrackWithAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(vm:KlangKisteViewModel,active:TrackWithAudio?,settings:AppSettingsEntity,childMode:Boolean,onOpenLibrary:()->Unit){
 val playing by vm.isPlaying.collectAsState();val pos by vm.positionMs.collectAsState();val duration by vm.durationMs.collectAsState();val volume by vm.volume.collectAsState();val sleepSeconds by vm.sleepSeconds.collectAsState()
 val bg=remember(settings.backgroundColor){runCatching{Color(AndroidColor.parseColor(settings.backgroundColor))}.getOrDefault(Color.White)}
 Box(Modifier.fillMaxSize().background(if(childMode&&settings.backgroundMode=="color")bg else Color(0xFF2C3E50))){
  if(childMode){
   when(settings.backgroundMode){
    "image"->{if(settings.backgroundUri.isNullOrBlank())Image(painterResource(R.drawable.kids_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else FullBackground(settings.backgroundUri!!)}
    "color"->Unit
    else->Image(painterResource(R.drawable.kids_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
   }
  }
  Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=if(childMode)22.dp else 12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceEvenly){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Text(if(childMode)"Meine KlangKiste" else "Player",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
    if(childMode&&settings.libraryEnabled)IconButton(onClick=onOpenLibrary){Icon(Icons.Default.LibraryMusic,"Bibliothek",tint=Color.White)}
    if(!childMode&&sleepSeconds>0)Text("🌙 ${sleepSeconds/60}:${(sleepSeconds%60).toString().padStart(2,'0')}",color=Color.White)
   }
   CoverArt(active?.track?.coverUri,Modifier.fillMaxWidth(if(childMode) 0.58f else 0.42f).aspectRatio(1f),active?.track?.title?:"Cover")
   Column(horizontalAlignment=Alignment.CenterHorizontally){
    Text(active?.track?.title?:"Bereit zum Spielen!",color=Color.White,fontSize=if(childMode)24.sp else 21.sp,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center,maxLines=2)
    active?.track?.let{val meta=listOfNotNull(it.track.genre,it.track.ageRecommendation?.let{"Ab $it J."},it.track.runtimeMinutes?.let{"$it min"}).joinToString(" • ");if(meta.isNotBlank())Text(meta,color=Color(0xFFEAF6F7),fontSize=13.sp)}
   }
   Column(Modifier.fillMaxWidth()){
    Slider(value=if(duration>0)pos.coerceAtMost(duration).toFloat() else 0f,onValueChange={vm.seekTo(it.toLong())},valueRange=0f..duration.coerceAtLeast(1).toFloat())
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(formatTime(pos),color=Color.White);Text("-${formatTime((duration-pos).coerceAtLeast(0))}",color=Color.White)}
   }
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
    IconButton(onClick={vm.adjustVolume(-.1f)}){Icon(Icons.Default.VolumeDown,"Leiser",tint=Color.White)}
    Slider(value=volume,onValueChange={vm.adjustVolume(it-volume)},modifier=Modifier.fillMaxWidth(0.65f))
    IconButton(onClick={vm.adjustVolume(0.1f)}){Icon(Icons.Default.VolumeUp,"Lauter",tint=Color.White)}
   }
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
    FilledIconButton(onClick=vm::restartPlaylist,modifier=Modifier.size(54.dp),shape=CircleShape){Icon(Icons.Default.Replay,"Von vorn")}
    FilledIconButton(onClick=vm::skipBack,modifier=Modifier.size(58.dp),shape=CircleShape){Icon(Icons.Default.Replay,"30 Sekunden zurück")}
    FilledIconButton(onClick=vm::togglePlay,modifier=Modifier.size(if(childMode)88.dp else 72.dp),shape=CircleShape){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,if(playing)"Pause" else "Wiedergabe",Modifier.size(46.dp))}
    FilledIconButton(onClick=vm::skipForward,modifier=Modifier.size(58.dp),shape=CircleShape){Icon(Icons.Default.FastForward,"30 Sekunden vor")}
    FilledIconButton(onClick=vm::nextFileOrCue,modifier=Modifier.size(54.dp),shape=CircleShape){Icon(Icons.Default.SkipNext,"Nächstes Kapitel/Datei")}
   }
   if(!childMode)Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=onOpenLibrary){Text("Bibliothek")};Button(onClick={vm.speakTitle(active?.track?.title?:"KlangKiste")}){Text("Titel ansagen")}}
  }
 }
}

@Composable
private fun FullBackground(uri:String){
 val context=LocalContext.current
 val image by produceState(initialValue=null as androidx.compose.ui.graphics.ImageBitmap?,key1=uri){value=withContext(Dispatchers.IO){runCatching{context.contentResolver.openInputStream(Uri.parse(uri))?.use{BitmapFactory.decodeStream(it)?.asImageBitmap()}}.getOrNull()}}
 if(image!=null)Image(image!!,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Image(painterResource(R.drawable.kids_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
}

@Composable
fun SleepOverlay(blackout:Boolean,onExit:()->Unit){
 val transition=rememberInfiniteTransition(label="sleep")
 val scale by transition.animateFloat(1f,1.04f,infiniteRepeatable(tween(3000),RepeatMode.Reverse),label="pulse")
 Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
  if(!blackout){Image(painterResource(R.drawable.background_sleep),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Column(horizontalAlignment=Alignment.CenterHorizontally){Image(painterResource(R.drawable.note_sleeping_small),"Schlafende Note",Modifier.size(150.dp).graphicsLayer(scaleX=scale,scaleY=scale),contentScale=ContentScale.Fit);Spacer(Modifier.height(16.dp));Text("Schlafenszeit …",color=Color.White.copy(alpha=.75f),fontSize=20.sp)}}
 }
}
private fun formatTime(ms:Long):String{val s=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(s/60,s%60)}
