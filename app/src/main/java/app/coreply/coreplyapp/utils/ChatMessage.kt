package app.coreply.coreplyapp.utils

import com.aallam.openai.api.chat.ChatRole

class ChatMessage {
    // Contains properties: sender, message, timestr and override the toString method
    var sender: String = ""
    var message: String = ""
    var timestr: String = ""

    // Constructor for ChatMessage
    constructor(sender: String, message: String, timestr: String) {
        this.sender = sender
        this.message = message
        this.timestr = timestr
    }

    fun getRole(): ChatRole {
        return if (sender == "Me") ChatRole.Assistant else ChatRole.User
    }

    override fun toString(): String {
        return "$sender:$message"
    }

    override fun equals(other: Any?): Boolean {
        return (other is ChatMessage) && (other.sender == sender) && (other.message == message) && (other.timestr == timestr)
    }
}