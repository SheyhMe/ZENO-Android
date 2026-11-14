package com.zeno.assistant.core.settings

/**
 * User-facing preferences shared by the UI and assistant services.
 * Persist this model through an implementation selected by the app layer.
 */
data class AssistantSettings(
    val language: Language = Language.AUTO,
    val theme: Theme = Theme.SYSTEM,
    val voiceEnabled: Boolean = false,
    val wakePhraseEnabled: Boolean = false
) {
    enum class Language {
        AUTO,
        UZBEK,
        ENGLISH
    }

    enum class Theme {
        SYSTEM,
        LIGHT,
        DARK
    }

    init {
        require(!wakePhraseEnabled || voiceEnabled) {
            "Wake phrase mode requires voice mode to be enabled"
        }
    }
}