package com.example.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.ai.OfflineFieldAnalyzer
import com.example.data.ai.RoadAiAnalyzer
import com.example.data.local.JalanAiDatabase
import com.example.data.network.NetworkStatusObserver
import com.example.data.repository.LocalRoadInspectionRepository
import com.example.data.repository.RoadInspectionRepository
import com.example.data.video.VideoFrameExtractor
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AnalyzeVideoScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InspectionDetailScreen
import com.example.ui.screens.MaintenanceScheduleScreen
import com.example.ui.viewmodel.InspectionsUiState
import com.example.ui.viewmodel.JalanAiViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_ANALYZE = "analyze"
const val ROUTE_SCHEDULE = "schedule"
const val ROUTE_DETAIL = "detail/{inspectionId}"

private enum class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD(
        route = ROUTE_DASHBOARD,
        label = "Beranda",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_dashboard"
    ),
    ANALYZE(
        route = ROUTE_ANALYZE,
        label = "Analisis",
        selectedIcon = Icons.Filled.Videocam,
        unselectedIcon = Icons.Outlined.Videocam,
        testTag = "nav_analyze"
    ),
    SCHEDULE(
        route = ROUTE_SCHEDULE,
        label = "Jadwal",
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_schedule"
    )
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var isDemoMode by rememberSaveable { mutableStateOf(false) }

    val user = currentUser
    LaunchedEffect(user) {
        if (user != null) {
            isDemoMode = false
        }
    }

    if (user == null && !isDemoMode) {
        AuthScreen(
            onAuthSuccess = { isDemoMode = false },
            onEnterDemoMode = { isDemoMode = true }
        )
    } else if (user != null) {
        key(user.uid) {
            MainShellScreen(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email ?: "Pengguna",
                onExitDemoToSignIn = { }
            )
        }
    } else {
        key("field_local") {
            MainShellScreen(
                currentUserId = null,
                userDisplayName = "Mode Lapangan Kebun (Lokal)",
                onExitDemoToSignIn = { isDemoMode = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShellScreen(
    currentUserId: String?,
    userDisplayName: String,
    onExitDemoToSignIn: () -> Unit
) {
    val sessionKey = currentUserId ?: "field_local"
    val viewModel: JalanAiViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localDb = JalanAiDatabase.getInstance(app)
                val localRepo = LocalRoadInspectionRepository(localDb.roadInspectionDao())
                val cloudRepo = if (!currentUserId.isNullOrBlank()) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    RoadInspectionRepository(db)
                } else {
                    null
                }
                JalanAiViewModel(
                    localRepository = localRepo,
                    cloudRepository = cloudRepo,
                    aiAnalyzer = RoadAiAnalyzer(),
                    offlineAnalyzer = OfflineFieldAnalyzer(),
                    frameExtractor = VideoFrameExtractor(app),
                    currentUserId = currentUserId,
                    isOnlineFlow = NetworkStatusObserver.observeIsOnline(app)
                )
            }
        }
    )

    val inspectionsState by viewModel.inspectionsState.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val unsyncedLocalCount by viewModel.unsyncedLocalCount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val isDetailRoute = currentRoute?.startsWith("detail/") == true

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (!isDetailRoute) {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        actions = {
                            if (viewModel.isLocalFieldMode) {
                                TextButton(
                                    onClick = onExitDemoToSignIn,
                                    modifier = Modifier.testTag("demo_login_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Login,
                                        contentDescription = null
                                    )
                                    Text(" Masuk Google")
                                }
                            } else {
                                IconButton(
                                    onClick = { showSignOutDialog = true },
                                    modifier = Modifier.testTag("sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Keluar akun"
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!useNavRail && !isDetailRoute) {
                    NavigationBar {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !isDetailRoute) {
                    NavigationRail {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    if ((viewModel.isLocalFieldMode || !isOnline) && !isDetailRoute) {
                        val bannerText = when {
                            viewModel.isLocalFieldMode && isOnline ->
                                "Mode Lapangan Kebun • Tersimpan permanen di Room lokal • AI Online"
                            viewModel.isLocalFieldMode && !isOnline ->
                                "Mode Offline Kebun • Tersimpan permanen di Room lokal • Tanpa Internet"
                            else ->
                                "Sedang Offline • Inspeksi baru disimpan lokal ke Room hingga koneksi kembali"
                        }
                        Surface(
                            color = if (isOnline) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bannerText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOnline) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                inspectionsState = inspectionsState,
                                isLocalFieldMode = viewModel.isLocalFieldMode,
                                isOnline = isOnline,
                                unsyncedLocalCount = unsyncedLocalCount,
                                isSyncing = isSyncing,
                                onSyncLocalToCloud = viewModel::syncLocalInspectionsToCloud,
                                onNavigateToAnalyze = { navigateTopTab(ROUTE_ANALYZE) },
                                onNavigateToSchedule = { navigateTopTab(ROUTE_SCHEDULE) },
                                onOpenDetail = { id -> navController.navigate("detail/$id") }
                            )
                        }
                        composable(ROUTE_ANALYZE) {
                            AnalyzeVideoScreen(
                                analysisState = analysisState,
                                isLocalFieldMode = viewModel.isLocalFieldMode,
                                isOnline = isOnline,
                                onLoadVideoUri = viewModel::loadVideoFromUri,
                                onLoadImageUri = viewModel::loadImageFrameFromUri,
                                onLoadLiveFrames = viewModel::loadLiveCapturedFrames,
                                onRunAiAnalysis = viewModel::runAiAnalysis,
                                onSaveAnalysis = viewModel::saveCurrentAnalysis,
                                onReset = viewModel::resetAnalysis,
                                onOpenSavedDetail = { id -> navController.navigate("detail/$id") }
                            )
                        }
                        composable(ROUTE_SCHEDULE) {
                            MaintenanceScheduleScreen(
                                inspectionsState = inspectionsState,
                                onUpdateSchedule = viewModel::updateSchedule,
                                onOpenDetail = { id -> navController.navigate("detail/$id") }
                            )
                        }
                        composable(
                            route = ROUTE_DETAIL,
                            arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("inspectionId").orEmpty()
                            val item = (inspectionsState as? InspectionsUiState.Success)
                                ?.items
                                ?.firstOrNull { it.id == id }
                            InspectionDetailScreen(
                                inspection = item,
                                onBack = { navController.popBackStack() },
                                onUpdateSchedule = { status, month, notes ->
                                    viewModel.updateSchedule(id, status, month, notes)
                                },
                                onDelete = {
                                    viewModel.deleteInspection(id) {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Keluar dari Akun?") },
            text = { Text("Masuk sebagai $userDisplayName. Anda dapat masuk kembali kapan saja untuk mengakses laporan inspeksi.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        signOutUser(credentialManager, scope)
                    }
                ) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
