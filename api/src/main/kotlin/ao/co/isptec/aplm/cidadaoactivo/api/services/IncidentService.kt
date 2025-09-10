package ao.co.isptec.aplm.cidadaoactivo.api.services

import ao.co.isptec.aplm.cidadaoactivo.api.dtos.NewIncident
import ao.co.isptec.aplm.cidadaoactivo.api.models.Incident
import ao.co.isptec.aplm.cidadaoactivo.api.repositories.IncidentRepository
import ao.co.isptec.aplm.cidadaoactivo.api.repositories.UserRepository
import org.springframework.stereotype.Service

@Service
class IncidentService(private val db: IncidentRepository, private val users: UserRepository) {
    fun getAllIncidents(): List<Incident> = db.findAll()
    
    fun getIncidentById(id: Long): Incident? = db.findById(id).orElse(null)
    
    fun getIncidentsByUserId(id: Long): List<Incident> = db.findByUserId(id)
    
    fun createIncident(incident: NewIncident): Incident? {
        return db.save(Incident(
            id = null,
            userId = incident.userId,
            title = incident.title,
            description = incident.description,
            datetime = incident.datetime,
            longitude = incident.longitude,
            latitude = incident.latitude,
            photoPath = incident.photoPath,
            videoPath = incident.videoPath,
            urgency = incident.urgency
        ))
    }
}