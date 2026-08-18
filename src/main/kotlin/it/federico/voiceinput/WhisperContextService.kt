package it.federico.voiceinput

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor

object WhisperContextService {

    private const val MAX_PSI_TERMS = 120
    private const val MAX_NAME_LENGTH = 100

    fun collect(editor: Editor?): List<String> {

        if (editor == null || editor.isDisposed) {
            return emptyList()
        }

        val project = editor.project ?: return emptyList()

        val virtualFile =
            editor.virtualFile ?: return emptyList()

        val psiFile =
            PsiManager
                .getInstance(project)
                .findFile(virtualFile)
                ?: return emptyList()

        return collectFromPsiFile(
            psiFile,
            editor.caretModel.offset
        )
    }

    private fun collectFromPsiFile(
        psiFile: PsiFile,
        caretOffset: Int
    ): List<String> {

        val result =
            LinkedHashSet<String>()

        /*
         * Il nome del file è molto importante.
         *
         * CustomerController.java
         * diventa CustomerController.
         */
        psiFile.virtualFile
            ?.nameWithoutExtension
            ?.let {
                addTerm(result, it)
            }

        /*
         * Prima raccogliamo gli elementi PSI che si trovano
         * direttamente attorno al cursore.
         *
         * Questi hanno la priorità maggiore.
         */
        collectCaretContext(
            psiFile,
            caretOffset,
            result
        )

        /*
         * Poi attraversiamo il file e raccogliamo tutti
         * gli elementi PSI che possiedono un nome:
         *
         * classi
         * metodi
         * campi
         * parametri
         * variabili
         * ecc.
         *
         * Il significato preciso dipende dal linguaggio,
         * ma PsiNamedElement è disponibile genericamente.
         */
        psiFile.accept(
            object :
                PsiRecursiveElementWalkingVisitor() {

                override fun visitElement(
                    element: PsiElement
                ) {

                    if (
                        result.size >=
                        MAX_PSI_TERMS
                    ) {
                        stopWalking()
                        return
                    }

                    if (
                        element is PsiNamedElement
                    ) {

                        addTerm(
                            result,
                            element.name
                        )
                    }

                    super.visitElement(element)
                }
            }
        )

        return result
            .take(MAX_PSI_TERMS)
    }

    private fun collectCaretContext(
        psiFile: PsiFile,
        caretOffset: Int,
        result: LinkedHashSet<String>
    ) {

        if (psiFile.textLength == 0) {
            return
        }

        val safeOffset =
            caretOffset.coerceIn(
                0,
                psiFile.textLength - 1
            )

        var element: PsiElement? =
            psiFile.findElementAt(
                safeOffset
            )

        /*
         * Risaliamo la gerarchia PSI.
         *
         * Per esempio:
         *
         * identificatore
         * → metodo
         * → classe
         * → file
         */
        while (
            element != null &&
            element != psiFile
        ) {

            if (
                element is PsiNamedElement
            ) {

                addTerm(
                    result,
                    element.name
                )
            }

            element =
                element.parent
        }
    }

    private fun addTerm(
        result: MutableSet<String>,
        value: String?
    ) {

        val term =
            value
                ?.trim()
                ?.takeIf {
                    it.length in 2..MAX_NAME_LENGTH
                }
                ?: return

        /*
         * Evitiamo nomi che non assomigliano minimamente
         * a identificatori di codice.
         */
        if (
            !term.matches(
                Regex(
                    """[A-Za-z_$][A-Za-z0-9_$]*"""
                )
            )
        ) {
            return
        }

        result += term
    }
}
