package dev.pivisolutions.dictus.service

/** Main-thread session gate. Tokens make delayed start intents harmless after cancellation. */
internal class DictationSessionOwnership {
    enum class Owner { LEGACY, COMPANION }
    enum class Phase { PENDING, RECORDING, TRANSCRIBING }
    private var nextToken = 0L
    var companionOwned = false
        private set
    private var releasing = false
    var token: Long? = null
        private set
    var phase: Phase? = null
        private set
    private var owner: Owner? = null
    private var destroyed = false

    fun acquireCompanion(): Boolean {
        if (destroyed || releasing || token != null) return false
        companionOwned = true
        return true
    }

    fun permits(requester: Owner): Boolean = !destroyed && !releasing &&
        (companionOwned == (requester == Owner.COMPANION))

    fun requestStart(requester: Owner): Long? {
        if (!permits(requester) || token != null) return null
        owner = requester
        phase = Phase.PENDING
        return (++nextToken).also { token = it }
    }

    fun acceptStart(request: Long): Boolean {
        if (destroyed || token != request || phase != Phase.PENDING) return false
        phase = Phase.RECORDING
        return true
    }

    fun beginTranscription(requester: Owner): Long? {
        if (!permits(requester) || owner != requester || phase != Phase.RECORDING) return null
        phase = Phase.TRANSCRIBING
        return token
    }

    fun finish(request: Long): Boolean {
        if (token != request) return false
        token = null
        owner = null
        phase = null
        if (releasing) {
            companionOwned = false
            releasing = false
        }
        return true
    }

    fun releaseCompanion() {
        if (!companionOwned) return
        if (token == null) companionOwned = false else releasing = true
    }

    fun destroy() {
        destroyed = true
        token = null
        phase = null
        owner = null
        companionOwned = false
        releasing = false
    }
}
