package ao.co.isptec.aplm.cidadaoactivo.api.models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "incidents")
data class Incident(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
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
