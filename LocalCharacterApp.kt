package com.localcharacter.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localcharacter.app.LocalCharacterApplication
import com.localcharacter.app.ui.chat.ChatViewModel
import com.localcharacter.app.ui.group.GroupChatViewModel
import com.localcharacter.app.ui.screens.GroupChatScreen
import com.localcharacter.app.ui.screens.GroupSettingsScreen
import com.localcharacter.app.ui.memory.MemoryViewModel
import com.localcharacter.app.ui.memory.GroupMemoryViewModel
import com.localcharacter.app.ui.screens.CharacterDetailScreen
import com.localcharacter.app.ui.screens.CharacterAiSettingsScreen
import com.localcharacter.app.ui.screens.CharacterEditorScreen
import com.localcharacter.app.ui.screens.ChatScreen
import com.localcharacter.app.ui.screens.ChatsScreen
import com.localcharacter.app.ui.screens.ExploreScreen
import com.localcharacter.app.ui.screens.HomeScreen
import com.localcharacter.app.ui.screens.ModelsScreen
import com.localcharacter.app.ui.screens.MemoryScreen
import com.localcharacter.app.ui.screens.GroupMemoryScreen
import com.localcharacter.app.ui.screens.OnboardingScreen
import com.localcharacter.app.ui.screens.RemoteCharacterDetailScreen
import com.localcharacter.app.ui.screens.SettingsScreen
import com.localcharacter.app.ui.screens.AiProvidersScreen
import com.localcharacter.app.ui.screens.RepositorySettingsScreen
import com.localcharacter.app.ui.screens.UserProfileScreen
import com.localcharacter.app.ui.screens.CharacterBehaviorSettingsScreen
import com.localcharacter.app.ui.screens.VoiceRepositorySettingsScreen
import com.localcharacter.app.ui.screens.VoiceSettingsScreen
import com.localcharacter.app.ui.motion.AppMotion
import com.localcharacter.app.ui.theme.LocalCharacterTheme
import com.localcharacter.app.performance.NavigationPerformance
import androidx.navigation.NavOptionsBuilder

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("home", "Inicio", Icons.Default.Home),
    Destination("explore", "Explorar", Icons.Default.Search),
    Destination("chats", "Chats", Icons.Default.Email),
    Destination("models", "Modelos", Icons.Default.Build),
    Destination("settings", "Ajustes", Icons.Default.Settings),
)

@Composable
fun LocalCharacterApp(viewModel: AppViewModel) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    LocalCharacterTheme(theme) {
        if (!onboardingComplete) {
            OnboardingScreen(viewModel)
        } else {
            MainNavigation(viewModel)
        }
    }
}

