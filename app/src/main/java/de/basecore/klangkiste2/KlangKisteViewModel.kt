package de.basecore.klangkiste2

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.room.withTransaction
import de.basecore.klangkiste2.core.CueParserJson
import de.basecore.klangkiste2.data.*
import de.basecore.klangkiste2.playback.PlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.Executor
import java.util.Locale

class KlangKisteViewModel(application:Application):AndroidViewModel(application){
 private val app=application.applicationContext
 private val db=KlangKisteDatabase.get(app)
 val tracks:StateFlow<List<TrackWithAudio>> = db.trackDao().observeAll().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val settings:StateFlow<AppSettingsEntity> = db.settingsDao().observe().map{it?:AppSettingsEntity()}.stateIn(viewModelScope,SharingStarted.Eagerly,AppSettingsEntity())
 val events:StateFlow<List<PlaybackEventEntity>> = db.statsDao().observeAll().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 private val _message=MutableStateFlow("");val message=_message.asStateFlow()
 private val _isChildMode=MutableStateFlow(false);val isChildMode=_isChildMode.asStateFlow()
 private val _childLibraryOpen=MutableStateFlow(false);val childLibraryOpen=_childLibraryOpen.asStateFlow()
 private val _isPlaying=MutableStateFlow(false);val isPlaying=_isPlaying.asStateFlow()
 private val _currentTrackId=MutableStateFlow<String?>(null);val currentTrackId=_currentTrackId.asStateFlow()
 private val _positionMs=MutableStateFlow(0L);val positionMs=_positionMs.asStateFlow()
 private val _durationMs=MutableStateFlow(0L);val durationMs=_durationMs.asStateFlow()
 private val _volume=MutableStateFlow(0.75f);val volume=_volume.asStateFlow()
 private val _sleeping=MutableStateFlow(false);val sleeping=_sleeping.asStateFlow()
 private val _blackout=MutableStateFlow(false);val blackout=_blackout.asStateFlow()
 private val _sleepSeconds=MutableStateFlow(0);val sleepSeconds=_sleepSeconds.asStateFlow()
 private val _faceDown=MutableStateFlow(false);val faceDown=_faceDown.asStateFlow()
 private val _ttsAvailable=MutableStateFlow(false);val ttsAvailable=_ttsAvailable.asStateFlow()
 private var controller:MediaController?=null
 private var progressJob:Job?=null
 private var sleepJob:Job?=null
 private var sleepPlayer:MediaPlayer?=null
 private var sessionStartMs:Long?=null
 private var lastSavedAt=0L
 private var pairingTrackId:String?=null
 private var tts:TextToSpeech?=null
 private var ttsStatus="Keine deutsche Stimme verfügbar."
 private val importer by lazy { ImportCoordinator(app,db,viewModelScope){ _message.value=it } }
 val candidates get()=importer.candidates

