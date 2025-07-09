package app.coreply.coreplyapp.applistener

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.coreply.coreplyapp.utils.ChatMessage

/**
 * Created on 1/18/17.
 */
data class SupportedAppProperty(
    val pkgName: String?,
    val triggerDetector: (AccessibilityNodeInfo, AccessibilityEvent?) -> Pair<Boolean, AccessibilityNodeInfo?>,
    val inputJudger: (AccessibilityNodeInfo, AccessibilityNodeInfo, String, String) -> Boolean,
    val textInputFinder: ((AccessibilityNodeInfo) -> AccessibilityNodeInfo?)?,
    val excludeWidgets: Array<String>,
    val messageListProcessor: (AccessibilityNodeInfo) -> MutableList<ChatMessage>,
    val typeEnum: DetectedApp,
    val quotedMessageWidgets: String? = null
)
