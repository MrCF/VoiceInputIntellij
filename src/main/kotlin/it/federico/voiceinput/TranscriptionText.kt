package it.federico.voiceinput

object TranscriptionText {
    fun format(text: String): String {
        val trimmed = text.trim()
        // Keep closing quotes/brackets even when the period precedes them.
        var end = trimmed.length
        while (end > 0 && trimmed[end - 1] in "\"'’”»)]}") end--
        var start = end
        while (start > 0 && trimmed[start - 1] in ".…。．") start--
        val withoutFinalPeriod = if (start == end) trimmed else trimmed.removeRange(start, end).trim()
        return withoutFinalPeriod.lowercase()
    }

    /** Only inspect adjacent characters, never copy the entire target document. */
    fun forInsertion(text: String, before: Char?, after: Char?): String {
        if (text.isBlank()) return ""
        val prefix = before != null && !before.isWhitespace() && before !in "([{«“\"'’" &&
                (text.first().isLetterOrDigit() || text.first() in "([{«“")
        val suffix = after != null && after.isLetterOrDigit() && !text.last().isWhitespace() &&
                text.last() !in "([{«“\"'’"
        return (if (prefix) " " else "") + text + (if (suffix) " " else "")
    }
}
