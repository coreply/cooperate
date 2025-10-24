package app.coreply.cooperate.applistener

import com.aallam.openai.api.core.Parameters
import kotlinx.serialization.json.JsonObject

/**
 * Created on 1/16/17.
 */

data class AvailableToolsProperty(
    val name: String,
    val description: String,
    val function: (JsonObject) -> String,
    val properties: Parameters
)
