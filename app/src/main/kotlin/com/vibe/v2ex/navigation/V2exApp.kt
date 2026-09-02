package com.vibe.v2ex.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.stateIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.vibe.v2ex.designsystem.GlassBarHeight
import com.vibe.v2ex.designsystem.GlassBarMargin
import com.vibe.v2ex.designsystem.LocalTabBarClearance
import com.vibe.v2ex.designsystem.isLiquidGlassSupported
import com.vibe.v2ex.designsystem.liquidGlass
import com.vibe.v2ex.feature.account.AccountScreen
import com.vibe.v2ex.feature.home.HomeScreen
import com.vibe.v2ex.feature.member.MemberScreen
import com.vibe.v2ex.feature.moderation.ModerationSettingsScreen
import com.vibe.v2ex.feature.profile.FavoritesScreen
import com.vibe.v2ex.feature.profile.HistoryScreen
import com.vibe.v2ex.feature.profile.MyPostsScreen
import com.vibe.v2ex.feature.profile.OfflineListScreen
import com.vibe.v2ex.feature.nodes.NodeCategoryScreen
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

// hasRoute 走序列化器匹配 — 之前用 route::class.qualifiedName 和 destination.route
// 比对字符串，R8 混淆后类名改了而路由串没改，release 包底栏因此消失。
private fun routeMatcher(route: Route): (NavDestination) -> Boolean {
    val routeClass = route::class
    return { destination -> destination.hasRoute(routeClass) }
}

/** 设计稿的 4 Tab：首页 / 节点 / 通知 / 我的（搜索移到首页顶栏玻璃圆钮）。 */
private val TABS = listOf(
    TabSpec(Route.Home, "首页", Icons.Outlined.Home, Icons.Filled.Home, routeMatcher(Route.Home)),
    TabSpec(Route.Nodes, "节点", Icons.Outlined.GridView, Icons.Outlined.GridView, routeMatcher(Route.Nodes)),
    TabSpec(Route.Notifications, "通知", Icons.Outlined.Notifications, Icons.Filled.Notifications, routeMatcher(Route.Notifications)),
    TabSpec(Route.Profile, "我的", Icons.Outlined.Person, Icons.Filled.Person, routeMatcher(Route.Profile)),
)

private fun NavDestination.isTab(): Boolean = TABS.any { it.matches(this) }

// navigation-compose 默认转场是 700ms 淡入淡出，返回时明显拖沓；
// 换成 iOS 式 300ms 横滑（push 右进 / pop 右出），Tab 间保留短淡入淡出。
private const val NAV_ANIM_MS = 300
private const val TAB_ANIM_MS = 200

/** 底部「通知」角标的数据桥 — UnreadNotificationsStore 的 StateFlow 化。 */
@dagger.hilt.android.lifecycle.HiltViewModel
class TabBadgeViewModel @javax.inject.Inject constructor(
    unreadNotificationsStore: com.vibe.v2ex.data.datastore.UnreadNotificationsStore,
) : androidx.lifecycle.ViewModel() {
    val unreadCount: kotlinx.coroutines.flow.StateFlow<Int> = unreadNotificationsStore.unreadCount
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            0,
        )
}

