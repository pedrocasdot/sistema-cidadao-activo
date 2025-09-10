package ao.co.isptec.aplm.cidadaoactivo.api.controllers

import ao.co.isptec.aplm.cidadaoactivo.api.dtos.IncidentResponse
import ao.co.isptec.aplm.cidadaoactivo.api.dtos.NewIncident
import ao.co.isptec.aplm.cidadaoactivo.api.dtos.NewIncidentRequest
import ao.co.isptec.aplm.cidadaoactivo.api.models.Incident
import ao.co.isptec.aplm.cidadaoactivo.api.services.IncidentService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.contracts.ReturnsNotNull

@RestController
@RequestMapping("/incidents")
@CrossOrigin
class IncidentController (private val service: IncidentService){
    private val uploadDir = Paths.get("uploads").toAbsolutePath().toString()
    
    @GetMapping("")
    fun getAllIncidents(): ResponseEntity<List<IncidentResponse>> {
        val incidents = service.getAllIncidents().map { createIncidentResponse(it) }
        return ResponseEntity(incidents, HttpStatus.OK)
    }
    
    @GetMapping("/user/{userId}")
    fun getIncidentsFromUser(@PathVariable userId: Long): ResponseEntity<List<IncidentResponse>> {
        val incidents = service.getIncidentsByUserId(userId).map { createIncidentResponse(it) }
        return ResponseEntity(incidents, HttpStatus.OK)
    }
    
    private fun createIncidentResponse(incident: Incident): IncidentResponse {
        return IncidentResponse(
            id = incident.id!!,
            description = incident.description,
            latitude = incident.latitude,
            longitude = incident.longitude,
            urgency = incident.urgency,
            datetime = incident.datetime.toString(),
            dbPhotoFilename = incident.photoPath,
            dbVideoFilename = incident.videoPath,
            userId = incident.userId,
            title = incident.title
        )
    }
    
    @PostMapping("", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun reportIncident(
        @RequestPart("incident") request: NewIncidentRequest, 
        @RequestPart("photo", required = false) photo: MultipartFile?,
        @RequestPart("video", required = false) video: MultipartFile?
    ): ResponseEntity<IncidentResponse?> {
        try {
            val photoPath = photo?.let { saveFile(it) }
            val videoPath = video?.let { saveFile(it) }
            val incident = NewIncident(
                title =  request.title,
                description = request.description,
                latitude = request.latitude,
                longitude = request.longitude,
                datetime = LocalDateTime.parse(request.datetime),
                urgency = request.urgency,
                videoPath = videoPath,
                photoPath = photoPath,
                userId = request.userId
            )
            val newIncident = service.createIncident(incident)
            val response = IncidentResponse(
                id = newIncident?.id!!,
                userId = newIncident.userId,
                datetime = newIncident.datetime.toString(),
                urgency = newIncident.urgency,
                longitude = newIncident.longitude,
                latitude = newIncident.latitude,
                description = newIncident.description,
                title = newIncident.title,
                dbPhotoFilename = newIncident.photoPath,
                dbVideoFilename = newIncident.videoPath
            )
            return ResponseEntity(response, HttpStatus.CREATED)
            
            
        } catch (e: IOException) {
            return ResponseEntity(null, HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    @GetMapping("/{id}")
    fun getIncident(@PathVariable id: Long): ResponseEntity<Incident?> {
        val incident = service.getIncidentById(id)
        return ResponseEntity(incident, HttpStatus.OK)
    }
    
    private fun saveFile(file: MultipartFile): String {
        val fileName = "${System.currentTimeMillis()}_${file.originalFilename}"
        val filePath = Paths.get(uploadDir, fileName).toString()
        Files.createDirectories(Paths.get(uploadDir)) // Ensure directory exists
        file.transferTo(File(filePath))
        return fileName  // Store only the file name in DB
    }

    @GetMapping("/files/{filename}")
    fun getFile(@PathVariable filename: String, response: HttpServletResponse) {
        val file = File("$uploadDir/$filename")
        if (file.exists()) {
            response.contentType = Files.probeContentType(file.toPath())
            Files.copy(file.toPath(), response.outputStream)
            response.outputStream.flush()
        } else {
            response.status = HttpServletResponse.SC_NOT_FOUND
        }
    }
    
    @GetMapping("/{id}/attachment")
    fun getIncidentAttachment(@PathVariable id: Long, response:HttpServletResponse) {
        val incident = service.getIncidentById(id)
        if (incident == null) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return
        }
        var file: File = File("")
        if (incident.photoPath != null) file = File("$uploadDir/${incident.photoPath}")
        else if (incident.videoPath != null) file = File("$uploadDir/${incident.videoPath}")
        
        if (file.exists()) {
            response.contentType = Files.probeContentType(file.toPath())
            Files.copy(file.toPath(), response.outputStream)
            response.outputStream.flush()
        } else {
            response.status = HttpServletResponse.SC_NOT_FOUND
        }
    }
}