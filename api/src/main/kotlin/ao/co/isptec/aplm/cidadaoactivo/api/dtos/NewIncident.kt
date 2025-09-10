package ao.co.isptec.aplm.cidadaoactivo.api.dtos

import java.time.LocalDateTime

data class NewIncident(
    val userId: Long,
    val title: String,
    val description: String,
    val urgency: Boolean,
    val latitude: Double,
    val longitude: Double,
    val datetime: LocalDateTime,
    val photoPath: String? = null,
    val videoPath: String? = null
)