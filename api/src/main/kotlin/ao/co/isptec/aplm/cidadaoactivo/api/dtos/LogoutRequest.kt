package ao.co.isptec.aplm.cidadaoactivo.api.dtos

data class LogoutRequest(
    val username: String,
    val token: String
)