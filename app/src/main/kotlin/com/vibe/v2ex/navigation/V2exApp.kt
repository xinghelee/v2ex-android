package com.vibe.v2ex.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vibe.v2ex.designsystem.LocalV2Dark
import com.vibe.v2ex.feature.account.AccountScreen
import com.vibe.v2ex.feature.home.HomeScreen
import com.vibe.v2ex.feature.moderation.ModerationSettingsScreen
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
    val iconSelected: ImageVector,
    val matches: (NavDestination) -> Boolean,
)

private fun routeMatcher(route: Route): (NavDestination) -> Boolean {
    val qualifiedName = route::class.qualifiedName
    return { destination -> destination.route == qualifiedName }
}

/** 设计稿的 4 Tab：首页 / 节点 / 通知 / 我的（搜索移到首页顶栏玻璃圆钮）。 */
private val TABS = listOf(
    TabSpec(Route.Home, "首页", Icons.Outlined.Home, Icons.Filled.Home, routeMatcher(Route.Home)),
    TabSpec(Route.Nodes, "节点", Icons.Outlined.GridView, Icons.Outlined.GridView, routeMatcher(Route.Nodes)),
    TabSpec(Route.Notifications, "通知", Icons.Outlined.Notifications, Icons.Filled.Notifications, routeMatcher(Route.Notifications)),
    TabSpec(Route.Profile, "我的", Icons.Outlined.Person, Icons.Filled.Person, routeMatcher(Route.Profile)),
)

@Composable
fun V2exApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = TABS.any { tab -> currentDestination?.hierarchy?.any(tab.matches) == true }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTab) {
                BottomTabBar(
                    currentDestination = currentDestination,
                    onTabClick = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable<Route.Home> {
                HomeScreen(
                    onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
                    onComposeClick = { navController.navigate(Route.Write()) },
                    onSearchClick = { navController.navigate(Route.Search) },
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
                    onModerationClick = { navController.navigate(Route.Moderation) },
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
            composable<Route.Moderation> {
                ModerationSettingsScreen(onBack = navController::popBackStack)
            }
            composable<Route.Write> {
                WriteScreen(onBack = navController::popBackStack)
            }
        }
    }
}

/** 安卓常规通栏底栏（iOS 设计语言的皮肤）：卡片白底 + 0.5dp 顶部细线，accent 选中态。 */
@Composable
private fun BottomTabBar(
    currentDestination: NavDestination?,
    onTabClick: (TabSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalV2Dark.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (dark) Color(0xFF1C1C1E) else Color.White,
    ) {
        Column {
            androidx.compose.material3.HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 6.dp),
            ) {
                TABS.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any(tab.matches) == true
                    val tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onTabClick(tab) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = if (selected) tab.iconSelected else tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
