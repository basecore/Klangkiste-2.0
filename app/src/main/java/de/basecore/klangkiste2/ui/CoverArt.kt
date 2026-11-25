package de.basecore.klangkiste2.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CoverArt(uriString: String?, modifier: Modifier = Modifier, contentDescription: String = "Cover") {
    val context = LocalContext.current
    val bitmap: ImageBitmap? by produceState(initialValue = null, key1 = uriString) {
        value = if (uriString.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(Uri.parse(uriString))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap = bitmap!!, contentDescription = contentDescription, modifier = modifier.clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
    else Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFE8F7F9)), contentAlignment = Alignment.Center) { Text("♫", fontSize = 48.sp, color = Color(0xFF258C8A)) }
}