 private val listener=object:Player.Listener{
  override fun onIsPlayingChanged(playing:Boolean){
   _isPlaying.value=playing
   if(playing){
    if(sessionStartMs==null){sessionStartMs=System.currentTimeMillis();logAsync("play",_currentTrackId.value,"Wiedergabe gestartet")}
    if(settings.value.sleepTimerMinutes>0&&sleepJob?.isActive!=true&&_sleepSeconds.value<=0)startSleepTimer(settings.value.sleepTimerMinutes)
   }else finishSession()
  }
  override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int){mediaItem?.mediaId?.takeIf{it.isNotBlank()}?.let{_currentTrackId.value=it}}
  override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_ENDED){val id=_currentTrackId.value?:return;viewModelScope.launch{db.trackDao().get(id)?.let{db.trackDao().upsert(it.copy(completed=true,lastTrackIndex=0,lastPositionMs=0))};log("completed",id,0.0,"Vollständig gehört")}}}
 }
 init{
  viewModelScope.launch{val s=db.settingsDao().get();if(s==null)db.settingsDao().upsert(AppSettingsEntity())else _isChildMode.value=s.startMode=="kids"}
  connectController()
  tts = TextToSpeech(app) { status ->
   val engine = tts
   val germanVoice = engine?.voices?.firstOrNull { it.locale.language.equals("de", true) && !it.isNetworkConnectionRequired }
       ?: engine?.voices?.firstOrNull { it.locale.language.equals("de", true) }
   if (status == TextToSpeech.SUCCESS && engine != null && germanVoice != null) {
       engine.language = germanVoice.locale
       engine.voice = germanVoice
       _ttsAvailable.value = true
       ttsStatus = "Deutsche Stimme: ${germanVoice.name}"
   } else {
       _ttsAvailable.value = false
       ttsStatus = "Keine deutsche TTS-Stimme verfügbar. Eine englische Stimme wird nicht für deutschen Text verwendet."
   }
  }
  progressJob=viewModelScope.launch{while(isActive){delay(1000);val c=controller;if(c!=null){_positionMs.value=c.currentPosition.coerceAtLeast(0);_durationMs.value=c.duration.takeIf{it!=C.TIME_UNSET&&it>0}?:0L;if(c.isPlaying&&System.currentTimeMillis()-lastSavedAt>=5000){lastSavedAt=System.currentTimeMillis();persistPosition()}}}}
 }
 private fun connectController(){
  val future=MediaController.Builder(app,SessionToken(app,ComponentName(app,PlaybackService::class.java))).buildAsync()
  future.addListener(Runnable{runCatching{future.get()}.onSuccess{c->controller=c;c.addListener(listener);_currentTrackId.value=c.currentMediaItem?.mediaId;_isPlaying.value=c.isPlaying}.onFailure{_message.value="Media3-Player wird verbunden."}},Executor{it.run()})
 }
 fun showMessage(text:String){_message.value=text}
 fun clearMessage(){_message.value=""}
 fun setChildMode(enabled:Boolean){_isChildMode.value=enabled;_childLibraryOpen.value=false;if(!enabled)_faceDown.value=false}
 fun setStartInKids(enabled:Boolean){updateSettings{it.copy(startMode=if(enabled)"kids" else "parent")}}
 fun openChildLibrary(open:Boolean){_childLibraryOpen.value=open}
 fun setFaceDown(value:Boolean){_faceDown.value=value&&settings.value.ecoMode&&_isChildMode.value}
 fun updateSettings(change:(AppSettingsEntity)->AppSettingsEntity){viewModelScope.launch{val next=change(settings.value);if(!next.ecoMode)_faceDown.value=false;db.settingsDao().upsert(next)}}
 fun playTrack(item:TrackWithAudio){
  viewModelScope.launch{
   val parts=item.audioParts.sortedBy{it.position};if(parts.isEmpty()){_message.value="Audio fehlt. Wähle im Elternbereich ‚Dateien verknüpfen‘.";return@launch}
   val c=controller?:run{_message.value="Player wird verbunden. Bitte kurz warten.";return@launch}
   val meta=MediaMetadata.Builder().setTitle(item.track.title).setArtist("KlangKiste").build()
   val queue=parts.map{MediaItem.Builder().setMediaId(item.track.id).setUri(Uri.parse(it.uriString)).setMediaMetadata(meta).build()}
   c.setMediaItems(queue,item.track.lastTrackIndex.coerceIn(0,queue.lastIndex),item.track.lastPositionMs.coerceAtLeast(0));c.prepare();runCatching{c.volume=playbackGain()};c.play()
   _currentTrackId.value=item.track.id;db.trackDao().upsert(item.track.copy(lastPlayedAt=System.currentTimeMillis()));updateSettings{it.copy(lastTrackId=item.track.id)}
  }
 }
 fun togglePlay(){val c=controller;if(c==null||c.mediaItemCount==0){_message.value="Wähle zuerst ein Hörspiel.";return};if(c.isPlaying)c.pause()else c.play()}
 fun seekTo(ms:Long){controller?.seekTo(ms.coerceAtLeast(0))}
 fun skipForward(){controller?.let{it.seekTo((it.currentPosition+30000).coerceAtMost(it.duration.takeIf{d->d>0&&d!=C.TIME_UNSET}?:Long.MAX_VALUE))};logAsync("seek",_currentTrackId.value,"+30 Sekunden")}
 fun skipBack(){controller?.let{it.seekTo((it.currentPosition-30000).coerceAtLeast(0))};logAsync("seek",_currentTrackId.value,"-30 Sekunden")}
 fun restartPlaylist(){controller?.seekTo(0,0);logAsync("control",_currentTrackId.value,"Reset")}
 fun nextFileOrCue(){val c=controller?:return;val track=tracks.value.firstOrNull{it.track.id==_currentTrackId.value}?.track;val chapter=track?.cueJson?.let{CueParserJson.parse(it)}.orEmpty().firstOrNull{it.timeSeconds*1000>c.currentPosition+2000};if(chapter!=null){c.seekTo(c.currentMediaItemIndex.coerceAtLeast(0),(chapter.timeSeconds*1000).toLong());logAsync("seek",_currentTrackId.value,"Kapitel ${chapter.title}")}else if(c.currentMediaItemIndex+1<c.mediaItemCount)c.seekToNextMediaItem()else _message.value="Ende der Playlist"}
 private fun playbackGain():Float{val level=_volume.value.coerceIn(0f,1f)*settings.value.maxVolume.coerceIn(0f,1f);return level*level}
 fun adjustVolume(delta:Float){_volume.value=(_volume.value+delta).coerceIn(0f,1f);val gain=playbackGain();runCatching{controller?.volume=gain};runCatching{sleepPlayer?.setVolume(gain,gain)};logAsync("volume",_currentTrackId.value,"${(_volume.value*100).toInt()}%")}
 fun playTestTone(){runCatching{MediaPlayer.create(app,R.raw.limit)?.apply{val v=settings.value.maxVolume;setVolume(v*v,v*v);setOnCompletionListener{it.release()};start()}?:run{_message.value="Testton nicht verfügbar."}}.onFailure{_message.value="Testtonfehler: ${it.message}"}}
 fun setSleepTimer(minutes:Double){updateSettings{it.copy(sleepTimerMinutes=minutes.coerceAtLeast(0.0))};sleepJob?.cancel();sleepJob=null;_sleepSeconds.value=0;app.startService(Intent(app,PlaybackService::class.java).setAction(PlaybackService.ACTION_CANCEL_SLEEP));if(minutes>0&&_isPlaying.value)startSleepTimer(minutes)}
 private fun startSleepTimer(minutes:Double){sleepJob?.cancel();val seconds=(minutes*60).toInt().coerceAtLeast(1);_sleepSeconds.value=seconds;app.startService(Intent(app,PlaybackService::class.java).setAction(PlaybackService.ACTION_SET_SLEEP).putExtra(PlaybackService.EXTRA_SECONDS,seconds.toLong()));sleepJob=viewModelScope.launch{while(_sleepSeconds.value>0&&isActive){delay(1000);if(_isPlaying.value)_sleepSeconds.value=(_sleepSeconds.value-1).coerceAtLeast(0)};if(_sleepSeconds.value==0&&isActive){_sleeping.value=true;controller?.pause();logAsync("sleep_start",_currentTrackId.value,"Schlafansicht");playSleepSound();delay(60000);_blackout.value=true;logAsync("sleep_blackout",_currentTrackId.value,"Schwarzer Overlay")}}}
 private fun playSleepSound(){
  releaseSleepSound()
  val gain=playbackGain()
  if(gain==0f)return
  runCatching{
   val player=MediaPlayer.create(app,R.raw.gutenacht)?:return
   sleepPlayer=player
   player.setVolume(gain,gain)
   player.setOnCompletionListener{if(sleepPlayer===it)releaseSleepSound()}
   player.setOnErrorListener{p,_,_->if(sleepPlayer===p)releaseSleepSound();true}
   player.start()
  }.onFailure{releaseSleepSound()}
 }
 private fun releaseSleepSound(){val player=sleepPlayer;sleepPlayer=null;player?.release()}
 fun dismissSleepView(){sleepJob?.cancel();sleepJob=null;releaseSleepSound();_sleeping.value=false;_blackout.value=false;_sleepSeconds.value=0;setChildMode(false);app.startService(Intent(app,PlaybackService::class.java).setAction(PlaybackService.ACTION_CANCEL_SLEEP))}
 private fun finishSession(){val start=sessionStartMs?:return;sessionStartMs=null;val seconds=(System.currentTimeMillis()-start)/1000.0;if(seconds>2)logAsync("stop",_currentTrackId.value,"Wiedergabe pausiert",seconds);persistPosition()}
 private fun persistPosition(){val c=controller?:return;val id=c.currentMediaItem?.mediaId?:_currentTrackId.value?:return;viewModelScope.launch{db.trackDao().get(id)?.let{db.trackDao().upsert(it.copy(lastTrackIndex=c.currentMediaItemIndex.coerceAtLeast(0),lastPositionMs=c.currentPosition.coerceAtLeast(0),lastPlayedAt=System.currentTimeMillis()))}}}
 private suspend fun log(type:String,id:String?,duration:Double,detail:String){db.statsDao().insert(PlaybackEventEntity(timestamp=System.currentTimeMillis(),type=type,trackId=id,durationSec=duration,details=detail))}
 private fun logAsync(type:String,id:String?,detail:String,duration:Double=0.0){viewModelScope.launch{log(type,id,duration,detail)}}
 fun toggleHidden(track:TrackEntity){viewModelScope.launch{db.trackDao().upsert(track.copy(hiddenInLibrary=!track.hiddenInLibrary))}}
 fun setAllHidden(hidden:Boolean){viewModelScope.launch{db.trackDao().setAllHidden(hidden);_message.value=if(hidden)"Alle Inhalte ausgeblendet." else "Alle Inhalte sichtbar."}}
 fun deleteTrack(track:TrackEntity){viewModelScope.launch{db.trackDao().delete(track.id);_message.value="Eintrag gelöscht."}}
 fun saveEditedTrack(track:TrackEntity){viewModelScope.launch{db.trackDao().upsert(track);_message.value="Änderungen gespeichert."}}
 fun beginNfcPairing(trackId:String){pairingTrackId=trackId;_message.value="Jetzt NFC-Tag an das Gerät halten."}
 fun onNfcTag(uid:String){viewModelScope.launch{val normalized=uid.replace(Regex("[:\\-\\s]"),"").uppercase(Locale.ROOT);log("scan",_currentTrackId.value,0.0,uid);if(_isChildMode.value){val id=db.nfcTagDao().get(normalized)?.trackId?:return@launch;val item=tracks.value.firstOrNull{it.track.id==id};if(item!=null)playTrack(item)else _message.value="Unbekannter NFC-Tag."}else{val target=pairingTrackId;if(target!=null){db.nfcTagDao().upsert(NfcTagEntity(normalized,target));pairingTrackId=null;_message.value="NFC-Tag gespeichert."}else _message.value="NFC-Tag erkannt: $uid"}}}
 fun speakTitle(title:String){if(!_ttsAvailable.value){_message.value=ttsStatus;return};tts?.speak(title,TextToSpeech.QUEUE_FLUSH,null,"klangkiste-title")}
 fun clearAll(){viewModelScope.launch{controller?.stop();db.withTransaction{db.trackDao().deleteAll();db.nfcTagDao().clear();db.statsDao().clear()};_message.value="Bibliothek und Verlauf geleert."}}

 fun scanFolder(uri:Uri,smart:Boolean)=importer.scanFolder(uri,smart)
 fun scanSelectedFiles(uris:List<Uri>)=importer.scanSelectedFiles(uris)
 val importCandidates get()=importer.candidates
 fun clearCandidates()=importer.clearCandidates()
 fun toggleCandidate(id:String)=importer.toggleCandidate(id)
 fun importCandidates(items:List<ImportCandidate>,copy:Boolean)=importer.importCandidates(items,copy)
 fun importBackup(uri:Uri)=importer.importBackup(uri){settings.value}
 fun exportBackup(uri:Uri)=importer.exportBackup(uri,tracks.value,events.value,settings.value)
 fun relinkFolder(uri:Uri,copy:Boolean)=importer.relinkFolder(uri,copy)
 fun installOhrkaCatalog()=importer.installOhrkaCatalog()
 fun loadOnlineExamples()=importer.loadOnlineExamples()

 override fun onCleared(){progressJob?.cancel();sleepJob?.cancel();releaseSleepSound();controller?.removeListener(listener);controller?.release();tts?.stop();tts?.shutdown();super.onCleared()}
}
