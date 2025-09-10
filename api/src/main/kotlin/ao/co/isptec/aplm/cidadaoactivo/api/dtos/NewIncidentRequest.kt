package ao.co.isptec.aplm.cidadaoactivo.api.dtos

import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

data class NewIncidentRequest(
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val urgency: Boolean,
    val datetime: String,
    val userId: Long
)
