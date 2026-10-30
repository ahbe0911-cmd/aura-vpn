package com.auravpn.vpn

import org.junit.Assert.*
import org.junit.Test

class XrayConfigBuilderTest {
    @Test fun createsTrojanTlsOutbound() {
        val result = XrayConfigBuilder.fromShareLink("trojan://password@example.com:443?security=tls&sni=example.com")
        assertTrue(result is EngineConfigResult.Ready)
        val json = org.json.JSONObject((result as EngineConfigResult.Ready).json)
        assertEquals("trojan", json.getJSONArray("outbounds").getJSONObject(0).getString("protocol"))
    }

    @Test fun createsWebSocketOutbound() {
        val result = XrayConfigBuilder.fromShareLink("trojan://password@example.com:443?type=ws&security=tls&path=%2Fvpn&host=cdn.example.com")
        assertTrue(result is EngineConfigResult.Ready)
        val outbound = org.json.JSONObject((result as EngineConfigResult.Ready).json).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("ws", outbound.getJSONObject("streamSettings").getString("network"))
        assertEquals("/vpn", outbound.getJSONObject("streamSettings").getJSONObject("wsSettings").getString("path"))
    }

    @Test fun rejectsUnsupportedTransport() {
        val result = XrayConfigBuilder.fromShareLink("trojan://password@example.com:443?type=grpc&security=tls")
        assertTrue(result is EngineConfigResult.Unsupported)
    }

    @Test fun rejectsUnrecognizedSecurityParameters() {
        val result = XrayConfigBuilder.fromShareLink("trojan://password@example.com:443?security=tls&pbk=secret")
        assertTrue(result is EngineConfigResult.Unsupported)
    }

    @Test fun rejectsInsecureTls() {
        val result = XrayConfigBuilder.fromShareLink("trojan://password@example.com:443?security=tls&allowInsecure=1")
        assertTrue(result is EngineConfigResult.Unsupported)
    }
}
