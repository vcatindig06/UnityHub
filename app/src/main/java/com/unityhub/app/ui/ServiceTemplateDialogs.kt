package com.unityhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.ServiceTemplateDto

@Composable
internal fun ServiceTemplateLibraryDialog(
    templates: List<ServiceTemplateDto>,
    itemCounts: Map<String, Int>,
    loading: Boolean,
    saving: Boolean,
    errorMessage: String,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRename: (String, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<ServiceTemplateDto?>(null) }
    var deleteTarget by remember { mutableStateOf<ServiceTemplateDto?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Setlist templates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Reusable worship orders saved by your team. Save a setlist from a service, then apply it to another service.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                when {
                    loading && templates.isEmpty() -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(26.dp))
                        }
                    }
                    templates.isEmpty() -> {
                        Card {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("No templates saved yet", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Open a service with a setlist and choose “Save as template” to create your first reusable order.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 390.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(templates, key = { it.id }) { template ->
                                Card {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        if (template.description.isNotBlank()) {
                                            Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            "${itemCounts[template.id] ?: 0} setlist items",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { renameTarget = template },
                                                enabled = !saving
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Rename ${template.name}")
                                            }
                                            IconButton(
                                                onClick = { deleteTarget = template },
                                                enabled = !saving
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${template.name}", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !loading && !saving) { Text("Refresh") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") }
        }
    )

    renameTarget?.let { template ->
        RenameServiceTemplateDialog(
            template = template,
            saving = saving,
            onDismiss = { renameTarget = null },
            onSave = { name, description ->
                onRename(template.id, name, description)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { if (!saving) deleteTarget = null },
            title = { Text("Delete template?") },
            text = {
                Text("“${template.name}” and its saved setlist items will be permanently deleted. Existing services that used this template will not be changed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(template.id)
                        deleteTarget = null
                    },
                    enabled = !saving
                ) { Text("Delete template") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = !saving) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RenameServiceTemplateDialog(
    template: ServiceTemplateDto,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable(template.id) { mutableStateOf(template.name) }
    var description by rememberSaveable(template.id) { mutableStateOf(template.description) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Rename template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Template name") },
                    supportingText = { Text("${name.trim().length}/100 · Use 2–100 characters") },
                    singleLine = true,
                    enabled = !saving,
                    isError = name.trim().length !in 2..100
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description (optional)") },
                    supportingText = { Text("${description.length}/500") },
                    minLines = 2,
                    enabled = !saving
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description) },
                enabled = !saving && name.trim().length in 2..100
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
internal fun SaveServiceTemplateDialog(
    saving: Boolean,
    errorMessage: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Save setlist as template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "Your current setlist order, custom items, notes, durations, song links, and saved ChordPro snapshots will be preserved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Template name *") },
                    supportingText = { Text("${name.trim().length}/100 characters") },
                    singleLine = true,
                    enabled = !saving,
                    isError = name.isNotBlank() && name.trim().length !in 2..100
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description (optional)") },
                    supportingText = { Text("${description.length}/500 characters") },
                    minLines = 2,
                    enabled = !saving
                )
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description) },
                enabled = !saving && name.trim().length in 2..100
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save template")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
internal fun ServiceTemplatePickerDialog(
    templates: List<ServiceTemplateDto>,
    itemCounts: Map<String, Int>,
    loading: Boolean,
    saving: Boolean,
    errorMessage: String,
    onDismiss: () -> Unit,
    onSelect: (ServiceTemplateDto) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Choose a setlist template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The selected template will be appended after the items already in this service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                when {
                    loading && templates.isEmpty() -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator(Modifier.size(26.dp)) }
                    }
                    templates.isEmpty() -> Text("No saved templates are available for this team.")
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(templates, key = { it.id }) { template ->
                            TextButton(
                                onClick = { onSelect(template) },
                                enabled = !saving && (itemCounts[template.id] ?: 0) > 0,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(template.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "${itemCounts[template.id] ?: 0} items" +
                                            if (template.description.isBlank()) "" else " · ${template.description}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
internal fun ConfirmApplyTemplateDialog(
    template: ServiceTemplateDto,
    targetServiceTitle: String,
    itemCount: Int,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Apply this template?") },
        text = {
            Text(
                "Append $itemCount items from “${template.name}” to “$targetServiceTitle”? Existing setlist items will remain in place. Song links, custom item details, notes, durations, and saved ChordPro snapshots will be retained."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Apply template")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