@Composable
fun V2exApp(
    deepLinkUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {},
    liquidGlassEnabled: Boolean = true,
    badgeViewModel: TabBadgeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val navController = rememberNavController()
    // 冷启动直接以 Deep Link 主题作为起点，避免先显示首页再跳转；后续 onNewIntent
    // 收到的新链接仍由下面的 LaunchedEffect 推入当前返回栈。
    val initialDeepLinkUri = remember { deepLinkUri }
    val initialRoute = remember { deepLinkUri?.toTopicRoute() ?: Route.Home }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = TABS.any { tab -> currentDestination?.hierarchy?.any(tab.matches) == true }
    val unreadCount by badgeViewModel.unreadCount.collectAsState()

    // 用户关掉、或设备低于 Android 12（连模糊都没有）时退回实心通栏底栏。
    val glassBar = liquidGlassEnabled && isLiquidGlassSupported
    val backdrop = rememberLayerBackdrop()
    // 玻璃底栏悬浮在内容之上，列表得自己让出胶囊 + 系统导航栏的高度。
    val clearance = if (onTab && glassBar) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            GlassBarHeight + GlassBarMargin * 2 + 16.dp
    } else {
        16.dp
    }

    LaunchedEffect(deepLinkUri) {
        val target = deepLinkUri?.toTopicRoute()
        if (target != null && deepLinkUri != initialDeepLinkUri) {
            navController.navigate(target) { launchSingleTop = true }
        }
        if (deepLinkUri != null) onDeepLinkHandled()
    }

    CompositionLocalProvider(LocalTabBarClearance provides clearance) {
        androidx.compose.material3.Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // 各屏幕自己做 statusBarsPadding；Scaffold 再注入系统栏 inset 会把顶部垫两次。
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (onTab && !glassBar) {
                    BottomTabBar(
                        currentDestination = currentDestination,
                        notificationBadgeCount = unreadCount,
                        onTabClick = { tab -> navController.navigateToTab(tab) },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AppNavHost(
                    navController = navController,
                    startDestination = initialRoute,
                    // 玻璃底栏要采样它下面的内容，先把整个 NavHost 录成背景层。
                    modifier = if (glassBar) Modifier.layerBackdrop(backdrop) else Modifier,
                )
                if (onTab && glassBar) {
                    LiquidTabBar(
                        backdrop = backdrop,
                        currentDestination = currentDestination,
                        notificationBadgeCount = unreadCount,
                        onTabClick = { tab -> navController.navigateToTab(tab) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/** 底栏切 tab：回到该 tab 的栈顶并复用已保存的状态。 */
private fun androidx.navigation.NavHostController.navigateToTab(tab: TabSpec) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    startDestination: Route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            if (initialState.destination.isTab() && targetState.destination.isTab()) {
                fadeIn(tween(TAB_ANIM_MS))
            } else {
                slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it }
            }
        },
        exitTransition = {
            if (initialState.destination.isTab() && targetState.destination.isTab()) {
                fadeOut(tween(TAB_ANIM_MS))
            } else {
                slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.isTab() && targetState.destination.isTab()) {
                fadeIn(tween(TAB_ANIM_MS))
            } else {
                slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 }
            }
        },
        popExitTransition = {
            if (initialState.destination.isTab() && targetState.destination.isTab()) {
                fadeOut(tween(TAB_ANIM_MS))
            } else {
                slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it }
            }
        },
        // 手势预测性返回（targetSdk 36+ 在 Android 16 上默认开启）不走上面的 pop 转场，
        // 而是库默认的“缩小成卡片再滑走”；显式指定后手势返回同样横滑，且拖动时跟手。
        predictivePopEnterTransition = { _ ->
            if (initialState.destination.isTab() && targetState.destination.isTab()) {
                fadeIn(tween(TAB_ANIM_MS))
            } else {
                slideInHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { -it / 4 }
            }
        },
        predictivePopExitTransition = { _ ->
            if (initialState.destination.isTab() && targetState.destination.isTab()) {
                fadeOut(tween(TAB_ANIM_MS))
            } else {
                slideOutHorizontally(tween(NAV_ANIM_MS, easing = FastOutSlowInEasing)) { it }
            }
        },
    ) {
        composable<Route.Home> {
            HomeScreen(
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
                onComposeClick = { navController.navigate(Route.Write()) },
                onSearchClick = { navController.navigate(Route.Search) },
                onNodeClick = { name -> navController.navigate(Route.NodeTopics(name)) },
            )
        }
        composable<Route.Nodes> {
            NodesScreen(
                onNodeClick = { name -> navController.navigate(Route.NodeTopics(name)) },
                onCategoryClick = { categoryId ->
                    navController.navigate(Route.NodeCategory(categoryId))
                },
            )
        }
        composable<Route.NodeCategory> { entry ->
            val route: Route.NodeCategory = entry.toRoute()
            NodeCategoryScreen(
                categoryId = route.categoryId,
                onBack = navController::popBackStack,
                onNodeClick = { name -> navController.navigate(Route.NodeTopics(name)) },
            )
        }
        composable<Route.Search> {
            SearchScreen(
                onBack = navController::popBackStack,
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
                onNodeClick = { name -> navController.navigate(Route.NodeTopics(name)) },
                onMemberClick = { username -> navController.navigate(Route.Member(username)) },
            )
        }
        composable<Route.Notifications> {
            NotificationsScreen(
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
            )
        }
        composable<Route.Profile> {
            ProfileScreen(
                onLoginClick = { navController.navigate(Route.Login) },
                onSettingsClick = { navController.navigate(Route.Settings) },
                onModerationClick = { navController.navigate(Route.Moderation) },
                onFavoritesClick = { navController.navigate(Route.Favorites) },
                onHistoryClick = { navController.navigate(Route.History) },
                onOfflineClick = { navController.navigate(Route.Offline) },
                onMyPostsClick = { navController.navigate(Route.MyPosts) },
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
            )
        }
        composable<Route.Topic> { entry ->
            val route: Route.Topic = entry.toRoute()
            TopicScreen(
                topicId = route.topicId,
                onBack = navController::popBackStack,
                onNodeClick = { name -> navController.navigate(Route.NodeTopics(name)) },
                onMemberClick = { username -> navController.navigate(Route.Member(username)) },
                onSettingsClick = { navController.navigate(Route.Settings) },
            )
        }
        composable<Route.Member> {
            MemberScreen(
                onBack = navController::popBackStack,
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
            )
        }
        composable<Route.Favorites> {
            FavoritesScreen(
                onBack = navController::popBackStack,
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
            )
        }
        composable<Route.History> {
            HistoryScreen(
                onBack = navController::popBackStack,
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
            )
        }
        composable<Route.Offline> {
            OfflineListScreen(
                onBack = navController::popBackStack,
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
            )
        }
        composable<Route.MyPosts> {
            MyPostsScreen(
                onBack = navController::popBackStack,
                onTopicClick = { id -> navController.navigate(Route.Topic(id)) },
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
            SettingsScreen(
                onBack = navController::popBackStack,
                onAccountClick = { navController.navigate(Route.Login) },
                onModerationClick = { navController.navigate(Route.Moderation) },
            )
        }
        composable<Route.Moderation> {
            ModerationSettingsScreen(onBack = navController::popBackStack)
        }
        composable<Route.Write> {
            WriteScreen(
                onBack = navController::popBackStack,
                onPublished = { newTopicId ->
                    // 发完直接落到自己的帖子上，省得再去首页找。
                    navController.popBackStack()
                    navController.navigate(Route.Topic(newTopicId))
                },
            )
        }
    }
}

internal fun Uri.toTopicRoute(): Route.Topic? {
    if (scheme != "https" && scheme != "http") return null
    val normalizedHost = host?.lowercase() ?: return null
    if (normalizedHost !in setOf("v2ex.com", "www.v2ex.com", "global.v2ex.com", "origin.v2ex.com", "edge.v2ex.com")) {
        return null
    }
    val topicId = Regex("^/t/(\\d+)").find(path.orEmpty())?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: return null
    val floor = Regex("^reply(\\d+)$", RegexOption.IGNORE_CASE)
        .matchEntire(fragment.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
    return Route.Topic(topicId = topicId, initialFloor = floor)
}

/** 安卓常规通栏底栏（iOS 设计语言的皮肤）：卡片白底 + 0.5dp 顶部细线，accent 选中态。 */
@Composable
private fun BottomTabBar(
    currentDestination: NavDestination?,
    onTabClick: (TabSpec) -> Unit,
    modifier: Modifier = Modifier,
    notificationBadgeCount: Int = 0,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
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
                    TabItem(
                        tab = tab,
                        selected = currentDestination?.hierarchy?.any(tab.matches) == true,
                        badgeCount = notificationBadgeCount,
                        onClick = { onTabClick(tab) },
                    )
                }
            }
        }
    }
}

