package ao.co.isptec.aplm.sca.offline

import android.content.Context
import android.util.Log
import ao.co.isptec.aplm.sca.database.entity.OcorrenciaEntity
import ao.co.isptec.aplm.sca.database.repository.OcorrenciaRepository
import ao.co.isptec.aplm.sca.model.Ocorrencia
import ao.co.isptec.aplm.sca.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.function.Consumer
import java.util.function.LongConsumer
import java.util.function.IntConsumer

/**
 * Helper class to demonstrate offline-first pattern integration
 * This shows how to modify existing activities to use Room database
 */
class OfflineFirstHelper(private val context: Context) {
    
    private val repository = OcorrenciaRepository(context)
    private val syncManager = SyncManager.getInstance(context)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Save ocorrencia offline-first
     * This method should replace direct API calls in existing activities
     */
    suspend fun saveOcorrenciaOfflineFirst(
        descricao: String,
        localizacaoSimbolica: String?,
        latitude: Double,
        longitude: Double,
        urgente: Boolean,
        fotoPath: String?,
        videoPath: String?,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Create entity object
            val ocorrenciaEntity = OcorrenciaEntity(
                descricao = descricao,
                localizacaoSimbolica = localizacaoSimbolica,
                latitude = latitude,
                longitude = longitude,
                dataHora = Date(),
                urgente = urgente,
                fotoPath = fotoPath,
                videoPath = videoPath,
                synced = false, // Mark as unsynced initially
                createdAt = Date(),
                updatedAt = Date()
            )
            
            // Save to local database first
            val localId = repository.insertOcorrencia(ocorrenciaEntity)
            
            Log.d(TAG, "Ocorrencia saved locally with ID: $localId")
            
            // Notify success immediately (offline-first)
            withContext(Dispatchers.Main) {
                onSuccess(localId)
            }
            // Schedule sync in background
            if (syncManager.isNetworkAvailable()) {
                syncManager.startSync()
                Log.d(TAG, "Sync scheduled for ocorrencia $localId")
            } else {
                Log.d(TAG, "No network available, ocorrencia will sync when online")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving ocorrencia offline", e)
            withContext(Dispatchers.Main) {
                onError("Erro ao salvar ocorrência: ${e.message}")
            }
        }
    }

