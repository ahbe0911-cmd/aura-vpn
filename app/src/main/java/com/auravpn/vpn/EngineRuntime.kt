package com.auravpn.vpn

import android.content.Context

/**
 * Runtime boundary for the native Xray engine. No native library is bundled yet.
 * The UI must never report CONNECTED from a config parse or VPN permission alone.
 */
interface EngineRuntime {
    fun availability(): EngineAvailability
    fun start(configJson: String): EngineStartResult
    fun stop()
}

sealed class EngineAvailability {
    object Available : EngineAvailability()
    data class Unavailable(val reason: String) : EngineAvailability()
}

sealed class EngineStartResult {
    object Started : EngineStartResult()
    data class Failed(val reason: String) : EngineStartResult()
}

/** Safe default until a verified Android-compatible native Xray build is packaged. */
class NativeXrayRuntime(private val context: Context) : EngineRuntime {
    override fun availability(): EngineAvailability =
        EngineAvailability.Unavailable("Xray native engine is not bundled with this APK")

    override fun start(configJson: String): EngineStartResult =
        EngineStartResult.Failed("Xray native engine is not bundled with this APK")

    override fun stop() = Unit
}

object EngineConnectionGate {
    fun canStart(config: EngineConfigResult, availability: EngineAvailability): EngineStartResult {
        if (config is EngineConfigResult.Unsupported) {
            return EngineStartResult.Failed(config.reason)
        }
        if (availability is EngineAvailability.Unavailable) {
            return EngineStartResult.Failed(availability.reason)
        }
        return EngineStartResult.Started
    }
}
