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

package app.coreply.cooperate.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.coreply.cooperate.ui.compose.AIResponseOverlay
import app.coreply.cooperate.ui.compose.LifeCycleThings
import app.coreply.cooperate.ui.compose.StopControlOverlay
import app.coreply.cooperate.utils.PixelCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class Overlay(
    context: Context?,
    val windowManager: WindowManager,
    private val overlayState: OverlayState
) : ContextWrapper(context), ViewModelStoreOwner {

    private var pixelCalculator: PixelCalculator
    private var aiResponseParams: WindowManager.LayoutParams
    private var stopButtonParams: WindowManager.LayoutParams
    private var aiResponseComposeView: ComposeView
    private var stopButtonComposeView: ComposeView
    private var DP8 = 0
    private var DP48 = 0
    private var DP20 = 0

    // Callback for stop button
    var onStopCallback: (() -> Unit)? = null

    override val viewModelStore = ViewModelStore()
    private val lifeCycleThings = LifeCycleThings()

    init {
        pixelCalculator = PixelCalculator(this)
        aiResponseParams = WindowManager.LayoutParams()
        stopButtonParams = WindowManager.LayoutParams()
        DP8 = pixelCalculator.dpToPx(8)
        DP48 = pixelCalculator.dpToPx(48)
        DP20 = pixelCalculator.dpToPx(20)

        aiResponseComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifeCycleThings)
            setViewTreeSavedStateRegistryOwner(lifeCycleThings)
            setViewTreeViewModelStoreOwner(this@Overlay)
        }

        stopButtonComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifeCycleThings)
            setViewTreeSavedStateRegistryOwner(lifeCycleThings)
            setViewTreeViewModelStoreOwner(this@Overlay)
        }


        // Configure window parameters for AI response overlay
        aiResponseParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        aiResponseParams.flags =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        aiResponseParams.format = PixelFormat.TRANSLUCENT
        aiResponseParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        aiResponseParams.y = DP48
        aiResponseParams.width = WindowManager.LayoutParams.MATCH_PARENT
        aiResponseParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        aiResponseParams.alpha = 1.0f
        aiResponseParams.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

        // Configure window parameters for stop button overlay
        stopButtonParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        stopButtonParams.flags =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        stopButtonParams.format = PixelFormat.TRANSLUCENT
        stopButtonParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        stopButtonParams.width = WindowManager.LayoutParams.MATCH_PARENT
        stopButtonParams.height = DP20
        stopButtonParams.y = 0  // Set to 0 when using BOTTOM gravity
        stopButtonParams.x = 0
        stopButtonParams.alpha = 1.0f
        stopButtonParams.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }


    fun disable() {
        removeAiResponseOverlay()
        removeStopButtonOverlay()
    }

    fun showAiResponse(response: String, isLoading: Boolean = false) {
        MainScope().launch {
            withContext(Dispatchers.Main) {
                overlayState.updateAiResponse(response)
                overlayState.updateAiLoading(isLoading)
                overlayState.updateShowAiResponse(true)

                aiResponseComposeView.setContent {
                    AIResponseOverlay(
                        response = response,
                        isLoading = isLoading,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Position AI response overlay at top of screen
                if (!aiResponseComposeView.isShown) {
                    windowManager.addView(aiResponseComposeView, aiResponseParams)
                } else {
                    windowManager.updateViewLayout(aiResponseComposeView, aiResponseParams)
                }

                lifeCycleThings.refreshLifecycle()
            }
        }
    }

    fun hideAiResponse() {
        overlayState.updateShowAiResponse(false)
        removeAiResponseOverlay()
    }

    fun showStopButton() {
        MainScope().launch {
            withContext(Dispatchers.Main) {
                stopButtonComposeView.setContent {
                    StopControlOverlay(
                        onStopClick = { onStopCallback?.invoke() }
                    )
                }

                if (!stopButtonComposeView.isShown) {
                    windowManager.addView(stopButtonComposeView, stopButtonParams)
                } else {
                    windowManager.updateViewLayout(stopButtonComposeView, stopButtonParams)
                }

                lifeCycleThings.refreshLifecycle()
            }
        }
    }

    fun removeAiResponseOverlay() {
        if (aiResponseComposeView.isShown) {
            windowManager.removeView(aiResponseComposeView)
        }
    }

    fun removeStopButtonOverlay() {
        if (stopButtonComposeView.isShown) {
            windowManager.removeView(stopButtonComposeView)
        }
    }
}
