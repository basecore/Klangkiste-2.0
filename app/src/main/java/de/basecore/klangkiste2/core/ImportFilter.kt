package de.basecore.klangkiste2.core

/** Positive terms are ANDed; a term prefixed with '-' excludes a match. */
object ImportFilter {
    fun matches(text: String, age: Int?, query: String, minAge: Int? = null, maxAge: Int? = null): Boolean {
        val haystack = text.lowercase().trim()
        val terms = query.lowercase().trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        for (term in terms) {
            if (term.startsWith("-") && term.length > 1) {
                if (haystack.contains(term.drop(1))) return false
            } else if (!haystack.contains(term)) return false
        }
        val effectiveAge = age ?: 0
        if (minAge != null && effectiveAge < minAge) return false
        if (maxAge != null && effectiveAge > maxAge) return false
        return true
    }
}
