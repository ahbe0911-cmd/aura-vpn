package com.auravpn.vpn

import org.junit.Assert.*
import org.junit.Test

class EngineConnectionGateTest {
    @Test fun missingEngineNeverStarts() {
        val result = EngineConnectionGate.canStart(
            EngineConfigResult.Ready("{}"),
            EngineAvailability.Unavailable("missing native core")
        )
        assertTrue(result is EngineStartResult.Failed)
    }

    @Test fun invalidConfigNeverStarts() {
        val result = EngineConnectionGate.canStart(
            EngineConfigResult.Unsupported("invalid transport"),
            EngineAvailability.Available
        )
        assertTrue(result is EngineStartResult.Failed)
    }

    @Test fun validatedConfigWithAvailableEnginePassesPreflight() {
        val result = EngineConnectionGate.canStart(
            EngineConfigResult.Ready("{}"),
            EngineAvailability.Available
        )
        assertTrue(result is EngineStartResult.Started)
    }
}
