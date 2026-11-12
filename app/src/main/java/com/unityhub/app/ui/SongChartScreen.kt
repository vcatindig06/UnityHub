package com.unityhub.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.unityhub.app.data.SongDto
import com.unityhub.app.domain.chordpro.ChordProLine
import com.unityhub.app.domain.chordpro.ChordProParser
import com.unityhub.app.domain.chordpro.ChordProDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.unityhub.app.domain.chordpro.ChordTransposer
import java.nio.charset.StandardCharsets

@Composable
fun SongChartScreen(
    song: SongDto,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var transpose by rememberSaveable(song.id) { mutableIntStateOf(0) }
    var chartScale by rememberSaveable(song.id) { mutableFloatStateOf(1f) }
    val context = LocalContext.current
    val document = remember(song.chordpro) { ChordProParser.parse(song.chordpro) }
    val originalKey = document.key?.takeIf(String::isNotBlank) ?: song.keySignature
    val displayKey = ChordTransposer.transposeKey(originalKey, transpose)
    val displayTempo = song.tempo ?: document.tempo
    val displayTimeSignature = song.timeSignature.takeIf(String::isNotBlank) ?: document.timeSignature.orEmpty()

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to song library")
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Performance settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ChartMetadata("Key", displayKey ?: "—")
                        ChartMetadata("Tempo", displayTempo?.let { "$it BPM" } ?: "—")
                        ChartMetadata("Time", displayTimeSignature.ifBlank { "—" })
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
                                onClick = { transpose = 0; chartScale = 1f },
                                enabled = transpose != 0 || chartScale != 1f
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
                    if (transpose != 0) {
                        Text(
                            "Transposed for this view only. The saved song and service setlists remain unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (document.lines.isEmpty()) {
            item {
                Text("No ChordPro content has been added to this song.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            itemsIndexed(document.lines) { _, line ->
                ChordProChartLine(line = line, transpose = transpose, scale = chartScale)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChartMetadata(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ChordProPreview(source: String, maxLines: Int = 8) {
    var document by remember { mutableStateOf<ChordProDocument?>(null) }
    var parsedAtLeastOnce by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        delay(250)
        val parsed = withContext(Dispatchers.Default) {
            runCatching { ChordProParser.parse(source) }.getOrNull()
        }
        document = parsed
        parsedAtLeastOnce = true
    }

    val currentDocument = document
    if (currentDocument == null) {
        Text(
            if (parsedAtLeastOnce) "The chart preview could not be generated."
            else "Updating chart preview…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    } else if (currentDocument.lines.isEmpty()) {
        Text("No chart lines to preview yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    } else {
        currentDocument.lines.take(maxLines).forEach { line ->
            ChordProChartLine(line = line, transpose = 0, scale = 0.9f)
        }
        if (currentDocument.lines.size > maxLines) {
            Text(
                "Preview only · ${currentDocument.lines.size - maxLines} more lines in the full chart",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ChordProChartLine(line: ChordProLine, transpose: Int, scale: Float = 1f) {
    when {
        line.section != null && line.lyrics.isBlank() -> {
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

internal fun buildChordRow(line: ChordProLine, transpose: Int): String {
    val output = StringBuilder()
    var cursor = 0
    line.chords.forEach { chord ->
        val target = chord.position.coerceAtLeast(cursor)
        repeat((target - cursor).coerceAtMost(120)) { output.append(' ') }
        cursor = target
        val value = ChordTransposer.transposeChord(chord.chord, transpose)
        output.append(value)
        cursor += value.length
    }
    return output.toString()
}
