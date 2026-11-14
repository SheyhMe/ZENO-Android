package com.zeno.assistant.core.model

import com.zeno.assistant.core.settings.AssistantSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreModelTest {
    @Test
    fun localDeviceActionsDoNotRequireRemoteConfirmation() {
        assertFalse(AssistantAction.LaunchApp("com.example.app").requiresUserConfirmation)
        assertFalse(AssistantAction.SearchWeb("weather").requiresUserConfirmation)
    }

    @Test
    fun externalSideEffectsRequireConfirmation() {
        assertTrue(AssistantAction.SendEmail("a@example.com", "Subject", "Body").requiresUserConfirmation)
        assertTrue(
            AssistantAction.CreateCalendarEvent("Meeting", 1_000L, 2_000L)
                .requiresUserConfirmation
        )
        assertTrue(
            AssistantAction.PublishRepositoryChange("owner/repo", "main", "Release")
                .requiresUserConfirmation
        )
    }

    @Test
    fun wakePhraseCannotBeEnabledWithoutVoiceMode() {
        try {
            AssistantSettings(voiceEnabled = false, wakePhraseEnabled = true)
            throw AssertionError("Invalid settings should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected invariant.
        }
    }

    @Test
    fun defaultSettingsAreSafeAndDisabled() {
        val settings = AssistantSettings()
        assertFalse(settings.voiceEnabled)
        assertFalse(settings.wakePhraseEnabled)
        assertTrue(settings.theme == AssistantSettings.Theme.SYSTEM)
    }
}
