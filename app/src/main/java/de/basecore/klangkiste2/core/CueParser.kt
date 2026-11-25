package de.basecore.klangkiste2.core

data class CueChapter(val title: String, val timeSeconds: Double)

object CueParser {
    private val titlePattern = Regex("^\s*TITLE\s+\"(.*)\"\s*$", RegexOption.IGNORE_CASE)
    private val indexPattern = Regex("^\s*INDEX\s+01\s+(\d{2}):(\d{2}):(\d{2})\s*$", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<CueChapter> {
        val out = mutableListOf<CueChapter>()
        var title: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("TRACK ", true)) title = null
            titlePattern.find(line)?.let { title = it.groupValues[1] }
            indexPattern.find(line)?.let { m ->
                val minutes = m.groupValues[1].toLongOrNull() ?: 0L
                val seconds = m.groupValues[2].toLongOrNull() ?: 0L
                val frames = m.groupValues[3].toLongOrNull() ?: 0L
                out += CueChapter(title?.takeIf { it.isNotBlank() } ?: "Kapitel ${out.size + 1}", minutes * 60.0 + seconds + frames / 75.0)
                title = null
            }
        }
        return out.sortedBy { it.timeSeconds }
    }
}
