package com.unityhub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.OfflineServiceCache
import com.unityhub.app.data.ServicePlanDto
import com.unityhub.app.data.ServicePlanItemDto
import com.unityhub.app.data.ServicePlanRepository
import com.unityhub.app.data.ServiceTemplateDto
import com.unityhub.app.data.ServiceTemplateRepository
import com.unityhub.app.data.ServiceAssignmentRules
import com.unityhub.app.data.SongDto
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ServicePlanningScreen(
    organizationId: String,
    userId: String,
    role: String?,
    openServicePlanId: String? = null,
    openServicePlanRequestId: String? = null,
    onOpenServicePlanHandled: () -> Unit = {},
    offlineOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val repository = remember { ServicePlanRepository(SupabaseClientProvider.client) }
    val templateRepository = remember { ServiceTemplateRepository(SupabaseClientProvider.client) }
    val context = LocalContext.current
    val offlineCache = remember(context) { OfflineServiceCache(context) }
    val scope = rememberCoroutineScope()
    var usingOfflineCopy by remember(organizationId, userId, offlineOnly) { mutableStateOf(offlineOnly) }
    var lastSyncedAtMillis by remember(organizationId, userId) { mutableStateOf<Long?>(null) }
    val canManage = !usingOfflineCopy && !offlineOnly && role in listOf("admin", "worship_leader")

    var plans by remember { mutableStateOf<List<ServicePlanDto>>(emptyList()) }
    var selectedPlan by remember { mutableStateOf<ServicePlanDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var assignmentOverviewOpen by rememberSaveable { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<ServicePlanDto?>(null) }
    var hasLoadedPlans by remember { mutableStateOf(false) }
    var consumedNavigationRequestId by remember { mutableStateOf<String?>(null) }
    var calendarView by rememberSaveable { mutableStateOf("month") }
    var selectedCalendarDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var displayedCalendarMonth by rememberSaveable { mutableStateOf(java.time.YearMonth.now().toString()) }
    var templates by remember { mutableStateOf<List<ServiceTemplateDto>>(emptyList()) }
    var templateItemCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var templatesDialogOpen by rememberSaveable { mutableStateOf(false) }
    var templatesLoading by remember { mutableStateOf(false) }
    var templatesSaving by remember { mutableStateOf(false) }
    var templatesError by remember { mutableStateOf("") }

    suspend fun loadTemplateLibrary() {
        templatesLoading = true
        templatesError = ""
        try {
            templates = templateRepository.loadTemplates(organizationId)
            templateItemCounts = templateRepository.loadItemCounts(organizationId)
        } catch (_: Exception) {
            templatesError = "The saved templates could not be loaded. Check your connection and try again."
        } finally {
            templatesLoading = false
        }
    }

    fun reloadTemplateLibrary() {
        scope.launch { loadTemplateLibrary() }
    }

    fun reload() {
        scope.launch {
            loading = true
            error = ""
            try {
                if (offlineOnly) {
                    val cached = offlineCache.loadServicePlans(userId, organizationId)
                    plans = cached.plans
                    lastSyncedAtMillis = cached.syncedAtMillis
                    usingOfflineCopy = true
                    if (plans.isEmpty()) {
                        error = "No saved services are available on this device yet. Connect to the internet and refresh to sync your service plans."
                    }
                } else {
                    try {
                        val onlinePlans = repository.loadPlans(organizationId)
                        val onlineItems = coroutineScope {
                            onlinePlans.map { servicePlan ->
                                async { repository.loadPlanItems(servicePlan.id, organizationId) }
                            }.awaitAll().flatten()
                        }
                        plans = onlinePlans
                        usingOfflineCopy = false
                        lastSyncedAtMillis = runCatching {
                            offlineCache.saveServiceData(userId, organizationId, onlinePlans, onlineItems)
                        }.getOrNull()
                    } catch (_: Exception) {
                        val cached = offlineCache.loadServicePlans(userId, organizationId)
                        if (cached.plans.isNotEmpty()) {
                            plans = cached.plans
                            lastSyncedAtMillis = cached.syncedAtMillis
                            usingOfflineCopy = true
                        } else {
                            plans = emptyList()
                            usingOfflineCopy = false
                            error = "Could not load services and no offline copy is saved. Check your internet connection."
                        }
                    }
                }
                hasLoadedPlans = true
            } catch (_: Exception) {
                error = "The saved service plans could not be opened on this device."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(organizationId, openServicePlanRequestId) {
        if (openServicePlanRequestId.isNullOrBlank()) {
            consumedNavigationRequestId = null
            if (!hasLoadedPlans) reload()
        } else if (consumedNavigationRequestId != openServicePlanRequestId) {
            consumedNavigationRequestId = openServicePlanRequestId
            loading = true
            try {
                val loadedPlans = repository.loadPlans(organizationId)
                val loadedItems = coroutineScope {
                    loadedPlans.map { servicePlan ->
                        async { repository.loadPlanItems(servicePlan.id, organizationId) }
                    }.awaitAll().flatten()
                }
                plans = loadedPlans
                usingOfflineCopy = false
                lastSyncedAtMillis = runCatching {
                    offlineCache.saveServiceData(userId, organizationId, loadedPlans, loadedItems)
                }.getOrNull()
                hasLoadedPlans = true
                selectedPlan = loadedPlans.firstOrNull { it.id == openServicePlanId }
                error = if (selectedPlan == null) {
                    "The service linked to this notification is no longer available."
                } else {
                    ""
                }
            } catch (_: Exception) {
                error = "The linked service could not be opened. Check your connection and try again."
            } finally {
                loading = false
                onOpenServicePlanHandled()
            }
        }
    }

    selectedPlan?.let { plan ->
        ServicePlanDetailScreen(
            plan = plan,
            availablePlans = plans,
            organizationId = organizationId,
            currentUserId = userId,
            canManage = canManage,
            offlineOnly = offlineOnly || usingOfflineCopy,
            onBack = {
                selectedPlan = null
                reload()
            },
            onEditPlan = {
                editingPlan = plan
                error = ""
                selectedPlan = null
                editorOpen = true
            },
            modifier = modifier
        )
        return
    }

    if (assignmentOverviewOpen) {
        AssignmentOverviewScreen(
            organizationId = organizationId,
            onBack = { assignmentOverviewOpen = false },
            onOpenServicePlan = { planId ->
                val matchingPlan = plans.firstOrNull { it.id == planId }
                if (matchingPlan != null) {
                    selectedPlan = matchingPlan
                    assignmentOverviewOpen = false
                } else {
                    assignmentOverviewOpen = false
                    error = "That service could not be found. Refresh the service list and try again."
                    reload()
                }
            },
            modifier = modifier
        )
        return
    }

    if (templatesDialogOpen) {
        ServiceTemplateLibraryDialog(
            templates = templates,
            itemCounts = templateItemCounts,
            loading = templatesLoading,
            saving = templatesSaving,
            errorMessage = templatesError,
            onDismiss = { if (!templatesSaving) templatesDialogOpen = false },
            onRefresh = { reloadTemplateLibrary() },
            onRename = { templateId, name, description ->
                scope.launch {
                    templatesSaving = true
                    templatesError = ""
                    try {
                        templateRepository.renameTemplate(templateId, organizationId, name, description)
                        loadTemplateLibrary()
                    } catch (exception: Exception) {
                        templatesError = exception.message ?: "The template could not be renamed."
                    } finally {
                        templatesSaving = false
                    }
                }
            },
            onDelete = { templateId ->
                scope.launch {
                    templatesSaving = true
                    templatesError = ""
                    try {
                        templateRepository.deleteTemplate(templateId, organizationId)
                        loadTemplateLibrary()
                    } catch (exception: Exception) {
                        templatesError = exception.message ?: "The template could not be deleted."
                    } finally {
                        templatesSaving = false
                    }
                }
            }
        )
    }

    if (editorOpen) {
        ServicePlanEditorDialog(
            plan = editingPlan,
            saving = saving,
            errorMessage = error,
            onDismiss = { editorOpen = false },
            onSave = { title, date, time, notes ->
                scope.launch {
                    saving = true
                    error = ""
                    try {
                        val existing = editingPlan
                        if (existing == null) {
                            repository.createPlan(organizationId, userId, title, date, time, notes)
                        } else {
                            repository.updatePlan(existing.id, organizationId, title, date, time, notes)
                        }
                        editorOpen = false
                        reload()
                    } catch (exception: Exception) {
                        error = exception.message ?: "The service plan could not be saved."
                    } finally {
                        saving = false
                    }
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Service planning", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${plans.size} planned services · Build and organize your setlists",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    templatesDialogOpen = true
                                    reloadTemplateLibrary()
                                },
                                enabled = !saving
                            ) { Text("Templates") }
                            Button(
                                onClick = {
                                    editingPlan = null
                                    error = ""
                                    editorOpen = true
                                },
                                enabled = !saving
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("New")
                            }
                        }
                    }
                }
                if (usingOfflineCopy) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "Offline copy · Read only",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                lastSyncedAtMillis?.let { timestamp ->
                                    "Last synced " + java.time.format.DateTimeFormatter
                                        .ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())
                                        .withZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.Instant.ofEpochMilli(timestamp))
                                } ?: "This device has a saved copy of your service plans.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Service details and saved setlists are available offline. Changes require an internet connection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                if (error.isNotBlank() && !editorOpen) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (canManage) {
            item {
                androidx.compose.material3.OutlinedButton(
                    onClick = { assignmentOverviewOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Assignment overview · Response tracker")
                }
            }
        }

        if (loading) {
            item {
                Column(Modifier.fillMaxWidth().padding(28.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        } else if (plans.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("No services planned yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (canManage) "Create your first service, set its date and time, then build the order of worship."
                            else "A team administrator or worship leader can create service plans for your team.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = calendarView == "month", onClick = { calendarView = "month" }, label = { Text("Month") })
                    FilterChip(selected = calendarView == "week", onClick = { calendarView = "week" }, label = { Text("Week") })
                    FilterChip(selected = calendarView == "list", onClick = { calendarView = "list" }, label = { Text("List") })
                }
            }
            if (calendarView == "list") {
                items(plans, key = { it.id }) { plan ->
                    ServicePlanCard(plan = plan, onClick = { selectedPlan = plan })
                }
            } else {
                item(key = "service-calendar-content") {
                    ServiceCalendarContent(
                        plans = plans,
                        mode = calendarView,
                        selectedDateText = selectedCalendarDate,
                        displayedMonthText = displayedCalendarMonth,
                        onSelectedDateChange = { selectedCalendarDate = it },
                        onDisplayedMonthChange = { displayedCalendarMonth = it },
                        onOpenServicePlan = { selectedPlan = it }
                    )
                }
            }
        }

        item {
            if (!offlineOnly) {
                TextButton(onClick = { reload() }, enabled = !loading) {
                    Text(if (usingOfflineCopy) "Retry online sync" else "Refresh services")
                }
            } else {
                Text(
                    "Reconnect and refresh your workspace to sync the latest service plans.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ServicePlanCard(plan: ServicePlanDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plan.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ServiceStatusLabel(plan.status)
            }
            Text(
                serviceDateTimeLabel(plan.serviceDate, plan.startTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (plan.notes.isNotBlank()) {
                Text(plan.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text("Open service plan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ServiceStatusLabel(status: String) {
    val label = when (status) {
        "published" -> "Published"
        "completed" -> "Completed"
        "cancelled" -> "Cancelled"
        else -> "Draft"
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = when (status) {
            "published" -> MaterialTheme.colorScheme.primary
            "completed" -> MaterialTheme.colorScheme.secondary
            "cancelled" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun ServicePlanEditorDialog(
    plan: ServicePlanDto?,
    saving: Boolean,
    errorMessage: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var title by rememberSaveable(plan?.id) { mutableStateOf(plan?.title.orEmpty()) }
    var date by rememberSaveable(plan?.id) { mutableStateOf(plan?.serviceDate.orEmpty()) }
    var time by rememberSaveable(plan?.id) { mutableStateOf(plan?.startTime?.take(5).orEmpty()) }
    var notes by rememberSaveable(plan?.id) { mutableStateOf(plan?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (plan == null) "Create service" else "Edit service") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Service title *") },
                    singleLine = true,
                    enabled = !saving
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Date (YYYY-MM-DD)") },
                    supportingText = { Text("Example: 2026-11-08") },
                    singleLine = true,
                    enabled = !saving
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it.take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Start time (24-hour HH:MM)") },
                    supportingText = { Text("Example: 09:30") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Service notes") },
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
                onClick = { onSave(title, date, time, notes) },
                enabled = !saving && title.isNotBlank()
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (plan == null) "Create service" else "Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun ServicePlanDetailScreen(
    plan: ServicePlanDto,
    availablePlans: List<ServicePlanDto>,
    organizationId: String,
    currentUserId: String,
    canManage: Boolean,
    offlineOnly: Boolean,
    onBack: () -> Unit,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { ServicePlanRepository(SupabaseClientProvider.client) }
    val templateRepository = remember { ServiceTemplateRepository(SupabaseClientProvider.client) }
    val context = LocalContext.current
    val offlineCache = remember(context) { OfflineServiceCache(context) }
    val scope = rememberCoroutineScope()
    var usingOfflineCopy by remember(plan.id, offlineOnly) { mutableStateOf(offlineOnly) }
    val effectiveCanManage = canManage && !usingOfflineCopy
    var items by remember(plan.id) { mutableStateOf<List<ServicePlanItemDto>>(emptyList()) }
    var songs by remember(plan.id) { mutableStateOf<List<SongDto>>(emptyList()) }
    var currentStatus by remember(plan.id) { mutableStateOf(plan.status) }
    var loading by remember(plan.id) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var songPickerOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var customItemOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var copySourceDialogOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var copySourceCandidate by remember(plan.id) { mutableStateOf<ServicePlanDto?>(null) }
    var copySuccessMessage by remember(plan.id) { mutableStateOf("") }
    var templates by remember(plan.id) { mutableStateOf<List<ServiceTemplateDto>>(emptyList()) }
    var templateItemCounts by remember(plan.id) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var templatesLoading by remember(plan.id) { mutableStateOf(false) }
    var templatePickerOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var saveTemplateDialogOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var templateCandidate by remember(plan.id) { mutableStateOf<ServiceTemplateDto?>(null) }
    var templateActionSaving by remember { mutableStateOf(false) }
    var templateDialogError by remember { mutableStateOf("") }
    var templateSuccessMessage by remember(plan.id) { mutableStateOf("") }
    val copySourcePlans = availablePlans
        .filter { it.id != plan.id && it.organizationId == organizationId }
        .sortedWith(compareByDescending<ServicePlanDto> { it.serviceDate.orEmpty() }
            .thenByDescending { it.startTime.orEmpty() }
            .thenBy { it.title.lowercase() })

    suspend fun refreshTemplates() {
        if (!canManage || offlineOnly || usingOfflineCopy) {
            templates = emptyList()
            templateItemCounts = emptyMap()
            return
        }
        templatesLoading = true
        templateDialogError = ""
        try {
            templates = templateRepository.loadTemplates(organizationId)
            templateItemCounts = templateRepository.loadItemCounts(organizationId)
        } catch (_: Exception) {
            templates = emptyList()
            templateItemCounts = emptyMap()
            templateDialogError = "Saved templates could not be loaded. Check your connection and try again."
        } finally {
            templatesLoading = false
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            error = ""
            try {
                if (offlineOnly || usingOfflineCopy) {
                    items = offlineCache.loadPlanItems(currentUserId, organizationId, plan.id)
                    songs = emptyList()
                    usingOfflineCopy = true
                    if (items.isEmpty()) {
                        error = "No saved setlist is available for this service on this device."
                    }
                } else {
                    try {
                        items = repository.loadPlanItems(plan.id, organizationId)
                        songs = repository.loadSongs(organizationId)
                        runCatching {
                            offlineCache.saveServicePlan(
                                currentUserId, organizationId, plan.copy(status = currentStatus), items
                            )
                        }
                        usingOfflineCopy = false
                        refreshTemplates()
                    } catch (_: Exception) {
                        items = offlineCache.loadPlanItems(currentUserId, organizationId, plan.id)
                        songs = emptyList()
                        templates = emptyList()
                        templateItemCounts = emptyMap()
                        usingOfflineCopy = true
                        if (items.isEmpty()) {
                            error = "Could not load this setlist and no offline copy is saved."
                        }
                    }
                }
            } catch (_: Exception) {
                error = "The saved setlist could not be opened on this device."
            } finally {
                loading = false
            }
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            saving = true
            error = ""
            try {
                action()
                reload()
            } catch (exception: Exception) {
                error = exception.message ?: "The change could not be saved."
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(plan.id) { reload() }

    if (songPickerOpen) {
        AlertDialog(
            onDismissRequest = { songPickerOpen = false },
            title = { Text("Add a song") },
            text = {
                if (songs.isEmpty()) {
                    Text("Your song library is empty. Add songs in the Songs tab first.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(songs, key = { it.id }) { song ->
                            TextButton(
                                onClick = {
                                    songPickerOpen = false
                                    runAction {
                                        repository.addSongToPlan(
                                            plan.id, organizationId, song,
                                            (items.maxOfOrNull { it.itemOrder } ?: -1) + 1
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(song.title, fontWeight = FontWeight.SemiBold)
                                    if (song.artist.isNotBlank()) {
                                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songPickerOpen = false }) { Text("Close") } }
        )
    }

    if (customItemOpen) {
        CustomServiceItemDialog(
            saving = saving,
            onDismiss = { customItemOpen = false },
            onSave = { title, type, duration ->
                customItemOpen = false
                runAction {
                    repository.addCustomItem(
                        plan.id, organizationId, title, type, duration,
                        (items.maxOfOrNull { it.itemOrder } ?: -1) + 1
                    )
                }
            }
        )
    }

    if (saveTemplateDialogOpen) {
        SaveServiceTemplateDialog(
            saving = templateActionSaving,
            errorMessage = templateDialogError,
            onDismiss = { if (!templateActionSaving) saveTemplateDialogOpen = false },
            onSave = { name, description ->
                scope.launch {
                    templateActionSaving = true
                    templateDialogError = ""
                    try {
                        val created = templateRepository.saveFromService(
                            servicePlanId = plan.id,
                            organizationId = organizationId,
                            userId = currentUserId,
                            name = name,
                            description = description
                        )
                        saveTemplateDialogOpen = false
                        templateSuccessMessage = "Saved “${created.name}” as a reusable setlist template."
                        refreshTemplates()
                    } catch (exception: Exception) {
                        templateDialogError = exception.message ?: "The setlist template could not be saved."
                    } finally {
                        templateActionSaving = false
                    }
                }
            }
        )
    }

    if (templatePickerOpen) {
        ServiceTemplatePickerDialog(
            templates = templates,
            itemCounts = templateItemCounts,
            loading = templatesLoading,
            saving = saving,
            errorMessage = templateDialogError,
            onDismiss = { templatePickerOpen = false },
            onSelect = { selectedTemplate ->
                templateCandidate = selectedTemplate
                templatePickerOpen = false
            }
        )
    }

    templateCandidate?.let { selectedTemplate ->
        ConfirmApplyTemplateDialog(
            template = selectedTemplate,
            targetServiceTitle = plan.title,
            itemCount = templateItemCounts[selectedTemplate.id] ?: 0,
            saving = saving,
            onDismiss = { if (!saving) templateCandidate = null },
            onConfirm = {
                templateCandidate = null
                templateSuccessMessage = ""
                runAction {
                    val count = templateRepository.applyToService(
                        templateId = selectedTemplate.id,
                        targetPlanId = plan.id,
                        organizationId = organizationId
                    )
                    templateSuccessMessage = "Added $count items from “${selectedTemplate.name}”."
                }
            }
        )
    }

    if (copySourceDialogOpen) {
        CopySetlistSourceDialog(
            plans = copySourcePlans,
            onDismiss = { copySourceDialogOpen = false },
            onSelect = { sourcePlan ->
                copySourceCandidate = sourcePlan
                copySourceDialogOpen = false
            }
        )
    }

    copySourceCandidate?.let { sourcePlan ->
        ConfirmCopySetlistDialog(
            sourcePlan = sourcePlan,
            targetPlan = plan,
            saving = saving,
            onDismiss = { if (!saving) copySourceCandidate = null },
            onConfirm = {
                copySourceCandidate = null
                copySuccessMessage = ""
                runAction {
                    val copiedCount = repository.copySetlist(
                        sourcePlanId = sourcePlan.id,
                        targetPlanId = plan.id,
                        organizationId = organizationId
                    )
                    copySuccessMessage = "$copiedCount setlist items copied from ${sourcePlan.title}."
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (usingOfflineCopy) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        "Offline copy · Read only. This setlist reflects the last successful sync.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to services")
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(plan.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(serviceDateTimeLabel(plan.serviceDate, plan.startTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ServiceStatusLabel(currentStatus)
                if (effectiveCanManage) {
                    IconButton(onClick = onEditPlan, enabled = !saving) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit service details")
                    }
                }
            }
        }

        if (plan.notes.isNotBlank()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Service notes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(plan.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            ServiceAssignmentsSection(
                servicePlanId = plan.id,
                organizationId = organizationId,
                currentUserId = currentUserId,
                canManage = effectiveCanManage && currentStatus != "completed" && currentStatus != "cancelled",
                allowResponses = ServiceAssignmentRules.canRespondToService(currentStatus),
                offlineOnly = offlineOnly || usingOfflineCopy
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Order of worship", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${items.size} items in the setlist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (effectiveCanManage && currentStatus != "completed" && currentStatus != "cancelled") {
                    TextButton(onClick = { songPickerOpen = true }, enabled = !saving) { Text("Add song") }
                }
            }
        }

        if (effectiveCanManage && currentStatus != "completed" && currentStatus != "cancelled") {
            item {
                OutlinedActionButton(
                    label = "Add prayer, reading or other item",
                    onClick = { customItemOpen = true },
                    enabled = !saving
                )
            }
        }

        if (effectiveCanManage && currentStatus != "completed" && currentStatus != "cancelled" && copySourcePlans.isNotEmpty()) {
            item {
                OutlinedActionButton(
                    label = "Copy setlist from another service",
                    onClick = { copySourceDialogOpen = true },
                    enabled = !saving && !loading
                )
            }
        }

        if (effectiveCanManage && currentStatus != "completed" && currentStatus != "cancelled" && templates.isNotEmpty()) {
            item {
                OutlinedActionButton(
                    label = "Apply a saved setlist template",
                    onClick = { templatePickerOpen = true },
                    enabled = !saving && !loading && !templatesLoading
                )
            }
        }

        if (effectiveCanManage && items.isNotEmpty()) {
            item {
                OutlinedActionButton(
                    label = "Save current setlist as a template",
                    onClick = {
                        templateDialogError = ""
                        saveTemplateDialogOpen = true
                    },
                    enabled = !saving && !loading
                )
            }
        }

        if (loading) {
            item { CircularProgressIndicator(Modifier.size(26.dp)) }
        } else if (items.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Your service order is empty", fontWeight = FontWeight.SemiBold)
                        Text("Add songs from your library or include prayers, readings and announcements.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            items(items, key = { it.id }) { serviceItem ->
                val index = items.indexOfFirst { it.id == serviceItem.id }
                ServicePlanItemCard(
                    item = serviceItem,
                    position = index + 1,
                    canManage = effectiveCanManage && currentStatus != "completed" && currentStatus != "cancelled",
                    saving = saving,
                    canMoveUp = index > 0,
                    canMoveDown = index < items.lastIndex,
                    onMoveUp = {
                        runAction { repository.moveItem(items, index, -1, plan.id, organizationId) }
                    },
                    onMoveDown = {
                        runAction { repository.moveItem(items, index, 1, plan.id, organizationId) }
                    },
                    onRemove = {
                        runAction { repository.removeItem(serviceItem.id, plan.id, organizationId) }
                    }
                )
            }
        }

        if (effectiveCanManage) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Service status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (currentStatus) {
                            "draft" -> Button(
                                onClick = {
                                    runAction {
                                        repository.updateStatus(plan.id, organizationId, "published")
                                        currentStatus = "published"
                                    }
                                },
                                enabled = !saving
                            ) { Text("Publish") }
                            "published" -> Button(
                                onClick = {
                                    runAction {
                                        repository.updateStatus(plan.id, organizationId, "completed")
                                        currentStatus = "completed"
                                    }
                                },
                                enabled = !saving
                            ) { Text("Mark completed") }
                        }
                        if (currentStatus == "draft" || currentStatus == "published") {
                            TextButton(
                                onClick = {
                                    runAction {
                                        repository.updateStatus(plan.id, organizationId, "cancelled")
                                        currentStatus = "cancelled"
                                    }
                                },
                                enabled = !saving
                            ) { Text("Cancel service") }
                        }
                    }
                    if (currentStatus == "completed" || currentStatus == "cancelled") {
                        Text("This service is ${currentStatus} and its setlist is read-only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (copySuccessMessage.isNotBlank()) {
            item {
                Text(
                    copySuccessMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (templateSuccessMessage.isNotBlank()) {
            item {
                Text(
                    templateSuccessMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (templateDialogError.isNotBlank() && !saveTemplateDialogOpen) {
            item {
                Text(
                    templateDialogError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (error.isNotBlank()) {
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        if (!offlineOnly) {
            item { TextButton(onClick = { reload() }, enabled = !loading && !saving) { Text("Refresh setlist") } }
        }
    }
}

@Composable
private fun ServicePlanItemCard(
    item: ServicePlanItemDto,
    position: Int,
    canManage: Boolean,
    saving: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(position.toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(item.itemType.replaceFirstChar { it.uppercase() }, item.durationMinutes?.let { "$it min" })
                        .filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.notes.isNotBlank()) {
                    Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canManage) {
                Column {
                    IconButton(onClick = onMoveUp, enabled = !saving && canMoveUp) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move item up")
                    }
                    IconButton(onClick = onMoveDown, enabled = !saving && canMoveDown) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move item down")
                    }
                }
                IconButton(onClick = onRemove, enabled = !saving) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CustomServiceItemDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?) -> Unit
) {
    val types = listOf("prayer", "reading", "announcement", "other")
    var title by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf("prayer") }
    var duration by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add service item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Item title *") }, singleLine = true, enabled = !saving)
                Text("Item type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val next = (types.indexOf(selectedType) + 1) % types.size
                            selectedType = types[next]
                        },
                        enabled = !saving
                    ) { Text(selectedType.replaceFirstChar { it.uppercase() }) }
                    Text("Tap to change type", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Duration in minutes (optional)") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, selectedType, duration.toIntOrNull()) },
                enabled = !saving && title.isNotBlank() &&
                    (duration.isBlank() || (duration.toIntOrNull()?.let { it in 0..600 } == true))
            ) { Text("Add item") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

@Composable
private fun OutlinedActionButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

private fun serviceDateTimeLabel(date: String?, time: String?): String {
    val dateLabel = date?.let {
        runCatching {
            LocalDate.parse(it).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
        }.getOrDefault(it)
    } ?: "Date not set"
    val timeLabel = time?.let {
        runCatching {
            LocalTime.parse(it).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        }.getOrDefault(it.take(5))
    }
    return if (timeLabel == null) dateLabel else "$dateLabel · $timeLabel"
}

@Composable
private fun CopySetlistSourceDialog(
    plans: List<ServicePlanDto>,
    onDismiss: () -> Unit,
    onSelect: (ServicePlanDto) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy from another service") },
        text = {
            if (plans.isEmpty()) {
                Text("There are no other services available in this team.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(plans, key = { it.id }) { sourcePlan ->
                        TextButton(
                            onClick = { onSelect(sourcePlan) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    sourcePlan.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    serviceDateTimeLabel(sourcePlan.serviceDate, sourcePlan.startTime) +
                                        " · " + sourcePlan.status.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConfirmCopySetlistDialog(
    sourcePlan: ServicePlanDto,
    targetPlan: ServicePlanDto,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Copy this setlist?") },
        text = {
            Text(
                "Copy the ordered setlist from “${sourcePlan.title}” into “${targetPlan.title}”? " +
                    "Items will be appended after the current items. Existing items will not be removed, " +
                    "and saved ChordPro chart snapshots will be preserved."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Copy setlist")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

