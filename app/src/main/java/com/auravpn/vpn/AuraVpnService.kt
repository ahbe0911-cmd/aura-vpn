package com.auravpn.vpn

import android.content.Intent
import android.net.VpnService
import android.os.IBinder

/**
 * Android VPN lifecycle entry point.
 *
 * This service deliberately does NOT establish a TUN interface until a
 * validated native packet-forwarding engine is integrated. Starting an
 * interface without forwarding would silently break device connectivity.
 */
class AuraVpnService : VpnService() {
    override fun onCreate() {
        super.onCreate()
        VpnRuntimeState.update(this, VpnRuntimeState.Status.IDLE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // No tunnel is established by this scaffold.
        VpnRuntimeState.update(this, VpnRuntimeState.Status.ENGINE_NOT_READY)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        VpnRuntimeState.update(this, VpnRuntimeState.Status.PERMISSION_REVOKED)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
