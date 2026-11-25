package de.basecore.klangkiste2.core

data class CueChapter(val title: String, val timeSeconds: Double)

object CueParser {
    private val titlePattern = Regex("""^\s*TITLE\s+"(.*)"\s*$""", RegexOption.IGNORE_CASE)
    private val indexPattern = Regex("""^\s*INDEX\s+01\s+(\d{2}):(\d{2}):(\d{2})\s*$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<CueChapter> {
        val result = mutableListOf<CueChapter>()
        var currentTitle: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("TRACK ", ignoreCase = true)) currentTitle = null
            titlePattern.find(line)?.let { currentTitle = it.groupValues[1] }
            indexPattern.find(line)?.let { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val frames = match.groupValues[3].toLongOrNull() ?: 0L
                result += CueChapter(currentTitle?.takeIf { it.isNotBlank() } ?: "Kapitel ${result.size + 1}", minutes * 60.0 + seconds + frames / 75.0)
                currentTitle = null
            }
        }
        return result.sortedBy { it.timeSeconds }
    }
}
