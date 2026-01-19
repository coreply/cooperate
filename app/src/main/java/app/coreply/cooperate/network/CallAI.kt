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

package app.coreply.cooperate.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import app.coreply.cooperate.applistener.AvailableToolsProperty
import app.coreply.cooperate.data.PreferencesManager
import app.coreply.cooperate.utils.TaskStateController
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.core.RequestOptions
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream


@OptIn(FlowPreview::class)
open class CallAI(
    context: Context,
    val availableTools: Map<String, AvailableToolsProperty>,
    private var listener: TaskStateController
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesManager = PreferencesManager.getInstance(context)

    // Flow to handle debouncing of user input
    private val screenshotFlow = MutableSharedFlow<Bitmap>(replay = 1)
    private val chatMessages: MutableList<ChatMessage> = mutableListOf()
    private val initialMessage = chatMessages.toMutableList()

    init {
        // Launch a coroutine to collect debounced user input and fetch suggestions
        coroutineScope.launch {
            preferencesManager.loadPreferences()
            screenshotFlow // adjust debounce delay as needed
                .debounce(1000)
                .collect { typingInfo ->
                    networkScope.launch {
                        fetchSuggestions(typingInfo)
                    }
                }
        }
    }

    private fun MutableList<ChatMessage>.append(
        toolCall: ToolCall.Function,
        functionResponse: String
    ) {
        val message = ChatMessage(
            role = ChatRole.Tool,
            toolCallId = toolCall.id,
            name = toolCall.function.name,
            content = functionResponse
        )
        add(message)
    }

    fun Bitmap.toBase64Url(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        return "data:image/png;base64,$base64String"
    }

    private fun ToolCall.Function.execute(): String {
        val functionToCall =
            availableTools[function.name] ?: error("Function ${function.name} not found")
        if (function.arguments.isNotEmpty()) {
            val functionArgs = function.argumentsAsJson()
            return functionToCall.function(functionArgs)
        } else {
            val functionArgs = JsonObject(mapOf())
            return functionToCall.function(functionArgs)
        }
    }

    fun onNewScreenshot(screenshotBitmap: Bitmap) {
        // Emit user input to the flow
        coroutineScope.launch {
            screenshotFlow.emit(screenshotBitmap)
        }
    }

    fun updateSystemPrompt(userPrompt: String) {
        // Clear existing messages and set new system prompt
        chatMessages.clear()
        chatMessages.add(
            ChatMessage(
                role = ChatRole.System,
                content = "${preferencesManager.customSystemPromptState.value}\n\nYour task: $userPrompt"
            )
        )
        // Update initial message for conversation resets
        initialMessage.clear()
        initialMessage.addAll(chatMessages)
    }

    fun resetConversation() {
        chatMessages.clear()
        chatMessages.addAll(initialMessage)
    }

    private suspend fun fetchSuggestions(screenshotBitmap: Bitmap) {
        try {
//            val base64URL = screenshotBitmap.toBase64Url()
//            chatMessages.add(
//                ChatMessage(
//                    role = ChatRole.User,
//                    content = ListContent(
//                        listOf(
//                            ImagePart(url = base64URL),
//                        )
//                    ).content,
//                )
//            )
//            This is intended as too many screenshots use a lot of tokens. Ask the model to output a text summary of the screenshot instead
            val message = requestSuggestionsFromServer(screenshotBitmap)
            chatMessages.add(message)

            // Display AI response via AppListener
            val responseText = message.content ?: "AI is processing..."
            listener.showAiResponse(responseText, isLoading = false)

            // Process tool calls
            kotlinx.coroutines.delay(1000)
            for (toolCall in message.toolCalls.orEmpty()) {
                if (toolCall is ToolCall.Function) {
                    kotlinx.coroutines.delay(1000)
                    // Hide AI response when using tools via AppListener
                    listener.hideAiResponse()

                    // Execute the tool call and get the response
                    val functionResponse = toolCall.execute()
                    chatMessages.append(toolCall, functionResponse)
                }
            }

            if (message.toolCalls?.isNotEmpty() ?: false) {
                listener.onScreenshotRequested()
            }
            else{
                // TODO: Implement end of task.
            }

        } catch (e: Exception) {
            // Handle exceptions via AppListener
            listener.showError("Error: ${e.message}")
            e.printStackTrace()
        }
    }

    open suspend fun requestSuggestionsFromServer(screenshotBitmap: Bitmap): ChatMessage {
        var baseUrl = preferencesManager.customApiUrlState.value
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }
        val host = OpenAIHost(
            baseUrl = baseUrl,
        )
        val config = OpenAIConfig(
            host = host,
            token = preferencesManager.customApiKeyState.value,
        )

        val openAI = OpenAI(config)
        var reqMessages = chatMessages.toMutableList()
        if (reqMessages.size > 20) {
            reqMessages = (initialMessage + reqMessages.subList(
                reqMessages.size - 10,
                reqMessages.size
            )).toMutableList()
        }
        val base64URL = screenshotBitmap.toBase64Url()
        val request = chatCompletionRequest {
            temperature = preferencesManager.temperatureState.value.toDouble()
            model = ModelId(preferencesManager.customModelNameState.value)
            topP = preferencesManager.topPState.value.toDouble()
            messages = reqMessages + arrayOf(
                ChatMessage(
                    role = ChatRole.User,
                    content = ListContent(
                        listOf(
                            ImagePart(url = base64URL),
                        )
                    ).content
                )
            )
            tools {
                availableTools.forEach {
                    function(
                        it.key,
                        it.value.description,
                        it.value.properties
                    )
                }
            }
            toolChoice = ToolChoice.Auto

        }
        Log.v("CallAI", "Requesting suggestions with prompt: $chatMessages")
        val response = openAI.chatCompletion(
            request,
            RequestOptions(
                headers = mapOf(
                    "HTTP-Referer" to "https://github.com/coreply/cooperate",
                    "X-Title" to "Cooperate: Android Control Using Claude"
                )
            )
        )

        Log.v("CallAI", "Response: ${response.choices.first().message}")
        return response.choices.first().message
    }
}