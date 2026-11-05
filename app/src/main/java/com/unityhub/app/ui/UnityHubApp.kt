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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.OrganizationDirectoryDto
import com.unityhub.app.data.OrganizationJoinRequestDto
import com.unityhub.app.data.OrganizationRepository
import com.unityhub.app.data.OrganizationWorkspace
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.push.UnityHubPushNavigation
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

private data class AppDestination(
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    AppDestination("Home", Icons.Default.Home),
    AppDestination("Songs", Icons.Default.LibraryMusic),
    AppDestination("Services", Icons.Default.CalendarMonth),
    AppDestination("Notifications", Icons.Default.Notifications),
    AppDestination("Team", Icons.Default.Groups)
)

@Composable
fun UnityHubApp(
    onSignOut: () -> Unit,
    pushNavigation: UnityHubPushNavigation? = null,
    onPushNavigationHandled: () -> Unit = {}
) {
    val client = remember { SupabaseClientProvider.client }
    val userId = remember { client.auth.currentSessionOrNull()?.user?.id.orEmpty() }
    val repository = remember { OrganizationRepository(client) }
    val scope = rememberCoroutineScope()

    var workspace by remember { mutableStateOf<OrganizationWorkspace?>(null) }
    var loading by remember { mutableStateOf(true) }
    var actionInProgress by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                workspace = repository.loadWorkspace(userId)
            } catch (_: Exception) {
                errorMessage = "UnityHub could not load your team information. Check your internet connection and try again."
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
                workspace = repository.loadWorkspace(userId)
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
            try {
                workspace = repository.loadWorkspace(userId)
            } catch (_: Exception) {
                errorMessage = "UnityHub could not load your team information. Check your internet connection and try again."
            } finally {
                loading = false
            }
        }
    }

    when {
        loading && workspace == null -> LoadingScreen()
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
            onPushNavigationHandled = onPushNavigationHandled
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
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Loading your UnityHub workspace")
    }
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

    val requestByOrganization = workspace.myRequests.associateBy { it.organizationId }
    val filteredOrganizations = workspace.directory
        .filter { it.name.contains(search.trim(), ignoreCase = true) }
        .sortedBy { it.name.lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
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
    onPushNavigationHandled: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var requestedServicePlanId by remember { mutableStateOf<String?>(null) }
    var serviceNavigationRequestId by remember { mutableStateOf<String?>(null) }
    val organization = workspace.organization ?: return

    LaunchedEffect(pushNavigation?.notificationId, pushNavigation?.servicePlanId) {
        val navigation = pushNavigation ?: return@LaunchedEffect
        if (!navigation.servicePlanId.isNullOrBlank()) {
            requestedServicePlanId = navigation.servicePlanId
            serviceNavigationRequestId = navigation.notificationId ?: navigation.servicePlanId
            selectedTab = 2
        } else {
            selectedTab = 3
        }
        onPushNavigationHandled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(organization.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${roleLabel(workspace.role)} · UnityHub workspace",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HomeScreen(
                organizationName = organization.name,
                memberCount = workspace.members.size,
                pendingCount = workspace.pendingRequests.size,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> SongLibraryScreen(
                organizationId = organization.id,
                userId = userId,
                role = workspace.role,
                modifier = Modifier.padding(innerPadding)
            )
            2 -> ServicePlanningScreen(
                organizationId = organization.id,
                userId = userId,
                role = workspace.role,
                openServicePlanId = requestedServicePlanId,
                openServicePlanRequestId = serviceNavigationRequestId,
                onOpenServicePlanHandled = {
                    requestedServicePlanId = null
                    serviceNavigationRequestId = null
                },
                modifier = Modifier.padding(innerPadding)
            )
            3 -> NotificationsScreen(
                userId = userId,
                onOpenServicePlan = { notification ->
                    val servicePlanId = notification.servicePlanId
                    if (!servicePlanId.isNullOrBlank()) {
                        requestedServicePlanId = servicePlanId
                        serviceNavigationRequestId = notification.id
                        selectedTab = 2
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            else -> TeamScreen(
                workspace = workspace,
                loading = loading,
                onReviewRequest = onReviewRequest,
                errorMessage = errorMessage,
                modifier = Modifier.padding(innerPadding)
            )
        }
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
