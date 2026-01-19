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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized state management for overlay functionality.
 * Contains all overlay-related state that needs to be coordinated across components.
 */
data class OverlayStateData(
    val aiResponse: String? = null,
    val aiLoading: Boolean = false,
    val showAiResponse: Boolean = false,
    val taskActive: Boolean = false
)

class OverlayState {
    private val _state = MutableStateFlow(OverlayStateData())
    val stateFlow: StateFlow<OverlayStateData> = _state.asStateFlow()


    fun updateAiResponse(response: String?) {
        _state.value = _state.value.copy(aiResponse = response)
    }

    fun updateAiLoading(loading: Boolean) {
        _state.value = _state.value.copy(aiLoading = loading)
    }

    fun updateShowAiResponse(show: Boolean) {
        _state.value = _state.value.copy(showAiResponse = show)
    }

    fun updateTaskActive(active: Boolean) {
        _state.value = _state.value.copy(taskActive = active)
    }

    val currentState: OverlayStateData
        get() = _state.value
}
