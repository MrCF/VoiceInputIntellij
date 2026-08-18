package it.federico.voiceinput

import com.intellij.openapi.editor.Editor

object WhisperPromptService {

    private const val MAX_REGEX_TERMS = 60

    private const val MAX_PROMPT_LENGTH = 1800

    private const val TEXT_RADIUS = 4000

    private val technicalIdentifier =
        Regex(
            """\b[A-Za-z_][A-Za-z0-9_]{2,}\b"""
        )

    fun buildPrompt(
        editor: Editor?,
        staticPrompt: String
    ): String {

        val terms =
            LinkedHashSet<String>()

        /*
         * 1. PSI
         *
         * È la fonte principale del contesto dinamico.
         */
        WhisperContextService
            .collect(editor)
            .forEach {
                terms += it
            }

        /*
         * 2. Regex
         *
         * Rimane come fallback e permette di recuperare
         * termini che il PSI generico potrebbe non
         * rappresentare come PsiNamedElement.
         */
        collectNearbyTextTerms(
            editor
        )
            .forEach {
                terms += it
            }

        val dynamicPrompt =
            terms.joinToString(", ")

        return buildString {

            if (staticPrompt.isNotBlank()) {
                append(
                    staticPrompt.trim()
                )
            }

            if (dynamicPrompt.isNotBlank()) {

                if (isNotEmpty()) {
                    append(", ")
                }

                append(dynamicPrompt)
            }

        }.take(
            MAX_PROMPT_LENGTH
        )
    }

    private fun collectNearbyTextTerms(
        editor: Editor?
    ): List<String> {

        if (
            editor == null ||
            editor.isDisposed
        ) {
            return emptyList()
        }

        val document =
            editor.document

        val text =
            document.text

        if (text.isEmpty()) {
            return emptyList()
        }

        val caret =
            editor.caretModel.offset

        val start =
            (caret - TEXT_RADIUS)
                .coerceAtLeast(0)

        val end =
            (caret + TEXT_RADIUS)
                .coerceAtMost(
                    text.length
                )

        val nearbyText =
            text.substring(
                start,
                end
            )

        return technicalIdentifier
            .findAll(nearbyText)
            .map {
                it.value
            }
            .filter {
                isInteresting(it)
            }
            .distinct()
            .sortedWith(
                compareByDescending<String> {
                    score(it)
                }.thenBy {
                    it.lowercase()
                }
            )
            .take(
                MAX_REGEX_TERMS
            )
            .toList()
    }

    private fun isInteresting(
        term: String
    ): Boolean {

        if (term.length < 3) {
            return false
        }

        /*
         * Un identificatore con maiuscole è spesso:
         *
         * CustomerController
         * ResponseEntity
         * HttpStatus
         *
         * quindi è particolarmente interessante.
         */
        if (
            term.any {
                it.isUpperCase()
            }
        ) {
            return true
        }

        if (term.contains("_")) {
            return true
        }

        return term in
                commonTechnicalTerms
    }

    private fun score(
        term: String
    ): Int {

        var score = 0

        if (
            term.any {
                it.isUpperCase()
            }
        ) {
            score += 10
        }

        if (term.contains("_")) {
            score += 5
        }

        when {

            term.endsWith(
                "Controller"
            ) -> score += 40

            term.endsWith(
                "Service"
            ) -> score += 40

            term.endsWith(
                "Repository"
            ) -> score += 40

            term.endsWith(
                "Request"
            ) -> score += 35

            term.endsWith(
                "Response"
            ) -> score += 35

            term.endsWith(
                "Dto",
                ignoreCase = true
            ) -> score += 30
        }

        when {

            term.startsWith(
                "find"
            ) -> score += 20

            term.startsWith(
                "create"
            ) -> score += 20

            term.startsWith(
                "save"
            ) -> score += 20

            term.startsWith(
                "update"
            ) -> score += 20

            term.startsWith(
                "delete"
            ) -> score += 20
        }

        return score
    }

    private val commonTechnicalTerms =
        setOf(
            "java",
            "kotlin",
            "spring",
            "boot",
            "rest",
            "endpoint",
            "controller",
            "service",
            "repository",
            "request",
            "response",
            "optional",
            "entity",
            "hibernate",
            "jpa",
            "json",
            "http",
            "https",
            "post",
            "get",
            "put",
            "patch",
            "delete",
            "null",
            "nullable",
            "override",
            "interface",
            "class",
            "enum",
            "record",
            "public",
            "private",
            "protected",
            "static"
        )
}
