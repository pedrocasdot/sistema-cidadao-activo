package ao.co.isptec.aplm.cidadaoactivo.api.dtos

data class SignupRequest(
    val name: String,
    val username: String,
    val password: String
)
