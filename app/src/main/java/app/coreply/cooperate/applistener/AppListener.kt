/**
 * Cooperate
 *
 * Copyright (C) 2025 Cooperate
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package app.coreply.cooperate.applistener

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.BroadcastReceiver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.graphics.scale
import app.coreply.cooperate.PromptInputActivity
import app.coreply.cooperate.R
import app.coreply.cooperate.network.CallAI
import app.coreply.cooperate.ui.Overlay
import app.coreply.cooperate.ui.OverlayState
import app.coreply.cooperate.utils.PixelCalculator
import app.coreply.cooperate.utils.PromptBroadcastManager
import app.coreply.cooperate.utils.TaskStateController
import com.aallam.openai.api.core.Parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.max


@OptIn(kotlinx.coroutines.FlowPreview::class)
open class AppListener : AccessibilityService(), TaskStateController {
    private var overlay: Overlay? = null
    private var overlayState: OverlayState? = null
    private var pixelCalculator: PixelCalculator? = null


    private val tools = mapOf(
        "click" to AvailableToolsProperty(
            "click", "Click on a specific point on the screen from 0,0", ::clickTool,
            Parameters.buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("x") {
                        put("type", "number")
                        put("description", "X coordinate of the click")
                    }
                    putJsonObject("y") {
                        put("type", "number")
                        put("description", "Y coordinate of the click")
                    }
                }
            }),
        "swipe" to AvailableToolsProperty(
            "swipe", "Swipe from one point to another on the screen", ::swipeTool,
            Parameters.buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("x_start") {
                        put("type", "number")
                        put("description", "X coordinate of the swipe start point")
                    }
                    putJsonObject("y_start") {
                        put("type", "number")
                        put("description", "Y coordinate of the swipe start point")
                    }
                    putJsonObject("x_end") {
                        put("type", "number")
                        put("description", "X coordinate of the swipe end point")
                    }
                    putJsonObject("y_end") {
                        put("type", "number")
                        put("description", "Y coordinate of the swipe end point")
                    }
                }
            }),
        "textEnter" to AvailableToolsProperty(
            "textEnter", "Enter text at a specific x-y coordinates where 0,0 is the top left.", ::textEnterTool,
            Parameters.buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("x") {
                        put("type", "number")
                        put("description", "X coordinate to tap before entering text")
                    }
                    putJsonObject("y") {
                        put("type", "number")
                        put("description", "Y coordinate to tap before entering text")
                    }
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Text to enter")
                    }
                }
            }),
        "goBack" to AvailableToolsProperty(
            "goBack", "Perform back navigation", ::backTool,
            Parameters.buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }),
        "goHome" to AvailableToolsProperty(
            "goHome", "Navigate to home screen", ::homeTool,
            Parameters.buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            })
    )
    open val ai by lazy { CallAI(this, tools, this) }

    // Coroutine scope for background operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var isTaskActive = false
    private var currentPrompt: String? = null
    private var promptReceiver: BroadcastReceiver? = null

    // Accessibility button controller properties
    private var mAccessibilityButtonController: AccessibilityButtonController? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? =
        null
    private var mIsAccessibilityButtonAvailable: Boolean = false

    // Store scale factor for coordinate transformation (uniform scaling)
    private var imageScaleFactor: Float = 1.0f

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

    }


    override fun onInterrupt() {
        overlay!!.disable()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = this.serviceInfo

        info.eventTypes =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        this.serviceInfo = info
        Toast.makeText(this, getString(R.string.app_accessibility_started), Toast.LENGTH_SHORT)
            .show()
        val appContext = applicationContext

        // Initialize accessibility button controller
        setupAccessibilityButton()

        // Initialize state management
        overlayState = OverlayState()

        if (overlay == null) {
            overlay = Overlay(
                appContext,
                getSystemService(WINDOW_SERVICE) as WindowManager,
                overlayState!!
            )
            overlay!!.disable()
            // Set up stop callback
            overlay!!.onStopCallback = { forceStopAITask() }
        } else {
            overlay!!.disable()
        }

        // Register broadcast receiver for prompt communication
        setupPromptReceiver()

        pixelCalculator = PixelCalculator(appContext)
    }

    // Implement ScreenshotRequestListener methods - single point of overlay control
    override fun showAiResponse(response: String, isLoading: Boolean) {
        overlay?.showAiResponse(response, isLoading)
    }

    override fun hideAiResponse() {
        overlay?.hideAiResponse()
    }

    override fun showToolExecution(toolName: String) {
        overlay?.showAiResponse("Executing: $toolName", isLoading = false)
    }

    override fun showError(error: String) {
        overlay?.showAiResponse(error, isLoading = false)
        Log.e("Cooperate", "AI Error: $error")
    }

    private fun setupAccessibilityButton() {
        // Initialize accessibility button controller
        mAccessibilityButtonController = accessibilityButtonController
        mIsAccessibilityButtonAvailable =
            mAccessibilityButtonController?.isAccessibilityButtonAvailable ?: false

        if (!mIsAccessibilityButtonAvailable) {
            Log.w("Cooperate", "Accessibility button not available")
        }

        // Request accessibility button in service info
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        }

        // Create accessibility button callback
        accessibilityButtonCallback =
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    Log.d("Cooperate", "Accessibility button pressed via callback!")

                    // Handle button click - same logic as onAccessibilityButtonClicked
                    if (isTaskActive) {
                        forceStopAITask()
                    } else {
                        launchPromptInputActivity()
                    }
                }

                override fun onAvailabilityChanged(
                    controller: AccessibilityButtonController,
                    available: Boolean
                ) {
                    if (controller == mAccessibilityButtonController) {
                        mIsAccessibilityButtonAvailable = available
                        Log.d("Cooperate", "Accessibility button availability changed: $available")

                        if (!available) {
                            Toast.makeText(
                                this@AppListener,
                                "Accessibility button is no longer available",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

        // Register the callback
        accessibilityButtonCallback?.also { callback ->
            mAccessibilityButtonController?.registerAccessibilityButtonCallback(callback)
            Log.d("Cooperate", "Accessibility button callback registered")
        }
    }

    private fun setupPromptReceiver() {
        promptReceiver = PromptBroadcastManager.createPromptReceiver(
            onPromptSubmitted = { prompt ->
                Log.d("AppListener", "Received prompt from activity: $prompt")
                startAITask(prompt)
            },
            onTaskCancelled = {
                Log.d("AppListener", "Task cancelled by user")
                // Don't start any task
            }
        )

        // Register receiver with RECEIVER_NOT_EXPORTED flag for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                promptReceiver,
                PromptBroadcastManager.getIntentFilter(),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(promptReceiver, PromptBroadcastManager.getIntentFilter(), "app.coreply.cooperate.permission.PROMPT_BROADCAST_PERMISSION", null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlay != null) overlay!!.disable()

        // Unregister accessibility button callback
        accessibilityButtonCallback?.let { callback ->
            mAccessibilityButtonController?.unregisterAccessibilityButtonCallback(callback)
            accessibilityButtonCallback = null
            Log.d("Cooperate", "Accessibility button callback unregistered")
        }

        // Unregister broadcast receiver
        promptReceiver?.let {
            unregisterReceiver(it)
            promptReceiver = null
        }

        // Cancel all background operations
        serviceScope.cancel()
    }

    override fun onScreenshotRequested() {
        screenshotTool()
    }

    private fun launchPromptInputActivity() {
        val intent = Intent(this, PromptInputActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        // No need for handlePromptInput() - the broadcast receiver will handle it
    }

    private fun startAITask(prompt: String) {
        Log.v("Cooperate", "Starting AI task with prompt: $prompt")
        isTaskActive = true
        currentPrompt = prompt

        // Update overlay state
        overlayState?.updateTaskActive(true)

        // Show stop button overlay
        overlay?.showStopButton()

        // Update the AI system prompt with user's request
        updateAIPrompt(prompt)

        // Start taking screenshots and processing
        screenshotTool()

        Toast.makeText(this, "AI task started", Toast.LENGTH_SHORT).show()
    }

    private fun forceStopAITask() {
        try {
            Log.v("Cooperate", "Stopping AI task")
            isTaskActive = false
            currentPrompt = null

            // Update overlay state
            overlayState?.updateTaskActive(false)
            overlayState?.updateShowAiResponse(false)

            // Hide overlays
            overlay?.hideAiResponse()
            overlay?.removeStopButtonOverlay()

            // Reset AI conversation
            resetAIConversation()

            Toast.makeText(this, "Cooperate disabled.", Toast.LENGTH_SHORT).show()
        } finally {
            disableSelf()
        }

    }

    private fun updateAIPrompt(userPrompt: String) {
        // Update the AI's system message with the user's specific request
        ai.updateSystemPrompt(userPrompt)
    }

    private fun resetAIConversation() {
        // Reset the AI conversation to initial state
        ai.resetConversation()
    }

    fun clickTool(args: JsonObject): String {
        var x = args["x"]?.jsonPrimitive?.floatOrNull ?: 0f
        var y = args["y"]?.jsonPrimitive?.floatOrNull ?: 0f

        // Transform coordinates from scaled image space back to actual screen space
        x = x / imageScaleFactor
        y = y / imageScaleFactor

        val tap = StrokeDescription(
            Path().apply { moveTo(x, y) }, 0,
            ViewConfiguration.getTapTimeout().toLong()
        )
        val builder = GestureDescription.Builder()
        builder.addStroke(tap)
        dispatchGesture(builder.build(), null, null)
        Log.v(
            "Cooperate",
            "Click action performed at actual screen coordinates ($x, $y), scale factor: $imageScaleFactor"
        )
        return "Click action performed successfully"
    }

    fun swipeTool(args: JsonObject): String {
        var x_start = args["x_start"]?.jsonPrimitive?.floatOrNull ?: 0f
        var y_start = args["y_start"]?.jsonPrimitive?.floatOrNull ?: 0f
        var x_end = args["x_end"]?.jsonPrimitive?.floatOrNull ?: 0f
        var y_end = args["y_end"]?.jsonPrimitive?.floatOrNull ?: 0f

        // Transform coordinates from scaled image space back to actual screen space
        x_start = x_start / imageScaleFactor
        y_start = y_start / imageScaleFactor
        x_end = x_end / imageScaleFactor
        y_end = y_end / imageScaleFactor

        val swipePath = Path().apply {
            moveTo(x_start, y_start)
            lineTo(x_end, y_end)
        }
        val swipe = StrokeDescription(
            swipePath, 0,
            200
        )
        val builder = GestureDescription.Builder()
        builder.addStroke(swipe)
        dispatchGesture(builder.build(), null, null)
        Log.v(
            "Cooperate",
            "Swipe action performed from actual screen coordinates ($x_start, $y_start) to ($x_end, $y_end), scale factor: $imageScaleFactor"
        )
        return "Swipe action performed successfully"
    }

    fun textEnterTool(args: JsonObject): String {
        var x = args["x"]?.jsonPrimitive?.floatOrNull ?: 0f
        var y = args["y"]?.jsonPrimitive?.floatOrNull ?: 0f

        // Transform coordinates from scaled image space back to actual screen space
        x = x / imageScaleFactor
        y = y / imageScaleFactor

        val text = args["text"]?.jsonPrimitive?.contentOrNull ?: ""
        val tap = StrokeDescription(
            Path().apply { moveTo(x, y) }, 0,
            ViewConfiguration.getTapTimeout().toLong()
        )
        val builder = GestureDescription.Builder()
        builder.addStroke(tap)
        dispatchGesture(builder.build(), null, null)
        Thread.sleep(800)

        val textField = rootInActiveWindow.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (textField != null) {
            textField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            })
            Log.v(
                "Cooperate",
                "Text entered at actual screen coordinates ($x, $y): $text, scale factor: $imageScaleFactor"
            )
            return "Text entered: $text"
        } else {
            Log.e("Cooperate", "Text field not found for input")
            return "Text field not found for input"
        }
    }

    fun screenshotTool() {
        serviceScope.launch {
            delay(1000)
            withContext(Dispatchers.Main) {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY, mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            Log.v("Cooperate", "Screenshot taken successfully")
                            val screenshotBitmap =
                                Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer,
                                    screenshot.colorSpace
                                )
                            if (screenshotBitmap != null) {
                                Log.v(
                                    "Cooperate",
                                    "Original bitmap resolution: ${screenshotBitmap.width} x ${screenshotBitmap.height}"
                                )

                                // Calculate dynamic scaling to make longest edge 1000px
                                val originalWidth = screenshotBitmap.width
                                val originalHeight = screenshotBitmap.height
                                val longestEdge = max(originalWidth, originalHeight)
                                val targetLongestEdge = 1000

                                val scaleFactor =
                                    targetLongestEdge.toFloat() / longestEdge.toFloat()
                                val newWidth = (originalWidth * scaleFactor).toInt()
                                val newHeight = (originalHeight * scaleFactor).toInt()

                                // Store scale factor for coordinate transformation
                                imageScaleFactor = scaleFactor

                                Log.v(
                                    "Cooperate",
                                    "Scaling bitmap from ${originalWidth}x${originalHeight} to ${newWidth}x${newHeight} (scale factor: $scaleFactor)"
                                )

                                val scaledBitmap =
                                    screenshotBitmap.scale(newWidth, newHeight, false)
                                ai.onNewScreenshot(scaledBitmap)
                            }
                        }

                        override fun onFailure(error: Int) {
                            Log.e("Cooperate", "Failed to take screenshot: $error")
                        }
                    })
            }
        }
    }

    fun backTool(args: JsonObject): String {
        performGlobalAction(GLOBAL_ACTION_BACK)
        Log.v("Cooperate", "Back performed")
        return "Back action performed"
    }

    fun homeTool(args: JsonObject): String {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Log.v("Cooperate", "Home performed")
        return "Home action performed"
    }
}
