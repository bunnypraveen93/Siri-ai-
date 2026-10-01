package com.praveen.siriai

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Taps WhatsApp's own "Send" button — ONLY right after the user has already
 * confirmed the message inside Siri AI (see CommandParser "WHATSAPP" + armAutoSend()).
 * Requires the user to enable this service once under
 * Settings > Accessibility > Siri AI.
 */
class WhatsAppAutoSendService : AccessibilityService() {

    companion object {
        @Volatile private var autoSendArmed = false
        fun armAutoSend() { autoSendArmed = true }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!autoSendArmed) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val root = rootInActiveWindow ?: return
        val sendButton = findSendButton(root) ?: return

        // మెసేజ్ టెక్స్ట్ ఫీల్డ్‌లో సెటిల్ అవ్వడానికి చిన్న delay
        Handler(Looper.getMainLooper()).postDelayed({
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            autoSendArmed = false
        }, 600)
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byDesc = node.findAccessibilityNodeInfosByText("Send")
        if (byDesc.isNotEmpty()) return byDesc[0]
        val byId = node.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (byId.isNotEmpty()) return byId[0]
        return null
    }

    override fun onInterrupt() {}
}
