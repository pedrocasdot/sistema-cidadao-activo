package ao.co.isptec.aplm.sca.database.dao

import androidx.room.*
import ao.co.isptec.aplm.sca.database.entity.OcorrenciaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcorrenciaDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ocorrencia: OcorrenciaEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg ocorrencias: OcorrenciaEntity)
    
    @Update
    suspend fun update(ocorrencia: OcorrenciaEntity)
    
    @Delete
    suspend fun delete(ocorrencia: OcorrenciaEntity)
    
    @Query("SELECT * FROM ocorrencias ORDER BY dataHora DESC")
    fun getAllFlow(): Flow<List<OcorrenciaEntity>>
    
    @Query("SELECT * FROM ocorrencias ORDER BY dataHora DESC")
    suspend fun getAll(): List<OcorrenciaEntity>
    
    @Query("SELECT * FROM ocorrencias WHERE id = :id")
    suspend fun getById(id: Long): OcorrenciaEntity?
    
    @Query("SELECT * FROM ocorrencias WHERE remoteId = :remoteId")
    suspend fun getByRemoteId(remoteId: Long): OcorrenciaEntity?
    
    @Query("SELECT * FROM ocorrencias WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getAllUnsynced(): List<OcorrenciaEntity>
    
    @Query("SELECT * FROM ocorrencias WHERE synced = 0 ORDER BY createdAt ASC")
    fun getAllUnsyncedFlow(): Flow<List<OcorrenciaEntity>>
    
    @Query("UPDATE ocorrencias SET synced = 1, remoteId = :remoteId WHERE id = :localId")
    suspend fun markAsSynced(localId: Long, remoteId: Long)
    
    @Query("UPDATE ocorrencias SET synced = 1 WHERE id = :localId")
    suspend fun markAsSynced(localId: Long)
    
    @Query("SELECT COUNT(*) FROM ocorrencias WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int
    
    @Query("SELECT COUNT(*) FROM ocorrencias WHERE synced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>
    
    @Query("DELETE FROM ocorrencias WHERE synced = 1 AND createdAt < :cutoffDate")
    suspend fun deleteOldSyncedOcorrencias(cutoffDate: Long)
    
    @Query("SELECT * FROM ocorrencias WHERE urgente = 1 ORDER BY dataHora DESC")
    suspend fun getUrgentOcorrencias(): List<OcorrenciaEntity>
    
    @Query("SELECT * FROM ocorrencias WHERE urgente = 1 ORDER BY dataHora DESC")
    fun getUrgentOcorrenciasFlow(): Flow<List<OcorrenciaEntity>>
}
