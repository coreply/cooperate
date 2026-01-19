package app.coreply.cooperate.utils

/**
 * Interface for handling screenshot requests and overlay operations
 * AppListener should be the single point of control for all overlay functionality
 */
interface TaskStateController {
    fun onScreenshotRequested()

    // Overlay control methods - AppListener will implement these
    fun showAiResponse(response: String, isLoading: Boolean = false)
    fun hideAiResponse()
    fun showToolExecution(toolName: String)
    fun showError(error: String)
}