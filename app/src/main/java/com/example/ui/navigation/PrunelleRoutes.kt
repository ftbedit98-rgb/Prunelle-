package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface PrunelleRoute {
    @Serializable data object Onboarding : PrunelleRoute
    @Serializable data object Home : PrunelleRoute
    @Serializable data object ChannelsStatuses : PrunelleRoute
    @Serializable data object Calls : PrunelleRoute
    @Serializable data object AiHub : PrunelleRoute
    @Serializable data class Chat(val conversationId: String) : PrunelleRoute
    @Serializable data object Search : PrunelleRoute
    @Serializable data object NewConversation : PrunelleRoute
    @Serializable data object CreateGroup : PrunelleRoute
    @Serializable data class GroupInfo(val conversationId: String) : PrunelleRoute
    @Serializable data object Contacts : PrunelleRoute
    @Serializable data class UserProfile(val contactId: String) : PrunelleRoute
    @Serializable data class LiveWallpapers(val targetConversationId: String? = null) : PrunelleRoute
    @Serializable data object Settings : PrunelleRoute
    @Serializable data object Notifications : PrunelleRoute
}