@Composable
private fun MainNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(route) { route?.let(NavigationPerformance::composed) }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbarHost.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            AnimatedVisibility(route in destinations.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    destinations.forEach { destination -> NavigationItem(destination, route == destination.route) { navController.navigateTopLevel(destination.route) } }
                }
            }
        },
    ) { rootPadding ->
        NavHost(
            navController,
            startDestination = "home",
            modifier = Modifier.padding(rootPadding),
            enterTransition = {
                fadeIn(AppMotion.screenTween()) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    AppMotion.screenTween(),
                    initialOffset = { it / 18 },
                )
            },
            exitTransition = {
                fadeOut(AppMotion.fastTween()) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    AppMotion.fastTween(),
                    targetOffset = { it / 22 },
                )
            },
            popEnterTransition = {
                fadeIn(AppMotion.screenTween()) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    AppMotion.screenTween(),
                    initialOffset = { it / 18 },
                )
            },
            popExitTransition = {
                fadeOut(AppMotion.fastTween()) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    AppMotion.fastTween(),
                    targetOffset = { it / 22 },
                )
            },
        ) {
            composable("home") {
                HomeScreen(viewModel, { navController.navigateMeasured("character/$it") }, { navController.navigateTopLevel("explore") }, { navController.navigateTopLevel("settings") })
            }
            composable("explore") {
                ExploreScreen(
                    viewModel,
                    { navController.navigateMeasured("character/$it") },
                    { provider, remoteId -> navController.navigateMeasured("remote/${Uri.encode(provider)}/${Uri.encode(remoteId)}") },
                    { navController.navigateMeasured("editor/new") },
                )
            }
            composable("chats") {
                ChatsScreen(viewModel, { navController.navigateMeasured("chat/$it") }, { navController.navigateMeasured("group/$it") })
            }
            composable("models") { ModelsScreen(viewModel) }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onProfile = { navController.navigateMeasured("profile") },
                    onVoices = { navController.navigateMeasured("voices") },
                    onRepositories = { navController.navigateMeasured("repositories") },
                    onAiProviders = { navController.navigateMeasured("ai-providers") },
                )
            }
            composable("ai-providers") { AiProvidersScreen(viewModel, navController::popBackStack) }
            composable("profile") { UserProfileScreen(viewModel, navController::popBackStack) }
            composable("voices") {
                VoiceSettingsScreen(
                    viewModel, navController::popBackStack,
                    onRepositories = { navController.navigateMeasured("voice-repositories") },
                )
            }
            composable("voice-repositories") { VoiceRepositorySettingsScreen(viewModel, navController::popBackStack) }
            composable("repositories") { RepositorySettingsScreen(viewModel, navController::popBackStack) }
            composable("character/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                CharacterDetailScreen(
                    id = entry.arguments?.getString("id").orEmpty(), viewModel = viewModel,
                    onBack = navController::popBackStack,
                    onChat = { navController.navigateMeasured("chat/$it") },
                    onEdit = { navController.navigateMeasured("editor/$it") },
                    onAiSettings = { navController.navigateMeasured("character-ai/$it") },
                    onBehaviorSettings = { navController.navigateMeasured("character-behavior/$it") },
                )
            }
            composable("character-ai/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                CharacterAiSettingsScreen(
                    characterId = entry.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                )
            }
            composable("character-behavior/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                CharacterBehaviorSettingsScreen(
                    characterId = entry.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                )
            }
            composable(
                "remote/{providerId}/{remoteId}",
                arguments = listOf(
                    navArgument("providerId") { type = NavType.StringType },
                    navArgument("remoteId") { type = NavType.StringType },
                ),
            ) { entry ->
                RemoteCharacterDetailScreen(
                    providerId = entry.arguments?.getString("providerId").orEmpty(),
                    remoteId = entry.arguments?.getString("remoteId").orEmpty(),
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                    onLocalCharacter = { localId -> navController.navigateMeasured("character/$localId") },
                    onChat = { conversationId -> navController.navigateMeasured("chat/$conversationId") },
                )
            }
            composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").takeUnless { it == "new" }
                CharacterEditorScreen(id, viewModel, navController::popBackStack) { savedId ->
                    navController.navigateMeasured("character/$savedId") { popUpTo("editor/{id}") { inclusive = true } }
                }
            }
            composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val conversationId = entry.arguments?.getString("id").orEmpty()
                val application = LocalContext.current.applicationContext as LocalCharacterApplication
                val chatViewModel: ChatViewModel = viewModel(
                    key = conversationId,
                    factory = ChatViewModel.Factory(conversationId, application.container),
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    onBack = navController::popBackStack,
                    onMemory = { navController.navigateMeasured("memory/$conversationId") },
                    onCharacter = { characterId -> navController.navigateMeasured("character/$characterId") },
                    onBranchCreated = { branchId -> navController.navigateMeasured("chat/$branchId") },
                )
            }
            composable("group/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val groupId = entry.arguments?.getString("id").orEmpty()
                val application = LocalContext.current.applicationContext as LocalCharacterApplication
                val groupViewModel: GroupChatViewModel = viewModel(
                    key = "group-$groupId",
                    factory = GroupChatViewModel.Factory(groupId, application.container),
                )
                GroupChatScreen(
                    viewModel = groupViewModel,
                    onBack = navController::popBackStack,
                    onMemory = { navController.navigateMeasured("group-memory/$groupId") },
                    onSettings = { navController.navigateMeasured("group-settings/$groupId") },
                    onBranchCreated = { branchId -> navController.navigateMeasured("group/$branchId") },
                )
            }
            composable("group-settings/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                GroupSettingsScreen(entry.arguments?.getString("id").orEmpty(), viewModel, navController::popBackStack)
            }
            composable("group-memory/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val groupId = entry.arguments?.getString("id").orEmpty()
                val application = LocalContext.current.applicationContext as LocalCharacterApplication
                val memoryViewModel: GroupMemoryViewModel = viewModel(
                    key = "group-memory-$groupId",
                    factory = GroupMemoryViewModel.Factory(groupId, application.container),
                )
                GroupMemoryScreen(memoryViewModel, navController::popBackStack)
            }
            composable("memory/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val conversationId = entry.arguments?.getString("id").orEmpty()
                val application = LocalContext.current.applicationContext as LocalCharacterApplication
                val memoryViewModel: MemoryViewModel = viewModel(
                    key = "memory-$conversationId",
                    factory = MemoryViewModel.Factory(conversationId, application.container),
                )
                MemoryScreen(memoryViewModel, navController::popBackStack)
            }
        }
    }
}

@Composable
private fun RowScope.NavigationItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "bottomNavIconScale",
    )
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(destination.icon, destination.label, modifier = Modifier.scale(iconScale)) },
        label = { Text(destination.label) },
    )
}

private fun NavHostController.navigateMeasured(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    NavigationPerformance.requested(route)
    navigate(route, builder)
}

private fun NavHostController.navigateTopLevel(route: String) {
    NavigationPerformance.requested(route)
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
