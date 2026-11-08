package de.koii.companion.seq

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PatternBank(
    val patterns: List<Pattern> = List(SLOTS) { Pattern.empty("Pattern ${it + 1}") },
    val selected: Int = 0,
) {
    companion object { const val SLOTS = 8 }

    val current: Pattern get() = patterns[selected.coerceIn(0, patterns.lastIndex)]
}

/** The eight pattern slots, kept in the app's private storage. */
class SequencerRepository(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "patterns.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _bank = MutableStateFlow(load())
    val bank: StateFlow<PatternBank> = _bank

    private var saveJob: Job? = null

    private fun load(): PatternBank = runCatching {
        val b = json.decodeFromString<PatternBank>(file.readText())
        if (b.patterns.size == PatternBank.SLOTS) b else PatternBank()
    }.getOrDefault(PatternBank())

    fun current(): Pattern = _bank.value.current

    fun select(i: Int) = _bank.update { it.copy(selected = i.coerceIn(0, PatternBank.SLOTS - 1)) }.also { scheduleSave() }

    /** Changes the selected pattern. */
    fun edit(f: (Pattern) -> Pattern) {
        _bank.update { b ->
            b.copy(patterns = b.patterns.toMutableList().also { it[b.selected] = f(b.current) })
        }
        scheduleSave()
    }

    /** Puts [p] into the selected slot. */
    fun replace(p: Pattern) = edit { p }

    fun copyToNext() {
        _bank.update { b ->
            val to = (b.selected + 1) % PatternBank.SLOTS
            b.copy(patterns = b.patterns.toMutableList().also { it[to] = b.current.copy(name = "Pattern ${to + 1}") }, selected = to)
        }
        scheduleSave()
    }

    fun exportJson(p: Pattern = current()): String = json.encodeToString(p.trimmed())

    fun parseJson(text: String): Pattern {
        val p = json.decodeFromString<Pattern>(text)
        require(p.steps.size == Pattern.TRACKS && p.steps.all { it.size == Pattern.MAX_STEPS }) { "Not a pattern file" }
        return p.copy(length = p.length.coerceIn(1, Pattern.MAX_STEPS), bpm = p.bpm.coerceIn(30, 300), swing = p.swing.coerceIn(0, 60))
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            runCatching { file.writeText(json.encodeToString(_bank.value)) }
        }
    }
}