/**
 * iOS 26 的液态玻璃底栏：一枚悬浮胶囊，内容从它下面滚过去，选中态是一颗会滑动的药丸。
 * 只在 [isLiquidGlassSupported] 为真时被调用。
 */
@Composable
private fun LiquidTabBar(
    backdrop: Backdrop,
    currentDestination: NavDestination?,
    onTabClick: (TabSpec) -> Unit,
    modifier: Modifier = Modifier,
    notificationBadgeCount: Int = 0,
) {
    val selectedIndex = TABS.indexOfFirst { tab -> currentDestination?.hierarchy?.any(tab.matches) == true }
    val shape = RoundedCornerShape(GlassBarHeight / 2)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = GlassBarMargin)
            .height(GlassBarHeight)
            .liquidGlass(
                backdrop = backdrop,
                shape = shape,
                // 玻璃本身要透，只补一层极淡的底色把文字撑起来。
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
            ),
    ) {
        if (selectedIndex >= 0) {
            val itemWidth = maxWidth / TABS.size
            val indicatorX by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                label = "tabIndicator",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorX)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = currentDestination?.hierarchy?.any(tab.matches) == true,
                    badgeCount = notificationBadgeCount,
                    onClick = { onTabClick(tab) },
                    rippleEnabled = false,
                )
            }
        }
    }
}

/** 两种底栏共用的单个 tab：图标 + 文字，通知 tab 额外挂未读角标。 */
@Composable
private fun RowScope.TabItem(
    tab: TabSpec,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    rippleEnabled: Boolean = true,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = null,
                // 玻璃底栏的按压反馈就是那颗滑过去的药丸，再叠一层灰涟漪会先糊一块灰、药丸后到。
                indication = if (rippleEnabled) LocalIndication.current else null,
            ) { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 图标上方与右侧预留出徽章的位置，负偏移不越出列的 clip 区（否则被裁）。
        Box {
            Icon(
                imageVector = if (selected) tab.iconSelected else tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier
                    .padding(top = 5.dp, start = 12.dp, end = 12.dp)
                    .size(24.dp),
            )
            if (tab.route == Route.Notifications && badgeCount > 0) {
                Text(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
