package com.viraplay.admin

data class AdminProfile(
    val role: String,
    val name: String,
    val credits: Int,
    val annualLicenseCredits: Int,
    val providerCode: String? = null,
    val dnsPrimary: String? = null,
    val dnsSecondary: String? = null,
    val directClients: Int = 0
) {
    val isMaster: Boolean get() = role.equals("MASTER", true)
}

data class PartnerInfo(
    val id: String,
    val name: String,
    val loginCode: String,
    val accessToken: String,
    val status: String,
    val credits: Int,
    val clients: Int,
    val dnsPrimary: String? = null,
    val dnsSecondary: String? = null,
    val directClients: Int = 0
) {
    val dnsConfigured: Boolean get() = !dnsPrimary.isNullOrBlank()
}

data class CreditEntry(
    val amount: Int,
    val kind: String,
    val note: String?,
    val createdAt: String?
)
