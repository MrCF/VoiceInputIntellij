package it.federico.voiceinput

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.APP)
class VoiceHistoryService :
    VoiceSessionService.Listener {

    companion object {

        private const val MAX_ITEMS =
            50

        fun getInstance():
                VoiceHistoryService {

            return ApplicationManager
                .getApplication()
                .getService(
                    VoiceHistoryService::class.java
                )
        }
    }

    data class Entry(
        val timestamp: LocalDateTime,
        val text: String
    )

    interface Listener {

        fun historyChanged()
    }

    private val entries =
        mutableListOf<Entry>()

    private val listeners =
        CopyOnWriteArrayList<Listener>()

    init {

        /*
         * Appena il service viene creato,
         * si registra per ricevere tutte
         * le trascrizioni completate.
         */
        VoiceSessionService.addListener(
            this
        )
    }

    fun getEntries():
            List<Entry> {

        synchronized(entries) {

            return entries.toList()
        }
    }

    fun clear() {

        synchronized(entries) {

            entries.clear()
        }

        notifyListeners()
    }

    fun addListener(
        listener: Listener
    ) {

        listeners.addIfAbsent(
            listener
        )
    }

    fun removeListener(
        listener: Listener
    ) {

        listeners.remove(
            listener
        )
    }

    override fun stateChanged(
        state:
        VoiceSessionService.State
    ) {

        /*
         * La History non deve reagire
         * ai cambi di stato.
         */
    }

    override fun transcriptionCompleted(
        text: String
    ) {

        if (text.isBlank()) {
            return
        }

        synchronized(entries) {

            /*
             * La più recente va in cima.
             */
            entries.add(
                0,
                Entry(
                    timestamp =
                        LocalDateTime.now(),

                    text =
                        text.trim()
                )
            )

            /*
             * Manteniamo al massimo
             * le ultime 50 trascrizioni.
             */
            while (
                entries.size >
                MAX_ITEMS
            ) {

                entries.removeAt(
                    entries.lastIndex
                )
            }
        }

        notifyListeners()
    }

    private fun notifyListeners() {

        listeners.forEach { listener ->

            try {

                listener.historyChanged()

            } catch (_: Exception) {

                /*
                 * Un problema nella UI della
                 * History non deve mai rompere
                 * Voice Input.
                 */
            }
        }
    }
}
