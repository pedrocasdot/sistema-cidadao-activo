package ao.co.isptec.aplm.cidadaoactivo.api.dtos

data class LoginResponse(
    val id: Long,
    val username: String,
    val name: String,
    val sessionToken: String
)
