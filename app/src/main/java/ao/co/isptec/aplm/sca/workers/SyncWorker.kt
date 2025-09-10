package ao.co.isptec.aplm.sca.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import ao.co.isptec.aplm.sca.database.repository.OcorrenciaRepository
import ao.co.isptec.aplm.sca.service.ApiService
import ao.co.isptec.aplm.sca.service.SessionManager
// ApiCallback is an inner interface of ApiService
import ao.co.isptec.aplm.sca.dto.NewIncidentRequest
import ao.co.isptec.aplm.sca.dto.IncidentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = OcorrenciaRepository(context)
    private val apiService = ApiService(context)
    private val sessionManager = SessionManager(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting sync work...")
            
            // Get all unsynced ocorrencias
            val unsyncedOcorrencias = repository.getAllUnsyncedOcorrencias()
            
            if (unsyncedOcorrencias.isEmpty()) {
                Log.d(TAG, "No unsynced ocorrencias found")
                // Re-agendar próxima execução em 1 minuto
                try {
                    scheduleOneMinuteRecurring(applicationContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to schedule next 1-minute sync", e)
                }
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Found ${unsyncedOcorrencias.size} unsynced ocorrencias")
            
            var successCount = 0
            var failureCount = 0
            
            // Sync each ocorrencia
            for (ocorrencia in unsyncedOcorrencias) {
                try {
                    // Convert entity to model for API call
                    val ocorrenciaModel = repository.entityToModel(ocorrencia)
                    
                    // Sync with real API
                    val syncResult = syncOcorrenciaWithServer(ocorrenciaModel)
                    
                    if (syncResult.success) {
                        // Mark as synced in local database
                        repository.markOcorrenciaAsSynced(
                            ocorrencia.id, 
                            syncResult.remoteId
                        )
                        successCount++
                        Log.d(TAG, "Successfully synced ocorrencia ${ocorrencia.id}")
                    } else {
                        failureCount++
                        Log.w(TAG, "Failed to sync ocorrencia ${ocorrencia.id}: ${syncResult.error}")
                    }
                    
                } catch (e: Exception) {
                    failureCount++
                    Log.e(TAG, "Error syncing ocorrencia ${ocorrencia.id}", e)
                }
            }
            
            Log.d(TAG, "Sync completed. Success: $successCount, Failures: $failureCount")
            
            // Return success if at least some items were synced, or if all failed but it's retryable
            val isSuccess = (successCount > 0 || failureCount == 0)
            if (isSuccess) {
                // Re-agendar próxima execução em 1 minuto
                try {
                    scheduleOneMinuteRecurring(applicationContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to schedule next 1-minute sync", e)
                }
                return@withContext Result.success()
            } else {
                return@withContext Result.retry()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker failed", e)
            return@withContext Result.failure()
        }
    }
    
    private suspend fun syncOcorrenciaWithServer(ocorrencia: ao.co.isptec.aplm.sca.model.Ocorrencia): SyncResult {
        return try {
            // Convert Ocorrencia to NewIncidentRequest
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val datetimeString = dateFormat.format(ocorrencia.dataHora ?: Date())
            
            val request = NewIncidentRequest(
                "Ocorrência", // title - Default title
                ocorrencia.descricao ?: "", // description
                ocorrencia.latitude, // latitude
                ocorrencia.longitude, // longitude
                datetimeString, // datetime
                ocorrencia.isUrgente, // urgency
                sessionManager.userId ?: 1L // userId - Get from session or default to 1
            )
            
            // Prepare photo and video files if they exist
            val photoFile = if (!ocorrencia.fotoPath.isNullOrEmpty()) {
                File(ocorrencia.fotoPath)
            } else null
            
            val videoFile = if (!ocorrencia.videoPath.isNullOrEmpty()) {
                File(ocorrencia.videoPath)
            } else null
            
            // Make API call using suspendCancellableCoroutine to convert callback to suspend function
            val response = suspendCancellableCoroutine<IncidentResponse> { continuation ->
                apiService.createOccurrence(
                    request,
                    photoFile,
                    videoFile,
                    object : ApiService.ApiCallback<IncidentResponse> {
                        override fun onSuccess(result: IncidentResponse?) {
                            if (result != null) {
                                continuation.resume(result)
                            } else {
                                continuation.resume(IncidentResponse()) // Empty response as fallback
                            }
                        }
                        
                        override fun onError(error: String?) {
                            continuation.cancel(Exception(error ?: "Unknown API error"))
                        }
                    }
                )
            }
            
            // Return success result with remote ID
            SyncResult(
                success = true,
                remoteId = response.id ?: System.currentTimeMillis(),
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing ocorrencia with server", e)
            SyncResult(
                success = false,
                remoteId = null,
                error = e.message
            )
        }
    }
    
    data class SyncResult(
        val success: Boolean,
        val remoteId: Long?,
        val error: String?
    )
    
    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "sync_ocorrencias"
        
        /**
         * Schedule a recurring sync every 1 minute using a self-rescheduling OneTimeWork.
         * Note: WorkManager enforces a 15-minute minimum for PeriodicWork; this method
         * provides a shorter cadence by chaining OneTimeWork with initialDelay.
         */
        fun scheduleOneMinuteRecurring(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )

            Log.d(TAG, "1-minute recurring sync scheduled (OneTimeWork)")
        }
        
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                1, TimeUnit.MINUTES // WorkManager enforces minimum 15 minutes; kept for compatibility
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            
            Log.d(TAG, "Periodic sync scheduled")
        }
        
        fun scheduleImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueue(syncRequest)
            
            Log.d(TAG, "Immediate sync scheduled")
        }
        
        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Sync cancelled")
        }
    }
}
