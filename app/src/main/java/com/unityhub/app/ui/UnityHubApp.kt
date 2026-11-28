package com.unityhub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.unityhub.app.ui.components.UnityHubLoadingScreen
import com.unityhub.app.ui.components.UnityHubLogoMark
import com.unityhub.app.data.OfflineWorkspaceCache
import com.unityhub.app.data.OfflineWorkspaceSnapshot
import com.unityhub.app.data.OrganizationDirectoryDto
import com.unityhub.app.data.OrganizationJoinRequestDto
import com.unityhub.app.data.OrganizationRepository
import com.unityhub.app.data.OrganizationWorkspace
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.NotificationRules
import com.unityhub.app.data.NotificationsRepository
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.sync.OfflineSyncScheduler
import com.unityhub.app.push.UnityHubPushNavigation
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

private data class AppDestination(
    val label: String,
    val icon: ImageVector
)

private const val HOME_TAB_INDEX = 0
private const val SONGS_TAB_INDEX = 1
private const val SERVICES_TAB_INDEX = 2
private const val COMMUNITY_TAB_INDEX = 3

private val destinations = listOf(
    AppDestination("Home", Icons.Default.Home),
    AppDestination("Songs", Icons.Default.LibraryMusic),
    AppDestination("Services", Icons.Default.CalendarMonth),
    AppDestination("Community", Icons.Default.Groups)
)

@Composable
fun UnityHubApp(
    onSignOut: () -> Unit,
    pushNavigation: UnityHubPushNavigation? = null,
    onPushNavigationHandled: () -> Unit = {},
    themeMode: String = "system",
    onThemeModeChanged: (String) -> Unit = {},
    fontScale: Float = 1f,
    onFontScaleChanged: (Float) -> Unit = {}
) {
    val client = remember { SupabaseClientProvider.client }
    val userId = remember { client.auth.currentSessionOrNull()?.user?.id.orEmpty() }
    val repository = remember { OrganizationRepository(client) }
    val context = LocalContext.current
    val offlineWorkspaceCache = remember(context) { OfflineWorkspaceCache(context) }
    val scope = rememberCoroutineScope()

    var workspace by remember { mutableStateOf<OrganizationWorkspace?>(null) }
    var offlineWorkspace by remember { mutableStateOf<OfflineWorkspaceSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var actionInProgress by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                val loadedWorkspace = repository.loadWorkspace(userId)
                workspace = loadedWorkspace
                offlineWorkspace = null
                val organization = loadedWorkspace.organization
                if (organization == null) {
                    offlineWorkspaceCache.clear(userId)
                } else {
                    offlineWorkspaceCache.save(userId, organization.id, organization.name)
                }
            } catch (_: Exception) {
                offlineWorkspace = offlineWorkspaceCache.load(userId)
                if (offlineWorkspace == null) {
                    errorMessage = "UnityHub could not load your team information. Check your internet connection and try again."
                }
            } finally {
                loading = false
            }
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            actionInProgress = true
            errorMessage = ""
            try {
                action()
                val refreshedWorkspace = repository.loadWorkspace(userId)
                workspace = refreshedWorkspace
                offlineWorkspace = null
                refreshedWorkspace.organization?.let { organization ->
                    offlineWorkspaceCache.save(userId, organization.id, organization.name)
                } ?: offlineWorkspaceCache.clear(userId)
            } catch (exception: Exception) {
                errorMessage = friendlyMessage(exception)
            } finally {
                actionInProgress = false
                loading = false
            }
        }
    }

    LaunchedEffect(userId) {
        if (userId.isBlank()) {
            errorMessage = "Your sign-in session could not be read. Please sign in again."
            loading = false
        } else {
            reload()
        }
    }

    LaunchedEffect(userId, workspace?.organization?.id) {
        val activeOrganizationId = workspace?.organization?.id
        if (userId.isNotBlank() && !activeOrganizationId.isNullOrBlank()) {
            OfflineSyncScheduler.schedule(context, userId, activeOrganizationId)
        } else if (workspace != null && userId.isNotBlank()) {
            OfflineSyncScheduler.cancel(context, userId)
        }
    }

    when {
        loading && workspace == null -> LoadingScreen()
        workspace == null && offlineWorkspace != null -> OfflineWorkspaceScreen(
            organizationId = offlineWorkspace!!.organizationId,
            organizationName = offlineWorkspace!!.organizationName,
            userId = userId,
            onRetry = { reload() },
            onSignOut = onSignOut
        )
        workspace == null -> ErrorScreen(
            message = errorMessage.ifBlank { "Your workspace could not be loaded." },
            onRetry = { reload() },
            onSignOut = onSignOut
        )
        workspace?.organization == null -> OrganizationOnboardingScreen(
            workspace = workspace!!,
            loading = actionInProgress,
            errorMessage = errorMessage,
            onCreateOrganization = { name ->
                runAction { repository.createOrganization(name) }
            },
            onRequestToJoin = { organizationId ->
                runAction { repository.requestToJoin(organizationId) }
            },
            onRefresh = { reload() },
            onSignOut = onSignOut
        )
        else -> WorkspaceDashboard(
            workspace = workspace!!,
            userId = userId,
            loading = actionInProgress,
            errorMessage = errorMessage,
            onReviewRequest = { requestId, approve ->
                runAction { repository.reviewJoinRequest(requestId, approve) }
            },
            onRefresh = { reload() },
            onSignOut = onSignOut,
            pushNavigation = pushNavigation,
            onPushNavigationHandled = onPushNavigationHandled,
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            fontScale = fontScale,
            onFontScaleChanged = onFontScaleChanged
        )
    }
}

