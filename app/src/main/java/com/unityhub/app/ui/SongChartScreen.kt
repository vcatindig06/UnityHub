package com.unityhub.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.unityhub.app.data.SongDto
import com.unityhub.app.domain.chordpro.ChordProLine
import com.unityhub.app.domain.chordpro.ChordProParser
import com.unityhub.app.domain.chordpro.ChordProDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.withContext
import com.unityhub.app.domain.chordpro.ChordTransposer
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongChartScreen(
    song: SongDto,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var transpose by rememberSaveable(song.id) { mutableIntStateOf(0) }
    var chartScale by rememberSaveable(song.id) { mutableFloatStateOf(1f) }
    var performanceSettingsExpanded by rememberSaveable(song.id) { mutableStateOf(false) }
    var autoScrollEnabled by rememberSaveable(song.id) { mutableStateOf(false) }
    var autoScrollSpeed by rememberSaveable(song.id) {
        mutableFloatStateOf(ChartAutoScrollRules.DEFAULT_SPEED_DP_PER_SECOND)
    }
    // Do not restore a scroll index from a different or previously opened chart.
    // Saved-song screens can contain very different row counts.
    val listState = remember(song.id) { LazyListState() }
    val density = LocalDensity.current
    val context = LocalContext.current

    LaunchedEffect(autoScrollEnabled, autoScrollSpeed, song.id) {
        if (autoScrollEnabled) {
            val pixelsPerSecond = with(density) { autoScrollSpeed.dp.toPx() }
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val elapsedSeconds = ((frame - previousFrame) / 1_000_000_000f)
                    .coerceIn(0f, 0.05f)
                listState.scrollBy(pixelsPerSecond * elapsedSeconds)
                previousFrame = frame
                if (!listState.canScrollForward) {
                    autoScrollEnabled = false
                    break
                }
            }
        }
    }
    var document by remember(song.id, song.chordpro) {
        mutableStateOf<ChordProDocument?>(null)
    }
    var isParsingDocument by remember(song.id, song.chordpro) {
        mutableStateOf(song.chordpro.isNotBlank())
    }
    var documentParseFailed by remember(song.id, song.chordpro) {
        mutableStateOf(false)
    }

    LaunchedEffect(song.id, song.chordpro) {
        document = null
        documentParseFailed = false
        if (song.chordpro.isBlank()) {
            isParsingDocument = false
            return@LaunchedEffect
        }

        isParsingDocument = true
        try {
            document = withContext(Dispatchers.Default) {
                require(song.chordpro.length <= 1_048_576) {
                    "The saved chart is larger than UnityHub can display."
                }
                ChordProParser.parse(song.chordpro)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // ExceptionInInitializerError and NoClassDefFoundError are Errors,
            // not Exceptions. They must not escape this screen and crash the app.
            // Keep cancellation handling above this branch.
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
            documentParseFailed = true
        } finally {
            isParsingDocument = false
        }
    }

    val originalKey = document?.key?.takeIf(String::isNotBlank) ?: song.keySignature
    val displayKey = ChordTransposer.transposeKey(originalKey, transpose)
    val displayTempo = song.tempo ?: document?.tempo
    val displayTimeSignature = song.timeSignature.takeIf(String::isNotBlank)
        ?: document?.timeSignature.orEmpty()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(song.chordpro.toByteArray(StandardCharsets.UTF_8))
                } ?: error("The selected file could not be created.")
            }.onSuccess {
                Toast.makeText(context, "ChordPro file exported.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "The chart could not be exported.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "${song.id}:chart-header") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to song library")
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(song.title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (song.artist.isNotBlank()) {
                        Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (song.chordpro.isNotBlank()) {
                    IconButton(onClick = {
                        val fileName = song.title
                            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                            .trim('_')
                            .ifBlank { "UnityHub_Song" }
                        exportLauncher.launch("$fileName.cho")
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export ChordPro file")
                    }
                }
            }
        }
        stickyHeader(key = "${song.id}:performance-settings") {
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { performanceSettingsExpanded = !performanceSettingsExpanded },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(if (performanceSettingsExpanded) "Performance ▴" else "Performance ▾")
                        }
                        TextButton(
                            onClick = { autoScrollEnabled = !autoScrollEnabled },
                            enabled = document?.lines?.isNotEmpty() == true,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(if (autoScrollEnabled) "Pause scroll" else "Start scroll")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ChartMetadata("Key", displayKey ?: "—")
                        ChartMetadata("Tempo", displayTempo?.let { "$it BPM" } ?: "—")
                        ChartMetadata("Time", displayTimeSignature.ifBlank { "—" })
                        document?.capo?.let { fret ->
                            ChartMetadata("Capo", if (fret == 0) "None" else "$fret")
                        }
                    }
                    if (performanceSettingsExpanded) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                "Speed · ${autoScrollSpeed.toInt()} dp/s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                modifier = Modifier.weight(1f),
                                value = autoScrollSpeed,
                                onValueChange = { autoScrollSpeed = ChartAutoScrollRules.clampSpeed(it) },
                                valueRange = ChartAutoScrollRules.MIN_SPEED_DP_PER_SECOND..
                                    ChartAutoScrollRules.MAX_SPEED_DP_PER_SECOND,
                                enabled = document?.lines?.isNotEmpty() == true
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Transpose", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    when {
                                        transpose > 0 -> "+$transpose semitones"
                                        transpose < 0 -> "$transpose semitones"
                                        else -> "Original key"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row {
                                IconButton(onClick = { transpose = (transpose - 1).coerceAtLeast(-11) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Transpose down one semitone")
                                }
                                IconButton(onClick = { transpose = (transpose + 1).coerceAtMost(11) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Transpose up one semitone")
                                }
                                IconButton(
                                    onClick = { transpose = 0; chartScale = 1f; autoScrollSpeed = ChartAutoScrollRules.DEFAULT_SPEED_DP_PER_SECOND },
                                    enabled = transpose != 0 || chartScale != 1f || autoScrollSpeed != ChartAutoScrollRules.DEFAULT_SPEED_DP_PER_SECOND
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset chart display")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { chartScale = (chartScale - 0.1f).coerceAtLeast(0.8f) },
                                enabled = chartScale > 0.8f
                            ) { Text("A−") }
                            Text(
                                "Text size",
                                modifier = Modifier.padding(top = 12.dp, start = 6.dp, end = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                            TextButton(
                                onClick = { chartScale = (chartScale + 0.1f).coerceAtMost(1.5f) },
                                enabled = chartScale < 1.5f
                            ) { Text("A+") }
                        }
                        TextButton(
                            onClick = { autoScrollEnabled = false; listState.animateScrollToItem(0) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) { Text("Back to beginning") }
                    }
                }
            }
        }
        item(key = "${song.id}:chart-loading") {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
                    CircularProgressIndicator()
                    Text(
                        "Preparing chart…",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            documentParseFailed -> item(key = "${song.id}:chart-error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "UnityHub could not format this saved chart, so its original text is shown below. You can still return to Songs and edit the chart.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        safeRawChartText(song.chordpro),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            document?.lines.isNullOrEmpty() -> item(key = "${song.id}:chart-empty") {
                Text(
                    "No ChordPro content has been added to this song.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> itemsIndexed(
                items = document?.lines.orEmpty(),
                key = { index, _ -> "${song.id}:chart-line:$index" }
            ) { _, line ->
                ChordProChartLine(line = line, transpose = transpose, scale = chartScale)
            }
        }
        if (document?.isTruncated == true) {
            item(key = "${song.id}:chart-truncated") {
                Text(
                    "This chart is shortened for safe display. The original saved ChordPro text is unchanged.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        item(key = "${song.id}:chart-bottom-space") { Spacer(Modifier.height(24.dp)) }
        }

    }
}

@Composable
private fun ChartMetadata(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}


internal object ChartAutoScrollRules {
    const val MIN_SPEED_DP_PER_SECOND = 20f
    const val MAX_SPEED_DP_PER_SECOND = 100f
    const val DEFAULT_SPEED_DP_PER_SECOND = 36f

    fun clampSpeed(speed: Float): Float =
        speed.coerceIn(MIN_SPEED_DP_PER_SECOND, MAX_SPEED_DP_PER_SECOND)
}

internal data class ChordProPreviewResult(
    val document: ChordProDocument? = null,
    val failed: Boolean = false,
    val failureReason: String? = null
)

internal object ChordProPreviewRules {
    // Keep preview parsing bounded and synchronous so a text-field edit is
    // reflected in the preview during the same Compose recomposition.
    const val MAX_PREVIEW_SOURCE_CHARACTERS = 32_768

    fun previewSource(source: String): String =
        source.take(MAX_PREVIEW_SOURCE_CHARACTERS)

    fun isTruncated(source: String): Boolean =
        source.length > MAX_PREVIEW_SOURCE_CHARACTERS

    fun parse(source: String): ChordProPreviewResult {
        if (source.isBlank()) return ChordProPreviewResult()
        return try {
            ChordProPreviewResult(document = ChordProParser.parse(previewSource(source)))
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
            // Android may wrap a parser's static-initialization failure in
            // ExceptionInInitializerError. Report the underlying cause instead
            // of hiding the useful exception behind the wrapper.
            var rootCause: Throwable = failure
            repeat(8) {
                val next = rootCause.cause ?: return@repeat
                if (next === rootCause) return@repeat
                rootCause = next
            }
            val detail = rootCause.message
                ?.filter { it == ' ' || it.isLetterOrDigit() || it in ".,:_-/()" }
                ?.take(120)
                ?.takeIf(String::isNotBlank)
            ChordProPreviewResult(
                failed = true,
                failureReason = listOfNotNull(
                    failure.javaClass.simpleName,
                    if (rootCause !== failure) rootCause.javaClass.simpleName else null,
                    detail
                ).joinToString(": ")
            )
        }
    }
}

internal const val MAX_RAW_CHART_FALLBACK_CHARACTERS = 12_000

internal fun safeRawChartText(source: String): String {
    val bounded = source.take(MAX_RAW_CHART_FALLBACK_CHARACTERS)
    val readable = bounded.filter { character ->
        character == '\n' || character == '\r' || character == '\t' || character >= ' '
    }
    return buildString {
        append(readable)
        if (source.length > MAX_RAW_CHART_FALLBACK_CHARACTERS) {
            append("\n\n[Chart text shortened for safe display]")
        }
    }
}

@Composable
internal fun ChordProPreview(source: String, maxLines: Int = 8) {
    val previewSource = remember(source) { ChordProPreviewRules.previewSource(source) }
    val previewIsTruncated = ChordProPreviewRules.isTruncated(source)
    val result = remember(previewSource) { ChordProPreviewRules.parse(previewSource) }
    val currentDocument = result.document

    when {
        previewSource.isBlank() -> Text(
            "Add sample lyrics or type a chord to see the live chart here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        result.failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "The preview could not be formatted. Check the ChordPro text and try again.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            result.failureReason?.let { reason ->
                Text(
                    "Parser detail: $reason",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        currentDocument == null || currentDocument.lines.isEmpty() -> Text(
            "No chart lines to preview yet. Add lyrics or a chord such as [G]Amazing grace.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        else -> {
            val visibleLineCount = maxLines.coerceIn(1, 16)
            currentDocument.lines.take(visibleLineCount).forEachIndexed { index, line ->
                key("preview-line-$index") {
                    ChordProChartLine(line = line, transpose = 0, scale = 0.9f)
                }
            }
            if (currentDocument.lines.size > visibleLineCount || previewIsTruncated) {
                Text(
                    if (previewIsTruncated) {
                        "Preview sample only · the complete chart text is retained"
                    } else {
                        "Preview only · " + (currentDocument.lines.size - visibleLineCount) +
                            " more lines in the full chart"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ChordProChartLine(line: ChordProLine, transpose: Int, scale: Float = 1f) {
    when {
        line.section != null && line.lyrics.isBlank() && line.chords.isEmpty() -> {
            Text(
                line.section.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = (MaterialTheme.typography.labelLarge.fontSize.value * scale).sp
                ),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
            )
        }
        line.comment -> {
            Text(
                line.lyrics,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = (MaterialTheme.typography.labelMedium.fontSize.value * scale).sp
                ),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        line.lyrics.isBlank() && line.chords.isEmpty() -> Spacer(Modifier.height(8.dp))
        else -> {
            val chordText = remember(line, transpose) { buildChordRow(line, transpose) }
            Column(Modifier.fillMaxWidth()) {
                if (chordText.isNotBlank()) {
                    Text(
                        chordText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = (MaterialTheme.typography.labelLarge.fontSize.value * scale).sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }
                Text(
                    line.lyrics,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * scale).sp
                    )
                )
            }
        }
    }
}

internal const val MAX_RENDERED_CHORDS_PER_LINE = 256
internal const val MAX_RENDERED_CHORD_ROW_CHARACTERS = 8_192

internal fun buildChordRow(line: ChordProLine, transpose: Int): String {
    val output = StringBuilder()
    var cursor = 0
    line.chords.take(MAX_RENDERED_CHORDS_PER_LINE).forEach { chord ->
        if (output.length >= MAX_RENDERED_CHORD_ROW_CHARACTERS) return@forEach
        val target = chord.position.coerceAtLeast(cursor)
        val spaces = (target - cursor).coerceIn(0, 120)
            .coerceAtMost(MAX_RENDERED_CHORD_ROW_CHARACTERS - output.length)
        repeat(spaces) { output.append(' ') }
        cursor = target
        val value = ChordTransposer.transposeChord(chord.chord, transpose).take(64)
        val remaining = MAX_RENDERED_CHORD_ROW_CHARACTERS - output.length
        output.append(value.take(remaining))
        cursor += value.length
    }
    return output.toString()
}
