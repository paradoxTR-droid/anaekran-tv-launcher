package dev.anaekran

interface IntroState {
    var introPending: Boolean
    var lastBootCount: Int
}

/** Giriş animasyonu: cihaz açılışından sonra ve bekleme modundan her uyanışta bir kez. */
class IntroGate(private val state: IntroState) {
    fun onScreenOff() {
        state.introPending = true
    }

    fun shouldPlay(bootCount: Int): Boolean =
        state.introPending || (bootCount >= 0 && bootCount != state.lastBootCount)

    fun markPlayed(bootCount: Int) {
        state.introPending = false
        if (bootCount >= 0) state.lastBootCount = bootCount
    }
}
