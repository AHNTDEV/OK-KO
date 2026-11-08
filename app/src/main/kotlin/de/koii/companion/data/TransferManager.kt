package de.koii.companion.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.koii.companion.service.TransferService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TransferState(
    val active: Boolean = false,
    val label: String = "",
    val done: Long = 0,
    val total: Long = 0,
    val queued: Int = 0,
    val lastError: String? = null,
) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Runs device transfers one after another in the application scope. While jobs are queued a foreground
 * service keeps the process alive, so Android does not kill a running backup or upload.
 */
class TransferManager(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state

    private val gate = Mutex()
    private var queued = 0

    fun enqueue(label: String, block: suspend (progress: (done: Long, total: Long) -> Unit) -> Unit) {
        queued++
        _state.value = _state.value.copy(queued = queued, lastError = null)
        if (queued == 1) ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
        scope.launch {
            gate.withLock {
                _state.value = _state.value.copy(active = true, label = label, done = 0, total = 0)
                try {
                    block { done, total -> _state.value = _state.value.copy(done = done, total = total) }
                } catch (e: Exception) {
                    _state.value = _state.value.copy(lastError = "$label: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            queued--
            _state.value = _state.value.copy(queued = queued, active = queued > 0)
        }
    }
}
