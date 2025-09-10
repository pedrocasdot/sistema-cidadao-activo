package ao.co.isptec.aplm.cidadaoactivo.api.repositories

import ao.co.isptec.aplm.cidadaoactivo.api.models.Incident
import org.springframework.data.jpa.repository.JpaRepository

interface IncidentRepository : JpaRepository<Incident, Long> {
    fun findByUserId(userId: Long): List<Incident>
}