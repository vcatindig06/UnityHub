package com.unityhub.app.ui

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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.unityhub.app.data.SongDto
import com.unityhub.app.data.SongRepository
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.launch

@Composable
fun SongLibraryScreen(
    organizationId: String,
    userId: String,
    role: String?,
    modifier: Modifier = Modifier
) {
    val repository = remember { SongRepository(SupabaseClientProvider.client) }
    val scope = rememberCoroutineScope()
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
        editorOpen = true
    }

    LaunchedEffect(organizationId) { reload() }

    selectedSong?.let { song ->
        SongChartScreen(song = song, onBack = { selectedSong = null }, modifier = modifier)
        return
    }

    if (deleteCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete song?") },
            text = { Text("“${deleteCandidate?.title}” will be removed from this team's song library.") },
            confirmButton = {
                TextButton(onClick = {
                    val song = deleteCandidate ?: return@TextButton
                    deleteCandidate = null
                    scope.launch {
                        saving = true
                        try {
                            repository.deleteSong(song.id, organizationId)
                            reload()
                        } catch (_: Exception) {
                            error = "The song could not be deleted."
                        } finally {
                            saving = false
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
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
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
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
                            Button(onClick = { startNewSong() }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Add")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search songs or artists") },
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
            } else {
                val filtered = songs.filter {
                    it.title.contains(search.trim(), true) ||
                        it.artist.contains(search.trim(), true)
                }
                if (filtered.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(28.dp)) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(if (songs.isEmpty()) "Your song library is empty" else "No matching songs")
                            Text(
                                if (songs.isEmpty() && canEdit) "Add your first song and start building your team's chord library."
                                else if (songs.isEmpty()) "A song editor or administrator can add songs for your team."
                                else "Try another title or artist.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { song ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { selectedSong = song },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOf(song.artist, song.keySignature.takeIf(String::isNotBlank)?.let { "Key $it" }, song.tempo?.let { "$it BPM" })
                                            .filterNotNull().filter(String::isNotBlank).joinToString(" · "),
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
                                    IconButton(onClick = { startEditing(song) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit song")
                                    }
                                    IconButton(onClick = { deleteCandidate = song }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete song", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
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
                    "Song details and chord chart",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            OutlinedTextField(title, onTitleChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Song title *") }, singleLine = true, enabled = !saving)
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
                    "Use directives such as {title: Song}, {key: G}, {start_of_verse}, {chorus}, and [G]chords inline with lyrics.",
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
        item {
            OutlinedTextField(notes, onNotesChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Team notes (optional)") }, minLines = 2, enabled = !saving)
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onCancel, enabled = !saving) { Text("Cancel") }
                    Button(onClick = onSave, enabled = !saving && title.isNotBlank(), modifier = Modifier.weight(1f)) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (isEditing) "Save changes" else "Save song")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
