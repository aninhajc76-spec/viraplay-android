package com.viraplay.admin

data class AdminProfile(
    val role: String,
    val name: String,
    val providerCode: String? = null,
    val dnsPrimary: String? = null,
    val dnsSecondary: String? = null,
    val totalDevices: Int = 0,
    val activeToday: Int = 0,
    val active7d: Int = 0,
    val activeWindow: Int = 0,
    val activeWindowDays: Int = 10
) {
    val isMaster: Boolean get() = role.equals("MASTER", true)
}

data class PartnerInfo(
    val id: String,
    val name: String,
    val loginCode: String,
    val accessToken: String,
    val status: String,
    val clients: Int,
    val dnsPrimary: String? = null,
    val dnsSecondary: String? = null,
    val directClients: Int = 0,
    val totalDevices: Int = 0,
    val activeToday: Int = 0,
    val active7d: Int = 0,
    val activeWindow: Int = 0,
    val activeWindowDays: Int = 10
) {
    val dnsConfigured: Boolean get() = !dnsPrimary.isNullOrBlank()
}
