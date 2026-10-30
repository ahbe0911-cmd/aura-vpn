package com.auravpn.vpn

import org.junit.Assert.*
import org.junit.Test

class XrayConfigBuilderTest {
    @Test fun createsVmessWebSocketTlsOutbound() {
        val payload = """{"v":"2","ps":"My VMess","add":"edge.example.com","port":"443","id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","scy":"auto","net":"ws","host":"cdn.example.com","path":"/ray","tls":"tls","sni":"edge.example.com"}"""
        val encoded = java.util.Base64.getEncoder().encodeToString(payload.toByteArray())
        val result = XrayConfigBuilder.fromShareLink("vmess://$encoded")
        assertTrue(result.toString(), result is EngineConfigResult.Ready)
        val outbound = org.json.JSONObject((result as EngineConfigResult.Ready).json)
            .getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vmess", outbound.getString("protocol"))
        assertEquals("ws", outbound.getJSONObject("streamSettings").getString("network"))
        assertEquals("/ray", outbound.getJSONObject("streamSettings").getJSONObject("wsSettings").getString("path"))
    }

    @Test fun rejectsVmessUnsupportedTransport() {
        val payload = """{"add":"edge.example.com","port":"443","id":"b831381d-6324-4d53-ad4f-8cda48b30811","net":"grpc","tls":"tls"}"""
        val encoded = java.util.Base64.getEncoder().encodeToString(payload.toByteArray())
        val result = XrayConfigBuilder.fromShareLink("vmess://$encoded")
        assertTrue(result is EngineConfigResult.Unsupported)
    }

    @Test fun createsShadowsocksOutbound() {
        val result = XrayConfigBuilder.fromShareLink("ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@example.com:8388#Home")
        assertTrue(result.toString(), result is EngineConfigResult.Ready)
        val outbound = org.json.JSONObject((result as EngineConfigResult.Ready).json)
            .getJSONArray("outbounds").getJSONObject(0)
        assertEquals("shadowsocks", outbound.getString("protocol"))
        val server = outbound.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("aes-256-gcm", server.getString("method"))
        assertEquals("password", server.getString("password"))
    }

    @Test fun rejectsUnsupportedShadowsocksCipher() {
        val result = XrayConfigBuilder.fromShareLink("ss://bWV0aG9kOnBhc3N3b3Jk@example.com:8388")
        assertTrue(result is EngineConfigResult.Unsupported)
    }

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
