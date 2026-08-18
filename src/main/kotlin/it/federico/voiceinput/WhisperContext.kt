package it.federico.voiceinput

data class WhisperContext(
    val fileName: String? = null,
    val language: String? = null,
    val terms: List<String> = emptyList()
)
