package de.basecore.klangkiste2.core

object ImportFilter {
    /** Positive terms are ANDed; tokens beginning with '-' exclude a match. */
    fun matches(text: String, age: Int?, query: String, minAge: Int? = null, maxAge: Int? = null): Boolean {
        val haystack = text.lowercase().trim()
        val terms = query.lowercase().trim().split(Regex("\s+")).filter { it.isNotBlank() }
        for (term in terms) {
            if (term.startsWith("-") && term.length > 1) {
                if (haystack.contains(term.drop(1))) return false
            } else if (!haystack.contains(term)) return false
        }
        // Native policy is consistent in import and library: missing age is treated as 0/no recommendation.
        val effectiveAge = age ?: 0
        if (minAge != null && effectiveAge < minAge) return false
        if (maxAge != null && effectiveAge > maxAge) return false
        return true
    }
}
