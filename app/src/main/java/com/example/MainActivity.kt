package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.identity.SukoonIdentity
import com.example.core.preferences.UserPreferences
import com.example.ui.components.SukoonTopBar
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.PyqVaultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TeacherScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.theme.SukoonTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.StudyToolsViewModel
import kotlinx.coroutines.launch

enum class SukoonDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    CHAT("chat", "Home / Chat", Icons.Filled.ChatBubble),
    LIBRARY("library", "My Library", Icons.Filled.MenuBook),
    PYQ_VAULT("pyq_vault", "PYQ Vault", Icons.Filled.Quiz),
    SAVED_VIDEOS("videos", "Saved Videos", Icons.Filled.PlayCircle),
    STUDY_PLANNER("planner", "Study Planner", Icons.Filled.CalendarMonth),
    TEACHER("teacher", "Learning / Teach AI", Icons.Filled.School),
    MEMORY("memory", "Memory", Icons.Filled.Psychology),
    SETTINGS("settings", "Settings", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SukoonApplication).container

        setContent {
            val prefs by container.preferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            val isOnline by container.connectivityMonitor.isOnline.collectAsStateWithLifecycle()

            SukoonTheme(themePreference = prefs.themePreference) {
                SukoonRootApp(
                    container = container,
                    isOnline = isOnline
                )
            }
        }
    }
}

@Composable
fun SukoonRootApp(
    container: AppContainer,
    isOnline: Boolean
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: SukoonDestination.CHAT.route
    val currentDest = SukoonDestination.entries.firstOrNull { it.route == currentRoute }
        ?: SukoonDestination.CHAT

    val chatVm: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
    val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val toolsVm: StudyToolsViewModel = viewModel(factory = StudyToolsViewModel.factory(container))
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navigateTo: (SukoonDestination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(SukoonDestination.CHAT.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        if (isExpanded) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(260.dp)) {
                        SukoonDrawerContent(
                            currentDest = currentDest,
                            onSelect = navigateTo
                        )
                    }
                }
            ) {
                SukoonScaffoldContent(
                    navController = navController,
                    currentDest = currentDest,
                    isOnline = isOnline,
                    showMenuButton = false,
                    onMenuClick = {},
                    onNavigate = navigateTo,
                    chatVm = chatVm,
                    libraryVm = libraryVm,
                    toolsVm = toolsVm,
                    settingsVm = settingsVm
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        SukoonDrawerContent(
                            currentDest = currentDest,
                            onSelect = { dest ->
                                scope.launch { drawerState.close() }
                                navigateTo(dest)
                            }
                        )
                    }
                }
            ) {
                SukoonScaffoldContent(
                    navController = navController,
                    currentDest = currentDest,
                    isOnline = isOnline,
                    showMenuButton = true,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNavigate = navigateTo,
                    chatVm = chatVm,
                    libraryVm = libraryVm,
                    toolsVm = toolsVm,
                    settingsVm = settingsVm
                )
            }
        }
    }
}

@Composable
private fun SukoonDrawerContent(
    currentDest: SukoonDestination,
    onSelect: (SukoonDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_sukoon_avatar),
                contentDescription = "SUKOON emblem",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(CircleShape)
            )
            Column {
                Text(
                    text = SukoonIdentity.APP_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = SukoonIdentity.HEADER_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SukoonDestination.entries.forEach { dest ->
            NavigationDrawerItem(
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
                selected = currentDest == dest,
                onClick = { onSelect(dest) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("nav_item_${dest.route}")
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Local-First • Private • Zero Cloud Auto-Upload",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun SukoonScaffoldContent(
    navController: NavHostController,
    currentDest: SukoonDestination,
    isOnline: Boolean,
    showMenuButton: Boolean,
    onMenuClick: () -> Unit,
    onNavigate: (SukoonDestination) -> Unit,
    chatVm: ChatViewModel,
    libraryVm: LibraryViewModel,
    toolsVm: StudyToolsViewModel,
    settingsVm: SettingsViewModel
) {
    Scaffold(
        topBar = {
            SukoonTopBar(
                currentSectionTitle = currentDest.label,
                isOnline = isOnline,
                showMenuButton = showMenuButton,
                onMenuClick = onMenuClick
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SukoonDestination.CHAT.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            composable(SukoonDestination.CHAT.route) {
                ChatScreen(
                    viewModel = chatVm,
                    onOpenSettings = { onNavigate(SukoonDestination.SETTINGS) },
                    onOpenCitationDoc = { docId, page ->
                        libraryVm.openDocumentById(docId, page)
                        onNavigate(SukoonDestination.LIBRARY)
                    }
                )
            }
            composable(SukoonDestination.LIBRARY.route) {
                LibraryScreen(viewModel = libraryVm)
            }
            composable(SukoonDestination.PYQ_VAULT.route) {
                PyqVaultScreen(viewModel = toolsVm)
            }
            composable(SukoonDestination.SAVED_VIDEOS.route) {
                VideosScreen(viewModel = toolsVm)
            }
            composable(SukoonDestination.STUDY_PLANNER.route) {
                PlannerScreen(viewModel = toolsVm)
            }
            composable(SukoonDestination.TEACHER.route) {
                TeacherScreen(viewModel = toolsVm)
            }
            composable(SukoonDestination.MEMORY.route) {
                MemoryScreen(viewModel = toolsVm)
            }
            composable(SukoonDestination.SETTINGS.route) {
                SettingsScreen(viewModel = settingsVm)
            }
        }
    }
}
