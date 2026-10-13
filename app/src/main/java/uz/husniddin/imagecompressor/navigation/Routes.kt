package uz.husniddin.imagecompressor.navigation

import kotlinx.serialization.Serializable

// Top-level tabs
@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

// Compression workflow (shown full-screen, without the tab bar)
@Serializable data object SelectionRoute
@Serializable data object ConfigureRoute
@Serializable data object ProcessingRoute
@Serializable data object ResultsRoute
@Serializable data class ComparisonRoute(val itemId: String)
