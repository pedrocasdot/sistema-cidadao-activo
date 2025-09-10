package ao.co.isptec.aplm.sca.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ao.co.isptec.aplm.sca.database.repository.OcorrenciaRepository
import ao.co.isptec.aplm.sca.workers.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SyncManager(private val context: Context) {
    
    private val repository = OcorrenciaRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _syncStatus = MutableLiveData<SyncStatus>()
    val syncStatus: LiveData<SyncStatus> = _syncStatus
    
    private val _unsyncedCount = MutableLiveData<Int>()
    val unsyncedCount: LiveData<Int> = _unsyncedCount
    
    init {
        // Monitor unsynced count
        scope.launch {
            repository.getUnsyncedCountFlow().collect { count ->
                _unsyncedCount.postValue(count)
                Log.d(TAG, "Unsynced count updated: $count")
            }
        }
        
        // Schedule 1-minute recurring sync (self-rescheduling OneTimeWork)
        // This achieves a cadence shorter than WorkManager's 15-minute minimum for PeriodicWork
        SyncWorker.scheduleOneMinuteRecurring(context)
    }
    
    fun startSync() {
        if (!isNetworkAvailable()) {
            _syncStatus.postValue(SyncStatus.NO_NETWORK)
            Log.w(TAG, "Cannot start sync: No network available")
            return
        }
        
        _syncStatus.postValue(SyncStatus.SYNCING)
        SyncWorker.scheduleImmediateSync(context)
        Log.d(TAG, "Sync started")
    }
    
    fun stopSync() {
        SyncWorker.cancelSync(context)
        _syncStatus.postValue(SyncStatus.IDLE)
        Log.d(TAG, "Sync stopped")
    }
    
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    
    suspend fun getUnsyncedCount(): Int {
        return repository.getUnsyncedCount()
    }
    
    fun enableAutoSync() {
        // Use 1-minute recurring OneTimeWork instead of PeriodicWork for faster cadence
        SyncWorker.scheduleOneMinuteRecurring(context)
        Log.d(TAG, "Auto sync enabled (1-minute recurring)")
    }
    
    fun disableAutoSync() {
        SyncWorker.cancelSync(context)
        Log.d(TAG, "Auto sync disabled")
    }
    
    enum class SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR,
        NO_NETWORK
    }
    
    companion object {
        private const val TAG = "SyncManager"
        
        @Volatile
        private var INSTANCE: SyncManager? = null
        
        @JvmStatic
        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