private fun friendlyMessage(exception: Exception): String {
    val message = exception.message.orEmpty().lowercase()
    return when {
        "already belongs" in message -> "This account already belongs to a worship team. Refresh the screen."
        "organization name" in message -> "Use a team name between 2 and 100 characters."
        "only an administrator" in message -> "Only an administrator can approve or decline requests."
        "request is not pending" in message -> "This request has already been reviewed. Refresh the screen."
        "organization not found" in message -> "That worship team is no longer available."
        else -> "The action could not be completed. Check your connection and try again."
    }
}

@Composable
private fun LoadingScreen() {
    UnityHubLoadingScreen()
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Unable to open UnityHub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizationOnboardingScreen(
    workspace: OrganizationWorkspace,
    loading: Boolean,
    errorMessage: String,
    onCreateOrganization: (String) -> Unit,
    onRequestToJoin: (String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(1) }
    var selectedPath by rememberSaveable { mutableStateOf("") }
    var organizationName by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = step > 1) {
        step = 1
        selectedPath = ""
    }

    val requestByOrganization = workspace.myRequests.associateBy { it.organizationId }
    val filteredOrganizations = workspace.directory
        .filter { it.name.contains(search.trim(), ignoreCase = true) }
        .sortedBy { it.name.lowercase() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    UnityHubLogoMark(
                        modifier = Modifier.padding(start = 12.dp).size(34.dp),
                        contentDescription = null
                    )
                },
                title = {
                    Column {
                        Text("Set up your team", fontWeight = FontWeight.Bold)
                        Text(
                            "A few steps to your worship workspace",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Welcome to UnityHub",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Connect your account to your church or worship team. You can create a new workspace or ask to join one that already exists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1, 2).forEach { number ->
                        val active = number <= step
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "STEP $number",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (number == 1) "Choose an option" else "Complete setup",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            if (step == 1) {
                item {
                    Text(
                        "How would you like to get started?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPath = "create"
                                step = 2
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                shape = RoundedCornerShape(13.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(12.dp).size(27.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("Create a new church or team", fontWeight = FontWeight.Bold)
                                Text(
                                    "Start a workspace and become its first administrator.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPath = "join"
                                step = 2
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                shape = RoundedCornerShape(13.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(12.dp).size(27.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("Join an existing church or team", fontWeight = FontWeight.Bold)
                                Text(
                                    "Find your team and send a request to its administrator.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Text(
                        "Not sure? Ask your worship leader whether your church already has a UnityHub workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    TextButton(
                        onClick = {
                            step = 1
                            selectedPath = ""
                        },
                        enabled = !loading
                    ) {
                        Text("← Back to options")
                    }
                }

                if (selectedPath == "create") {
                    item {
                        Text(
                            "Create your workspace",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Enter the church or worship-team name exactly as your members will recognize it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Church or team details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = organizationName,
                                    onValueChange = { organizationName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Church / worship team name") },
                                    placeholder = { Text("e.g. Unity Worship Team") },
                                    singleLine = true,
                                    enabled = !loading,
                                    isError = organizationName.isNotBlank() && organizationName.trim().length !in 2..100,
                                    supportingText = {
                                        Text(
                                            if (organizationName.isBlank()) {
                                                "Use 2–100 characters."
                                            } else {
                                                "${organizationName.trim().length}/100 characters"
                                            }
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                                )
                                Text(
                                    "Your account will become the first administrator. You can invite members and approve their join requests from the Team section.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { onCreateOrganization(organizationName.trim()) },
                                    enabled = !loading && organizationName.trim().length in 2..100,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Create church workspace")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            "Find your church or team",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Search the directory. When you request to join, an administrator must approve your membership before the workspace opens.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search church or team name") },
                            placeholder = { Text("Enter a team name") },
                            singleLine = true,
                            enabled = !loading
                        )
                    }
                    if (filteredOrganizations.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        if (workspace.directory.isEmpty()) {
                                            "No workspaces are listed yet"
                                        } else {
                                            "No matching teams found"
                                        },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (workspace.directory.isEmpty()) {
                                            "Ask your church administrator to create a UnityHub workspace, or go back and create one for your team."
                                        } else {
                                            "Try a shorter search or check the spelling. You can also refresh the directory."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredOrganizations, key = { it.organizationId }) { organization ->
                            OrganizationDirectoryCard(
                                organization = organization,
                                request = requestByOrganization[organization.organizationId],
                                loading = loading,
                                onRequest = { onRequestToJoin(organization.organizationId) }
                            )
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Refresh team directory")
                        }
                    }
                }
            }

            if (errorMessage.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationDirectoryCard(
    organization: OrganizationDirectoryDto,
    request: OrganizationJoinRequestDto?,
    loading: Boolean,
    onRequest: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(organization.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (request?.status) {
                "pending" -> Text(
                    "Request sent · Waiting for administrator approval",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
                "rejected" -> Text(
                    "Your previous request was declined. You may submit a new request.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                else -> Text(
                    "Send a membership request to this worship team.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = onRequest,
                enabled = !loading && request?.status != "pending",
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (request?.status == "pending") "Waiting for approval"
                    else if (request?.status == "rejected") "Request to join again"
                    else "Request to join"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceDashboard(
    workspace: OrganizationWorkspace,
    userId: String,
    loading: Boolean,
    errorMessage: String,
    onReviewRequest: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    pushNavigation: UnityHubPushNavigation?,
    onPushNavigationHandled: () -> Unit,
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
    fontScale: Float,
    onFontScaleChanged: (Float) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(HOME_TAB_INDEX) }
    var tabHistory by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showTeamManagement by rememberSaveable { mutableStateOf(false) }
    var showPersonalProfile by rememberSaveable { mutableStateOf(false) }
    var showTeamChat by rememberSaveable { mutableStateOf(false) }
    var showSharedFiles by rememberSaveable { mutableStateOf(false) }
    var requestedChatConversationId by remember { mutableStateOf<String?>(null) }
    var requestedChatMessageId by remember { mutableStateOf<String?>(null) }
    var showMemberProfile by rememberSaveable { mutableStateOf(false) }
    var selectedMemberProfile by remember { mutableStateOf<ServiceMemberOption?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var chatReturnDestination by rememberSaveable { mutableStateOf("home") }
    var requestedServicePlanId by remember { mutableStateOf<String?>(null) }
    var serviceNavigationRequestId by remember { mutableStateOf<String?>(null) }
    var requestedRehearsalId by remember { mutableStateOf<String?>(null) }
    var rehearsalNavigationRequestId by remember { mutableStateOf<String?>(null) }
    var songEditorRequestId by rememberSaveable { mutableIntStateOf(0) }
    var createSetlistRequestId by rememberSaveable { mutableIntStateOf(0) }
    val notificationsRepository = remember { NotificationsRepository(SupabaseClientProvider.client) }
    var unreadNotificationCount by remember(userId) { mutableStateOf(0) }
    val organization = workspace.organization ?: return

    LaunchedEffect(userId) {
        if (userId.isBlank()) return@LaunchedEffect
        try {
            notificationsRepository.observeNotifications(userId).collect { incoming ->
                unreadNotificationCount = NotificationRules.unreadCount(incoming)
            }
        } catch (_: Exception) {
            runCatching { notificationsRepository.loadNotifications(userId) }
                .onSuccess { unreadNotificationCount = NotificationRules.unreadCount(it) }
        }
    }

    fun navigateToTab(destination: Int) {
        tabHistory = AppBackNavigationRules.recordTabChange(tabHistory, selectedTab, destination)
        selectedTab = destination
    }

    fun returnFromTeamChat() {
        showTeamChat = false
        requestedChatConversationId = null
        requestedChatMessageId = null
        when (chatReturnDestination) {
            "team" -> showTeamManagement = true
            "notifications" -> showNotifications = true
            "community" -> Unit
        }
        chatReturnDestination = "home"
    }

    BackHandler(
        enabled = showMemberProfile || showNotifications || showTeamManagement || showTeamChat || showSharedFiles || showPersonalProfile || showSettings ||
            tabHistory.isNotEmpty() || selectedTab != HOME_TAB_INDEX
    ) {
        when {
            showMemberProfile -> showMemberProfile = false
            showTeamChat -> returnFromTeamChat()
            showSharedFiles -> showSharedFiles = false
            showSettings -> showSettings = false
            showPersonalProfile -> showPersonalProfile = false
            showTeamManagement -> showTeamManagement = false
            showNotifications -> showNotifications = false
            else -> {
                val previousTab = AppBackNavigationRules.previousTab(selectedTab, tabHistory)
                if (previousTab != null) {
                    selectedTab = previousTab
                    tabHistory = AppBackNavigationRules.popHistory(tabHistory)
                }
            }
        }
    }

    LaunchedEffect(
        pushNavigation?.notificationId,
        pushNavigation?.servicePlanId,
        pushNavigation?.rehearsalId,
        pushNavigation?.teamMessageId,
        pushNavigation?.organizationId,
        pushNavigation?.conversationId
    ) {
        val navigation = pushNavigation ?: return@LaunchedEffect
        showNotifications = false
        showSharedFiles = false
        showTeamManagement = false
        showPersonalProfile = false
        when {
            !navigation.teamMessageId.isNullOrBlank() -> {
                if (navigation.organizationId == organization.id &&
                    !navigation.conversationId.isNullOrBlank()
                ) {
                    requestedServicePlanId = null
                    serviceNavigationRequestId = null
                    requestedRehearsalId = null
                    rehearsalNavigationRequestId = null
                    chatReturnDestination = "home"
                    requestedChatConversationId = navigation.conversationId
                    requestedChatMessageId = navigation.teamMessageId
                    showTeamChat = true
                } else {
                    showNotifications = true
                }
            }
            !navigation.rehearsalId.isNullOrBlank() -> {
                requestedRehearsalId = navigation.rehearsalId
                rehearsalNavigationRequestId = navigation.notificationId ?: navigation.rehearsalId
                navigateToTab(SERVICES_TAB_INDEX)
            }
            !navigation.servicePlanId.isNullOrBlank() -> {
                requestedServicePlanId = navigation.servicePlanId
                serviceNavigationRequestId = navigation.notificationId ?: navigation.servicePlanId
                navigateToTab(SERVICES_TAB_INDEX)
            }
            else -> showNotifications = true
        }
        onPushNavigationHandled()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showMemberProfile || showNotifications || showTeamManagement || showTeamChat || showSharedFiles || showPersonalProfile || showSettings) {
                        IconButton(
                            onClick = {
                                when {
                                    showMemberProfile -> showMemberProfile = false
                                    showTeamChat -> returnFromTeamChat()
                                    showSharedFiles -> showSharedFiles = false
                                    showSettings -> showSettings = false
                                    showPersonalProfile -> showPersonalProfile = false
                                    showTeamManagement -> showTeamManagement = false
                                    showNotifications -> showNotifications = false
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                    } else {
                        UnityHubLogoMark(
                            modifier = Modifier.padding(start = 4.dp).size(30.dp),
                            contentDescription = null
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        when {
                            showNotifications -> "Notifications"
                            showSettings -> "Settings"
                            showPersonalProfile -> "Profile"
                            showTeamManagement -> "Team"
                            showTeamChat -> "Team Chat"
                            showSharedFiles -> "Shared files"
                            selectedTab == COMMUNITY_TAB_INDEX -> "Community"
                            selectedTab == SONGS_TAB_INDEX -> "Songs"
                            selectedTab == SERVICES_TAB_INDEX -> "Services"
                            else -> "Home"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!showNotifications && !showTeamManagement && !showTeamChat && !showSharedFiles && !showPersonalProfile && !showSettings) {
                        IconButton(
                            onClick = {
                                chatReturnDestination = if (selectedTab == COMMUNITY_TAB_INDEX) "community" else "home"
                                showTeamChat = true
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Team Chat", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { showNotifications = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationCount > 0) {
                                        Badge {
                                            Text(if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications", modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!showNotifications && !showTeamManagement && !showTeamChat && !showSharedFiles && !showPersonalProfile && !showSettings) {
                WorkspaceBottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { destination -> navigateToTab(destination) },
                    onPrimaryAction = {
                        if (selectedTab == SONGS_TAB_INDEX) {
                            createSetlistRequestId += 1
                        } else {
                            songEditorRequestId += 1
                            navigateToTab(SONGS_TAB_INDEX)
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when {
            showSettings -> AccountSettingsScreen(
                organizationName = organization.name,
                role = roleLabel(workspace.role),
                onSignOut = onSignOut,
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged,
                fontScale = fontScale,
                onFontScaleChanged = onFontScaleChanged,
                onOpenProfileDetails = {
                    showSettings = false
                    showPersonalProfile = true
                },
                modifier = Modifier.padding(innerPadding)
            )
            showNotifications -> NotificationsScreen(
                userId = userId,
                currentOrganizationId = organization.id,
                onOpenServicePlan = { notification ->
                    val servicePlanId = notification.servicePlanId
                    if (!servicePlanId.isNullOrBlank()) {
                        showNotifications = false
                        requestedServicePlanId = servicePlanId
                        serviceNavigationRequestId = notification.id
                        requestedRehearsalId = null
                        rehearsalNavigationRequestId = null
                        navigateToTab(SERVICES_TAB_INDEX)
                    }
                },
                onOpenRehearsal = { notification ->
                    val rehearsalId = notification.rehearsalId
                    if (!rehearsalId.isNullOrBlank()) {
                        showNotifications = false
                        requestedRehearsalId = rehearsalId
                        rehearsalNavigationRequestId = notification.id
                        requestedServicePlanId = null
                        serviceNavigationRequestId = null
                        navigateToTab(SERVICES_TAB_INDEX)
                    }
                },
                onOpenTeamChat = { notification ->
                    if (
                        notification.organizationId == organization.id &&
                        !notification.teamMessageId.isNullOrBlank() &&
                        !notification.conversationId.isNullOrBlank()
                    ) {
                        requestedServicePlanId = null
                        serviceNavigationRequestId = null
                        requestedRehearsalId = null
                        rehearsalNavigationRequestId = null
                        chatReturnDestination = "notifications"
                        requestedChatConversationId = notification.conversationId
                        requestedChatMessageId = notification.teamMessageId
                        showNotifications = false
                        showTeamChat = true
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            showMemberProfile -> selectedMemberProfile?.let { member ->
                MemberProfileScreen(
                    organizationId = organization.id,
                    member = member,
                    onBack = { showMemberProfile = false },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            showPersonalProfile -> ProfileOverviewScreen(
                organizationId = organization.id,
                organizationName = organization.name,
                userId = userId,
                role = roleLabel(workspace.role),
                onOpenSettings = { showSettings = true },
                modifier = Modifier.padding(innerPadding)
            )
            showTeamManagement -> TeamManagementScreen(
                workspace = workspace,
                currentUserId = userId,
                onReviewRequest = onReviewRequest,
                errorMessage = errorMessage,
                onWorkspaceChanged = onRefresh,
                onOpenMemberProfile = { member ->
                    selectedMemberProfile = member
                    showMemberProfile = true
                },
                modifier = Modifier.padding(innerPadding)
            )
            showTeamChat -> TeamChatScreen(
                organizationId = organization.id,
                organizationName = organization.name,
                userId = userId,
                openConversationId = requestedChatConversationId,
                openMessageId = requestedChatMessageId,
                onOpenMemberProfile = { member ->
                    selectedMemberProfile = member
                    showMemberProfile = true
                },
                onBack = { returnFromTeamChat() },
                modifier = Modifier.padding(innerPadding)
            )
            showSharedFiles -> SharedFilesScreen(
                organizationId = organization.id,
                userId = userId,
                modifier = Modifier.padding(innerPadding)
            )
            else -> when (selectedTab) {
                HOME_TAB_INDEX -> HomeDashboardScreen(
                    organizationId = organization.id,
                    userId = userId,
                    role = workspace.role,
                    memberCount = workspace.members.size,
                    pendingCount = workspace.pendingRequests.size,
                    onOpenSongs = { navigateToTab(SONGS_TAB_INDEX) },
                    onOpenServices = {
                        requestedServicePlanId = null
                        serviceNavigationRequestId = null
                        navigateToTab(SERVICES_TAB_INDEX)
                    },
                    onOpenServicePlan = { planId ->
                        requestedServicePlanId = planId
                        serviceNavigationRequestId = "dashboard-$planId"
                        navigateToTab(SERVICES_TAB_INDEX)
                    },
                    onOpenRehearsal = { rehearsalId ->
                        requestedRehearsalId = rehearsalId
                        rehearsalNavigationRequestId = "dashboard-$rehearsalId"
                        requestedServicePlanId = null
                        serviceNavigationRequestId = null
                        navigateToTab(SERVICES_TAB_INDEX)
                    },
                    onRefreshWorkspace = onRefresh,
                    modifier = Modifier.padding(innerPadding)
                )
                SONGS_TAB_INDEX -> SongLibraryScreen(
                    organizationId = organization.id,
                    userId = userId,
                    role = workspace.role,
                    openSongEditorRequestId = songEditorRequestId,
                    onSongEditorRequestHandled = { songEditorRequestId = 0 },
                    openCreateSetlistRequestId = createSetlistRequestId,
                    onCreateSetlistRequestHandled = { createSetlistRequestId = 0 },
                    modifier = Modifier.padding(innerPadding)
                )
                SERVICES_TAB_INDEX -> ServicePlanningScreen(
                    organizationId = organization.id,
                    userId = userId,
                    role = workspace.role,
                    openServicePlanId = requestedServicePlanId,
                    openServicePlanRequestId = serviceNavigationRequestId,
                    onOpenServicePlanHandled = {
                        requestedServicePlanId = null
                        serviceNavigationRequestId = null
                    },
                    openRehearsalId = requestedRehearsalId,
                    openRehearsalRequestId = rehearsalNavigationRequestId,
                    onOpenRehearsalHandled = {
                        requestedRehearsalId = null
                        rehearsalNavigationRequestId = null
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                else -> CommunityHubScreen(
                    organizationId = organization.id,
                    memberCount = workspace.members.size,
                    onOpenTeam = { showTeamManagement = true },
                    onOpenChat = {
                        showSharedFiles = false
                        chatReturnDestination = "community"
                        showTeamChat = true
                    },
                    onOpenFiles = {
                        showTeamChat = false
                        showSharedFiles = true
                    },
                    onOpenProfile = { showPersonalProfile = true },
                    onOpenServices = { navigateToTab(SERVICES_TAB_INDEX) },
                    onOpenServicePlan = { planId ->
                        requestedServicePlanId = planId
                        serviceNavigationRequestId = null
                        navigateToTab(SERVICES_TAB_INDEX)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
@Composable
internal fun WorkspaceBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == HOME_TAB_INDEX,
            onClick = { onTabSelected(HOME_TAB_INDEX) },
            icon = { Icon(destinations[0].icon, contentDescription = null) },
            label = { Text(destinations[0].label) },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            selected = selectedTab == SONGS_TAB_INDEX,
            onClick = { onTabSelected(SONGS_TAB_INDEX) },
            icon = { Icon(destinations[1].icon, contentDescription = null) },
            label = { Text(destinations[1].label) },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Box(
            // Keep this slot width-weighted only. Expanding its height can make
            // Scaffold measure the bottom bar as tall as the whole window.
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = onPrimaryAction,
                modifier = Modifier.size(72.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(27.dp)
                    )
                    Text(
                        if (selectedTab == SONGS_TAB_INDEX) "Setlist" else "Song",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        NavigationBarItem(
            selected = selectedTab == SERVICES_TAB_INDEX,
            onClick = { onTabSelected(SERVICES_TAB_INDEX) },
            icon = { Icon(destinations[2].icon, contentDescription = null) },
            label = { Text(destinations[2].label) },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            selected = selectedTab == COMMUNITY_TAB_INDEX,
            onClick = { onTabSelected(COMMUNITY_TAB_INDEX) },
            icon = { Icon(destinations[3].icon, contentDescription = null) },
            label = { Text(destinations[3].label) },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun HomeScreen(
    organizationName: String,
    memberCount: Int,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Welcome to your workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Plan services, organize songs, and serve together.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("YOUR WORSHIP TEAM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                    Spacer(Modifier.height(8.dp))
                    Text(organizationName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Your team workspace is connected to the UnityHub database.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Members", memberCount.toString(), Modifier.weight(1f))
                SummaryCard("Pending requests", pendingCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text("Getting started", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Your organization is active. Open Team to review membership requests and see your current members.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TeamScreen(
    workspace: OrganizationWorkspace,
    loading: Boolean,
    onReviewRequest: (String, Boolean) -> Unit,
    errorMessage: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Team", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Your membership and access roles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text("Members (${workspace.members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(workspace.members, key = { it.id }) { member ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(roleLabel(member.role), fontWeight = FontWeight.SemiBold)
                        Text(
                            "Account · ${member.userId.takeLast(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (member.userId == workspace.members.firstOrNull { it.role == "admin" }?.userId) {
                        Text("Admin", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (workspace.role == "admin") {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Join requests (${workspace.pendingRequests.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Approve a request to add the person as a Team Member, or decline it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (workspace.pendingRequests.isEmpty()) {
                item {
                    Text("There are no pending join requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(workspace.pendingRequests, key = { it.id }) { request ->
                    JoinRequestCard(
                        request = request,
                        loading = loading,
                        onApprove = { onReviewRequest(request.id, true) },
                        onDecline = { onReviewRequest(request.id, false) }
                    )
                }
            }
        } else {
            item {
                Text(
                    "Only organization administrators can approve or decline join requests.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (errorMessage.isNotBlank()) {
            item { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun JoinRequestCard(
    request: OrganizationJoinRequestDto,
    loading: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(request.requesterDisplayName.ifBlank { "UnityHub member" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Account · ${request.userId.takeLast(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (request.message.isNotBlank()) {
                Text(request.message, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("Approve")
                }
                TextButton(onClick = onDecline, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("Decline")
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun roleLabel(role: String?): String = when (role) {
    "admin" -> "Administrator"
    "worship_leader" -> "Worship Leader"
    "song_editor" -> "Song Editor"
    "viewer" -> "Viewer"
    "team_member" -> "Team Member"
    else -> "Team Member"
}
