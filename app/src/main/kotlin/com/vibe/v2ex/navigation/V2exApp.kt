package com.vibe.v2ex.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vibe.v2ex.feature.account.AccountScreen
import com.vibe.v2ex.feature.home.HomeScreen
import com.vibe.v2ex.feature.nodes.NodeTopicsScreen
import com.vibe.v2ex.feature.nodes.NodesScreen
import com.vibe.v2ex.feature.notifications.NotificationsScreen
import com.vibe.v2ex.feature.profile.ProfileScreen
import com.vibe.v2ex.feature.search.SearchScreen
import com.vibe.v2ex.feature.settings.SettingsScreen
import com.vibe.v2ex.feature.topic.TopicScreen
import com.vibe.v2ex.feature.write.WriteScreen

private data class TabSpec(
    val route: Route,
    val label: String,
    val icon: ImageVector,
    val matches: (NavDestination) -> Boolean,
)

private fun routeMatcher(route: Route): (NavDestination) -> Boolean {
    val qualifiedName = route::class.qualifiedName
    return { destination -> destination.route == qualifiedName }
}

private val TABS = listOf(
    TabSpec(Route.Home, "首页", Icons.Filled.Home, routeMatcher(Route.Home)),
    TabSpec(Route.Nodes, "节点", Icons.Filled.Storage, routeMatcher(Route.Nodes)),
    TabSpec(Route.Search, "搜索", Icons.Filled.Search, routeMatcher(Route.Search)),
    TabSpec(Route.Notifications, "通知", Icons.Filled.Notifications, routeMatcher(Route.Notifications)),
    TabSpec(Route.Profile, "我", Icons.Filled.Person, routeMatcher(Route.Profile)),
)

@Composable
fun V2exApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = TABS.any { tab -> currentDestination?.hierarchy?.any(tab.matches) == true }

    Scaffold(
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any(tab.matches) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Route.Home> {
                HomeScreen(
                    onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
                    onComposeClick = { navController.navigate(Route.Write()) },
                )
            }
            composable<Route.Nodes> {
                NodesScreen(onNodeClick = { name -> navController.navigate(Route.NodeTopics(name)) })
            }
            composable<Route.Search> {
                SearchScreen(
                    onBack = navController::popBackStack,
                    onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
                )
            }
            composable<Route.Notifications> {
                NotificationsScreen()
            }
            composable<Route.Profile> {
                ProfileScreen(
                    onLoginClick = { navController.navigate(Route.Login) },
                    onSettingsClick = { navController.navigate(Route.Settings) },
                )
            }
            composable<Route.Topic> { entry ->
                val route: Route.Topic = entry.toRoute()
                TopicScreen(
                    topicId = route.topicId,
                    onBack = navController::popBackStack,
                    onReplyClick = { navController.navigate(Route.Write(route.topicId)) },
                )
            }
            composable<Route.NodeTopics> {
                NodeTopicsScreen(
                    onBack = navController::popBackStack,
                    onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
                )
            }
            composable<Route.Login> {
                AccountScreen(onBack = navController::popBackStack)
            }
            composable<Route.Settings> {
                SettingsScreen(onBack = navController::popBackStack)
            }
            composable<Route.Write> {
                WriteScreen(onBack = navController::popBackStack)
            }
        }
    }
}
