package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.core.service.TranscriptionRetention

/**
 * Binds history retention to one microphone session and only permits privacy downgrades.
 * Moving from a private editor to an ordinary editor can never make existing audio durable.
 */
class ImeTranscriptionRetentionSession {
    private var retention = TranscriptionRetention.EPHEMERAL

    fun begin(editorEligible: Boolean, personalizedLearningAllowed: Boolean) {
        retention = resolve(editorEligible, personalizedLearningAllowed)
    }

    fun restrict(editorEligible: Boolean, personalizedLearningAllowed: Boolean) {
        if (resolve(editorEligible, personalizedLearningAllowed) == TranscriptionRetention.EPHEMERAL) {
            retention = TranscriptionRetention.EPHEMERAL
        }
    }

    fun consume(): TranscriptionRetention = retention.also {
        retention = TranscriptionRetention.EPHEMERAL
    }

    fun reset() {
        retention = TranscriptionRetention.EPHEMERAL
    }

    companion object {
        fun resolve(
            editorEligible: Boolean,
            personalizedLearningAllowed: Boolean,
        ): TranscriptionRetention = if (editorEligible && personalizedLearningAllowed) {
            TranscriptionRetention.PERSIST_HISTORY
        } else {
            TranscriptionRetention.EPHEMERAL
        }
    }
}
