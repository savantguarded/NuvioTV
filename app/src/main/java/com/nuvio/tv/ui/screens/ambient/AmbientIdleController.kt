package com.nuvio.tv.ui.screens.ambient

import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AmbientIdleController {
    private val _lastInteractionAt = MutableStateFlow(SystemClock.uptimeMillis())
    val lastInteractionAt: StateFlow<Long> = _lastInteractionAt.asStateFlow()

    private val _isShowing = MutableStateFlow(false)
    val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

    private var swallowedKeyCode: Int? = null

    // [fork] round 38 ambient_open_details: the slide on screen, and OK asking for its details page
    @Volatile var currentSlide: AmbientSlide? = null
    private val _openRequests = MutableSharedFlow<AmbientSlide>(extraBufferCapacity = 1)
    val openRequests: SharedFlow<AmbientSlide> = _openRequests.asSharedFlow()

    fun onInteraction() {
        _lastInteractionAt.value = SystemClock.uptimeMillis()
    }

    fun show() {
        _isShowing.value = true
    }

    fun dismiss() {
        _isShowing.value = false
        currentSlide = null
        onInteraction()
    }

    fun consumeKeyEvent(event: KeyEvent): Boolean {
        onInteraction()
        val continuesSwallowedPress = event.keyCode == swallowedKeyCode &&
            (event.action == KeyEvent.ACTION_UP || event.repeatCount > 0)
        if (continuesSwallowedPress) {
            if (event.action == KeyEvent.ACTION_UP) swallowedKeyCode = null
            return true
        }
        swallowedKeyCode = null
        if (_isShowing.value && event.action == KeyEvent.ACTION_DOWN) {
            swallowedKeyCode = event.keyCode
            val slide = currentSlide
            if (com.nuvio.tv.NuvioCFeatures.AMBIENT_OPEN_DETAILS && event.keyCode.isOkKey() &&
                slide != null && slide.itemId.isNotBlank() && slide.itemType.isNotBlank()
            ) {
                _openRequests.tryEmit(slide)
            }
            dismiss()
            return true
        }
        return false
    }
}

private fun Int.isOkKey(): Boolean =
    this == KeyEvent.KEYCODE_DPAD_CENTER || this == KeyEvent.KEYCODE_ENTER || this == KeyEvent.KEYCODE_NUMPAD_ENTER
