package com.unityhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.SongDto
import com.unityhub.app.domain.chordpro.ChordProLine
import com.unityhub.app.domain.chordpro.ChordProParser
import com.unityhub.app.domain.chordpro.ChordTransposer

@Composable
fun SongChartScreen(
    song: SongDto,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var transpose by rememberSaveable(song.id) { mutableIntStateOf(0) }
    val document = remember(song.chordpro) { ChordProParser.parse(song.chordpro) }
    val displayKey = ChordTransposer.transposeKey(
        document.key?.takeIf(String::isNotBlank) ?: song.keySignature,
        transpose
    )

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
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Performance settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(displayKey ?: "Not specified", style = MaterialTheme.typography.titleMedium)
                        }
                        Row {
                            IconButton(onClick = { transpose = (transpose - 1).coerceAtLeast(-11) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Transpose down one semitone")
                            }
                            Column(Modifier.padding(top = 10.dp)) {
                                Text(
                                    when {
                                        transpose > 0 -> "+$transpose"
                                        else -> transpose.toString()
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("semitones", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { transpose = (transpose + 1).coerceAtMost(11) }) {
                                Icon(Icons.Default.Add, contentDescription = "Transpose up one semitone")
                            }
                        }
                    }
                    if (transpose != 0) {
                        Text(
                            "Transposed for this view only. The saved song remains unchanged.",
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
                ChartLine(line = line, transpose = transpose)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChartLine(line: ChordProLine, transpose: Int) {
    when {
        line.section != null && line.lyrics.isBlank() -> {
            Text(
                line.section.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
            )
        }
        line.comment -> {
            Text(
                line.lyrics,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        line.lyrics.isBlank() && line.chords.isEmpty() -> Spacer(Modifier.height(8.dp))
        else -> {
            val chordText = remember(line, transpose) {
                buildChordRow(line, transpose)
            }
            Column(Modifier.fillMaxWidth()) {
                if (chordText.isNotBlank()) {
                    Text(
                        chordText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }
                Text(
                    line.lyrics,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun buildChordRow(line: ChordProLine, transpose: Int): String {
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