    /**
     * Save a received shared ocorrencia locally but mark it as already synced
     * so it is NEVER pushed to the server (it does not belong to the current user).
     */
    suspend fun saveReceivedSharedOcorrencia(
        descricao: String,
        localizacaoSimbolica: String?,
        latitude: Double,
        longitude: Double,
        urgente: Boolean,
        fotoPath: String?,
        videoPath: String?,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val now = Date()
            val ocorrenciaEntity = OcorrenciaEntity(
                descricao = descricao,
                localizacaoSimbolica = localizacaoSimbolica,
                latitude = latitude,
                longitude = longitude,
                dataHora = now,
                urgente = urgente,
                fotoPath = fotoPath,
                videoPath = videoPath,
                synced = true, // Important: mark as synced to avoid server sync
                createdAt = now,
                updatedAt = now
            )
            val localId = repository.insertOcorrencia(ocorrenciaEntity)
            Log.d(TAG, "Received shared ocorrencia saved locally with ID: $localId (no sync)")
            withContext(Dispatchers.Main) {
                onSuccess(localId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving received shared ocorrencia", e)
            withContext(Dispatchers.Main) {
                onError("Erro ao salvar ocorrência recebida: ${e.message}")
            }
        }
    }

    /**
     * Java-friendly async variant for saving received shared ocorrencias
     */
    fun saveReceivedSharedOcorrenciaAsync(
        descricao: String,
        localizacaoSimbolica: String?,
        latitude: Double,
        longitude: Double,
        urgente: Boolean,
        fotoPath: String?,
        videoPath: String?,
        onSuccess: LongConsumer?,
        onError: Consumer<String>?
    ) {
        ioScope.launch {
            try {
                saveReceivedSharedOcorrencia(
                    descricao,
                    localizacaoSimbolica,
                    latitude,
                    longitude,
                    urgente,
                    fotoPath,
                    videoPath,
                    { id -> onSuccess?.accept(id) },
                    { err -> onError?.accept(err) }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError?.accept(e.message ?: "Erro desconhecido")
                }
            }
        }
    }

    /**
     * Java-friendly overload using java.util.function.* consumers (no return value needed).
     */
    fun saveOcorrenciaOfflineFirstAsync(
        descricao: String,
        localizacaoSimbolica: String?,
        latitude: Double,
        longitude: Double,
        urgente: Boolean,
        fotoPath: String?,
        videoPath: String?,
        onSuccess: LongConsumer?,
        onError: Consumer<String>?
    ) {
        ioScope.launch {
            try {
                saveOcorrenciaOfflineFirst(
                    descricao,
                    localizacaoSimbolica,
                    latitude,
                    longitude,
                    urgente,
                    fotoPath,
                    videoPath,
                    { id -> onSuccess?.accept(id) },
                    { err -> onError?.accept(err) }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError?.accept(e.message ?: "Erro desconhecido")
                }
            }
        }
    }

    
    
    /**
     * Get all ocorrencias from local database
     */
    suspend fun getAllOcorrencias(): List<OcorrenciaEntity> {
        return repository.getAllOcorrencias()
    }
    
    /**
     * Get ocorrencia by ID from local database
     */
    suspend fun getOcorrenciaById(id: Long): OcorrenciaEntity? {
        return repository.getOcorrenciaById(id)
    }
    
    /**
     * Update existing ocorrencia
     */
    suspend fun updateOcorrencia(
        id: Long,
        descricao: String,
        localizacaoSimbolica: String?,
        latitude: Double,
        longitude: Double,
        urgente: Boolean,
        fotoPath: String?,
        videoPath: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val existingOcorrencia = repository.getOcorrenciaById(id)
            if (existingOcorrencia != null) {
                val updatedOcorrencia = existingOcorrencia.copy(
                    descricao = descricao,
                    localizacaoSimbolica = localizacaoSimbolica,
                    latitude = latitude,
                    longitude = longitude,
                    urgente = urgente,
                    fotoPath = fotoPath,
                    videoPath = videoPath,
                    synced = false, // Mark as unsynced after update
                    updatedAt = Date()
                )
                
                repository.updateOcorrencia(updatedOcorrencia)
                
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
                
                // Schedule sync
                if (syncManager.isNetworkAvailable()) {
                    syncManager.startSync()
                }
                
            } else {
                withContext(Dispatchers.Main) {
                    onError("Ocorrência não encontrada")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating ocorrencia", e)
            withContext(Dispatchers.Main) {
                onError("Erro ao atualizar ocorrência: ${e.message}")
            }
        }
    }
    
    /**
     * Delete ocorrencia
     */
    suspend fun deleteOcorrencia(
        id: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val ocorrencia = repository.getOcorrenciaById(id)
            if (ocorrencia != null) {
                repository.deleteOcorrencia(ocorrencia)
                
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
                
                Log.d(TAG, "Ocorrencia $id deleted locally")
                
            } else {
                withContext(Dispatchers.Main) {
                    onError("Ocorrência não encontrada")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting ocorrencia", e)
            withContext(Dispatchers.Main) {
                onError("Erro ao deletar ocorrência: ${e.message}")
            }
        }
    }
    
    /**
     * Get sync status information
     */
    suspend fun getSyncInfo(): SyncInfo {
        val unsyncedCount = repository.getUnsyncedCount()
        val totalCount = repository.getAllOcorrencias().size
        val isNetworkAvailable = syncManager.isNetworkAvailable()
        
        return SyncInfo(
            unsyncedCount = unsyncedCount,
            totalCount = totalCount,
            isNetworkAvailable = isNetworkAvailable,
            syncEnabled = true // You can add logic to check if sync is enabled
        )
    }

    fun getSyncInfoAsync(callback: Consumer<SyncInfo>) {
        ioScope.launch {
            try {
                val info = getSyncInfo()
                withContext(Dispatchers.Main) {
                    callback.accept(info)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting sync info", e)
            }
        }
    }
    
    /**
     * Update share counter for an existing ocorrencia
     */
    fun updateShareCounter(
        id: Long,
        newShareCount: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        ioScope.launch {
            try {
                val existingEntity = repository.getOcorrenciaById(id)
                if (existingEntity != null) {
                    val updatedEntity = existingEntity.copy(
                        contadorPartilha = newShareCount,
                        updatedAt = Date()
                    )
                    repository.updateOcorrencia(updatedEntity)
                    
                    Log.d(TAG, "Share counter updated for ocorrencia $id: $newShareCount")
                    
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    Log.e(TAG, "Ocorrencia not found with ID: $id")
                    withContext(Dispatchers.Main) {
                        onError("Ocorrência não encontrada")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating share counter", e)
                withContext(Dispatchers.Main) {
                    onError("Erro ao atualizar contador: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Java-friendly async variant for updating share counter
     */
    fun updateShareCounterAsync(
        id: Long,
        newShareCount: Int,
        onSuccess: Runnable?,
        onError: Consumer<String>?
    ) {
        updateShareCounter(
            id = id,
            newShareCount = newShareCount,
            onSuccess = { onSuccess?.run() },
            onError = { error -> onError?.accept(error) }
        )
    }

    /**
     * Force sync all unsynced ocorrencias
     */
    fun forceSyncAll() {
        if (syncManager.isNetworkAvailable()) {
            syncManager.startSync()
            Log.d(TAG, "Force sync initiated")
        } else {
            Log.w(TAG, "Cannot force sync: No network available")
        }
    }

    fun getAllOcorrenciasAsync(callback: Consumer<List<OcorrenciaEntity>>) {
        ioScope.launch {
            try {
                val list = repository.getAllOcorrencias()
                withContext(Dispatchers.Main) {
                    callback.accept(list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading ocorrencias", e)
            }
        }
    }

    fun getUnsyncedCountAsync(callback: IntConsumer) {
        ioScope.launch {
            try {
                val count = repository.getUnsyncedCount()
                withContext(Dispatchers.Main) {
                    callback.accept(count)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting unsynced count", e)
            }
        }
    }
    
    data class SyncInfo(
        val unsyncedCount: Int,
        val totalCount: Int,
        val isNetworkAvailable: Boolean,
        val syncEnabled: Boolean
    )
    
    companion object {
        private const val TAG = "OfflineFirstHelper"
        
        /**
         * Extension function to convert Ocorrencia model to Entity
         */
        fun Ocorrencia.toEntity(synced: Boolean = false): OcorrenciaEntity {
            return OcorrenciaEntity(
                id = if (this.id > 0) this.id.toLong() else 0,
                descricao = this.descricao,
                localizacaoSimbolica = this.localizacaoSimbolica,
                latitude = this.latitude,
                longitude = this.longitude,
                dataHora = this.dataHora,
                urgente = this.isUrgente,
                contadorPartilha = this.contadorPartilha,
                fotoPath = this.fotoPath,
                videoPath = this.videoPath,
                synced = synced,
                createdAt = Date(),
                updatedAt = Date()
            )
        }
        
        /**
         * Extension function to convert Entity to Ocorrencia model
         */
        fun OcorrenciaEntity.toModel(): Ocorrencia {
            val ocorrencia = Ocorrencia()
            ocorrencia.id = this.id.toInt()
            ocorrencia.descricao = this.descricao
            ocorrencia.localizacaoSimbolica = this.localizacaoSimbolica
            ocorrencia.latitude = this.latitude
            ocorrencia.longitude = this.longitude
            ocorrencia.dataHora = this.dataHora
            ocorrencia.isUrgente = this.urgente
            ocorrencia.contadorPartilha = this.contadorPartilha
            ocorrencia.fotoPath = this.fotoPath
            ocorrencia.videoPath = this.videoPath
            return ocorrencia
        }
    }
}
