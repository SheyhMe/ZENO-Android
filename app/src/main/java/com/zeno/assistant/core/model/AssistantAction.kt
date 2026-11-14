package com.zeno.assistant.core.model

/**
 * Typed actions proposed by the assistant.
 *
 * Device-specific execution belongs in the Android platform layer.
 * Remote side effects must be confirmed by the user before execution.
 */
sealed interface AssistantAction {
    data class LaunchApp(val packageName: String) : AssistantAction
    data class SearchWeb(val query: String) : AssistantAction
    data class SetAlarm(val hour: Int, val minute: Int, val label: String? = null) : AssistantAction
    data class StartTimer(val durationSeconds: Long, val label: String? = null) : AssistantAction
    data class OpenSystemSettings(val settingsAction: String) : AssistantAction

    data class SendEmail(
        val recipient: String,
        val subject: String,
        val body: String
    ) : AssistantAction

    data class CreateCalendarEvent(
        val title: String,
        val startEpochMillis: Long,
        val endEpochMillis: Long
    ) : AssistantAction

    data class PublishRepositoryChange(
        val repository: String,
        val branch: String,
        val summary: String
    ) : AssistantAction

    val requiresUserConfirmation: Boolean
        get() = when (this) {
            is SendEmail,
            is CreateCalendarEvent,
            is PublishRepositoryChange -> true
            else -> false
        }
}