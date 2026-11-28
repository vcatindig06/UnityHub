package com.unityhub.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.unityhub.app.data.OfflineServiceCache
import com.unityhub.app.data.ServicePlanDto
import com.unityhub.app.data.ServicePlanFilterRules
import com.unityhub.app.data.ServicePlanStatusFilter
import com.unityhub.app.data.ServicePlanItemDto
import com.unityhub.app.data.ServicePlanRepository
import com.unityhub.app.data.ServiceRunSheetRules
import com.unityhub.app.data.ServiceTemplateDto
import com.unityhub.app.data.ServiceTemplateRepository
import com.unityhub.app.data.ServiceAssignmentRules
import com.unityhub.app.data.SongDto
import com.unityhub.app.data.SongSetlistDto
import com.unityhub.app.data.SongSetlistRepository
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicePlanningScreen(
    organizationId: String,
    userId: String,
    role: String?,
    openServicePlanId: String? = null,
    openServicePlanRequestId: String? = null,
    onOpenServicePlanHandled: () -> Unit = {},
    openRehearsalId: String? = null,
    openRehearsalRequestId: String? = null,
    onOpenRehearsalHandled: () -> Unit = {},
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
    var refreshing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var assignmentOverviewOpen by rememberSaveable { mutableStateOf(false) }
    var rehearsalsOpen by rememberSaveable { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<ServicePlanDto?>(null) }
    var hasLoadedPlans by remember { mutableStateOf(false) }
    var consumedNavigationRequestId by remember { mutableStateOf<String?>(null) }
    var calendarView by rememberSaveable { mutableStateOf("month") }
    var selectedCalendarDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var displayedCalendarMonth by rememberSaveable { mutableStateOf(java.time.YearMonth.now().toString()) }
    var serviceStatusFilter by rememberSaveable(organizationId) {
        mutableStateOf(ServicePlanStatusFilter.ALL)
    }
    var serviceSearch by rememberSaveable(organizationId) { mutableStateOf("") }
    val filteredPlans = remember(plans, serviceStatusFilter, serviceSearch) {
        ServicePlanFilterRules.filter(plans, serviceStatusFilter, serviceSearch)
    }
    val hasActiveServiceFilters = serviceStatusFilter != ServicePlanStatusFilter.ALL || serviceSearch.isNotBlank()
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

    fun reload(showIndicator: Boolean = false) {
        scope.launch {
            refreshing = showIndicator
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
                refreshing = false
            }
        }
    }

    BackHandler(enabled = rehearsalsOpen || selectedPlan != null || assignmentOverviewOpen) {
        when {
            rehearsalsOpen -> rehearsalsOpen = false
            selectedPlan != null -> {
                selectedPlan = null
                reload()
            }
            assignmentOverviewOpen -> assignmentOverviewOpen = false
        }
    }

    LaunchedEffect(openRehearsalRequestId) {
        if (!openRehearsalRequestId.isNullOrBlank()) rehearsalsOpen = true
    }

    if (rehearsalsOpen) {
        RehearsalManagementScreen(
            organizationId = organizationId,
            userId = userId,
            role = role,
            onBack = { rehearsalsOpen = false },
            openRehearsalId = openRehearsalId,
            openRehearsalRequestId = openRehearsalRequestId,
            onOpenRehearsalHandled = onOpenRehearsalHandled,
            onOpenServicePlan = { servicePlanId ->
                scope.launch {
                    val availablePlans = runCatching {
                        if (plans.isEmpty()) repository.loadPlans(organizationId) else plans
                    }.getOrElse {
                        error = "The linked service could not be loaded. Check your connection and try again."
                        emptyList()
                    }
                    val linked = availablePlans.firstOrNull { it.id == servicePlanId }
                    if (linked != null) {
                        plans = availablePlans
                        rehearsalsOpen = false
                        selectedPlan = linked
                    } else if (error.isBlank()) {
                        error = "The linked service is no longer available."
                        rehearsalsOpen = false
                    }
                }
            },
            modifier = modifier
        )
        return
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
            canManage = canManage,
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
            onDismiss = {
                editorOpen = false
            },
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

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (!offlineOnly && !loading && !saving) reload(showIndicator = true) },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                if (hasActiveServiceFilters) "${filteredPlans.size} of ${plans.size} planned services" else "${plans.size} planned services",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!usingOfflineCopy && !offlineOnly) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        templatesDialogOpen = true
                                        reloadTemplateLibrary()
                                    },
                                    enabled = !saving
                                ) { Text("Templates") }
                                if (canManage) {
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
                            }
                        }
                    }
                    if (error.isNotBlank() && !editorOpen) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
        }

        if (!offlineOnly && !usingOfflineCopy) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { rehearsalsOpen = true },
                        modifier = Modifier.weight(1f),
                        enabled = !saving
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Rehearsals")
                    }
                    if (canManage) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { assignmentOverviewOpen = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Assignments")
                        }
                    }
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
                        Text("No services planned", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (canManage) "Create a service to get started."
                            else "A team leader can create a service plan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = serviceSearch,
                    onValueChange = { serviceSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search services") },
                    placeholder = { Text("Title, date, or notes") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (serviceSearch.isNotBlank()) {
                        {
                            IconButton(onClick = { serviceSearch = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    } else null
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServicePlanStatusFilter.values().forEach { filter ->
                        FilterChip(
                            selected = serviceStatusFilter == filter,
                            onClick = { serviceStatusFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = calendarView == "month", onClick = { calendarView = "month" }, label = { Text("Month") })
                    FilterChip(selected = calendarView == "week", onClick = { calendarView = "week" }, label = { Text("Week") })
                    FilterChip(selected = calendarView == "list", onClick = { calendarView = "list" }, label = { Text("List") })
                }
            }
            if (filteredPlans.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "No services match these filters.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = {
                                    serviceSearch = ""
                                    serviceStatusFilter = ServicePlanStatusFilter.ALL
                                }
                            ) {
                                Text("Clear filters")
                            }
                        }
                    }
                }
            } else if (calendarView == "list") {
                items(filteredPlans, key = { it.id }) { plan ->
                    ServicePlanCard(plan = plan, onClick = { selectedPlan = plan })
                }
            } else {
                item(key = "service-calendar-content") {
                    ServiceCalendarContent(
                        plans = filteredPlans,
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

        }
    }
}

@Composable
private fun ServicePlanCard(plan: ServicePlanDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
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
                NativeDatePickerField(
                    value = date,
                    label = "Service date",
                    enabled = !saving,
                    onValueChange = { date = it }
                )
                NativeTimePickerField(
                    value = time,
                    label = "Service start",
                    enabled = !saving,
                    onValueChange = { time = it }
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
    val songSetlistRepository = remember { SongSetlistRepository(SupabaseClientProvider.client) }
    val context = LocalContext.current
    val offlineCache = remember(context) { OfflineServiceCache(context) }
    val scope = rememberCoroutineScope()
    var usingOfflineCopy by remember(plan.id, offlineOnly) { mutableStateOf(offlineOnly) }
    val effectiveCanManage = canManage && !usingOfflineCopy
    var items by remember(plan.id) { mutableStateOf<List<ServicePlanItemDto>>(emptyList()) }
    var songs by remember(plan.id) { mutableStateOf<List<SongDto>>(emptyList()) }
    var savedSongSetlists by remember(plan.id) { mutableStateOf<List<SongSetlistDto>>(emptyList()) }
    var songSetlistCounts by remember(plan.id) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var currentStatus by remember(plan.id) { mutableStateOf(plan.status) }
    var loading by remember(plan.id) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var songPickerOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var songSetlistPickerOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var customItemOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var durationEditingItem by remember(plan.id) { mutableStateOf<ServicePlanItemDto?>(null) }
    var notesEditingItem by remember(plan.id) { mutableStateOf<ServicePlanItemDto?>(null) }
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
    var liveStageOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
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
                    savedSongSetlists = emptyList()
                    songSetlistCounts = emptyMap()
                    usingOfflineCopy = true
                    if (items.isEmpty()) {
                        error = "No saved setlist is available for this service on this device."
                    }
                } else {
                    try {
                        items = repository.loadPlanItems(plan.id, organizationId)
                        songs = repository.loadSongs(organizationId)
                        val storedSongLists = songSetlistRepository.loadSetlists(organizationId)
                        val storedSongListItems = songSetlistRepository.loadAllItems(organizationId)
                        val loadedSongSetlistCounts = storedSongListItems.groupingBy { it.setlistId }.eachCount()
                        songSetlistCounts = loadedSongSetlistCounts
                        savedSongSetlists = storedSongLists.filter { (loadedSongSetlistCounts[it.id] ?: 0) >= 2 }
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
                        savedSongSetlists = emptyList()
                        songSetlistCounts = emptyMap()
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

    if (liveStageOpen) {
        LiveStageScreen(
            serviceTitle = plan.title,
            items = items,
            onBack = { liveStageOpen = false },
            modifier = modifier
        )
        return
    }

    if (songPickerOpen) {
        AlertDialog(
            onDismissRequest = { songPickerOpen = false },
            title = { Text("Add a song or setlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedActionButton(
                        label = "Add a setlist",
                        onClick = {
                            songPickerOpen = false
                            songSetlistPickerOpen = true
                        },
                        enabled = !saving && savedSongSetlists.isNotEmpty()
                    )
                    if (savedSongSetlists.isEmpty()) {
                        Text(
                            "Create a saved song setlist with at least two songs in the Songs tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (songs.isEmpty()) {
                        Text("Your song library is empty. Add songs in the Songs tab first.")
                    } else {
                        Text("Or choose an individual song", style = MaterialTheme.typography.labelLarge)
                        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                }
            },
            confirmButton = { TextButton(onClick = { songPickerOpen = false }) { Text("Close") } }
        )
    }

    if (durationEditingItem != null) {
        val itemToEdit = durationEditingItem!!
        EditServiceItemDurationDialog(
            item = itemToEdit,
            saving = saving,
            errorMessage = error,
            onDismiss = { if (!saving) durationEditingItem = null },
            onSave = { duration ->
                runAction {
                    repository.updateItemDuration(
                        itemId = itemToEdit.id,
                        planId = plan.id,
                        organizationId = organizationId,
                        durationMinutes = duration
                    )
                    durationEditingItem = null
                }
            }
        )
    }

    if (notesEditingItem != null) {
        val itemToEdit = notesEditingItem!!
        EditServiceItemNotesDialog(
            item = itemToEdit,
            saving = saving,
            errorMessage = error,
            onDismiss = { if (!saving) notesEditingItem = null },
            onSave = { notes ->
                runAction {
                    repository.updateItemNotes(
                        itemId = itemToEdit.id,
                        planId = plan.id,
                        organizationId = organizationId,
                        notes = notes
                    )
                    notesEditingItem = null
                }
            }
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

    if (songSetlistPickerOpen) {
        AlertDialog(
            onDismissRequest = { songSetlistPickerOpen = false },
            title = { Text("Add a saved song setlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose a setlist containing two or more songs.")
                    if (savedSongSetlists.isEmpty()) {
                        Text("No eligible song setlists are available yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(savedSongSetlists, key = { it.id }) { setlist ->
                                TextButton(
                                    onClick = {
                                        songSetlistPickerOpen = false
                                        runAction {
                                            val added = repository.addSongSetlistToPlan(
                                                planId = plan.id,
                                                organizationId = organizationId,
                                                songSetlistId = setlist.id
                                            )
                                            error = ""
                                            copySuccessMessage = "Added $added songs from “${setlist.name}”."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !saving
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(setlist.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${songSetlistCounts[setlist.id] ?: 0} songs",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songSetlistPickerOpen = false }) { Text("Close") } }
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
                    Text(
                        plan.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
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

        if (!loading && items.isNotEmpty()) {
            item {
                Button(
                    onClick = { liveStageOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Start Live Stage")
                }
            }
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

        if (items.isNotEmpty()) {
            item {
                ServiceRunSheetSummaryCard(
                    summary = ServiceRunSheetRules.summarize(
                        itemDurations = items.map { it.durationMinutes },
                        serviceStartTime = plan.startTime
                    )
                )
            }
        }

        if (effectiveCanManage && currentStatus != "completed" && currentStatus != "cancelled") {
            item {
                OutlinedActionButton(
                    label = "Add prayer, reading or custom item",
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
                    },
                    onEditDuration = { durationEditingItem = serviceItem },
                    onEditNotes = { notesEditingItem = serviceItem }
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
    onRemove: () -> Unit,
    onEditDuration: () -> Unit,
    onEditNotes: () -> Unit
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
                if (canManage) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onEditDuration,
                            enabled = !saving,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (item.durationMinutes == null) "Set duration" else "Edit duration")
                        }
                        TextButton(
                            onClick = onEditNotes,
                            enabled = !saving,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (item.notes.isBlank()) "Add notes" else "Edit notes")
                        }
                    }
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
private fun ServiceRunSheetSummaryCard(
    summary: com.unityhub.app.data.ServiceRunSheetSummary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "RUN OF SHOW",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                ServiceRunSheetRules.formatDuration(summary.totalMinutes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            when {
                summary.itemsWithoutDuration > 0 -> Text(
                    "${summary.itemsWithoutDuration} ${if (summary.itemsWithoutDuration == 1) "item needs" else "items need"} a duration before a finish time can be estimated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                summary.estimatedFinishTime != null -> Text(
                    "Estimated finish · ${summary.estimatedFinishTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                else -> Text(
                    "Set the service start time to estimate the finish.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun EditServiceItemNotesDialog(
    item: ServicePlanItemDto,
    saving: Boolean,
    errorMessage: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var notes by rememberSaveable(item.id) { mutableStateOf(item.notes) }
    val validNotes = notes.length <= 500

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Set item notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(501) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes and cues") },
                    placeholder = { Text("e.g. Scripture reference, transition cue, or leader reminder") },
                    supportingText = { Text("${notes.length}/500 characters") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !saving,
                    isError = !validNotes
                )
                if (!validNotes) {
                    Text("Item notes must be 500 characters or fewer.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(notes) }, enabled = !saving && validNotes) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save notes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditServiceItemDurationDialog(
    item: ServicePlanItemDto,
    saving: Boolean,
    errorMessage: String,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit
) {
    var duration by rememberSaveable(item.id) {
        mutableStateOf(item.durationMinutes?.toString().orEmpty())
    }
    val parsedDuration = duration.toIntOrNull()
    val validDuration = duration.isBlank() || (parsedDuration != null && parsedDuration in 0..600)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Set item duration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Duration in minutes") },
                    supportingText = { Text("Enter 0–600 minutes. Leave blank to remove the duration.") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !validDuration
                )
                if (!validDuration) {
                    Text("Duration must be between 0 and 600 minutes.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(if (duration.isBlank()) null else parsedDuration) },
                enabled = !saving && validDuration
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save duration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun CustomServiceItemDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?) -> Unit
) {
    val types = listOf("prayer", "reading", "announcement")
    var title by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf("prayer") }
    var duration by rememberSaveable { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val customSelected = selectedType == "other"

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add service item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Item type", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (customSelected) "Custom" else selectedType.replaceFirstChar { it.uppercase() })
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        (types + "custom").distinct().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(if (type == "custom") "Custom" else type.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    selectedType = if (type == "custom") "other" else type
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (customSelected) "Custom item name *" else "Item title *") },
                    singleLine = true,
                    enabled = !saving
                )
                if (customSelected) {
                    Text(
                        "This item will be saved as a custom service item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            LocalDate.parse(it).format(DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.getDefault()))
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



@Composable
private fun NativeDatePickerField(
    value: String,
    label: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val selected = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onValueChange(LocalDate.of(year, month + 1, day).toString()) },
                selected.year,
                selected.monthValue - 1,
                selected.dayOfMonth
            ).show()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null)
        Text("$label · " + runCatching {
            LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.getDefault()))
        }.getOrDefault("Select date"))
    }
}

@Composable
private fun NativeTimePickerField(
    value: String,
    label: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val selected = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0))
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onValueChange(LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"))) },
                selected.hour,
                selected.minute,
                false
            ).show()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null)
        Text("$label · " + runCatching {
            LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        }.getOrDefault("Select time"))
    }
}
