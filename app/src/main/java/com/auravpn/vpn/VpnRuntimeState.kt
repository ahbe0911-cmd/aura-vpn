package com.auravpn.vpn

import android.content.Context

/** A diagnostic state, not a claim that Android has an active VPN tunnel. */
object VpnRuntimeState {
    enum class Status { IDLE, ENGINE_NOT_READY, PERMISSION_REVOKED }
    fun update(context: Context, status: Status) {
        context.applicationContext.getSharedPreferences("vpn_runtime", Context.MODE_PRIVATE)
            .edit().putString("last_status", status.name).apply()
    }
    fun last(context: Context): Status =
        runCatching {
            Status.valueOf(context.applicationContext
                .getSharedPreferences("vpn_runtime", Context.MODE_PRIVATE)
                .getString("last_status", Status.IDLE.name) ?: Status.IDLE.name)
        }.getOrDefault(Status.IDLE)
}
