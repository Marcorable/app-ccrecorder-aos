package com.marcorable.ccrecorder.core.model

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val displayMode: DisplayMode = DisplayMode.SYSTEM,
    val skipIntervalSeconds: Int = DEFAULT_SKIP_INTERVAL_SECONDS,
    val keepScreenAwake: Boolean = false,
) {
    companion object {
        const val DEFAULT_SKIP_INTERVAL_SECONDS = 5
        val VALID_SKIP_INTERVAL_SECONDS = setOf(5, 10, 15, 30)
    }
}

enum class DisplayMode {
    SYSTEM,
    LIGHT,
    DARK,
}
