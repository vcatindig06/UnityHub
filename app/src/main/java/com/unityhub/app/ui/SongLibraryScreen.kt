package com.unityhub.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.unityhub.app.data.SongDto
import com.unityhub.app.data.SongRepository
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.domain.chordpro.ChordProParser
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

@Composable
fun SongLibraryScreen(
    organizationId: String,
    userId: String,
    role: String?,
    modifier: Modifier = Modifier
) {
    val repository = remember { SongRepository(SupabaseClientProvider.client) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canEdit = role in listOf("admin", "worship_leader", "song_editor")

    var songs by remember { mutableStateOf<List<SongDto>>(emptyList()) }
    var selectedSong by remember { mutableStateOf<SongDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingSong by remember { mutableStateOf<SongDto?>(null) }
    var deleteCandidate by remember { mutableStateOf<SongDto?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var keySignature by rememberSaveable { mutableStateOf("") }
    var tempo by rememberSaveable { mutableStateOf("") }
    var timeSignature by rememberSaveable { mutableStateOf("4/4") }
    var chordpro by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            try {
                songs = repository.loadSongs(organizationId)
                error = ""
            } catch (_: Exception) {
                error = "Could not load songs. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    fun startNewSong() {
        editingSong = null
        title = ""
        artist = ""
        keySignature = ""
        tempo = ""
        timeSignature = "4/4"
        chordpro = ""
        notes = ""
        error = ""
        importMessage = ""
        editorOpen = true
    }

    fun startEditing(song: SongDto) {
        editingSong = song
        title = song.title
        artist = song.artist
        keySignature = song.keySignature
        tempo = song.tempo?.toString().orEmpty()
        timeSignature = song.timeSignature
        chordpro = song.chordpro
        notes = song.notes
        error = ""
        importMessage = ""
        editorOpen = true
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                saving = true
                error = ""
                importMessage = ""
                try {
                    val source = context.contentResolver.openInputStream(uri)?.use(::readLimitedText)
                        ?: error("The selected file could not be opened.")
                    require(source.isNotBlank()) { "The selected file is empty." }
                    val document = ChordProParser.parse(source)
                    editingSong = null
                    title = document.title ?: importedFileTitle(uri)
                    artist = document.artist.orEmpty()
                    keySignature = document.key.orEmpty()
                    tempo = document.tempo?.toString().orEmpty()
                    timeSignature = document.timeSignature ?: "4/4"
                    chordpro = source
                    notes = ""
                    importMessage = "File imported. Review the song details and chart before saving."
                    editorOpen = true
                } catch (exception: Exception) {
                    error = exception.message ?: "The ChordPro file could not be imported."
                } finally {
                    saving = false
                }
            }
        }
    }

    LaunchedEffect(organizationId) { reload() }

    selectedSong?.let { song ->
        SongChartScreen(song = song, onBack = { selectedSong = null }, modifier = modifier)
        return
    }

    if (deleteCandidate != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) deleteCandidate = null },
            title = { Text("Delete song?") },
            text = { Text("“${deleteCandidate?.title}” will be removed from this team's song library. Existing service setlists keep their saved chart snapshots.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val song = deleteCandidate ?: return@TextButton
                        deleteCandidate = null
                        scope.launch {
                            saving = true
                            try {
                                repository.deleteSong(song.id, organizationId)
                                reload()
                            } catch (_: Exception) {
                                error = "The song could not be deleted. It may already be used by a saved service."
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }, enabled = !saving) { Text("Cancel") }
            }
        )
    }

    if (editorOpen) {
        SongEditor(
            isEditing = editingSong != null,
            saving = saving,
            title = title,
            onTitleChange = { title = it },
            artist = artist,
            onArtistChange = { artist = it },
            keySignature = keySignature,
            onKeyChange = { keySignature = it },
            tempo = tempo,
            onTempoChange = { tempo = it.filter(Char::isDigit).take(3) },
            timeSignature = timeSignature,
            onTimeSignatureChange = { timeSignature = it },
            chordpro = chordpro,
            onChordproChange = { chordpro = it },
            notes = notes,
            onNotesChange = { notes = it },
            error = error,
            importMessage = importMessage,
            onCancel = { editorOpen = false },
            onSave = {
                val parsedTempo = tempo.toIntOrNull()
                if (tempo.isNotBlank() && parsedTempo == null) {
                    error = "Enter a valid tempo."
                } else {
                    scope.launch {
                        saving = true
                        error = ""
                        try {
                            repository.saveSong(
                                organizationId = organizationId,
                                userId = userId,
                                existingId = editingSong?.id,
                                title = title,
                                artist = artist,
                                keySignature = keySignature,
                                tempo = parsedTempo,
                                timeSignature = timeSignature,
                                chordpro = chordpro,
                                notes = notes
                            )
                            editorOpen = false
                            importMessage = ""
                            reload()
                        } catch (exception: Exception) {
                            error = exception.message ?: "The song could not be saved."
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        )
    } else {
        val filteredSongs = songs.filter { song ->
            val query = search.trim()
            query.isBlank() ||
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.keySignature.contains(query, ignoreCase = true) ||
                song.timeSignature.contains(query, ignoreCase = true)
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Song library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${songs.size} songs in your team library",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEdit) {
                            Button(onClick = { startNewSong() }, enabled = !saving) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Add")
                            }
                        }
                    }
                    if (canEdit) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Text("Import ChordPro file")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search title, artist, key, or time signature") },
                        singleLine = true
                    )
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (loading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            } else if (filteredSongs.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(28.dp)) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(if (songs.isEmpty()) "Your song library is empty" else "No matching songs")
                        Text(
                            if (songs.isEmpty() && canEdit) "Add a song manually or import a ChordPro file to start building your team's chart library."
                            else if (songs.isEmpty()) "A song editor or administrator can add songs for your team."
                            else "Try another title, artist, key, or time signature.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(filteredSongs, key = { it.id }) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { selectedSong = song },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(
                                        song.artist,
                                        song.keySignature.takeIf(String::isNotBlank)?.let { "Key $it" },
                                        song.tempo?.let { "$it BPM" },
                                        song.timeSignature.takeIf(String::isNotBlank)
                                    ).filterNotNull().filter(String::isNotBlank).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (song.chordpro.isNotBlank()) {
                                    Text(
                                        "Chord chart available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (canEdit) {
                                Row {
                                    androidx.compose.material3.IconButton(onClick = { startEditing(song) }, enabled = !saving) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit song")
                                    }
                                    androidx.compose.material3.IconButton(onClick = { deleteCandidate = song }, enabled = !saving) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete song", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { reload() }, enabled = !loading && !saving, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh song library")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SongEditor(
    isEditing: Boolean,
    saving: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    artist: String,
    onArtistChange: (String) -> Unit,
    keySignature: String,
    onKeyChange: (String) -> Unit,
    tempo: String,
    onTempoChange: (String) -> Unit,
    timeSignature: String,
    onTimeSignatureChange: (String) -> Unit,
    chordpro: String,
    onChordproChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    error: String,
    importMessage: String,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(20.dp)) {
                Text(if (isEditing) "Edit song" else "Add a song", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Song details and ChordPro chart",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                label = { Text("Song title *") },
                singleLine = true,
                enabled = !saving,
                isError = title.trim().length > 200
            )
        }
        item {
            OutlinedTextField(artist, onArtistChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Artist") }, singleLine = true, enabled = !saving)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(keySignature, onKeyChange, Modifier.weight(1f), label = { Text("Key") }, singleLine = true, enabled = !saving)
                OutlinedTextField(tempo, onTempoChange, Modifier.weight(1f), label = { Text("Tempo (BPM)") }, singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
        item {
            OutlinedTextField(timeSignature, onTimeSignatureChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Time signature") }, singleLine = true, enabled = !saving)
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("ChordPro chart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Supports title, artist, key, tempo, time signature, verse/chorus/bridge sections, comments, and inline chords such as [G]Amazing [D]grace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = chordpro,
                    onValueChange = onChordproChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 10,
                    label = { Text("Paste or type ChordPro text") },
                    enabled = !saving
                )
            }
        }
        if (chordpro.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live chart preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        ChordProPreview(source = chordpro, maxLines = 8)
                    }
                }
            }
        }
        item {
            OutlinedTextField(notes, onNotesChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Team notes (optional)") }, minLines = 2, enabled = !saving)
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (importMessage.isNotBlank()) {
                    Text(importMessage, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onCancel, enabled = !saving) { Text("Cancel") }
                    Button(onClick = onSave, enabled = !saving && title.trim().length in 1..200, modifier = Modifier.weight(1f)) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (isEditing) "Save changes" else "Save song")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun readLimitedText(input: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var totalBytes = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        totalBytes += count
        require(totalBytes <= 1_048_576) { "ChordPro files must be 1 MB or smaller." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray().toString(StandardCharsets.UTF_8)
}

private fun importedFileTitle(uri: Uri): String {
    val rawName = uri.lastPathSegment.orEmpty()
        .substringAfterLast('/')
        .substringAfterLast(':')
    val cleanName = rawName
        .substringBeforeLast('.', rawName)
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
    return cleanName.ifBlank { "Imported song" }
}
