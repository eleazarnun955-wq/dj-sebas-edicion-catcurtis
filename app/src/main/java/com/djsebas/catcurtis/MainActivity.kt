package com.djsebas.catcurtis

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.graphics.Color as AndroidColor
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.concurrent.Executors

private val Bg = Color(0xFF0B0B12)
private val Panel = Color(0xFF171724)
private val Accent = Color(0xFFB64CFF)
private data class Clip(val uri: Uri, val name: String, val kind: String, val durationMs: Long = 0L)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) { EditorScreen() } }
 }
}

@OptIn(UnstableApi::class)
@Composable
private fun EditorScreen() {
 val context = LocalContext.current
 val clips = remember { mutableStateListOf<Clip>() }
 val undoHistory = remember { mutableStateListOf<List<Clip>>() }
 val redoHistory = remember { mutableStateListOf<List<Clip>>() }
 var selected by remember { mutableStateOf<Clip?>(null) }
 var trimStart by remember { mutableFloatStateOf(0f) }
 var trimEnd by remember { mutableFloatStateOf(1f) }
 var status by remember { mutableStateOf("Importa videos, fotos o música para comenzar") }
 var titleText by remember { mutableStateOf("") }
 var filterMode by remember { mutableStateOf("Original") }
 var previewVolume by remember { mutableFloatStateOf(1f) }
 var exportAudioVolume by remember { mutableFloatStateOf(1f) }
 var exporting by remember { mutableStateOf(false) }
 val worker = remember { Executors.newSingleThreadExecutor() }
 val mainHandler = remember { Handler(Looper.getMainLooper()) }

 // Historial de cambios de la línea de tiempo (máximo 30 pasos).
 fun saveTimelineState() {
  undoHistory.add(clips.toList())
  if (undoHistory.size > 30) undoHistory.removeAt(0)
  redoHistory.clear()
 }
 fun restoreTimeline(items: List<Clip>) {
  clips.clear()
  clips.addAll(items)
  selected = selected?.let { current -> clips.firstOrNull { it.uri == current.uri } }
   ?: clips.firstOrNull { it.kind == "VIDEO" }
  trimStart = 0f
  trimEnd = selected?.durationMs?.coerceAtLeast(1L)?.toFloat() ?: 1f
 }
 fun undoTimeline() {
  if (undoHistory.isNotEmpty()) {
   redoHistory.add(clips.toList())
   val previous = undoHistory.removeAt(undoHistory.lastIndex)
   restoreTimeline(previous)
   status = "Último cambio de la línea de tiempo deshecho"
  }
 }
 fun redoTimeline() {
  if (redoHistory.isNotEmpty()) {
   undoHistory.add(clips.toList())
   val next = redoHistory.removeAt(redoHistory.lastIndex)
   restoreTimeline(next)
   status = "Cambio de la línea de tiempo restaurado"
  }
 }
 val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
  if (uris.isNotEmpty()) saveTimelineState()
  uris.forEach { uri ->
   runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   val mime = context.contentResolver.getType(uri).orEmpty()
   val kind = when { mime.startsWith("video") -> "VIDEO"; mime.startsWith("audio") -> "AUDIO"; mime.startsWith("image") -> "FOTO"; else -> "ARCHIVO" }
   val duration = if (kind == "VIDEO") runCatching {
    val retriever = MediaMetadataRetriever()
    try { retriever.setDataSource(context, uri); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
    finally { retriever.release() }
   }.getOrDefault(0L) else 0L
   val clip = Clip(uri, uri.lastPathSegment?.substringAfterLast('/')?.take(28) ?: "Medio importado", kind, duration)
   clips.add(clip)
   if (selected == null && kind == "VIDEO") { selected = clip; trimStart = 0f; trimEnd = duration.coerceAtLeast(1L).toFloat() }
  }
  status = if (uris.isEmpty()) "No se seleccionaron archivos" else "${uris.size} archivo(s) agregados"
 }
 val player = remember { ExoPlayer.Builder(context).build() }
 LaunchedEffect(previewVolume) { player.volume = previewVolume }
 LaunchedEffect(selected, trimStart, trimEnd) {
  selected?.takeIf { it.kind == "VIDEO" }?.let { clip ->
   val start = trimStart.toLong().coerceAtLeast(0L)
   val end = trimEnd.toLong().coerceAtLeast(start + 1L)
   val item = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()
   ).build()
   player.setMediaItem(item); player.prepare()
  }
 }
 DisposableEffect(Unit) { onDispose { player.release(); worker.shutdown() } }

 fun exportVideo() {
  val visualClips = clips.filter { it.kind == "VIDEO" || it.kind == "FOTO" }
  val clip = selected
  if (visualClips.isEmpty()) { status = "Importa al menos un video o una foto"; return }
  if (clip?.kind == "VIDEO" && (clip.durationMs <= 0L || trimEnd <= trimStart)) { status = "El rango de recorte no es válido"; return }
  exporting = true
  status = "Preparando ${visualClips.size} clip(s) de video/foto… no cierres la aplicación"
  val output = File(context.cacheDir, "catcurtis_${System.currentTimeMillis()}.mp4")
  val sequenceItems = mutableListOf<EditedMediaItem>()
  visualClips.forEach { video ->
   val mediaBuilder = MediaItem.Builder().setUri(video.uri)
   if (video.kind == "FOTO") mediaBuilder.setImageDurationMs(5000L)
   if (video.kind == "VIDEO" && video == clip) {
    mediaBuilder.setClippingConfiguration(
     MediaItem.ClippingConfiguration.Builder().setStartPositionMs(trimStart.toLong()).setEndPositionMs(trimEnd.toLong()).build()
    )
   }
   val editedBuilder = EditedMediaItem.Builder(mediaBuilder.build())
   val videoEffects = when (filterMode) {
    "Blanco y negro" -> listOf(RgbFilter.createGrayscaleFilter())
    "Negativo" -> listOf(RgbFilter.createInvertedFilter())
    else -> emptyList()
   }
   val overlays = if (titleText.isNotBlank()) {
    val styledTitle = SpannableString(titleText.trim()).apply {
     setSpan(ForegroundColorSpan(AndroidColor.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val titleSettings = StaticOverlaySettings.Builder()
     .setOverlayFrameAnchor(0f, 0.78f)
     .setBackgroundFrameAnchor(0f, 0.78f)
     .build()
    listOf(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(styledTitle, titleSettings))))
   } else emptyList()
   if (videoEffects.isNotEmpty() || overlays.isNotEmpty()) {
    editedBuilder.setEffects(Effects(emptyList(), videoEffects + overlays))
   }
   sequenceItems.add(editedBuilder.build())
  }
  val sequence = EditedMediaItemSequence(sequenceItems)
  // Todas las pistas de audio se mezclan simultáneamente y se repiten
  // hasta completar la duración del montaje visual.
  val audioSequences = clips.filter { it.kind == "AUDIO" }.map { audioClip ->
   val audioProcessor = ChannelMixingAudioProcessor()
   // Matrices de ganancia para audio mono, estéreo y multicanal habitual.
   for (channelCount in 1..6) {
    audioProcessor.putChannelMixingMatrix(
     ChannelMixingMatrix.createForConstantPower(channelCount, channelCount).scaleBy(exportAudioVolume)
    )
   }
   val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(audioClip.uri))
    .setEffects(Effects(listOf(audioProcessor), emptyList()))
    .build()
   EditedMediaItemSequence.Builder(audioItem).setIsLooping(true).build()
  }
  val composition = Composition.Builder(listOf(sequence) + audioSequences).build()
  val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
   override fun onCompleted(composition: Composition, exportResult: ExportResult) {
    worker.execute {
     try {
      val values = ContentValues().apply {
       put(MediaStore.Video.Media.DISPLAY_NAME, "DJ_SEBAS_CATCURTIS_${System.currentTimeMillis()}.mp4")
       put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
       put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DJ SEBAS CATCURTIS")
      }
      val target = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("No se pudo crear el archivo")
      context.contentResolver.openOutputStream(target)?.use { out -> output.inputStream().use { it.copyTo(out) } } ?: error("No se pudo guardar")
      output.delete()
      mainHandler.post { exporting = false; status = "¡Listo! Video guardado en Películas/DJ SEBAS CATCURTIS" }
     } catch (e: Exception) {
      mainHandler.post { exporting = false; status = "Error al guardar: ${e.message ?: "intenta otra vez"}" }
     }
    }
   }
   override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
    output.delete()
    mainHandler.post { exporting = false; status = "Error al procesar: ${exportException.message ?: "formato no compatible"}" }
   }
  }).build()
  runCatching { transformer.start(composition, output.absolutePath) }.onFailure {
   exporting = false; status = "No se pudo iniciar: ${it.message}"
  }
 }

 Column(Modifier.fillMaxSize().background(Bg).padding(14.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
   Column(Modifier.weight(1f)) { Text("DJ SEBAS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("EDICIÓN CATCURTIS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
   Button(onClick = { exportVideo() }, enabled = !exporting, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
    if (exporting) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.SaveAlt, null)
    Spacer(Modifier.width(5.dp)); Text(if (exporting) "Exportando" else "Exportar proyecto")
   }
  }
  Spacer(Modifier.height(12.dp))
  Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
   if (selected?.kind == "VIDEO") AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
   else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.size(58.dp)); Text("VISTA PREVIA", color = Color.LightGray, fontWeight = FontWeight.Bold); Text("Selecciona un video de tu biblioteca", color = Color.Gray, fontSize = 11.sp) }
  }
  Spacer(Modifier.height(9.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("RECORTE DEL VIDEO SELECCIONADO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(selected?.let { if (it.durationMs > 0) "${"%.1f".format((trimEnd-trimStart)/1000f)} s" else "Sin duración" } ?: "Sin selección", color = Accent, fontSize = 10.sp) }
  if (selected?.kind == "VIDEO" && (selected?.durationMs ?: 0L) > 0L) {
   RangeSlider(value = trimStart.coerceAtMost(trimEnd)..trimEnd.coerceAtLeast(trimStart + 0.01f), onValueChange = { trimStart = it.start; trimEnd = it.endInclusive }, valueRange = 0f..selected!!.durationMs.toFloat(), modifier = Modifier.fillMaxWidth())
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Inicio ${"%.1f".format(trimStart/1000f)} s", color = Color.LightGray, fontSize = 10.sp); Text("Fin ${"%.1f".format(trimEnd/1000f)} s", color = Color.LightGray, fontSize = 10.sp) }
  } else Text("Selecciona un video para ajustar inicio y fin", color = Color.Gray, fontSize = 10.sp)
  Spacer(Modifier.height(9.dp))
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Text("HERRAMIENTAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
   Spacer(Modifier.weight(1f))
   TextButton(onClick = { undoTimeline() }, enabled = undoHistory.isNotEmpty()) {
    Icon(Icons.Default.Undo, null, modifier = Modifier.size(17.dp))
    Spacer(Modifier.width(3.dp)); Text("Deshacer", fontSize = 10.sp)
   }
   TextButton(onClick = { redoTimeline() }, enabled = redoHistory.isNotEmpty()) {
    Icon(Icons.Default.Redo, null, modifier = Modifier.size(17.dp))
    Spacer(Modifier.width(3.dp)); Text("Rehacer", fontSize = 10.sp)
   }
  }
  Spacer(Modifier.height(7.dp))
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   ToolButton("Añadir", Icons.Default.Add) { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }
   ToolButton("Cortar", Icons.Default.ContentCut) { status = "Ajusta inicio y fin del clip seleccionado y exporta el proyecto" }
   ToolButton("Audio", Icons.Default.MusicNote) { picker.launch(arrayOf("audio/*")) }
   ToolButton("Fotos", Icons.Default.Photo) { picker.launch(arrayOf("image/*")) }
   ToolButton("Eliminar", Icons.Default.Delete) { selected?.let { saveTimelineState(); clips.remove(it); selected = clips.firstOrNull { c -> c.kind == "VIDEO" }; trimStart = 0f; trimEnd = selected?.durationMs?.coerceAtLeast(1L)?.toFloat() ?: 1f; status = "Elemento eliminado" } }
  }
  Spacer(Modifier.height(10.dp))
  Text("FILTRO DE VIDEO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
   listOf("Original", "Blanco y negro", "Negativo").forEach { option ->
    FilterChip(
     selected = filterMode == option,
     onClick = { filterMode = option },
     label = { Text(option, fontSize = 10.sp) },
     colors = FilterChipDefaults.filterChipColors(
      selectedContainerColor = Accent,
      selectedLabelColor = Color.White,
      labelColor = Color.LightGray
     )
    )
   }
  }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("VOLUMEN DE VISTA PREVIA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("${(previewVolume * 100).toInt()}%", color = Accent, fontSize = 11.sp) }
  Slider(value = previewVolume, onValueChange = { previewVolume = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
  Text("Este control ajusta únicamente la vista previa.", color = Color.Gray, fontSize = 9.sp)
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Text("VOLUMEN DE MÚSICA EXPORTADA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
   Spacer(Modifier.weight(1f))
   Text("${(exportAudioVolume * 100).toInt()}%", color = Accent, fontSize = 11.sp)
  }
  Slider(value = exportAudioVolume, onValueChange = { exportAudioVolume = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
  Text("Ajusta el nivel de todas las pistas musicales en el video final.", color = Color.Gray, fontSize = 9.sp)
  Text("TÍTULO SOBRE EL VIDEO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  OutlinedTextField(
   value = titleText,
   onValueChange = { titleText = it.take(80) },
   modifier = Modifier.fillMaxWidth(),
   singleLine = true,
   placeholder = { Text("Escribe un título o nombre para tu video") },
   colors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color.Gray,
    cursorColor = Accent
   )
  )
  Text("El título se integrará al exportar el montaje.", color = Color.Gray, fontSize = 9.sp)
  Spacer(Modifier.height(10.dp))
  Row(verticalAlignment = Alignment.CenterVertically) { Text("LÍNEA DE TIEMPO • AJUSTA EL ORDEN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.weight(1f)); Text("${clips.size} elementos", color = Color.Gray, fontSize = 11.sp) }
  Spacer(Modifier.height(6.dp))
  Row(Modifier.fillMaxWidth().height(105.dp).background(Panel, RoundedCornerShape(12.dp)).padding(7.dp)) {
   if (clips.isEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("＋ Agrega videos, fotos o música. Videos y fotos se exportan en orden.", color = Color.Gray, fontSize = 12.sp) }
   else LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { items(clips) { clip ->
    Row(Modifier.fillMaxWidth().background(if (selected == clip) Color(0xFF38234A) else Color(0xFF242434), RoundedCornerShape(7.dp)).clickable { selected = clip; trimStart = 0f; trimEnd = clip.durationMs.coerceAtLeast(1L).toFloat() }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
     Icon(if (clip.kind == "VIDEO") Icons.Default.VideoLibrary else if (clip.kind == "AUDIO") Icons.Default.MusicNote else Icons.Default.Photo, null, tint = Accent)
     Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(clip.name, color = Color.White, fontSize = 11.sp); Text(if (clip.durationMs > 0) "${clip.kind} • ${"%.1f".format(clip.durationMs/1000f)} s" else clip.kind, color = Color.LightGray, fontSize = 9.sp) }
     val position = clips.indexOf(clip)
     IconButton(onClick = { if (position > 0) { saveTimelineState(); clips.removeAt(position); clips.add(position - 1, clip); status = "Orden actualizado" } }, enabled = position > 0, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Subir", tint = if (position > 0) Accent else Color.DarkGray) }
     IconButton(onClick = { if (position < clips.lastIndex) { saveTimelineState(); clips.removeAt(position); clips.add(position + 1, clip); status = "Orden actualizado" } }, enabled = position < clips.lastIndex, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Bajar", tint = if (position < clips.lastIndex) Accent else Color.DarkGray) }
    }
   } }
  }
  Spacer(Modifier.height(7.dp)); Text(status, color = Color(0xFFB9A8C8), fontSize = 10.sp, maxLines = 2)
  Text("Exportación: clips en orden, música en bucle, título y filtro seleccionados. Recorte aplicado al video seleccionado.", color = Color.Gray, fontSize = 9.sp, maxLines = 2)
 }
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
 Column(Modifier.width(68.dp).background(Panel, RoundedCornerShape(12.dp)).clickable { action() }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(5.dp)); Text(label, color = Color.White, fontSize = 10.sp) }
}
