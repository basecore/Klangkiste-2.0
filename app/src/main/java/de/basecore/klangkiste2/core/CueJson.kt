package de.basecore.klangkiste2.core
import org.json.JSONArray
object CueParserJson {
    fun parse(json: String): List<CueChapter> = runCatching {
        val a=JSONArray(json)
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { CueChapter(it.optString("title", "Kapitel ${i+1}"), it.optDouble("time", 0.0)) } }.sortedBy { it.timeSeconds }
    }.getOrDefault(emptyList())
}
