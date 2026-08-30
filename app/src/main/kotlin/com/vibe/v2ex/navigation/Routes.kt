package com.vibe.v2ex.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Nodes : Route
    @Serializable data object Notifications : Route
    @Serializable data object Profile : Route

    @Serializable data class Topic(val topicId: Long, val initialFloor: Int? = null) : Route
    @Serializable data class NodeTopics(val nodeName: String) : Route
    @Serializable data object Search : Route
    @Serializable data object Login : Route
    @Serializable data class Write(val topicId: Long? = null) : Route
    @Serializable data object Settings : Route
    @Serializable data object Moderation : Route
    @Serializable data class Member(val username: String) : Route
    @Serializable data object Favorites : Route
    @Serializable data object History : Route
    @Serializable data object Offline : Route
    @Serializable data object MyPosts : Route
}

val TAB_ROUTES: List<Route> = listOf(Route.Home, Route.Nodes, Route.Notifications, Route.Profile)
