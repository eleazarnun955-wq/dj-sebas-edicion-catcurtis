package com.djsebas.catcurtis

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Bg = Color(0xFF0B0B12)
private val Panel = Color(0xFF171724)
private val Accent = Color(0xFFB64CFF)
private data class Clip(val uri: Uri, val name: String, val kind: String)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) { EditorScreen() } } }
}

@Composable
private fun EditorScreen() {
 val context = LocalContext.current
 val clips = remember { mutableStateListOf<Clip>() }
 var selected by remember { mutableStateOf<Clip?>(null) }
 var status by remember { mutableStateOf("Importa videos, fotos o música para comenzar") }
 val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
  uris.forEach { uri ->
   runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   val type = context.contentResolver.getType(uri).orEmpty()
   val kind = when { type.startsWith("video") -> "VIDEO"; type.startsWith("audio") -> "AUDIO"; type.startsWith("image") -> "FOTO"; else -> "ARCHIVO" }
   val clip = Clip(uri, uri.lastPathSegment?.substringAfterLast('/')?.take(28) ?: "Medio importado", kind)
   clips.add(clip); if (selected == null && kind == "VIDEO") selected = clip
  }
  status = if (uris.isEmpty()) "No se seleccionaron archivos" else "${uris.size} archivo(s) agregados a tu biblioteca"
 }
 val player = remember { ExoPlayer.Builder(context).build() }
 LaunchedEffect(selected) { selected?.takeIf { it.kind == "VIDEO" }?.let { player.setMediaItem(MediaItem.fromUri(it.uri)); player.prepare() } }
 DisposableEffect(Unit) { onDispose { player.release() } }
 Column(Modifier.fillMaxSize().background(Bg).padding(14.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
   Column(Modifier.weight(1f)) { Text("DJ SEBAS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("EDICIÓN CATCURTIS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
   Button(onClick = { status = "La exportación multipista se habilitará en la siguiente etapa" }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Icon(Icons.Default.SaveAlt, null); Spacer(Modifier.width(5.dp)); Text("Exportar") }
  }
  Spacer(Modifier.height(14.dp))
  Box(Modifier.fillMaxWidth().weight(1.05f).background(Color.Black, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
   if (selected?.kind == "VIDEO") AndroidView(factory = { PlayerView(it).apply { player = player; useController = true } }, modifier = Modifier.fillMaxSize())
   else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.size(58.dp)); Text("VISTA PREVIA", color = Color.LightGray, fontWeight = FontWeight.Bold); Text("Selecciona un video de tu biblioteca", color = Color.Gray, fontSize = 11.sp) }
  }
  Spacer(Modifier.height(12.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("HERRAMIENTAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text("PRO EDITOR", color = Accent, fontSize = 10.sp) }
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   ToolButton("Añadir", Icons.Default.Add) { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }
   ToolButton("Cortar", Icons.Default.ContentCut) { status = "Selecciona un clip en la línea de tiempo para editarlo" }
   ToolButton("Audio", Icons.Default.MusicNote) { picker.launch(arrayOf("audio/*")) }
   ToolButton("Fotos", Icons.Default.Photo) { picker.launch(arrayOf("image/*")) }
   ToolButton("Ajustes", Icons.Default.Tune) { status = "Herramientas de color y efectos en preparación" }
  }
  Spacer(Modifier.height(12.dp))
  Row(verticalAlignment = Alignment.CenterVertically) { Text("LÍNEA DE TIEMPO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.weight(1f)); Text("${clips.size} clips", color = Color.Gray, fontSize = 11.sp) }
  Spacer(Modifier.height(7.dp))
  Row(Modifier.fillMaxWidth().height(112.dp).background(Panel, RoundedCornerShape(12.dp)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
   if (clips.isEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("＋  Agrega medios para crear tu proyecto", color = Color.Gray, fontSize = 12.sp) }
   else LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { items(clips) { clip -> Row(Modifier.fillMaxWidth().background(if (selected == clip) Color(0xFF38234A) else Color(0xFF242434), RoundedCornerShape(7.dp)).clickable { selected = clip }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (clip.kind == "VIDEO") Icons.Default.VideoLibrary else if (clip.kind == "AUDIO") Icons.Default.MusicNote else Icons.Default.Photo, null, tint = Accent); Spacer(Modifier.width(8.dp)); Column { Text(clip.name, color = Color.White, fontSize = 11.sp); Text(clip.kind, color = Color.LightGray, fontSize = 9.sp) } } } }
  }
  Spacer(Modifier.height(8.dp)); Text(status, color = Color(0xFFB9A8C8), fontSize = 10.sp, maxLines = 2)
 }
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
 Column(Modifier.width(68.dp).background(Panel, RoundedCornerShape(12.dp)).clickable { action() }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(5.dp)); Text(label, color = Color.White, fontSize = 10.sp) }
}
