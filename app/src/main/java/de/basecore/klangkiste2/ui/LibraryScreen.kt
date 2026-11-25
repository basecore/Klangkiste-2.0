package de.basecore.klangkiste2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.core.ImportFilter
import de.basecore.klangkiste2.data.AppSettingsEntity
import de.basecore.klangkiste2.data.TrackEntity
import de.basecore.klangkiste2.data.TrackWithAudio

@Composable
fun LibraryScreen(vm:KlangKisteViewModel,tracks:List<TrackWithAudio>,settings:AppSettingsEntity,childMode:Boolean,onBack:(()->Unit)?){
 var query by remember{mutableStateOf("")};var genre by remember{mutableStateOf("Alle")};var recent by remember{mutableStateOf(false)};var ageMenu by remember{mutableStateOf(false)};var info by remember{mutableStateOf<TrackEntity?>(null)}
 val listMode=settings.adminListView&&!childMode
 val genres=listOf("Alle")+tracks.map{it.track.genre}.filter{it.isNotBlank()}.distinct().sorted()
 val visible=tracks.filter{item->val t=item.track;(!childMode||!t.hiddenInLibrary)&&(settings.maxAge>=99||t.ageRecommendation==null||t.ageRecommendation<=settings.maxAge)&&(genre=="Alle"||genre==t.genre)&&ImportFilter.matches("${t.title} ${t.description} ${t.genre} ${t.tagsCsv}",t.ageRecommendation,query)}.let{if(recent)it.filter{it.track.lastPlayedAt>0}.sortedByDescending{it.track.lastPlayedAt}.take(3)else it.sortedBy{it.track.title.lowercase()}}
 Column(Modifier.fillMaxSize().background(Color(0xFF2C3E50)).padding(14.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(if(childMode)"Meine KlangKiste 📚" else "Bibliothek",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);if(onBack!=null)TextButton(onClick=onBack){Text("Zurück",color=Color.White)};if(!childMode)TextButton(onClick={vm.updateSettings{it.copy(adminListView=!it.adminListView)}}){Text(if(listMode)"▦ Raster" else "☰ Liste",color=Color.White)}}
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Titel, Beschreibung, Genre oder Tag")})
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){FilterChip(!recent,{recent=false},{Text("Alle")});FilterChip(recent,{recent=true},{Text("Zuletzt gehört")});Box{TextButton(onClick={ageMenu=true}){Text(if(settings.maxAge>=99)"Alle Alter" else "Bis ${settings.maxAge} J.",color=Color.White)};DropdownMenu(ageMenu,{ageMenu=false}){listOf(99,0,1,2,3,4,5,6,8,10,12).forEach{a->DropdownMenuItem(text={Text(if(a==99)"Alle anzeigen" else if(a==0)"0 J. / ohne Angabe" else "Bis $a Jahre")},onClick={vm.updateSettings{it.copy(maxAge=a)};ageMenu=false})}}}}
  LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(genres){g->FilterChip(genre==g,{genre=g},{Text(g)})}}
  if(visible.isEmpty())Text("Keine passenden Inhalte gefunden.",color=Color.White,modifier=Modifier.padding(18.dp))
  else if(listMode)LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){items(visible,key={it.track.id}){item->TrackRow(item,childMode,vm){info=item.track}}}
  else LazyVerticalGrid(columns=GridCells.Fixed(2),verticalArrangement=Arrangement.spacedBy(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){items(visible,key={it.track.id}){item->TrackTile(item,childMode,vm){info=item.track}}}
 }
 info?.let{t->AlertDialog(onDismissRequest={info=null},title={Text(t.title)},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){CoverArt(t.coverUri,Modifier.fillMaxWidth().height(180.dp));Text(t.description.ifBlank{"Keine Beschreibung vorhanden."});Text(listOfNotNull(t.ageRecommendation?.let{"Ab $it J."},t.genre,t.runtimeMinutes?.let{"$it min"}).joinToString(" • "))}},confirmButton={TextButton(onClick={info=null}){Text("Schließen")}})}
}

@Composable private fun TrackTile(item:TrackWithAudio,child:Boolean,vm:KlangKisteViewModel,onInfo:()->Unit){Card(onClick={vm.playTrack(item)}){Column{Box{CoverArt(item.track.coverUri,Modifier.fillMaxWidth().height(150.dp));if(!child)TextButton(onClick=onInfo,modifier=Modifier.align(Alignment.TopEnd)){Text("ⓘ")};if(item.track.completed)Text("✓",Modifier.align(Alignment.BottomEnd).padding(8.dp),color=Color(0xFF2ECC71),fontSize=22.sp,fontWeight=FontWeight.Bold)};Text(item.track.title,Modifier.padding(10.dp),fontWeight=FontWeight.Bold,maxLines=2)}}}
@Composable private fun TrackRow(item:TrackWithAudio,child:Boolean,vm:KlangKisteViewModel,onInfo:()->Unit){Card(onClick={vm.playTrack(item)},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){CoverArt(item.track.coverUri,Modifier.height(58.dp).weight(1f));Column(Modifier.weight(3f).padding(horizontal=10.dp)){Text(item.track.title,fontWeight=FontWeight.Bold,maxLines=1);Text(listOfNotNull(item.track.genre,item.track.ageRecommendation?.let{"$it J."}).joinToString(" • "),fontSize=12.sp)};if(!child)TextButton(onClick=onInfo){Text("Info")}}}}
