package app.coreply.cooperate.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Better communication using broadcasts between PromptInputActivity and AppListener service
 */
object PromptBroadcastManager {
    const val ACTION_PROMPT_SUBMITTED = "app.coreply.cooperate.PROMPT_SUBMITTED"
    const val ACTION_TASK_CANCELLED = "app.coreply.cooperate.TASK_CANCELLED"
    const val EXTRA_PROMPT_TEXT = "prompt_text"
    
    fun sendPromptSubmitted(context: Context, promptText: String) {
        val intent = Intent(ACTION_PROMPT_SUBMITTED).apply {
            putExtra(EXTRA_PROMPT_TEXT, promptText)
            setPackage(context.packageName) // Keep it internal to our app
        }
        context.sendBroadcast(intent)
        Log.d("PromptBroadcast", "Sent prompt: $promptText")
    }
    
    fun sendTaskCancelled(context: Context) {
        val intent = Intent(ACTION_TASK_CANCELLED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
        Log.d("PromptBroadcast", "Sent task cancelled")
    }
    
    fun createPromptReceiver(
        onPromptSubmitted: (String) -> Unit,
        onTaskCancelled: () -> Unit
    ): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PROMPT_SUBMITTED -> {
                        val promptText = intent.getStringExtra(EXTRA_PROMPT_TEXT) ?: return
                        Log.d("PromptBroadcast", "Received prompt: $promptText")
                        onPromptSubmitted(promptText)
                    }
                    ACTION_TASK_CANCELLED -> {
                        Log.d("PromptBroadcast", "Received task cancelled")
                        onTaskCancelled()
                    }
                }
            }
        }
    }
    
    fun getIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(ACTION_PROMPT_SUBMITTED)
            addAction(ACTION_TASK_CANCELLED)
        }
    }
}
