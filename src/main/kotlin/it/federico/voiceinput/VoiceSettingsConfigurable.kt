package it.federico.voiceinput

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.sound.sampled.AudioFormat

class VoiceSettingsConfigurable :
    Configurable {

    private var panel: JPanel? = null

    private val whisper =
        WhisperService()

    private val benchmarkRecorder =
        BenchmarkAudioRecorder()

    private var benchmarkAudioDuration =
        0.0

    /*
     * MODEL / MODE
     */

    private val modeCombo =
        JComboBox(
            WhisperModelManager
                .models
                .toTypedArray()
        )

    private val modelNameLabel =
        JBLabel()

    /*
     * LANGUAGE
     */

    private val languageCombo =
        JComboBox(
            arrayOf(
                LanguageOption(
                    "it",
                    "Italian"
                ),
                LanguageOption(
                    "en",
                    "English"
                ),
                LanguageOption(
                    "auto",
                    "Auto detect"
                )
            )
        )

    private val inputDeviceCombo =
        JComboBox(
            AudioInputManager.devices(
                AudioFormat(16_000f, 16, 1, true, false)
            ).toTypedArray()
        )

    /*
     * THREADS
     */

    private val threadsSpinner =
        JSpinner(
            SpinnerNumberModel(
                ThreadConfig.recommended,
                ThreadConfig.minimum,
                ThreadConfig.maximum,
                1
            )
        )

    private val threadsInfoLabel =
        JBLabel(
            "Recommended: ${ThreadConfig.recommended} — " +
                    "Available: ${ThreadConfig.available}"
        )

    /*
     * MODEL MANAGEMENT
     */

    private val modelStatusLabel =
        JBLabel()

    private val modelActionButton =
        JButton()

    private val removeModelButton =
        JButton("Remove")

    private val modelProgressBar =
        JProgressBar(
            0,
            100
        ).apply {
            isStringPainted = true
            isVisible = false
        }

    private val modelProgressLabel =
        JBLabel("").apply {
            isVisible = false
        }

    /*
     * VOICE ACTIVITY DETECTION
     */

    private val vadEnabledCheckBox =
        JCheckBox("Enable VAD")

    private val vadStatusLabel =
        JBLabel()

    private val vadActionButton =
        JButton()

    private val removeVadButton =
        JButton("Remove")

    private val vadProgressBar =
        JProgressBar(
            0,
            100
        ).apply {
            isStringPainted = true
            isVisible = false
        }

    private val vadProgressLabel =
        JBLabel("").apply {
            isVisible = false
        }

    private val vadThresholdSpinner =
        JSpinner(
            SpinnerNumberModel(
                0.50,
                0.0,
                1.0,
                0.05
            )
        )

    private val vadMinSpeechSpinner =
        JSpinner(
            SpinnerNumberModel(
                250,
                0,
                10_000,
                50
            )
        )

    private val vadMinSilenceSpinner =
        JSpinner(
            SpinnerNumberModel(
                700,
                0,
                10_000,
                50
            )
        )

    private val vadSpeechPadSpinner =
        JSpinner(
            SpinnerNumberModel(
                250,
                0,
                5_000,
                50
            )
        )

    private val vadOverlapSpinner =
        JSpinner(
            SpinnerNumberModel(
                0.10,
                0.0,
                5.0,
                0.05
            )
        )

    /*
     * AUTOMATIC STOP
     */

    private val autoStopEnabledCheckBox =
        JCheckBox("Stop recording after silence")

    private val autoStopSilenceSpinner =
        JSpinner(
            SpinnerNumberModel(
                3.0,
                1.5,
                10.0,
                0.5
            )
        )

    private val recordingAudioFeedbackCheckBox =
        JCheckBox("Play sounds when recording starts and stops")

    /*
     * BENCHMARK
     */

    private val benchmarkTypeCombo =
        JComboBox(
            BenchmarkType.entries
                .toTypedArray()
        )

    private val benchmarkSentenceLabel =
        JBLabel()

    private val benchmarkButton =
        JButton(
            "Start benchmark"
        )

    private val benchmarkStatusLabel =
        JBLabel()

    private val benchmarkResultArea =
        JBTextArea().apply {

            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }

    /*
     * TECHNICAL PROMPT
     */

    private val promptArea =
        JBTextArea()

    /*
     * DATA TYPES
     */

    private data class LanguageOption(
        val id: String,
        val displayName: String
    ) {

        override fun toString(): String {
            return displayName
        }
    }

    private enum class BenchmarkType(
        private val displayName: String
    ) {

        NATURAL(
            "Natural language"
        ),

        TECHNICAL(
            "Technical language"
        );

        override fun toString(): String {
            return displayName
        }
    }

    private data class BenchmarkDefinition(
        val sentence: String,
        val technicalTerms: List<String> =
            emptyList()
    )

    override fun getDisplayName(): String {
        return "Voice Input"
    }

    override fun createComponent(): JComponent {

        promptArea.lineWrap = true
        promptArea.wrapStyleWord = true

        val promptScrollPane =
            JScrollPane(
                promptArea
            ).apply {

                preferredSize =
                    Dimension(
                        500,
                        120
                    )
            }

        val resultScrollPane =
            JScrollPane(
                benchmarkResultArea
            ).apply {

                preferredSize =
                    Dimension(
                        500,
                        180
                    )
            }

        /*
         * THREADS PANEL
         */

        val threadsPanel =
            JPanel(
                BorderLayout(
                    10,
                    0
                )
            )

        threadsPanel.add(
            threadsSpinner,
            BorderLayout.WEST
        )

        threadsPanel.add(
            threadsInfoLabel,
            BorderLayout.CENTER
        )

        /*
         * LISTENERS
         */

        modeCombo.addActionListener {

            updateModelStatus()
            updateModelName()
            clearBenchmarkResult()
        }

        languageCombo.addActionListener {

            updateBenchmarkSentence()
        }

        benchmarkTypeCombo.addActionListener {

            updateBenchmarkSentence()
        }

        modelActionButton.addActionListener {

            downloadSelectedModel()
        }

        removeModelButton.addActionListener {

            removeSelectedModel()
        }

        vadEnabledCheckBox.addActionListener {

            updateVadControls()
            updateAutoStopControls()
            clearBenchmarkResult()
        }

        autoStopEnabledCheckBox.addActionListener {

            updateAutoStopControls()
            clearBenchmarkResult()
        }

        vadActionButton.addActionListener {

            downloadVadModel()
        }

        removeVadButton.addActionListener {

            removeVadModel()
        }

        benchmarkButton.addActionListener {

            toggleBenchmark()
        }

        /*
         * FORM
         */

        panel =
            FormBuilder
                .createFormBuilder()

                .addLabeledComponent(
                    JBLabel("Mode:"),
                    modeCombo,
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Model:"),
                    modelNameLabel,
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Model status:"),
                    createModelPanel(),
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Language:"),
                    languageCombo,
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Audio input:"),
                    inputDeviceCombo,
                    1,
                    false
                )

                .addComponent(
                    recordingAudioFeedbackCheckBox
                )

                .addLabeledComponent(
                    JBLabel("Threads:"),
                    threadsPanel,
                    1,
                    false
                )

                .addSeparator()

                .addComponent(
                    JBLabel("<html><b>Voice Activity Detection</b></html>")
                )

                .addComponent(
                    vadEnabledCheckBox
                )

                .addLabeledComponent(
                    JBLabel("VAD model:"),
                    createVadPanel(),
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Threshold:"),
                    vadThresholdSpinner,
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Min speech:"),
                    createValueWithUnitPanel(
                        vadMinSpeechSpinner,
                        "ms"
                    ),
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Min silence:"),
                    createValueWithUnitPanel(
                        vadMinSilenceSpinner,
                        "ms"
                    ),
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Speech padding:"),
                    createValueWithUnitPanel(
                        vadSpeechPadSpinner,
                        "ms"
                    ),
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Overlap:"),
                    createValueWithUnitPanel(
                        vadOverlapSpinner,
                        "s"
                    ),
                    1,
                    false
                )

                .addComponent(
                    JBLabel("<html><b>Automatic stop (silence detection)</b></html>")
                )

                .addComponent(
                    autoStopEnabledCheckBox
                )

                .addLabeledComponent(
                    JBLabel("Silence duration:"),
                    createValueWithUnitPanel(
                        autoStopSilenceSpinner,
                        "s"
                    ),
                    1,
                    false
                )

                .addSeparator()

                .addLabeledComponent(
                    JBLabel("Benchmark type:"),
                    benchmarkTypeCombo,
                    1,
                    false
                )

                .addLabeledComponent(
                    JBLabel("Read aloud:"),
                    benchmarkSentenceLabel,
                    1,
                    false
                )

                .addComponent(
                    benchmarkButton
                )

                .addComponent(
                    benchmarkStatusLabel
                )

                .addLabeledComponent(
                    JBLabel("Benchmark result:"),
                    resultScrollPane,
                    1,
                    false
                )

                .addSeparator()

                .addLabeledComponent(
                    JBLabel("Technical prompt:"),
                    promptScrollPane,
                    1,
                    false
                )

                .addComponentFillVertically(
                    JPanel(),
                    0
                )

                .panel

        reset()

        return panel!!
    }

    /*
     * MODEL PANEL
     */

    private fun createModelPanel(): JPanel {

        val container =
            JPanel(
                BorderLayout(
                    10,
                    5
                )
            )

        val top =
            JPanel(
                BorderLayout(
                    10,
                    0
                )
            )

        top.add(
            modelStatusLabel,
            BorderLayout.CENTER
        )

        val buttons =
            JPanel(
                FlowLayout(
                    FlowLayout.RIGHT,
                    5,
                    0
                )
            )

        buttons.add(
            modelActionButton
        )

        buttons.add(
            removeModelButton
        )

        top.add(
            buttons,
            BorderLayout.EAST
        )

        container.add(
            top,
            BorderLayout.NORTH
        )

        val progress =
            JPanel(
                BorderLayout(
                    5,
                    3
                )
            )

        progress.add(
            modelProgressLabel,
            BorderLayout.NORTH
        )

        progress.add(
            modelProgressBar,
            BorderLayout.CENTER
        )

        container.add(
            progress,
            BorderLayout.CENTER
        )

        return container
    }

    /*
     * VAD PANEL
     */

    private fun createVadPanel(): JPanel {

        val container =
            JPanel(
                BorderLayout(
                    10,
                    5
                )
            )

        val top =
            JPanel(
                BorderLayout(
                    10,
                    0
                )
            )

        top.add(
            vadStatusLabel,
            BorderLayout.CENTER
        )

        val buttons =
            JPanel(
                FlowLayout(
                    FlowLayout.RIGHT,
                    5,
                    0
                )
            )

        buttons.add(
            vadActionButton
        )

        buttons.add(
            removeVadButton
        )

        top.add(
            buttons,
            BorderLayout.EAST
        )

        container.add(
            top,
            BorderLayout.NORTH
        )

        val progress =
            JPanel(
                BorderLayout(
                    5,
                    3
                )
            )

        progress.add(
            vadProgressLabel,
            BorderLayout.NORTH
        )

        progress.add(
            vadProgressBar,
            BorderLayout.CENTER
        )

        container.add(
            progress,
            BorderLayout.CENTER
        )

        return container
    }

    private fun createValueWithUnitPanel(
        component: JComponent,
        unit: String
    ): JPanel {

        return JPanel(
            FlowLayout(
                FlowLayout.LEFT,
                5,
                0
            )
        ).apply {

            add(component)
            add(JBLabel(unit))
        }
    }

    /*
     * CURRENT SELECTION
     */

    private fun selectedModel():
            WhisperModelManager.ModelDefinition {

        return modeCombo.selectedItem
                as WhisperModelManager.ModelDefinition
    }

    private fun selectedLanguage():
            LanguageOption {

        return languageCombo.selectedItem
                as LanguageOption
    }

    private fun selectedInputDevice():
            AudioInputManager.Device {

        return inputDeviceCombo.selectedItem
                as AudioInputManager.Device
    }

    private fun selectedBenchmarkType():
            BenchmarkType {

        return benchmarkTypeCombo.selectedItem
                as BenchmarkType
    }

    private fun selectedThreads(): Int {

        return (
                threadsSpinner.value
                        as Number
                ).toInt()
    }

    /*
     * MODEL UI
     */

    private fun updateModelName() {

        modelNameLabel.text =
            selectedModel()
                .displayName
    }

    private fun updateModelStatus() {

        val model =
            selectedModel()

        val installed =
            WhisperModelManager
                .isInstalled(model)

        if (installed) {

            modelStatusLabel.text =
                "${model.displayName} — Installed"

            modelActionButton.text =
                "Re-download"

            removeModelButton.isEnabled =
                true

            benchmarkButton.isEnabled =
                true

        } else {

            modelStatusLabel.text =
                "${model.displayName} — " +
                        "Not installed " +
                        "(${model.approximateSize})"

            modelActionButton.text =
                "Download"

            removeModelButton.isEnabled =
                false

            benchmarkButton.isEnabled =
                false
        }
    }

    /*
     * VAD UI
     */

    private fun updateVadStatus() {

        val installed =
            WhisperVadManager.isInstalled()

        if (installed) {

            vadStatusLabel.text =
                "Installed"

            vadActionButton.text =
                "Re-download"

            removeVadButton.isEnabled =
                true

        } else {

            vadStatusLabel.text =
                "Not installed"

            vadActionButton.text =
                "Download"

            removeVadButton.isEnabled =
                false

            if (vadEnabledCheckBox.isSelected) {
                vadEnabledCheckBox.isSelected =
                    false
            }
        }

        vadEnabledCheckBox.isEnabled =
            installed

        updateVadControls()
        updateAutoStopControls()
    }

    private fun updateVadControls() {

        val enabled =
            vadEnabledCheckBox.isSelected &&
                    WhisperVadManager.isInstalled()

        vadThresholdSpinner.isEnabled =
            enabled

        vadMinSpeechSpinner.isEnabled =
            enabled

        vadMinSilenceSpinner.isEnabled =
            enabled

        vadSpeechPadSpinner.isEnabled =
            enabled

        vadOverlapSpinner.isEnabled =
            enabled
    }

    private fun updateAutoStopControls() {

        autoStopEnabledCheckBox.isEnabled =
            true

        autoStopSilenceSpinner.isEnabled =
            autoStopEnabledCheckBox.isSelected
    }

    private fun setVadDownloadingState(
        downloading: Boolean
    ) {

        vadEnabledCheckBox.isEnabled =
            !downloading &&
                    WhisperVadManager.isInstalled()

        vadActionButton.isEnabled =
            !downloading

        removeVadButton.isEnabled =
            !downloading &&
                    WhisperVadManager.isInstalled()

        vadProgressBar.isVisible =
            downloading

        vadProgressLabel.isVisible =
            downloading

        if (!downloading) {

            vadProgressBar.value =
                0

            vadProgressBar.string =
                ""

            vadProgressLabel.text =
                ""
        }

        updateVadControls()
        updateAutoStopControls()
    }

    private fun downloadVadModel() {

        val project =
            com.intellij.openapi.project
                .ProjectManager
                .getInstance()
                .openProjects
                .firstOrNull()

        setVadDownloadingState(
            true
        )

        vadStatusLabel.text =
            "Downloading..."

        vadProgressLabel.text =
            "Preparing download..."

        vadProgressBar.value =
            0

        vadProgressBar.string =
            "0%"

        object :
            Task.Backgroundable(
                project,
                "Downloading Voice Input VAD Model",
                true
            ) {

            override fun run(
                indicator: ProgressIndicator
            ) {

                WhisperVadManager.download(
                    object :
                        ProgressIndicator
                        by indicator {

                        override fun setFraction(
                            fraction: Double
                        ) {

                            indicator.fraction =
                                fraction

                            val percent =
                                (
                                        fraction *
                                                100
                                        )
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        100
                                    )

                            SwingUtilities
                                .invokeLater {

                                    vadProgressBar.value =
                                        percent

                                    vadProgressBar.string =
                                        "$percent%"
                                }
                        }

                        override fun setText(
                            text: String?
                        ) {

                            indicator.text =
                                text

                            SwingUtilities
                                .invokeLater {

                                    vadProgressLabel.text =
                                        text ?: ""
                                }
                        }

                        override fun setText2(
                            text: String?
                        ) {

                            indicator.text2 =
                                text

                            SwingUtilities
                                .invokeLater {

                                    if (
                                        !text
                                            .isNullOrBlank()
                                    ) {

                                        vadProgressLabel.text =
                                            text
                                    }
                                }
                        }
                    }
                )
            }

            override fun onSuccess() {

                setVadDownloadingState(
                    false
                )

                updateVadStatus()

                Messages.showInfoMessage(
                    project,
                    "VAD model installed successfully.",
                    "Voice Input"
                )
            }

            override fun onThrowable(
                error: Throwable
            ) {

                setVadDownloadingState(
                    false
                )

                updateVadStatus()

                Messages.showErrorDialog(
                    project,
                    error.message
                        ?: "Unable to download VAD model.",
                    "Voice Input"
                )
            }

            override fun onCancel() {

                setVadDownloadingState(
                    false
                )

                updateVadStatus()
            }
        }.queue()
    }

    private fun removeVadModel() {

        val answer =
            Messages.showYesNoDialog(
                "Remove the local VAD model?",
                "Voice Input",
                Messages.getQuestionIcon()
            )

        if (
            answer !=
            Messages.YES
        ) {
            return
        }

        try {

            WhisperVadManager.delete()

            vadEnabledCheckBox.isSelected =
                false

            updateVadStatus()

        } catch (ex: Exception) {

            Messages.showErrorDialog(
                ex.message
                    ?: "Unable to remove VAD model.",
                "Voice Input"
            )
        }
    }

    /*
     * BENCHMARK DEFINITIONS
     */

    private fun benchmarkDefinition():
            BenchmarkDefinition {

        val language =
            selectedLanguage().id

        val type =
            selectedBenchmarkType()

        /*
         * Auto-detect usa l'italiano come frase predefinita.
         * In futuro potremo rendere anche questo selezionabile.
         */
        return when {

            language == "en" &&
                    type == BenchmarkType.NATURAL ->

                BenchmarkDefinition(
                    sentence =
                        "The trophy didn't fit into the brown suitcase " +
                                "because it was too large."
                )

            language == "en" &&
                    type == BenchmarkType.TECHNICAL ->

                BenchmarkDefinition(
                    sentence =
                        "In CustomerController add a PostMapping endpoint " +
                                "that calls CustomerService and returns " +
                                "a ResponseEntity.",
                    technicalTerms =
                        listOf(
                            "CustomerController",
                            "PostMapping",
                            "CustomerService",
                            "ResponseEntity"
                        )
                )

            type == BenchmarkType.TECHNICAL ->

                BenchmarkDefinition(
                    sentence =
                        "Nel CustomerController aggiungi un endpoint " +
                                "PostMapping che chiama CustomerService " +
                                "e restituisce una ResponseEntity.",
                    technicalTerms =
                        listOf(
                            "CustomerController",
                            "PostMapping",
                            "CustomerService",
                            "ResponseEntity"
                        )
                )

            else ->

                BenchmarkDefinition(
                    sentence =
                        "Il trofeo non entrava nella valigia marrone " +
                                "perché era troppo grande."
                )
        }
    }

    private fun updateBenchmarkSentence() {

        val definition =
            benchmarkDefinition()

        benchmarkSentenceLabel.text =
            "<html>${definition.sentence}</html>"

        clearBenchmarkResult()
    }

    private fun clearBenchmarkResult() {

        benchmarkResultArea.text = ""
        benchmarkStatusLabel.text = ""
    }

    /*
     * BENCHMARK RECORDING
     */

    private fun toggleBenchmark() {

        if (benchmarkRecorder.isRecording) {

            stopBenchmark()

        } else {

            startBenchmark()
        }
    }

    private fun startBenchmark() {

        val model =
            selectedModel()

        if (
            !WhisperModelManager
                .isInstalled(model)
        ) {

            Messages.showWarningDialog(
                "The selected model is not installed.",
                "Voice Input"
            )

            return
        }

        try {

            clearBenchmarkResult()

            benchmarkStatusLabel.text =
                "Recording... Read the sentence above."

            benchmarkRecorder.start()

            benchmarkButton.text =
                "Stop benchmark"

            setBenchmarkControlsEnabled(
                false
            )

            /*
             * Il pulsante Stop deve ovviamente
             * rimanere utilizzabile.
             */
            benchmarkButton.isEnabled =
                true

        } catch (ex: Exception) {

            benchmarkStatusLabel.text =
                ""

            Messages.showErrorDialog(
                ex.message
                    ?: "Unable to start benchmark recording.",
                "Voice Input"
            )
        }
    }

    private fun stopBenchmark() {

        benchmarkAudioDuration =
            benchmarkRecorder.stop()

        benchmarkButton.text =
            "Start benchmark"

        benchmarkButton.isEnabled =
            false

        benchmarkStatusLabel.text =
            "Transcribing benchmark..."

        /*
         * Catturiamo tutto prima di partire con il
         * background task.
         */

        val model =
            selectedModel()

        val language =
            selectedLanguage()

        val benchmarkType =
            selectedBenchmarkType()

        val definition =
            benchmarkDefinition()

        val threads =
            selectedThreads()

        /*
         * Per il benchmark tecnico il prompt è utile:
         * stiamo misurando il comportamento reale del
         * plugin, quindi manteniamo il technical prompt
         * configurato dall'utente.
         */
        val prompt =
            promptArea.text.trim()

        val project =
            com.intellij.openapi.project
                .ProjectManager
                .getInstance()
                .openProjects
                .firstOrNull()

        object :
            Task.Backgroundable(
                project,
                "Running Voice Input Benchmark",
                false
            ) {

            private var transcription =
                ""

            private var transcriptionSeconds =
                0.0

            override fun run(
                indicator: ProgressIndicator
            ) {

                val started =
                    System.nanoTime()

                transcription =
                    whisper.transcribe(
                        audioFile =
                            benchmarkRecorder.outputFile,

                        prompt =
                            prompt,

                        modelDefinition =
                            model,

                        language =
                            language.id,

                        threads =
                            threads
                    )

                transcriptionSeconds =
                    (
                            System.nanoTime() -
                                    started
                            ) /
                            1_000_000_000.0
            }

            override fun onSuccess() {

                setBenchmarkControlsEnabled(
                    true
                )

                benchmarkStatusLabel.text =
                    "Benchmark completed"

                val realTimeFactor =
                    if (
                        benchmarkAudioDuration > 0
                    ) {

                        transcriptionSeconds /
                                benchmarkAudioDuration

                    } else {

                        0.0
                    }

                benchmarkResultArea.text =
                    buildBenchmarkResult(
                        model =
                            model,

                        language =
                            language,

                        benchmarkType =
                            benchmarkType,

                        definition =
                            definition,

                        threads =
                            threads,

                        transcription =
                            transcription,

                        transcriptionSeconds =
                            transcriptionSeconds,

                        realTimeFactor =
                            realTimeFactor
                    )
            }

            override fun onThrowable(
                error: Throwable
            ) {

                setBenchmarkControlsEnabled(
                    true
                )

                benchmarkStatusLabel.text =
                    "Benchmark failed"

                Messages.showErrorDialog(
                    error.message
                        ?: "Benchmark failed.",
                    "Voice Input"
                )
            }
        }.queue()
    }

    private fun setBenchmarkControlsEnabled(
        enabled: Boolean
    ) {

        modeCombo.isEnabled =
            enabled

        languageCombo.isEnabled =
            enabled

        benchmarkTypeCombo.isEnabled =
            enabled

        threadsSpinner.isEnabled =
            enabled

        vadEnabledCheckBox.isEnabled =
            enabled &&
                    WhisperVadManager.isInstalled()

        vadActionButton.isEnabled =
            enabled

        removeVadButton.isEnabled =
            enabled &&
                    WhisperVadManager.isInstalled()

        autoStopEnabledCheckBox.isEnabled =
            enabled

        if (enabled) {
            updateVadControls()
            updateAutoStopControls()
        } else {
            vadThresholdSpinner.isEnabled = false
            vadMinSpeechSpinner.isEnabled = false
            vadMinSilenceSpinner.isEnabled = false
            vadSpeechPadSpinner.isEnabled = false
            vadOverlapSpinner.isEnabled = false
            autoStopEnabledCheckBox.isEnabled = false
            autoStopSilenceSpinner.isEnabled = false
        }

        modelActionButton.isEnabled =
            enabled

        removeModelButton.isEnabled =
            enabled &&
                    WhisperModelManager
                        .isInstalled(
                            selectedModel()
                        )

        benchmarkButton.isEnabled =
            enabled &&
                    WhisperModelManager
                        .isInstalled(
                            selectedModel()
                        )
    }

    /*
     * BENCHMARK RESULT
     */

    private fun buildBenchmarkResult(
        model:
        WhisperModelManager.ModelDefinition,

        language:
        LanguageOption,

        benchmarkType:
        BenchmarkType,

        definition:
        BenchmarkDefinition,

        threads:
        Int,

        transcription:
        String,

        transcriptionSeconds:
        Double,

        realTimeFactor:
        Double
    ): String {

        return buildString {

            appendLine(
                "Mode: ${model.modeName}"
            )

            appendLine(
                "Model: ${model.displayName}"
            )

            appendLine(
                "Language: ${language.displayName}"
            )

            appendLine(
                "Threads: $threads / ${ThreadConfig.available}"
            )

            appendLine(
                "Benchmark: $benchmarkType"
            )

            appendLine()

            appendLine(
                "Audio: %.2f s".format(
                    benchmarkAudioDuration
                )
            )

            appendLine(
                "Transcription: %.2f s".format(
                    transcriptionSeconds
                )
            )

            appendLine(
                "Real-time factor: %.2fx".format(
                    realTimeFactor
                )
            )

            appendLine()

            appendLine("Expected:")
            appendLine(
                definition.sentence
            )

            appendLine()

            appendLine("Recognized:")
            appendLine(
                transcription
            )

            /*
             * Solo il benchmark tecnico ha una
             * valutazione dei termini.
             */
            if (
                benchmarkType ==
                BenchmarkType.TECHNICAL
            ) {

                appendLine()
                appendLine(
                    "Technical terms:"
                )

                appendTechnicalTermResults(
                    this,
                    definition.technicalTerms,
                    transcription
                )
            }
        }
    }

    private fun appendTechnicalTermResults(
        builder: StringBuilder,
        terms: List<String>,
        transcription: String
    ) {

        val normalizedTranscription =
            normalizeForComparison(
                transcription
            )

        var recognized =
            0

        val results =
            terms.map { term ->

                val found =
                    technicalTermMatches(
                        term,
                        normalizedTranscription
                    )

                if (found) {
                    recognized++
                }

                term to found
            }

        builder.appendLine(
            "$recognized / ${terms.size}"
        )

        results.forEach {
                (term, found) ->

            builder.appendLine(
                "$term ${if (found) "✓" else "✗"}"
            )
        }
    }

    /*
     * Qui volutamente non confrontiamo soltanto:
     *
     *     CustomerController
     *
     * perché Whisper potrebbe produrre:
     *
     *     Customer Controller
     *
     * che per il nostro scopo è comunque un
     * riconoscimento corretto.
     */

    private fun technicalTermMatches(
        term: String,
        normalizedTranscription: String
    ): Boolean {

        val normalizedTerm =
            normalizeForComparison(
                term
            )

        if (
            normalizedTranscription
                .contains(normalizedTerm)
        ) {
            return true
        }

        val splitTerm =
            splitCamelCase(term)

        val normalizedSplitTerm =
            normalizeForComparison(
                splitTerm
            )

        return normalizedTranscription
            .contains(
                normalizedSplitTerm
            )
    }

    private fun splitCamelCase(
        value: String
    ): String {

        return value.replace(
            Regex(
                "([a-z0-9])([A-Z])"
            ),
            "$1 $2"
        )
    }

    private fun normalizeForComparison(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                Regex(
                    "[^a-z0-9]+"
                ),
                ""
            )
    }

    /*
     * MODEL DOWNLOAD
     */

    private fun setDownloadingState(
        downloading: Boolean
    ) {

        modeCombo.isEnabled =
            !downloading

        languageCombo.isEnabled =
            !downloading

        benchmarkTypeCombo.isEnabled =
            !downloading

        threadsSpinner.isEnabled =
            !downloading

        modelActionButton.isEnabled =
            !downloading

        removeModelButton.isEnabled =
            !downloading &&
                    WhisperModelManager
                        .isInstalled(
                            selectedModel()
                        )

        benchmarkButton.isEnabled =
            !downloading &&
                    WhisperModelManager
                        .isInstalled(
                            selectedModel()
                        )

        modelProgressBar.isVisible =
            downloading

        modelProgressLabel.isVisible =
            downloading

        if (!downloading) {

            modelProgressBar.value =
                0

            modelProgressBar.string =
                ""

            modelProgressLabel.text =
                ""
        }
    }

    private fun downloadSelectedModel() {

        val model =
            selectedModel()

        val project =
            com.intellij.openapi.project
                .ProjectManager
                .getInstance()
                .openProjects
                .firstOrNull()

        setDownloadingState(
            true
        )

        modelStatusLabel.text =
            "${model.displayName} — Downloading..."

        modelProgressLabel.text =
            "Preparing download..."

        modelProgressBar.value =
            0

        modelProgressBar.string =
            "0%"

        object :
            Task.Backgroundable(
                project,
                "Downloading Voice Input Model",
                true
            ) {

            override fun run(
                indicator:
                ProgressIndicator
            ) {

                WhisperModelManager.download(
                    model,

                    object :
                        ProgressIndicator
                        by indicator {

                        override fun setFraction(
                            fraction: Double
                        ) {

                            indicator.fraction =
                                fraction

                            val percent =
                                (
                                        fraction *
                                                100
                                        )
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        100
                                    )

                            SwingUtilities
                                .invokeLater {

                                    modelProgressBar.value =
                                        percent

                                    modelProgressBar.string =
                                        "$percent%"
                                }
                        }

                        override fun setText(
                            text: String?
                        ) {

                            indicator.text =
                                text

                            SwingUtilities
                                .invokeLater {

                                    modelProgressLabel.text =
                                        text ?: ""
                                }
                        }

                        override fun setText2(
                            text: String?
                        ) {

                            indicator.text2 =
                                text

                            SwingUtilities
                                .invokeLater {

                                    if (
                                        !text
                                            .isNullOrBlank()
                                    ) {

                                        modelProgressLabel.text =
                                            text
                                    }
                                }
                        }
                    }
                )
            }

            override fun onSuccess() {

                setDownloadingState(
                    false
                )

                updateModelStatus()

                Messages.showInfoMessage(
                    project,
                    "${model.displayName} installed successfully.",
                    "Voice Input"
                )
            }

            override fun onThrowable(
                error: Throwable
            ) {

                setDownloadingState(
                    false
                )

                updateModelStatus()

                Messages.showErrorDialog(
                    project,
                    error.message
                        ?: "Unable to download model.",
                    "Voice Input"
                )
            }

            override fun onCancel() {

                setDownloadingState(
                    false
                )

                updateModelStatus()
            }
        }.queue()
    }

    private fun removeSelectedModel() {

        val model =
            selectedModel()

        val answer =
            Messages.showYesNoDialog(
                "Remove the local " +
                        "${model.displayName} model?",
                "Voice Input",
                Messages.getQuestionIcon()
            )

        if (
            answer !=
            Messages.YES
        ) {
            return
        }

        try {

            WhisperModelManager
                .delete(model)

            updateModelStatus()

        } catch (ex: Exception) {

            Messages.showErrorDialog(
                ex.message
                    ?: "Unable to remove model.",
                "Voice Input"
            )
        }
    }

    /*
     * CONFIGURABLE
     */

    override fun isModified(): Boolean {

        val settings =
            VoiceSettings
                .getInstance()
                .state

        return selectedModel().id !=
                settings.modelId ||

                selectedLanguage().id !=
                settings.language ||

                selectedInputDevice().id !=
                settings.inputDeviceId ||

                selectedThreads() !=
                settings.threads ||

                vadEnabledCheckBox.isSelected !=
                settings.vadEnabled ||

                (vadThresholdSpinner.value as Number).toDouble() !=
                settings.vadThreshold ||

                (vadMinSpeechSpinner.value as Number).toInt() !=
                settings.vadMinSpeechDurationMs ||

                (vadMinSilenceSpinner.value as Number).toInt() !=
                settings.vadMinSilenceDurationMs ||

                (vadSpeechPadSpinner.value as Number).toInt() !=
                settings.vadSpeechPadMs ||

                (vadOverlapSpinner.value as Number).toDouble() !=
                settings.vadSamplesOverlapSeconds ||

                autoStopEnabledCheckBox.isSelected !=
                settings.autoStopEnabled ||

                (autoStopSilenceSpinner.value as Number).toDouble() !=
                settings.autoStopSilenceSeconds ||

                recordingAudioFeedbackCheckBox.isSelected !=
                settings.recordingAudioFeedbackEnabled ||

                promptArea.text
                    .trim() !=
                settings.prompt
    }

    override fun apply() {

        val settings =
            VoiceSettings
                .getInstance()
                .state

        settings.modelId =
            selectedModel().id

        settings.language =
            selectedLanguage().id

        settings.inputDeviceId =
            selectedInputDevice().id

        settings.threads =
            selectedThreads()

        settings.vadEnabled =
            vadEnabledCheckBox.isSelected &&
                    WhisperVadManager.isInstalled()

        settings.vadThreshold =
            (vadThresholdSpinner.value as Number)
                .toDouble()

        settings.vadMinSpeechDurationMs =
            (vadMinSpeechSpinner.value as Number)
                .toInt()

        settings.vadMinSilenceDurationMs =
            (vadMinSilenceSpinner.value as Number)
                .toInt()

        settings.vadSpeechPadMs =
            (vadSpeechPadSpinner.value as Number)
                .toInt()

        settings.vadSamplesOverlapSeconds =
            (vadOverlapSpinner.value as Number)
                .toDouble()

        settings.autoStopEnabled =
            autoStopEnabledCheckBox.isSelected

        settings.autoStopSilenceSeconds =
            (autoStopSilenceSpinner.value as Number)
                .toDouble()

        settings.recordingAudioFeedbackEnabled =
            recordingAudioFeedbackCheckBox.isSelected

        settings.prompt =
            promptArea.text.trim()
    }

    override fun reset() {

        val settings =
            VoiceSettings
                .getInstance()
                .state

        modeCombo.selectedItem =
            WhisperModelManager
                .getModelDefinition(
                    settings.modelId
                )

        val language =
            (0 until languageCombo.itemCount)
                .map {
                    languageCombo
                        .getItemAt(it)
                }
                .firstOrNull {
                    it.id ==
                            settings.language
                }
                ?: languageCombo
                    .getItemAt(0)

        languageCombo.selectedItem =
            language

        inputDeviceCombo.selectedItem =
            (0 until inputDeviceCombo.itemCount)
                .map { inputDeviceCombo.getItemAt(it) }
                .firstOrNull { it.id == settings.inputDeviceId }
                ?: inputDeviceCombo.getItemAt(0)

        threadsSpinner.value =
            settings.threads
                .coerceIn(
                    ThreadConfig.minimum,
                    ThreadConfig.maximum
                )

        vadEnabledCheckBox.isSelected =
            settings.vadEnabled &&
                    WhisperVadManager.isInstalled()

        vadThresholdSpinner.value =
            settings.vadThreshold

        vadMinSpeechSpinner.value =
            settings.vadMinSpeechDurationMs

        vadMinSilenceSpinner.value =
            settings.vadMinSilenceDurationMs

        vadSpeechPadSpinner.value =
            settings.vadSpeechPadMs

        vadOverlapSpinner.value =
            settings.vadSamplesOverlapSeconds

        autoStopEnabledCheckBox.isSelected =
            settings.autoStopEnabled

        autoStopSilenceSpinner.value =
            settings.autoStopSilenceSeconds
                .coerceIn(
                    1.5,
                    10.0
                )

        recordingAudioFeedbackCheckBox.isSelected =
            settings.recordingAudioFeedbackEnabled

        promptArea.text =
            settings.prompt

        benchmarkTypeCombo.selectedItem =
            BenchmarkType.NATURAL

        updateModelName()
        updateModelStatus()
        updateVadStatus()
        updateAutoStopControls()
        updateBenchmarkSentence()
    }

    override fun disposeUIResources() {

        if (
            benchmarkRecorder.isRecording
        ) {
            benchmarkRecorder.stop()
        }

        panel = null
    }
}
