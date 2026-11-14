package com.zeno.assistant

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ZenoAccessibilityService : AccessibilityService() {
    companion object {
        const val ACTION_COMMAND = "com.zeno.assistant.ACCESSIBILITY_COMMAND"
        const val EXTRA_COMMAND = "command"
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra(EXTRA_COMMAND)?.let(::runCommand)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(ACTION_COMMAND)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(commandReceiver, filter)
    }

    private fun runCommand(command: String) {
        when {
            command == "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            command == "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            command == "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            command == "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            command == "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            command == "scroll_down" -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            command == "scroll_up" -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            command.startsWith("click:") -> clickText(command.removePrefix("click:").trim())
            command.startsWith("type:") -> typeText(command.removePrefix("type:"))
        }
    }

    private fun scroll(action: Int) {
        val root = rootInActiveWindow ?: return
        if (!scrollNode(root, action)) root.performAction(action)
    }

    private fun scrollNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable && node.performAction(action)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scrollNode(child, action)) return true
        }
        return false
    }

    private fun clickText(value: String) {
        if (value.isBlank()) return
        val root = rootInActiveWindow ?: return
        val matches = root.findAccessibilityNodeInfosByText(value)
        val node = matches.firstOrNull { it.isClickable } ?: matches.firstOrNull() ?: return
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun typeText(value: String) {
        val root = rootInActiveWindow ?: return
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
