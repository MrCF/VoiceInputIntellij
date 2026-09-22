package it.federico.voiceinput

enum class PunctuationMode(private val label: String) {
    WITHOUT_FINAL_PERIOD("No final period (recommended)"),
    NONE("No punctuation");

    override fun toString(): String = label
}

object TranscriptionText {
    fun format(text: String, mode: PunctuationMode): String {
        val trimmed = text.trim()
        if (mode == PunctuationMode.WITHOUT_FINAL_PERIOD) {
            // Keep closing quotes/brackets even when the period precedes them.
            var end = trimmed.length
            while (end > 0 && trimmed[end - 1] in "\"'’”»)]}") end--
            var start = end
            while (start > 0 && trimmed[start - 1] in ".…。．") start--
            return if (start == end) trimmed else trimmed.removeRange(start, end).trim()
        }

        return buildString(trimmed.length) {
            var pendingSpace = false
            trimmed.forEachIndexed { index, char ->
                val previous = trimmed.getOrNull(index - 1)
                val next = trimmed.getOrNull(index + 1)
                val wordApostrophe = char in "'’" && previous?.isLetter() == true && next?.isLetter() == true
                val decimalSeparator = char in ".," && previous?.isDigit() == true && next?.isDigit() == true
                if (char.isWhitespace() || (isPunctuation(char) && !wordApostrophe && !decimalSeparator)) {
                    pendingSpace = isNotEmpty()
                } else {
                    if (pendingSpace) append(' ')
                    append(char)
                    pendingSpace = false
                }
            }
        }
    }

    private fun isPunctuation(char: Char): Boolean = when (Character.getType(char)) {
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt() -> true

        else -> false
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
