package de.basecore.klangkiste2.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val KlangPalette = lightColorScheme(primary=Color(0xFF2ECC71),onPrimary=Color.White,secondary=Color(0xFF3498DB),onSecondary=Color.White,tertiary=Color(0xFF9B59B6),background=Color(0xFFF4F7F6),surface=Color.White,error=Color(0xFFFF4757))
@Composable fun KlangKisteTheme(content:@Composable ()->Unit){MaterialTheme(colorScheme=KlangPalette,content=content)}
