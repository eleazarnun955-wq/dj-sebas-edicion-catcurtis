package com.djsebas.catcurtis

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size as Media3Size
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
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val Bg = Color(0xFF0B0B12)
private val Panel = Color(0xFF171724)
private val Accent = Color(0xFFB64CFF)
private data class Clip(val uri: Uri, val name: String, val kind: String, val durationMs: Long = 0L)
private data class ClipTiming(val startMs: Long, val endMs: Long, val fadeInMs: Long, val fadeOutMs: Long)
private data class EditorSnapshot(
 val clips: List<Clip>,
 val selectedUri: Uri?,
 val trimStart: Float,
 val trimEnd: Float,
 val titleText: String,
 val filterMode: String,
 val previewVolume: Float,
 val exportAudioVolume: Float,
 val photoDurationSeconds: Float,
 val titleScale: Float,
 val titleColor: Int,
 val titleVerticalPosition: Float,
 val videoTrimRanges: Map<Uri, Pair<Long, Long>>,
 val audioTrackVolumes: Map<Uri, Float>,
 val audioTrimRanges: Map<Uri, Pair<Long, Long>>,
 val clipFilters: Map<Uri, String>,
 val clipTransitionDurationsMs: Map<Uri, Float>,
 val transitionDurationMs: Float
)

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
 var projectRestored by remember { mutableStateOf(false) }
 val thumbnails = remember { mutableStateMapOf<Uri, Bitmap>() }
 val undoHistory = remember { mutableStateListOf<EditorSnapshot>() }
 val redoHistory = remember { mutableStateListOf<EditorSnapshot>() }
 var selected by remember { mutableStateOf<Clip?>(null) }
 var trimStart by remember { mutableFloatStateOf(0f) }
 var trimEnd by remember { mutableFloatStateOf(1f) }
 var status by remember { mutableStateOf("Importa videos, fotos o música para comenzar") }
 var titleText by remember { mutableStateOf("") }
 var filterMode by remember { mutableStateOf("Original") }
 var previewVolume by remember { mutableFloatStateOf(1f) }
 var exportAudioVolume by remember { mutableFloatStateOf(1f) }
  var photoDurationSeconds by remember { mutableFloatStateOf(5f) }
 var transitionDurationMs by remember { mutableFloatStateOf(500f) }
 var titleScale by remember { mutableFloatStateOf(1f) }
 var titleColor by remember { mutableIntStateOf(AndroidColor.WHITE) }
 var titleVerticalPosition by remember { mutableFloatStateOf(0.78f) }
 var titlePositionHistorySaved by remember { mutableStateOf(false) }
 var titleScaleHistorySaved by remember { mutableStateOf(false) }
 val audioTrackVolumes = remember { mutableStateMapOf<Uri, Float>() }
 val audioTrimRanges = remember { mutableStateMapOf<Uri, Pair<Long, Long>>() }
 val clipFilters = remember { mutableStateMapOf<Uri, String>() }
 val clipTransitionDurationsMs = remember { mutableStateMapOf<Uri, Float>() }
 val selectedTransitionDurationMs = selected?.uri?.let { clipTransitionDurationsMs[it] } ?: transitionDurationMs
 val selectedFilterMode = selected?.uri?.let { clipFilters[it] } ?: filterMode
 val videoTrimRanges = remember { mutableStateMapOf<Uri, Pair<Long, Long>>() }
 var exporting by remember { mutableStateOf(false) }
 var trimHistorySaved by remember { mutableStateOf(false) }
 var previewVolumeHistorySaved by remember { mutableStateOf(false) }
 var exportVolumeHistorySaved by remember { mutableStateOf(false) }
 var trackVolumeHistorySaved by remember { mutableStateOf(false) }
 var audioTrimHistorySaved by remember { mutableStateOf(false) }
 var photoDurationHistorySaved by remember { mutableStateOf(false) }
 var transitionHistorySaved by remember { mutableStateOf(false) }
 var titleHistorySaved by remember { mutableStateOf(false) }
 val worker = remember { Executors.newSingleThreadExecutor() }
 val mainHandler = remember { Handler(Looper.getMainLooper()) }

 fun captureEditorState() = EditorSnapshot(
  clips.toList(), selected?.uri, trimStart, trimEnd, titleText, filterMode,
  previewVolume, exportAudioVolume, photoDurationSeconds, titleScale, titleColor, titleVerticalPosition,
  videoTrimRanges.toMap(), audioTrackVolumes.toMap(), audioTrimRanges.toMap(), clipFilters.toMap(), clipTransitionDurationsMs.toMap(), transitionDurationMs
 )
 fun saveTimelineState() {
  undoHistory.add(captureEditorState())
  if (undoHistory.size > 30) undoHistory.removeAt(0)
  redoHistory.clear()
 }
 fun restoreTimeline(snapshot: EditorSnapshot) {
  clips.clear(); clips.addAll(snapshot.clips)
  selected = clips.firstOrNull { it.uri == snapshot.selectedUri } ?: clips.firstOrNull { it.kind == "VIDEO" || it.kind == "FOTO" }
  trimStart = snapshot.trimStart; trimEnd = snapshot.trimEnd
  titleText = snapshot.titleText; filterMode = snapshot.filterMode
  previewVolume = snapshot.previewVolume; exportAudioVolume = snapshot.exportAudioVolume
  photoDurationSeconds = snapshot.photoDurationSeconds
  titleScale = snapshot.titleScale
  titleColor = snapshot.titleColor
  titleVerticalPosition = snapshot.titleVerticalPosition
  videoTrimRanges.clear(); videoTrimRanges.putAll(snapshot.videoTrimRanges)
  audioTrackVolumes.clear(); audioTrackVolumes.putAll(snapshot.audioTrackVolumes)
  audioTrimRanges.clear(); audioTrimRanges.putAll(snapshot.audioTrimRanges)
  clipFilters.clear(); clipFilters.putAll(snapshot.clipFilters)
  clipTransitionDurationsMs.clear(); clipTransitionDurationsMs.putAll(snapshot.clipTransitionDurationsMs)
  transitionDurationMs = snapshot.transitionDurationMs
 }
 fun undoTimeline() {
  if (undoHistory.isNotEmpty()) {
   redoHistory.add(captureEditorState())
   val previous = undoHistory.removeAt(undoHistory.lastIndex)
   restoreTimeline(previous)
   status = "Último cambio de edición deshecho"
  }
 }
 fun redoTimeline() {
  if (redoHistory.isNotEmpty()) {
   undoHistory.add(captureEditorState())
   val next = redoHistory.removeAt(redoHistory.lastIndex)
   restoreTimeline(next)
   status = "Cambio de edición restaurado"
  }
 }
 val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
  if (uris.isNotEmpty()) saveTimelineState()
  uris.forEach { uri ->
   runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   val mime = context.contentResolver.getType(uri).orEmpty()
   val kind = when { mime.startsWith("video") -> "VIDEO"; mime.startsWith("audio") -> "AUDIO"; mime.startsWith("image") -> "FOTO"; else -> "ARCHIVO" }
   val duration = if (kind == "VIDEO" || kind == "AUDIO") runCatching {
    val retriever = MediaMetadataRetriever()
    try { retriever.setDataSource(context, uri); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
    finally { retriever.release() }
   }.getOrDefault(0L) else 0L
   val clip = Clip(uri, uri.lastPathSegment?.substringAfterLast('/')?.take(28) ?: "Medio importado", kind, duration)
   clips.add(clip)
   if (kind == "VIDEO") videoTrimRanges[uri] = 0L to duration.coerceAtLeast(1L)
   if (kind == "AUDIO") audioTrimRanges[uri] = 0L to duration.coerceAtLeast(1L)
   if (selected == null && (kind == "VIDEO" || kind == "FOTO")) { selected = clip; trimStart = 0f; trimEnd = if (kind == "VIDEO") duration.coerceAtLeast(1L).toFloat() else 1f }
  }
  val audioDuration = clips.firstOrNull { it.kind == "AUDIO" && it.durationMs > 0L }?.durationMs ?: 0L
  status = if (uris.isEmpty()) "No se seleccionaron archivos" else if (audioDuration > 0L && clips.any { it.kind == "FOTO" } && clips.filter { it.kind == "VIDEO" || it.kind == "FOTO" }.all { it.kind == "FOTO" }) "Audio detectado: las fotos se ajustarán automáticamente a ${"%.1f".format(audioDuration / 1000f)} s al exportar" else "${uris.size} archivo(s) agregados"
 }
 LaunchedEffect(clips.map { it.uri }) {
  val loaded = withContext(Dispatchers.IO) {
   clips.filter { it.kind == "VIDEO" || it.kind == "FOTO" }.mapNotNull { clip ->
    val bitmap = runCatching {
     // Primero solicita una miniatura al proveedor de Android; si falla, usa un decodificador alternativo.
     if (Build.VERSION.SDK_INT >= 29) {
      runCatching { context.contentResolver.loadThumbnail(clip.uri, Size(480, 320), null) }.getOrNull()
     } else null
    }.getOrNull() ?: runCatching {
     if (clip.kind == "VIDEO") {
      val retriever = MediaMetadataRetriever()
      try {
       retriever.setDataSource(context, clip.uri)
       retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      } finally { retriever.release() }
     } else {
      // Decodificación en dos pasos para evitar cargar fotografías enormes completas en memoria.
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(clip.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      val sample = BitmapFactory.Options().apply {
       inSampleSize = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        var factor = 1
        while (bounds.outWidth / factor > 960 || bounds.outHeight / factor > 960) factor *= 2
        factor
       } else 1
      }
      context.contentResolver.openInputStream(clip.uri)?.use { BitmapFactory.decodeStream(it, null, sample) }
     }
    }.getOrNull()
    bitmap?.let { clip.uri to it }
   }
  }
  loaded.forEach { (uri, bitmap) -> thumbnails[uri] = bitmap }
 }
 val player = remember { ExoPlayer.Builder(context).build() }
 LaunchedEffect(previewVolume) { player.volume = previewVolume }
 LaunchedEffect(selected, trimStart, trimEnd, selectedFilterMode) {
  selected?.takeIf { it.kind == "VIDEO" }?.let { clip ->
   val start = trimStart.toLong().coerceAtLeast(0L)
   val end = trimEnd.toLong().coerceAtLeast(start + 1L)
   val item = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()
   ).build()
   player.setVideoEffects(when (selectedFilterMode) {
    "Blanco y negro" -> listOf(RgbFilter.createGrayscaleFilter())
    "Negativo" -> listOf(RgbFilter.createInvertedFilter())
    "Sepia" -> listOf(RgbMatrix { _, _ -> floatArrayOf(0.393f,0.349f,0.272f,0f,0.769f,0.686f,0.534f,0f,0.189f,0.168f,0.131f,0f,0f,0f,0f,1f) })
    "Cálido" -> listOf(RgbMatrix { _, _ -> floatArrayOf(1.12f,0f,0f,0f,0f,1.02f,0f,0f,0f,0f,0.88f,0f,0f,0f,0f,1f) })
    "Frío" -> listOf(RgbMatrix { _, _ -> floatArrayOf(0.88f,0f,0f,0f,0f,1.02f,0f,0f,0f,0f,1.12f,0f,0f,0f,0f,1f) })
    else -> emptyList()
   })
   player.setMediaItem(item); player.prepare()
  } ?: run {
   player.pause()
   player.clearMediaItems()
  }
 }

 DisposableEffect(Unit) { onDispose { player.release(); worker.shutdown() } }

 // Recupera el último proyecto local al abrir la aplicación.
 LaunchedEffect(Unit) {
  val prefs = context.getSharedPreferences("catcurtis_project", android.content.Context.MODE_PRIVATE)
  val raw = prefs.getString("project_json", null)
  if (!raw.isNullOrBlank()) {
   runCatching {
    val json = JSONObject(raw)
    val grantedUris = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
    val restoredClips = mutableListOf<Clip>()
    val items = json.optJSONArray("clips") ?: JSONArray()
    for (i in 0 until items.length()) {
     val item = items.optJSONObject(i) ?: continue
     val uriText = item.optString("uri")
     val kind = item.optString("kind")
     if (uriText.isNotBlank() && uriText in grantedUris && kind in setOf("VIDEO", "FOTO", "AUDIO")) {
      restoredClips.add(Clip(Uri.parse(uriText), item.optString("name", "Medio importado"), kind, item.optLong("durationMs", 0L)))
     }
    }
    clips.clear()
    clips.addAll(restoredClips)
    val selectedText = json.optString("selectedUri")
    val selectedUri = selectedText.takeIf { it.isNotBlank() && it != "null" }?.let { Uri.parse(it) }
    selected = clips.firstOrNull { it.uri == selectedUri } ?: clips.firstOrNull { it.kind == "VIDEO" || it.kind == "FOTO" }
    trimStart = json.optDouble("trimStart", 0.0).toFloat()
    trimEnd = json.optDouble("trimEnd", 1.0).toFloat()
    titleText = json.optString("titleText", "")
    filterMode = json.optString("filterMode", "Original")
    previewVolume = json.optDouble("previewVolume", 1.0).toFloat().coerceIn(0f, 1f)
    exportAudioVolume = json.optDouble("exportAudioVolume", 1.0).toFloat().coerceIn(0f, 1f)
    photoDurationSeconds = json.optDouble("photoDurationSeconds", 5.0).toFloat().coerceIn(1f, 15f)
    transitionDurationMs = json.optDouble("transitionDurationMs", 500.0).toFloat().coerceIn(0f, 2000f)
    titleScale = json.optDouble("titleScale", 1.0).toFloat().coerceIn(0.5f, 2f)
    titleColor = json.optInt("titleColor", AndroidColor.WHITE)
    titleVerticalPosition = json.optDouble("titleVerticalPosition", 0.78).toFloat().coerceIn(0.15f, 0.85f)
    videoTrimRanges.clear()
    val trims = json.optJSONArray("videoTrimRanges") ?: JSONArray()
    for (i in 0 until trims.length()) {
     val item = trims.optJSONObject(i) ?: continue
     val uri = Uri.parse(item.optString("uri"))
     if (clips.any { it.uri == uri }) videoTrimRanges[uri] = item.optLong("start") to item.optLong("end")
    }
    audioTrackVolumes.clear()
    val volumes = json.optJSONArray("audioTrackVolumes") ?: JSONArray()
    for (i in 0 until volumes.length()) {
     val item = volumes.optJSONObject(i) ?: continue
     val uri = Uri.parse(item.optString("uri"))
     if (clips.any { it.uri == uri && it.kind == "AUDIO" }) audioTrackVolumes[uri] = item.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f)
    }
    audioTrimRanges.clear()
    val audioTrims = json.optJSONArray("audioTrimRanges") ?: JSONArray()
    for (i in 0 until audioTrims.length()) {
     val item = audioTrims.optJSONObject(i) ?: continue
     val uri = Uri.parse(item.optString("uri"))
     if (clips.any { it.uri == uri && it.kind == "AUDIO" }) audioTrimRanges[uri] = item.optLong("start") to item.optLong("end")
    }
    clipFilters.clear()
    val savedFilters = json.optJSONArray("clipFilters") ?: JSONArray()
    for (i in 0 until savedFilters.length()) {
     val item = savedFilters.optJSONObject(i) ?: continue
     val uri = Uri.parse(item.optString("uri"))
     if (clips.any { it.uri == uri && (it.kind == "VIDEO" || it.kind == "FOTO") }) clipFilters[uri] = item.optString("filter", "Original")
    }
    clipTransitionDurationsMs.clear()
    val savedTransitions = json.optJSONArray("clipTransitionDurationsMs") ?: JSONArray()
    for (i in 0 until savedTransitions.length()) {
     val item = savedTransitions.optJSONObject(i) ?: continue
     val uri = Uri.parse(item.optString("uri"))
     if (clips.any { it.uri == uri && (it.kind == "VIDEO" || it.kind == "FOTO") }) clipTransitionDurationsMs[uri] = item.optDouble("durationMs", 500.0).toFloat().coerceIn(0f, 2000f)
    }
    status = if (clips.isEmpty()) "Proyecto anterior: algunos archivos ya no tienen permiso" else "Proyecto anterior restaurado • ${clips.size} elementos"
   }.onFailure { status = "No se pudo recuperar el proyecto: ${it.message}" }
  }
  projectRestored = true
 }

 // Guarda los cambios después de una breve pausa para evitar escrituras en cada movimiento del control.
 LaunchedEffect(projectRestored, clips.toList(), selected?.uri, trimStart, trimEnd, titleText, filterMode,
  previewVolume, exportAudioVolume, photoDurationSeconds, transitionDurationMs, titleScale, titleColor, titleVerticalPosition,
  videoTrimRanges.toMap(), audioTrackVolumes.toMap(), audioTrimRanges.toMap(), clipFilters.toMap(), clipTransitionDurationsMs.toMap()) {
  if (projectRestored) {
   delay(350)
   val json = JSONObject()
   val items = JSONArray()
   clips.forEach { clip ->
    items.put(JSONObject().put("uri", clip.uri.toString()).put("name", clip.name).put("kind", clip.kind).put("durationMs", clip.durationMs))
   }
   json.put("clips", items)
   json.put("selectedUri", selected?.uri?.toString())
   json.put("trimStart", trimStart.toDouble())
   json.put("trimEnd", trimEnd.toDouble())
   json.put("titleText", titleText)
   json.put("filterMode", filterMode)
   json.put("previewVolume", previewVolume.toDouble())
   json.put("exportAudioVolume", exportAudioVolume.toDouble())
   json.put("photoDurationSeconds", photoDurationSeconds.toDouble())
   json.put("transitionDurationMs", transitionDurationMs.toDouble())
   json.put("titleScale", titleScale.toDouble())
   json.put("titleColor", titleColor)
   json.put("titleVerticalPosition", titleVerticalPosition.toDouble())
   val trims = JSONArray()
   videoTrimRanges.forEach { (uri, range) -> trims.put(JSONObject().put("uri", uri.toString()).put("start", range.first).put("end", range.second)) }
   json.put("videoTrimRanges", trims)
   val volumes = JSONArray()
   audioTrackVolumes.forEach { (uri, volume) -> volumes.put(JSONObject().put("uri", uri.toString()).put("volume", volume.toDouble())) }
   json.put("audioTrackVolumes", volumes)
   val audioTrims = JSONArray()
   audioTrimRanges.forEach { (uri, range) -> audioTrims.put(JSONObject().put("uri", uri.toString()).put("start", range.first).put("end", range.second)) }
   json.put("audioTrimRanges", audioTrims)
   val filters = JSONArray()
   clipFilters.forEach { (uri, filter) -> filters.put(JSONObject().put("uri", uri.toString()).put("filter", filter)) }
   json.put("clipFilters", filters)
   val transitions = JSONArray()
   clipTransitionDurationsMs.forEach { (uri, duration) -> transitions.put(JSONObject().put("uri", uri.toString()).put("durationMs", duration.toDouble())) }
   json.put("clipTransitionDurationsMs", transitions)
   context.getSharedPreferences("catcurtis_project", android.content.Context.MODE_PRIVATE).edit().putString("project_json", json.toString()).apply()
  }
 }

 fun exportVideo() {
  val visualClips = clips.filter { it.kind == "VIDEO" || it.kind == "FOTO" }
  if (visualClips.isEmpty()) { status = "Importa al menos un video o una foto"; return }
  val invalidTrim = visualClips.filter { it.kind == "VIDEO" }.any { video ->
   val range = videoTrimRanges[video.uri] ?: (0L to video.durationMs)
   video.durationMs <= 0L || range.second <= range.first || range.second > video.durationMs
  }
  if (invalidTrim) { status = "Hay un video con un rango de recorte no válido"; return }
  exporting = true
  status = "Preparando ${visualClips.size} clip(s) de video/foto… no cierres la aplicación"
  val output = File(context.cacheDir, "catcurtis_${System.currentTimeMillis()}.mp4")
  val sequenceItems = mutableListOf<EditedMediaItem>()
  val firstAudio = clips.firstOrNull { it.kind == "AUDIO" && it.durationMs > 0L }
  val firstAudioRange = firstAudio?.let { audioTrimRanges[it.uri] ?: (0L to it.durationMs) }
  val audioDurationMs = firstAudioRange?.let { (it.second - it.first).coerceAtLeast(0L) } ?: 0L
  val photoCount = visualClips.count { it.kind == "FOTO" }
  fun slideshowDuration(photoMs: Long): Long {
   val durations = visualClips.map { clip ->
    if (clip.kind == "FOTO") photoMs.coerceAtLeast(1L)
    else {
     val range = videoTrimRanges[clip.uri] ?: (0L to clip.durationMs)
     (range.second - range.first).coerceAtLeast(1L)
    }
   }
   val overlaps = (0 until (durations.size - 1)).sumOf { index ->
    minOf((clipTransitionDurationsMs[visualClips[index].uri] ?: transitionDurationMs).toLong().coerceAtLeast(0L), durations[index] / 2L, durations[index + 1] / 2L)
   }
   return durations.sum() - overlaps
  }
  val autoPhotoDurationMs = if (audioDurationMs > 0L && photoCount > 0) {
   var low = 1L
   var high = (audioDurationMs + visualClips.sumOf { clip ->
    if (clip.kind == "VIDEO") {
     val range = videoTrimRanges[clip.uri] ?: (0L to clip.durationMs)
     (range.second - range.first).coerceAtLeast(1L)
    } else 0L
   } + 500L * visualClips.size).coerceAtLeast(1L)
   while (low < high) {
    val middle = low + (high - low) / 2L
    if (slideshowDuration(middle) < audioDurationMs) low = middle + 1L else high = middle
   }
   listOf(low, (low - 1L).coerceAtLeast(1L)).minBy { kotlin.math.abs(slideshowDuration(it) - audioDurationMs) }
  } else (photoDurationSeconds * 1000f).toLong().coerceAtLeast(1L)
  val clipDurationsMs = visualClips.map { video ->
   if (video.kind == "FOTO") autoPhotoDurationMs
   else {
    val range = videoTrimRanges[video.uri] ?: (0L to video.durationMs)
    (range.second - range.first).coerceAtLeast(1L)
   }
  }
  val overlapDurationsMs = (0 until (visualClips.size - 1)).map { index ->
   minOf((clipTransitionDurationsMs[visualClips[index].uri] ?: transitionDurationMs).toLong().coerceAtLeast(0L), clipDurationsMs[index] / 2L, clipDurationsMs[index + 1] / 2L)
  }
  val clipTimings = mutableListOf<ClipTiming>()
  var timelineEndMs = 0L
  visualClips.forEachIndexed { index, video ->
   val mediaBuilder = MediaItem.Builder().setUri(video.uri)
   if (video.kind == "FOTO") mediaBuilder.setImageDurationMs(autoPhotoDurationMs)
   if (video.kind == "VIDEO") {
    val range = videoTrimRanges[video.uri] ?: (0L to video.durationMs)
    mediaBuilder.setClippingConfiguration(
     MediaItem.ClippingConfiguration.Builder().setStartPositionMs(range.first).setEndPositionMs(range.second).build()
    )
   }
   val editedBuilder = EditedMediaItem.Builder(mediaBuilder.build())
   val clipFilterMode = clipFilters[video.uri] ?: filterMode
   val videoEffects: List<androidx.media3.common.Effect> = when (clipFilterMode) {
    "Blanco y negro" -> listOf(RgbFilter.createGrayscaleFilter())
    "Negativo" -> listOf(RgbFilter.createInvertedFilter())
    "Sepia" -> listOf(RgbMatrix { _, _ -> floatArrayOf(0.393f,0.349f,0.272f,0f,0.769f,0.686f,0.534f,0f,0.189f,0.168f,0.131f,0f,0f,0f,0f,1f) })
    "Cálido" -> listOf(RgbMatrix { _, _ -> floatArrayOf(1.12f,0f,0f,0f,0f,1.02f,0f,0f,0f,0f,0.88f,0f,0f,0f,0f,1f) })
    "Frío" -> listOf(RgbMatrix { _, _ -> floatArrayOf(0.88f,0f,0f,0f,0f,1.02f,0f,0f,0f,0f,1.12f,0f,0f,0f,0f,1f) })
    else -> emptyList()
   }
   val overlays = if (titleText.isNotBlank()) {
    val styledTitle = SpannableString(titleText.trim()).apply {
     setSpan(ForegroundColorSpan(titleColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
     setSpan(RelativeSizeSpan(titleScale), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val titleSettings = StaticOverlaySettings.Builder()
     .setOverlayFrameAnchor(0f, titleVerticalPosition)
     .setBackgroundFrameAnchor(0f, titleVerticalPosition)
     .build()
    listOf(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(styledTitle, titleSettings))))
   } else emptyList()
   if (videoEffects.isNotEmpty() || overlays.isNotEmpty()) {
    editedBuilder.setEffects(Effects(emptyList(), videoEffects + overlays))
   }
   val fadeInMs = if (index > 0) overlapDurationsMs[index - 1] else 0L
   val fadeOutMs = if (index < visualClips.lastIndex) overlapDurationsMs[index] else 0L
   val startMs = if (index == 0) 0L else (timelineEndMs - fadeInMs).coerceAtLeast(0L)
   val endMs = startMs + clipDurationsMs[index]
   clipTimings.add(ClipTiming(startMs, endMs, fadeInMs, fadeOutMs))
   timelineEndMs = endMs
   sequenceItems.add(editedBuilder.build())
  }
  val videoSequences = sequenceItems.mapIndexed { index, item ->
   val timing = clipTimings[index]
   val sequenceBuilder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
   if (timing.startMs > 0L) sequenceBuilder.addGap(timing.startMs * 1000L)
   sequenceBuilder.addItem(item)
   if (timelineEndMs > timing.endMs) sequenceBuilder.addGap((timelineEndMs - timing.endMs) * 1000L)
   sequenceBuilder.build()
  }
  val audioSequences = clips.filter { it.kind == "AUDIO" }.map { audioClip ->
   val audioProcessor = ChannelMixingAudioProcessor()
   for (channelCount in 1..6) {
    audioProcessor.putChannelMixingMatrix(
     ChannelMixingMatrix.createForConstantPower(channelCount, channelCount).scaleBy(exportAudioVolume * (audioTrackVolumes[audioClip.uri] ?: 1f))
    )
   }
   val audioRange = audioTrimRanges[audioClip.uri] ?: (0L to audioClip.durationMs)
   val audioMedia = MediaItem.Builder().setUri(audioClip.uri).setClippingConfiguration(
    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(audioRange.first.coerceAtLeast(0L)).setEndPositionMs(audioRange.second.coerceAtLeast(audioRange.first + 1L)).build()
   ).build()
   val audioItem = EditedMediaItem.Builder(audioMedia)
    .setEffects(Effects(listOf(audioProcessor), emptyList()))
    .build()
   EditedMediaItemSequence.Builder(audioItem).setIsLooping(true).build()
  }
  val compositorSettings = object : VideoCompositorSettings {
   override fun getOutputSize(inputSizes: List<Media3Size>): Media3Size = inputSizes.first()
   override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
    val timing = clipTimings.getOrNull(inputId) ?: return object : OverlaySettings {}
    val fadeIn = if (timing.fadeInMs > 0L) {
     ((presentationTimeUs - timing.startMs * 1000L).toFloat() / (timing.fadeInMs * 1000L)).coerceIn(0f, 1f)
    } else 1f
    val fadeOut = if (timing.fadeOutMs > 0L) {
     ((timing.endMs * 1000L - presentationTimeUs).toFloat() / (timing.fadeOutMs * 1000L)).coerceIn(0f, 1f)
    } else 1f
    // La pista entrante gana opacidad sobre la anterior; la anterior permanece debajo para evitar fundidos a negro.
    val alpha = fadeIn
    return object : OverlaySettings { override fun getAlphaScale(): Float = alpha }
   }
  }
  val composition = Composition.Builder(videoSequences + audioSequences)
   .setVideoCompositorSettings(compositorSettings)
   .build()
  val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
   override fun onCompleted(composition: Composition, exportResult: ExportResult) {
    worker.execute {
     var savedUri: Uri? = null
     try {
      val values = ContentValues().apply {
       put(MediaStore.Video.Media.DISPLAY_NAME, "DJ_SEBAS_CATCURTIS_${System.currentTimeMillis()}.mp4")
       put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
       put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DJ SEBAS CATCURTIS")
      }
      val target = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("No se pudo crear el archivo")
      savedUri = target
      context.contentResolver.openOutputStream(target)?.use { out -> output.inputStream().use { it.copyTo(out) } } ?: error("No se pudo guardar")
      output.delete()
      savedUri = null
      mainHandler.post { exporting = false; status = "¡Listo! Video guardado en Películas/DJ SEBAS CATCURTIS" }
     } catch (e: Exception) {
      savedUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
      output.delete()
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
   output.delete()
   exporting = false; status = "No se pudo iniciar: ${it.message}"
  }
 }

 Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(14.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
   Column(Modifier.weight(1f)) { Text("DJ SEBAS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("EDICIÓN CATCURTIS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
   Button(onClick = { exportVideo() }, enabled = !exporting, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
    if (exporting) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.SaveAlt, null)
    Spacer(Modifier.width(5.dp)); Text(if (exporting) "Exportando" else "Exportar proyecto")
   }
  }
  Spacer(Modifier.height(12.dp))
  Box(Modifier.fillMaxWidth().height(220.dp).background(Color.Black, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
   when (selected?.kind) {
    "VIDEO" -> AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
    "FOTO" -> {
     val photoBitmap = selected?.uri?.let { thumbnails[it] }
     if (photoBitmap != null) {
      Image(
       bitmap = photoBitmap.asImageBitmap(),
       contentDescription = "Vista previa de la fotografía seleccionada",
       modifier = Modifier.fillMaxSize(),
       contentScale = androidx.compose.ui.layout.ContentScale.Fit,
       colorFilter = when (selectedFilterMode) {
        "Blanco y negro" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(0.213f,0.715f,0.072f,0f,0f,0.213f,0.715f,0.072f,0f,0f,0.213f,0.715f,0.072f,0f,0f,0f,0f,0f,1f,0f)))
        "Negativo" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f,0f,-1f,0f,0f,255f,0f,0f,-1f,0f,255f,0f,0f,0f,1f,0f)))
        "Sepia" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(0.393f,0.769f,0.189f,0f,0f,0.349f,0.686f,0.168f,0f,0f,0.272f,0.534f,0.131f,0f,0f,0f,0f,0f,1f,0f)))
        "Cálido" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(1.12f,0f,0f,0f,0f,0f,1.02f,0f,0f,0f,0f,0f,0.88f,0f,0f,0f,0f,0f,1f,0f)))
        "Frío" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(0.88f,0f,0f,0f,0f,0f,1.02f,0f,0f,0f,0f,0f,1.12f,0f,0f,0f,0f,0f,1f,0f)))
        else -> null
       }
      )
     } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
       CircularProgressIndicator(color = Accent, modifier = Modifier.size(34.dp))
       Spacer(Modifier.height(8.dp))
       Text("CARGANDO FOTO…", color = Color.LightGray, fontWeight = FontWeight.Bold)
      }
     }
    }
    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
     Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.size(58.dp))
     Text("VISTA PREVIA", color = Color.LightGray, fontWeight = FontWeight.Bold)
     Text("Selecciona un video o una foto de tu biblioteca", color = Color.Gray, fontSize = 11.sp)
    }
   }
   if (titleText.isNotBlank()) {
    val titleAlignment = when {
     titleVerticalPosition < 0.4f -> Alignment.TopCenter
     titleVerticalPosition > 0.65f -> Alignment.BottomCenter
     else -> Alignment.Center
    }
    Text(
     text = titleText,
     color = Color(titleColor),
     fontSize = (28f * titleScale).sp,
     fontWeight = FontWeight.Bold,
     modifier = Modifier.align(titleAlignment)
      .padding(12.dp)
      .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
      .padding(horizontal = 12.dp, vertical = 7.dp)
    )
   }
  }
  Spacer(Modifier.height(9.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("RECORTE DEL VIDEO SELECCIONADO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(selected?.let { if (it.durationMs > 0) "${"%.1f".format((trimEnd-trimStart)/1000f)} s" else "Sin duración" } ?: "Sin selección", color = Accent, fontSize = 10.sp) }
  if (selected?.kind == "VIDEO" && (selected?.durationMs ?: 0L) > 0L) {
   RangeSlider(value = trimStart.coerceAtMost(trimEnd)..trimEnd.coerceAtLeast(trimStart + 0.01f), onValueChange = {
    if (!trimHistorySaved) { saveTimelineState(); trimHistorySaved = true }
    trimStart = it.start; trimEnd = it.endInclusive
    selected?.let { current -> videoTrimRanges[current.uri] = trimStart.toLong() to trimEnd.toLong() }
   }, valueRange = 0f..selected!!.durationMs.toFloat(), modifier = Modifier.fillMaxWidth(), onValueChangeFinished = { trimHistorySaved = false })
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
   ToolButton("Confirmar corte", Icons.Default.ContentCut) {
    val current = selected
    if (current?.kind == "VIDEO") {
     val start = trimStart.toLong().coerceAtLeast(0L)
     val end = trimEnd.toLong().coerceAtMost(current.durationMs).coerceAtLeast(start + 1L)
     if (end > start) {
      videoTrimRanges[current.uri] = start to end
      status = "Recorte confirmado: se usará al exportar"
     } else status = "Selecciona un rango válido para recortar"
    } else status = "Selecciona primero un video y ajusta inicio y fin"
   }
   ToolButton("Audio", Icons.Default.MusicNote) { picker.launch(arrayOf("audio/*")) }
   ToolButton("Fotos", Icons.Default.Photo) { picker.launch(arrayOf("image/*")) }
   ToolButton("Eliminar", Icons.Default.Delete) { selected?.let { removed -> saveTimelineState(); clips.remove(removed); videoTrimRanges.remove(removed.uri); audioTrackVolumes.remove(removed.uri); audioTrimRanges.remove(removed.uri); clipFilters.remove(removed.uri); clipTransitionDurationsMs.remove(removed.uri); selected = clips.firstOrNull { c -> c.kind == "VIDEO" || c.kind == "FOTO" }; val range = selected?.let { videoTrimRanges[it.uri] }; trimStart = (range?.first ?: 0L).toFloat(); trimEnd = (range?.second ?: selected?.durationMs?.takeIf { it > 0L } ?: 1L).toFloat(); status = "Elemento eliminado" } }
  }
  Spacer(Modifier.height(10.dp))
  Text("FILTROS DE FOTO Y VIDEO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
   listOf("Original", "Blanco y negro", "Negativo", "Sepia", "Cálido", "Frío").forEach { option ->
    FilterChip(
     selected = selectedFilterMode == option,
     onClick = { if (selectedFilterMode != option) { saveTimelineState(); val current = selected; if (current != null && (current.kind == "VIDEO" || current.kind == "FOTO")) clipFilters[current.uri] = option else filterMode = option } },
     label = { Text(option, fontSize = 10.sp) },
     colors = FilterChipDefaults.filterChipColors(
      selectedContainerColor = Accent,
      selectedLabelColor = Color.White,
      labelColor = Color.LightGray
     )
    )
   }
  }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Text("DURACIÓN DE TRANSICIÓN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
   Spacer(Modifier.weight(1f))
   Text(if (selectedTransitionDurationMs == 0f) "Corte" else "${"%.1f".format(selectedTransitionDurationMs / 1000f)} s", color = Accent, fontSize = 11.sp)
  }
  Slider(value = selectedTransitionDurationMs, onValueChange = { value -> if (!transitionHistorySaved) { saveTimelineState(); transitionHistorySaved = true }; val current = selected; if (current != null && (current.kind == "VIDEO" || current.kind == "FOTO")) clipTransitionDurationsMs[current.uri] = value else transitionDurationMs = value }, valueRange = 0f..2000f, steps = 19, modifier = Modifier.fillMaxWidth(), onValueChangeFinished = { transitionHistorySaved = false })
  Text(if (selected?.kind == "VIDEO" || selected?.kind == "FOTO") "Transición desde el clip seleccionado hacia el siguiente; hasta 2 segundos." else "Duración general de transición para los clips sin ajuste individual.", color = Color.Gray, fontSize = 9.sp)
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("VOLUMEN DE VISTA PREVIA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("${(previewVolume * 100).toInt()}%", color = Accent, fontSize = 11.sp) }
  Slider(value = previewVolume, onValueChange = { if (!previewVolumeHistorySaved) { saveTimelineState(); previewVolumeHistorySaved = true }; previewVolume = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth(), onValueChangeFinished = { previewVolumeHistorySaved = false })
  Text("Este control ajusta únicamente la vista previa.", color = Color.Gray, fontSize = 9.sp)
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Text("VOLUMEN DE MÚSICA EXPORTADA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
   Spacer(Modifier.weight(1f))
   Text("${(exportAudioVolume * 100).toInt()}%", color = Accent, fontSize = 11.sp)
  }
  Slider(value = exportAudioVolume, onValueChange = { if (!exportVolumeHistorySaved) { saveTimelineState(); exportVolumeHistorySaved = true }; exportAudioVolume = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth(), onValueChangeFinished = { exportVolumeHistorySaved = false })
  Text("Ajusta el nivel general de todas las pistas musicales.", color = Color.Gray, fontSize = 9.sp)
  if (selected?.kind == "AUDIO") {
   val selectedAudio = selected!!
   val trackVolume = audioTrackVolumes[selectedAudio.uri] ?: 1f
   Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("VOLUMEN DE ESTA PISTA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Spacer(Modifier.weight(1f))
    Text("${(trackVolume * 100).toInt()}%", color = Accent, fontSize = 11.sp)
   }
   Slider(
    value = trackVolume,
    onValueChange = { if (!trackVolumeHistorySaved) { saveTimelineState(); trackVolumeHistorySaved = true }; audioTrackVolumes[selectedAudio.uri] = it },
    valueRange = 0f..1f,
    modifier = Modifier.fillMaxWidth(),
    onValueChangeFinished = { trackVolumeHistorySaved = false }
   )
   Text("Selecciona una pista de audio en la línea de tiempo para ajustar su volumen por separado.", color = Color.Gray, fontSize = 9.sp)
   if (selectedAudio.durationMs > 0L) {
    val audioRange = audioTrimRanges[selectedAudio.uri] ?: (0L to selectedAudio.durationMs)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     Text("RECORTE DE ESTA PISTA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
     Spacer(Modifier.weight(1f))
     Text("${"%.1f".format((audioRange.second - audioRange.first) / 1000f)} s", color = Accent, fontSize = 11.sp)
    }
    RangeSlider(value = audioRange.first.toFloat().coerceAtLeast(0f)..audioRange.second.toFloat().coerceAtMost(selectedAudio.durationMs.toFloat()), onValueChange = { range ->
     if (!audioTrimHistorySaved) { saveTimelineState(); audioTrimHistorySaved = true }
     audioTrimRanges[selectedAudio.uri] = range.start.toLong() to range.endInclusive.toLong()
    }, valueRange = 0f..selectedAudio.durationMs.toFloat(), modifier = Modifier.fillMaxWidth(), onValueChangeFinished = { audioTrimHistorySaved = false })
    Text("Recorta el inicio y el final de esta canción antes de exportar.", color = Color.Gray, fontSize = 9.sp)
   }
  }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Text("DURACIÓN DE CADA FOTO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
   Spacer(Modifier.weight(1f))
   Text("${photoDurationSeconds.toInt()} s", color = Accent, fontSize = 11.sp)
  }
  Slider(
   value = photoDurationSeconds,
   onValueChange = { if (!photoDurationHistorySaved) { saveTimelineState(); photoDurationHistorySaved = true }; photoDurationSeconds = it },
   valueRange = 1f..15f,
   steps = 13,
   modifier = Modifier.fillMaxWidth(),
   onValueChangeFinished = { photoDurationHistorySaved = false }
  )
  val hasAutoPhotoAudio = clips.any { it.kind == "AUDIO" && it.durationMs > 0L } && clips.any { it.kind == "FOTO" }
  Text(
   if (hasAutoPhotoAudio) "AJUSTE AUTOMÁTICO ACTIVO: las fotos se distribuyen para que el montaje coincida lo más posible con la duración de la música, incluso si mezclas videos."
   else "Elige cuánto tiempo aparece cada fotografía al exportar. Importa una canción para ajustar automáticamente la duración de tus fotos.",
   color = if (hasAutoPhotoAudio) Accent else Color.Gray,
   fontSize = 9.sp
  )
  Text("TÍTULO SOBRE EL VIDEO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  OutlinedTextField(
   value = titleText,
   onValueChange = { value ->
    if (value != titleText && !titleHistorySaved) { saveTimelineState(); titleHistorySaved = true }
    titleText = value.take(80)
   },
   modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) titleHistorySaved = false },
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
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Text("TAMAÑO DEL TÍTULO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
   Spacer(Modifier.weight(1f))
   Text("${(titleScale * 100).toInt()}%", color = Accent, fontSize = 11.sp)
  }
  Slider(
   value = titleScale,
   onValueChange = { if (!titleScaleHistorySaved) { saveTimelineState(); titleScaleHistorySaved = true }; titleScale = it },
   valueRange = 0.5f..2f,
   steps = 5,
   modifier = Modifier.fillMaxWidth(),
   onValueChangeFinished = { titleScaleHistorySaved = false }
  )
  Text("Ajusta el tamaño del texto superpuesto al video.", color = Color.Gray, fontSize = 9.sp)
  Text("COLOR DEL TÍTULO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
   listOf("Blanco" to AndroidColor.WHITE, "Amarillo" to AndroidColor.YELLOW, "Rojo" to AndroidColor.RED, "Cian" to AndroidColor.CYAN, "Verde" to AndroidColor.GREEN).forEach { (label, value) ->
    Column(Modifier.clickable { if (titleColor != value) { saveTimelineState(); titleColor = value } }, horizontalAlignment = Alignment.CenterHorizontally) {
     Box(Modifier.size(25.dp).background(Color(value), RoundedCornerShape(50)).padding(2.dp), contentAlignment = Alignment.Center) {
      if (titleColor == value) Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
     }
     Text(label, color = Color.LightGray, fontSize = 8.sp)
    }
   }
  }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Text("POSICIÓN VERTICAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
   Spacer(Modifier.weight(1f))
   Text(if (titleVerticalPosition < 0.4f) "Arriba" else if (titleVerticalPosition > 0.65f) "Abajo" else "Centro", color = Accent, fontSize = 11.sp)
  }
  Slider(
   value = titleVerticalPosition,
   onValueChange = { if (!titlePositionHistorySaved) { saveTimelineState(); titlePositionHistorySaved = true }; titleVerticalPosition = it },
   valueRange = 0.15f..0.85f,
   modifier = Modifier.fillMaxWidth(),
   onValueChangeFinished = { titlePositionHistorySaved = false }
  )
  Text("Mueve el título entre la parte superior, el centro y la parte inferior del video.", color = Color.Gray, fontSize = 9.sp)
  Spacer(Modifier.height(10.dp))
  Row(verticalAlignment = Alignment.CenterVertically) { Text("LÍNEA DE TIEMPO • AJUSTA EL ORDEN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.weight(1f)); Text("${clips.size} elementos", color = Color.Gray, fontSize = 11.sp) }
  Spacer(Modifier.height(6.dp))
  // Vista de montaje multipista: separa visualmente los clips y la música.
  val visualTrack = clips.filter { it.kind == "VIDEO" || it.kind == "FOTO" }
  val audioTrack = clips.filter { it.kind == "AUDIO" }
  Text("VIDEO Y FOTOGRAFÍAS", color = Color(0xFFBDA0FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(78.dp).background(Color(0xFF11111B), RoundedCornerShape(9.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
   if (visualTrack.isEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Agrega fotos o videos para crear tu montaje", color = Color.Gray, fontSize = 10.sp) }
   visualTrack.forEachIndexed { index, clip ->
    val durationSeconds = if (clip.kind == "FOTO") photoDurationSeconds else {
     val range = videoTrimRanges[clip.uri] ?: (0L to clip.durationMs)
     ((range.second - range.first).coerceAtLeast(1000L) / 1000f)
    }
    val blockWidth = (durationSeconds * 17f).coerceIn(76f, 190f)
    Box(Modifier.width(blockWidth.dp).fillMaxHeight().pointerInput(clip.uri, visualTrack.map { it.uri }) {
     var dragDistance = 0f
     detectDragGestures(onDrag = { change, dragAmount ->
      change.consume()
      dragDistance += dragAmount.x
      if (dragDistance > 34f || dragDistance < -34f) {
       val fromVisual = visualTrack.indexOfFirst { it.uri == clip.uri }
       val toVisual = (fromVisual + if (dragDistance > 0f) 1 else -1).coerceIn(0, visualTrack.lastIndex)
       if (fromVisual != toVisual) {
        saveTimelineState()
        val movingIndex = clips.indexOfFirst { it.uri == clip.uri }
        val targetIndex = clips.indexOfFirst { it.uri == visualTrack[toVisual].uri }
        val moving = clips.removeAt(movingIndex)
        clips.add(if (movingIndex < targetIndex) targetIndex - 1 else targetIndex, moving)
        status = "Orden de la línea de tiempo actualizado"
       }
       dragDistance = 0f
      }
     })
    }.background(if (selected?.uri == clip.uri) Color(0xFF59347A) else Color(0xFF30213F), RoundedCornerShape(6.dp)).clickable {
     selected = clip
     val range = videoTrimRanges[clip.uri] ?: (0L to clip.durationMs.coerceAtLeast(1L))
     trimStart = range.first.toFloat(); trimEnd = range.second.toFloat()
    }.padding(4.dp)) {
     val thumb = thumbnails[clip.uri]
     if (thumb != null) Image(bitmap = thumb.asImageBitmap(), contentDescription = clip.name, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
     Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
      Text("${index + 1}. ${clip.kind}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      Text("${"%.1f".format(durationSeconds)} s", color = Color.White, fontSize = 8.sp)
     }
    }
   }
  }
  Text("MÚSICA Y AUDIO", color = Color(0xFF83D8B5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(54.dp).background(Color(0xFF11111B), RoundedCornerShape(9.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
   if (audioTrack.isEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Agrega una canción o pista de audio", color = Color.Gray, fontSize = 10.sp) }
   audioTrack.forEachIndexed { index, clip ->
    val blockWidth = ((clip.durationMs / 1000f) * 13f).coerceIn(90f, 240f)
    Row(Modifier.width(blockWidth.dp).fillMaxHeight().background(if (selected?.uri == clip.uri) Color(0xFF205D4A) else Color(0xFF174735), RoundedCornerShape(6.dp)).clickable { selected = clip }.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
     Icon(Icons.Default.MusicNote, null, tint = Color(0xFF9AF0C8), modifier = Modifier.size(18.dp))
     Spacer(Modifier.width(5.dp))
     Column(Modifier.weight(1f)) {
      Text("${index + 1}. ${clip.name}", color = Color.White, fontSize = 9.sp, maxLines = 1)
      Text("${"%.1f".format(clip.durationMs / 1000f)} s • ${((audioTrackVolumes[clip.uri] ?: 1f) * 100).toInt()}%", color = Color(0xFFB6E8D2), fontSize = 8.sp)
     }
    }
   }
  }
  Text("Selecciona cualquier bloque para editarlo; usa la lista inferior para cambiar el orden de los clips.", color = Color.Gray, fontSize = 9.sp)
  Row(Modifier.fillMaxWidth().height(105.dp).background(Panel, RoundedCornerShape(12.dp)).padding(7.dp)) {
   if (clips.isEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("＋ Agrega videos, fotos o música. Videos y fotos se exportan en orden.", color = Color.Gray, fontSize = 12.sp) }
   else LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) { items(clips) { clip ->
    Row(Modifier.fillMaxWidth().pointerInput(clip.uri, clips.size) { var dragDistance = 0f; detectDragGestures(onDrag = { change, dragAmount -> change.consume(); dragDistance += dragAmount.y; if (dragDistance > 32f) { val from = clips.indexOfFirst { it.uri == clip.uri }; if (from in 0 until clips.lastIndex) { saveTimelineState(); val moving = clips.removeAt(from); clips.add(from + 1, moving); status = "Clip movido hacia abajo"; }; dragDistance = 0f } else if (dragDistance < -32f) { val from = clips.indexOfFirst { it.uri == clip.uri }; if (from > 0) { saveTimelineState(); val moving = clips.removeAt(from); clips.add(from - 1, moving); status = "Clip movido hacia arriba"; }; dragDistance = 0f } }) }.background(if (selected == clip) Color(0xFF38234A) else Color(0xFF242434), RoundedCornerShape(7.dp)).clickable {
     selected = clip
     val range = videoTrimRanges[clip.uri] ?: (0L to clip.durationMs.coerceAtLeast(1L))
     trimStart = range.first.toFloat(); trimEnd = range.second.toFloat()
    }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
     val thumb = thumbnails[clip.uri]
     if (thumb != null && (clip.kind == "VIDEO" || clip.kind == "FOTO")) {
      Image(bitmap = thumb.asImageBitmap(), contentDescription = "Miniatura de ${clip.name}", modifier = Modifier.size(width = 54.dp, height = 40.dp))
     } else {
      Icon(if (clip.kind == "VIDEO") Icons.Default.VideoLibrary else if (clip.kind == "AUDIO") Icons.Default.MusicNote else Icons.Default.Photo, null, tint = Accent, modifier = Modifier.size(30.dp))
     }
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
