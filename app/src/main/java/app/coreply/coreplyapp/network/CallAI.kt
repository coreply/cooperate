/**
 * coreply
 *
 * Copyright (C) 2024 coreply
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

package app.coreply.coreplyapp.network

import android.content.Context
import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import app.coreply.coreplyapp.utils.ChatContents
import app.coreply.coreplyapp.data.PreferencesManager
import app.coreply.coreplyapp.utils.SuggestionUpdateListener
import com.aallam.openai.api.core.RequestOptions
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.sample
import java.util.concurrent.ConcurrentHashMap


class SuggestionStorageClass(private var listener: SuggestionUpdateListener? = null) {
    private val _suggestionHistory = ConcurrentHashMap<String, String>()
    private val PUNCTUATIONS = listOf(
        "!", "\"", ")", ",", ".", ":",
        ";", "?", "]", "~", "，","。", "：", "；", "？", "）","】", "！", "、", "」",
    )
    private val PUNCTUATIONS_REGEX = "(?=[!\")\\],.:;?~，。：；？）】！、」])".toRegex()

    fun splitAndKeepPunctuations(text: String): List<String> {
        return text.split(PUNCTUATIONS_REGEX).filter { it.isNotEmpty() }
    }


    // Remove all punctuations from the text, remove whitespaces, and lower all characters
    fun getKeyFromText(text: String): String {
        var key = text.trim()
        for (punctuation in PUNCTUATIONS) {
            key = key.replace(punctuation, "")
        }
        key = key.replace(" ", "")
        key = key.lowercase()
        if (!text.isBlank() && PUNCTUATIONS.contains(text.last().toString())) {
            key += "-"

        }
        return key
    }
    fun String.replaceWhiteSpaces(): String {
        return this.replace("\\s+".toRegex(), " ")
    }
    fun getSuggestion(toBeCompleted: String): String? {
        if (toBeCompleted.isBlank()) {
            if (_suggestionHistory.containsKey("")) {
                return _suggestionHistory[""]!!
            }
        }
        for (i in 0..toBeCompleted.length) {
            val target: String = getKeyFromText(toBeCompleted.substring(0, i))
            if (_suggestionHistory.containsKey(target)) {
                val starting = toBeCompleted.substring(i)
                val suggestion = _suggestionHistory[target]!!
                if (starting.isEmpty() || (suggestion.startsWith(starting) &&
                            suggestion.length > starting.length)) {
                    return suggestion.substring(starting.length)
                }
            }
        }
        return null;
    }

    fun clearSuggestion() {
        _suggestionHistory.clear()
    }

    fun setSuggestionUpdateListener(listener: SuggestionUpdateListener) {
        this.listener = listener
    }

    fun updateSuggestion(typingInfo: TypingInfo, newSuggestion: String) {
        if(newSuggestion.replaceWhiteSpaces().lowercase().startsWith(typingInfo.currentTyping.replaceWhiteSpaces().lowercase())) {
            val frontTrimmedSuggestion = newSuggestion.replaceWhiteSpaces().substring(typingInfo.currentTyping.replaceWhiteSpaces().length)
            val splittedText = splitAndKeepPunctuations(frontTrimmedSuggestion)
//            Log.v("CallAI", "Splitted text: $splittedText")
            for (i in 0..splittedText.size - 2) {
//                Log.v("CallAI", getKeyFromText(typingInfo.currentTyping + splittedText.subList(0, i + 1).joinToString("")))
                _suggestionHistory[getKeyFromText(typingInfo.currentTyping + splittedText.subList(0, i + 1).joinToString(""))] =
                    splittedText[i + 1]
            }
            _suggestionHistory[getKeyFromText(typingInfo.currentTyping)] = if( splittedText.isNotEmpty()) splittedText[0] else ""
            listener?.onSuggestionUpdated(typingInfo, frontTrimmedSuggestion) // huh actually the arguments are unused
        }
    }
}

data class TypingInfo(val pastMessages: ChatContents, val currentTyping: String) {
    val currentTypingTrimmed =
        currentTyping.substring(0, currentTyping.length - currentTyping.split(" ").last().length)
            .trimEnd()
}

open class CallAI(open val suggestionStorage: SuggestionStorageClass, private val context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesManager = PreferencesManager.getInstance(context)

    // Flow to handle debouncing of user input
    private val userInputFlow = MutableSharedFlow<TypingInfo>(replay = 1)

    init {
        // Launch a coroutine to collect debounced user input and fetch suggestions
        coroutineScope.launch {
            preferencesManager.loadPreferences()
            userInputFlow // adjust debounce delay as needed
                .debounce(200)
                .collect { typingInfo ->
                    networkScope.launch{
                        fetchSuggestions(typingInfo)
                    }
                }
        }
    }

    fun onUserInputChanged(typingInfo: TypingInfo) {
        // Emit user input to the flow
        coroutineScope.launch {
            userInputFlow.emit(typingInfo)
        }
    }

    private suspend fun fetchSuggestions(typingInfo: TypingInfo) {
        try {
            var suggestions =
                requestSuggestionsFromServer(typingInfo)
            suggestions = suggestions.replace("\n", " ")
            if (suggestions.startsWith(" ")) {
                suggestions = " " + suggestions.trim()
            }
            suggestionStorage.updateSuggestion(typingInfo, suggestions.trimEnd())
        } catch (e: Exception) {
            // Handle exceptions such as network errors
            e.printStackTrace()
        }
    }

    open suspend fun requestSuggestionsFromServer(
        typingInfo: TypingInfo
    ): String {
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
        var userPrompt = "Given this chat history\n" +
                typingInfo.pastMessages.getCoreply2Format() + "\nIn addition to the message I sent,\n" +
                "What else should I send? Or start a new topic?"
        if (typingInfo.currentTyping.isNotBlank()) {
            userPrompt += "The reply should start with '${typingInfo.currentTyping.replace("\\s+".toRegex(), " ")}'\n"
        }
        val request = ChatCompletionRequest(
            temperature = preferencesManager.temperatureState.value.toDouble(),
            model = ModelId(preferencesManager.customModelNameState.value),
            topP = preferencesManager.topPState.value.toDouble(),
            maxTokens = 1000,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = preferencesManager.customSystemPromptState.value.takeIf { it.isNotBlank() }
                        ?: "You are an AI texting assistant. You will be given a list of text messages between the user (indicated by 'Message I sent:'), and other people (indicated by their names or simply 'Message I received:'). You may also receive a screenshot of the conversation. Your job is to suggest the next message the user should send. Match the tone and style of the conversation. The user may request the message start or end with a certain prefix (both could be parts of a longer word) . The user may quote a specific message. In this case, make sure your suggestions are about the quoted message.\nOutput the suggested text only. Do not output anything else. Do not surround output with quotation marks"
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = userPrompt
                ),


                ),
        )
        val response = openAI.chatCompletion(
            request,
            RequestOptions(
                headers = mapOf(
                    "HTTP-Referer" to "https://coreply.app",
                    "X-Title" to "Coreply: Autocomplete for Texting"
                )
            )
        )
        return response.choices.first().message.content?.trim() ?: ""
    }
}