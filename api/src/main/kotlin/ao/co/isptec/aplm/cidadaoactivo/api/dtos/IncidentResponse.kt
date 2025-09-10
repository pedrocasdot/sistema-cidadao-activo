package ao.co.isptec.aplm.cidadaoactivo.api.dtos

import java.time.LocalDateTime

data class IncidentResponse(
    val id: Long,
    val userId: Long,
    val title: String,
    val description: String,
    val urgency: Boolean,
    val datetime: String,
    val latitude: Double,
    val longitude: Double,
    val dbPhotoFilename: String?,
    val dbVideoFilename: String?,
)
