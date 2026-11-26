package com.unityhub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.SongDto
import com.unityhub.app.data.SongSetlistDto
import com.unityhub.app.data.SongSetlistItemDto
import com.unityhub.app.data.SongSetlistRepository
import com.unityhub.app.data.SongSetlistRules
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SongSetlistsScreen(
    organizationId: String,
    userId: String,
    songs: List<SongDto>,
    canManage: Boolean,
    openCreateSetlistRequestId: Int = 0,
    onCreateSetlistRequestHandled: () -> Unit = {},
    onBackToSongs: () -> Unit,
    onOpenSong: (SongDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember(organizationId) {
        SongSetlistRepository(SupabaseClientProvider.client)
    }
    val scope = rememberCoroutineScope()

    var setlists by remember(organizationId) { mutableStateOf<List<SongSetlistDto>>(emptyList()) }
    var itemsBySetlist by remember(organizationId) {
        mutableStateOf<Map<String, List<SongSetlistItemDto>>>(emptyMap())
    }
    var selectedSetlist by remember { mutableStateOf<SongSetlistDto?>(null) }
    var selectedItems by remember { mutableStateOf<List<SongSetlistItemDto>>(emptyList()) }
    var loading by remember(organizationId) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var createDialogOpen by rememberSaveable { mutableStateOf(false) }
    var addSongsDialogOpen by rememberSaveable { mutableStateOf(false) }
    var renameDialogOpen by rememberSaveable { mutableStateOf(false) }
    var duplicateDialogOpen by rememberSaveable { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<SongSetlistDto?>(null) }
    var setlistNameDraft by rememberSaveable { mutableStateOf("") }
    var setlistDescriptionDraft by rememberSaveable { mutableStateOf("") }
    var duplicateNameDraft by rememberSaveable { mutableStateOf("") }
    var pickerSearch by rememberSaveable { mutableStateOf("") }
    var selectedSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun reloadData(
        selectedId: String? = selectedSetlist?.id,
        showLoader: Boolean = false
    ) {
        if (showLoader) loading = true
        errorMessage = ""
        try {
            val loadedSetlists = repository.loadSetlists(organizationId)
            val loadedItems = repository.loadAllItems(organizationId)
            val groupedItems = loadedItems.groupBy { it.setlistId }
                .mapValues { (_, rows) ->
                    rows.sortedWith(compareBy<SongSetlistItemDto> { it.itemOrder }
                        .thenBy { it.createdAt }
                        .thenBy { it.id })
                }
            setlists = loadedSetlists
            itemsBySetlist = groupedItems
            selectedSetlist = selectedId?.let { id -> loadedSetlists.firstOrNull { it.id == id } }
            selectedItems = selectedSetlist?.let { groupedItems[it.id].orEmpty() }.orEmpty()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            errorMessage = exception.message
                ?: "Setlists could not be loaded. Check your connection and try again."
        } finally {
            if (showLoader) loading = false
        }
    }

    fun openCreateDialog() {
        setlistNameDraft = ""
        setlistDescriptionDraft = ""
        selectedSongIds = emptySet()
        pickerSearch = ""
        errorMessage = ""
        createDialogOpen = true
    }

    fun openAddSongsDialog() {
        selectedSongIds = emptySet()
        pickerSearch = ""
        errorMessage = ""
        addSongsDialogOpen = true
    }

    fun performAction(
        action: suspend () -> Unit,
        selectIdAfter: String? = selectedSetlist?.id,
        useUpdatedSelection: Boolean = false
    ) {
        scope.launch {
            saving = true
            errorMessage = ""
            try {
                action()
                val refreshedSelection = if (useUpdatedSelection) selectedSetlist?.id else selectIdAfter
                reloadData(selectId = refreshedSelection)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                errorMessage = exception.message ?: "The setlist could not be updated."
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(organizationId) {
        reloadData(selectedId = null, showLoader = true)
    }

    LaunchedEffect(openCreateSetlistRequestId) {
        if (openCreateSetlistRequestId > 0) {
            openCreateDialog()
            onCreateSetlistRequestHandled()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = if (selectedSetlist == null) onBackToSongs else {
                {
                    selectedSetlist = null
                    selectedItems = emptyList()
                    errorMessage = ""
                }
            }) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = if (selectedSetlist == null) "Back to songs" else "Back to setlists"
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    selectedSetlist?.name ?: "Setlists",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (selectedSetlist == null) {
                        "Organize multiple songs into reusable song collections."
                    } else {
                        "${selectedItems.size} songs · Independent from service schedules"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedSetlist == null && canManage) {
                Button(onClick = ::openCreateDialog, enabled = !saving) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                    Text("New")
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (selectedSetlist == null) {
            if (setlists.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("No setlists yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (canManage) {
                            "Create a setlist and choose at least two songs from your team library. You can reorder or update it later."
                        } else {
                            "Your team’s saved song collections will appear here."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (canManage) {
                        Button(onClick = ::openCreateDialog, enabled = !saving) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                            Text("Create setlist")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(setlists, key = { it.id }) { setlist ->
                        val count = itemsBySetlist[setlist.id].orEmpty().size
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedSetlist = setlist
                                selectedItems = itemsBySetlist[setlist.id].orEmpty()
                                errorMessage = ""
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    setlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (setlist.description.isNotBlank()) {
                                    Text(setlist.description, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "$count songs · Updated ${setlist.updatedAt.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Open setlist",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        } else {
            val currentSetlist = selectedSetlist!!
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canManage) {
                    Button(onClick = ::openAddSongsDialog, enabled = !saving) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                        Text("Add songs")
                    }
                    OutlinedButton(
                        onClick = {
                            setlistNameDraft = currentSetlist.name
                            setlistDescriptionDraft = currentSetlist.description
                            renameDialogOpen = true
                        },
                        enabled = !saving
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text("Edit")
                    }
                }
                OutlinedButton(
                    onClick = {
                        duplicateNameDraft = currentSetlist.name + " (copy)"
                        duplicateDialogOpen = true
                    },
                    enabled = !saving
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text("Duplicate")
                }
                if (canManage) {
                    IconButton(onClick = { deleteCandidate = currentSetlist }, enabled = !saving) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete setlist",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (!canManage) {
                Text(
                    "Read-only access",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (selectedItems.isEmpty()) {
                Text(
                    "This setlist has no songs yet.",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedItems, key = { it.id }) { item ->
                        val index = selectedItems.indexOfFirst { it.id == item.id }
                        val linkedSong = songs.firstOrNull { it.id == item.songId }
                        val chartSong = linkedSong ?: SongDto(
                            id = "setlist-snapshot-${item.id}",
                            organizationId = organizationId,
                            title = item.title,
                            artist = item.artist,
                            keySignature = item.keySignature,
                            tempo = null,
                            timeSignature = "",
                            chordpro = item.chordproSnapshot,
                            notes = "",
                            createdBy = userId,
                            updatedBy = userId,
                            createdAt = item.createdAt,
                            updatedAt = item.createdAt
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    (index + 1).toString().padStart(2, '0'),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Column(
                                    modifier = Modifier.weight(1f).clickable(
                                        enabled = linkedSong != null || item.chordproSnapshot.isNotBlank()
                                    ) {
                                        onOpenSong(chartSong)
                                    },
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOf(item.artist, item.keySignature.takeIf(String::isNotBlank)?.let { "Key $it" })
                                            .filterNotNull()
                                            .filter(String::isNotBlank)
                                            .joinToString(" · ")
                                            .ifBlank { "Song chart snapshot" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (item.songId == null) {
                                        Text(
                                            "Original library song removed · open saved chart snapshot",
                                            color = MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                if (canManage) {
                                    Column {
                                        IconButton(
                                            onClick = {
                                                performAction({
                                                    repository.moveItem(currentSetlist.id, organizationId, index, -1)
                                                })
                                            },
                                            enabled = !saving && index > 0,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move song up")
                                        }
                                        IconButton(
                                            onClick = {
                                                performAction({
                                                    repository.moveItem(currentSetlist.id, organizationId, index, 1)
                                                })
                                            },
                                            enabled = !saving && index < selectedItems.lastIndex,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move song down")
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            performAction({
                                                repository.removeItem(item.id, currentSetlist.id, organizationId)
                                            })
                                        },
                                        enabled = !saving && SongSetlistRules.canRemoveSong(selectedItems.size),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remove song from setlist",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (selectedItems.size == SongSetlistRules.MIN_SONGS) {
                        item {
                            Text(
                                "A setlist must keep at least two songs.",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (createDialogOpen) {
        SongSetlistSongPickerDialog(
            title = "Create setlist",
            songs = songs,
            selectedSongIds = selectedSongIds,
            alreadyInSetlistIds = emptySet(),
            minimumSelections = SongSetlistRules.MIN_SONGS,
            includeSetlistDetails = true,
            name = setlistNameDraft,
            onNameChange = { setlistNameDraft = it },
            description = setlistDescriptionDraft,
            onDescriptionChange = { setlistDescriptionDraft = it },
            search = pickerSearch,
            onSearchChange = { pickerSearch = it },
            saving = saving,
            errorMessage = errorMessage,
            confirmLabel = "Create setlist",
            onToggleSong = { songId, checked ->
                selectedSongIds = if (checked) selectedSongIds + songId else selectedSongIds - songId
            },
            onDismiss = { if (!saving) createDialogOpen = false },
            onConfirm = {
                val selectedSongs = songs.filter { it.id in selectedSongIds }
                performAction(
                    action = {
                        val created = repository.createSetlist(
                            organizationId = organizationId,
                            userId = userId,
                            name = setlistNameDraft,
                            description = setlistDescriptionDraft,
                            songs = selectedSongs
                        )
                        selectedSetlist = created
                    },
                    selectIdAfter = null,
                    useUpdatedSelection = true
                )
                createDialogOpen = false
            }
        )
    }

    if (addSongsDialogOpen && selectedSetlist != null) {
        val currentSetlist = selectedSetlist!!
        val existingIds = selectedItems.mapNotNullTo(mutableSetOf()) { it.songId }
        SongSetlistSongPickerDialog(
            title = "Add songs",
            songs = songs,
            selectedSongIds = selectedSongIds,
            alreadyInSetlistIds = existingIds,
            minimumSelections = 1,
            includeSetlistDetails = false,
            name = "",
            onNameChange = {},
            description = "",
            onDescriptionChange = {},
            search = pickerSearch,
            onSearchChange = { pickerSearch = it },
            saving = saving,
            errorMessage = errorMessage,
            confirmLabel = "Add selected songs",
            onToggleSong = { songId, checked ->
                selectedSongIds = if (checked) selectedSongIds + songId else selectedSongIds - songId
            },
            onDismiss = { if (!saving) addSongsDialogOpen = false },
            onConfirm = {
                val selectedSongs = songs.filter { it.id in selectedSongIds }
                performAction(
                    action = {
                        repository.addSongs(currentSetlist.id, organizationId, selectedSongs)
                    },
                    selectIdAfter = currentSetlist.id
                )
                addSongsDialogOpen = false
            }
        )
    }

    if (renameDialogOpen && selectedSetlist != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) renameDialogOpen = false },
            title = { Text("Edit setlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = setlistNameDraft,
                        onValueChange = { setlistNameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Setlist name") },
                        singleLine = true,
                        enabled = !saving
                    )
                    OutlinedTextField(
                        value = setlistDescriptionDraft,
                        onValueChange = { setlistDescriptionDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                        maxLines = 4,
                        enabled = !saving,
                        supportingText = { Text("${setlistDescriptionDraft.length}/500") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = selectedSetlist ?: return@Button
                        performAction({
                            repository.renameSetlist(
                                current.id,
                                organizationId,
                                setlistNameDraft,
                                setlistDescriptionDraft
                            )
                        })
                        renameDialogOpen = false
                    },
                    enabled = !saving && setlistNameDraft.trim().length in 2..100 &&
                        setlistDescriptionDraft.length <= 500
                ) { Text("Save changes") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogOpen = false }, enabled = !saving) {
                    Text("Cancel")
                }
            }
        )
    }

    if (duplicateDialogOpen && selectedSetlist != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) duplicateDialogOpen = false },
            title = { Text("Duplicate setlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create another setlist with the same songs and order.")
                    OutlinedTextField(
                        value = duplicateNameDraft,
                        onValueChange = { duplicateNameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New setlist name") },
                        singleLine = true,
                        enabled = !saving
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = selectedSetlist ?: return@Button
                        performAction(
                            action = {
                                val duplicated = repository.duplicateSetlist(
                                    current.id,
                                    organizationId,
                                    userId,
                                    duplicateNameDraft
                                )
                                selectedSetlist = duplicated
                            },
                            selectIdAfter = null,
                            useUpdatedSelection = true
                        )
                        duplicateDialogOpen = false
                    },
                    enabled = !saving && duplicateNameDraft.trim().length in 2..100
                ) { Text("Duplicate") }
            },
            dismissButton = {
                TextButton(onClick = { duplicateDialogOpen = false }, enabled = !saving) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!saving) deleteCandidate = null },
            title = { Text("Delete setlist?") },
            text = { Text("\"${candidate.name}\" and its song order will be deleted. The songs in your team library will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        performAction(
                            action = { repository.deleteSetlist(candidate.id, organizationId) },
                            selectIdAfter = if (selectedSetlist?.id == candidate.id) null else selectedSetlist?.id
                        )
                        if (selectedSetlist?.id == candidate.id) {
                            selectedSetlist = null
                            selectedItems = emptyList()
                        }
                        deleteCandidate = null
                    },
                    enabled = !saving
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }, enabled = !saving) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SongSetlistSongPickerDialog(
    title: String,
    songs: List<SongDto>,
    selectedSongIds: Set<String>,
    alreadyInSetlistIds: Set<String>,
    minimumSelections: Int,
    includeSetlistDetails: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    saving: Boolean,
    errorMessage: String,
    confirmLabel: String,
    onToggleSong: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val filteredSongs = remember(songs, search) {
        val query = search.trim().lowercase()
        if (query.isBlank()) songs
        else songs.filter {
            it.title.lowercase().contains(query) ||
                it.artist.lowercase().contains(query) ||
                it.keySignature.lowercase().contains(query)
        }
    }
    val selectedCount = selectedSongIds.count { it !in alreadyInSetlistIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (includeSetlistDetails) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Setlist name *") },
                        singleLine = true,
                        enabled = !saving
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes / theme (optional)") },
                        minLines = 2,
                        maxLines = 3,
                        enabled = !saving,
                        supportingText = { Text("${description.length}/500") }
                    )
                }
                Text(
                    if (minimumSelections == 2) {
                        "Select at least two songs. You can reorder them after creating the setlist."
                    } else {
                        "Choose songs to add. Songs already in this setlist are marked."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search songs") },
                    singleLine = true,
                    enabled = !saving
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredSongs, key = { it.id }) { song ->
                        val exists = song.id in alreadyInSetlistIds
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(
                                enabled = !saving && !exists
                            ) {
                                onToggleSong(song.id, song.id !in selectedSongIds)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = exists || song.id in selectedSongIds,
                                onCheckedChange = { checked ->
                                    if (!exists) onToggleSong(song.id, checked)
                                },
                                enabled = !saving && !exists
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(song.title, fontWeight = FontWeight.Medium)
                                Text(
                                    listOf(song.artist, song.keySignature.takeIf(String::isNotBlank)?.let { "Key $it" })
                                        .filterNotNull()
                                        .filter(String::isNotBlank)
                                        .joinToString(" · ")
                                        .ifBlank { "No artist or key entered" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (exists) {
                                Text(
                                    "Added",
                                    modifier = Modifier.padding(end = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Text(
                    if (minimumSelections == 2) {
                        "$selectedCount selected · minimum 2"
                    } else {
                        "$selectedCount selected"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (filteredSongs.isEmpty()) {
                    Text(
                        "No matching songs in your library.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !saving && selectedCount >= minimumSelections &&
                    (!includeSetlistDetails || name.trim().length in 2..100 && description.length <= 500)
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        }
    )
}
