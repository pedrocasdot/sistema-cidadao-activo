package ao.co.isptec.aplm.sca.database.repository

import android.content.Context
import ao.co.isptec.aplm.sca.database.SCADatabase
import ao.co.isptec.aplm.sca.database.dao.OcorrenciaDao
import ao.co.isptec.aplm.sca.database.entity.OcorrenciaEntity
import ao.co.isptec.aplm.sca.model.Ocorrencia
import kotlinx.coroutines.flow.Flow
import java.util.Date

class OcorrenciaRepository(context: Context) {
    
    private val database = SCADatabase.getInstance(context)
    private val ocorrenciaDao: OcorrenciaDao = database.ocorrenciaDao()
    
    // Flow methods for reactive UI updates
    fun getAllOcorrenciasFlow(): Flow<List<OcorrenciaEntity>> = ocorrenciaDao.getAllFlow()
    
    fun getUnsyncedCountFlow(): Flow<Int> = ocorrenciaDao.getUnsyncedCountFlow()
    
    fun getUrgentOcorrenciasFlow(): Flow<List<OcorrenciaEntity>> = ocorrenciaDao.getUrgentOcorrenciasFlow()
    
    // Suspend methods for coroutines
    suspend fun insertOcorrencia(ocorrencia: OcorrenciaEntity): Long {
        return ocorrenciaDao.insert(ocorrencia)
    }
    
    suspend fun updateOcorrencia(ocorrencia: OcorrenciaEntity) {
        ocorrenciaDao.update(ocorrencia.copy(updatedAt = Date()))
    }
    
    suspend fun deleteOcorrencia(ocorrencia: OcorrenciaEntity) {
        ocorrenciaDao.delete(ocorrencia)
    }
    
    suspend fun getAllOcorrencias(): List<OcorrenciaEntity> {
        return ocorrenciaDao.getAll()
    }
    
    suspend fun getOcorrenciaById(id: Long): OcorrenciaEntity? {
        return ocorrenciaDao.getById(id)
    }
    
    suspend fun getOcorrenciaByRemoteId(remoteId: Long): OcorrenciaEntity? {
        return ocorrenciaDao.getByRemoteId(remoteId)
    }
    
    suspend fun getAllUnsyncedOcorrencias(): List<OcorrenciaEntity> {
        return ocorrenciaDao.getAllUnsynced()
    }
    
    suspend fun markOcorrenciaAsSynced(localId: Long, remoteId: Long? = null) {
        if (remoteId != null) {
            ocorrenciaDao.markAsSynced(localId, remoteId)
        } else {
            ocorrenciaDao.markAsSynced(localId)
        }
    }
    
    suspend fun getUnsyncedCount(): Int {
        return ocorrenciaDao.getUnsyncedCount()
    }
    
    // Utility methods
    suspend fun cleanupOldSyncedOcorrencias(daysToKeep: Int = 30) {
        val cutoffDate = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        ocorrenciaDao.deleteOldSyncedOcorrencias(cutoffDate)
    }
    
    // Conversion methods between Entity and Model
    fun entityToModel(entity: OcorrenciaEntity): Ocorrencia {
        val ocorrencia = Ocorrencia()
        ocorrencia.id = entity.id.toInt()
        ocorrencia.descricao = entity.descricao
        ocorrencia.localizacaoSimbolica = entity.localizacaoSimbolica
        ocorrencia.latitude = entity.latitude
        ocorrencia.longitude = entity.longitude
        ocorrencia.dataHora = entity.dataHora
        ocorrencia.isUrgente = entity.urgente
        ocorrencia.contadorPartilha = entity.contadorPartilha
        ocorrencia.fotoPath = entity.fotoPath
        ocorrencia.videoPath = entity.videoPath
        return ocorrencia
    }
    
    fun modelToEntity(model: Ocorrencia, synced: Boolean = false): OcorrenciaEntity {
        return OcorrenciaEntity(
            id = if (model.id > 0) model.id.toLong() else 0,
            descricao = model.descricao,
            localizacaoSimbolica = model.localizacaoSimbolica,
            latitude = model.latitude,
            longitude = model.longitude,
            dataHora = model.dataHora,
            urgente = model.isUrgente,
            contadorPartilha = model.contadorPartilha,
            fotoPath = model.fotoPath,
            videoPath = model.videoPath,
            synced = synced,
            createdAt = Date(),
            updatedAt = Date()
        )
    }
}
