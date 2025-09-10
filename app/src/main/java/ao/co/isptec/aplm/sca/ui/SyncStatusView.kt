package ao.co.isptec.aplm.sca.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import ao.co.isptec.aplm.sca.R
import ao.co.isptec.aplm.sca.sync.SyncManager
import kotlinx.coroutines.launch

/**
 * Custom view to show sync status to users
 * Can be added to any activity or fragment to display offline/sync information
 */
class SyncStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var syncManager: SyncManager
    
    init {
        orientation = HORIZONTAL
        initializeView()
    }
    
    private fun initializeView() {
        // For now, create views programmatically
        // In a real implementation, you would inflate from XML layout
        
        statusText = TextView(context).apply {
            text = "Verificando status..."
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            visibility = GONE
        }
        
        addView(statusText)
        addView(progressBar)
        
        syncManager = SyncManager.getInstance(context)
    }
    
    /**
     * Start observing sync status
     * Call this from your activity's onCreate or onResume
     */
    fun startObserving(lifecycleOwner: LifecycleOwner) {
        // Observe sync status
        syncManager.syncStatus.observe(lifecycleOwner) { status ->
            updateSyncStatus(status)
        }
        
        // Observe unsynced count
        syncManager.unsyncedCount.observe(lifecycleOwner) { count ->
            updateUnsyncedCount(count)
        }
    }
    
    private fun updateSyncStatus(status: SyncManager.SyncStatus) {
        when (status) {
            SyncManager.SyncStatus.IDLE -> {
                progressBar.visibility = GONE
                statusText.text = "Sincronizado"
            }
            SyncManager.SyncStatus.SYNCING -> {
                progressBar.visibility = VISIBLE
                statusText.text = "Sincronizando..."
            }
            SyncManager.SyncStatus.SUCCESS -> {
                progressBar.visibility = GONE
                statusText.text = "Sincronização concluída"
            }
            SyncManager.SyncStatus.ERROR -> {
                progressBar.visibility = GONE
                statusText.text = "Erro na sincronização"
            }
            SyncManager.SyncStatus.NO_NETWORK -> {
                progressBar.visibility = GONE
                statusText.text = "Sem conexão - dados salvos localmente"
            }
        }
    }
    
    private fun updateUnsyncedCount(count: Int) {
        if (count > 0) {
            statusText.text = "$count item(s) aguardando sincronização"
        }
    }
    
    /**
     * Manually trigger sync
     */
    fun triggerSync() {
        syncManager.startSync()
    }
    
    /**
     * Check if network is available
     */
    fun isNetworkAvailable(): Boolean {
        return syncManager.isNetworkAvailable()
    }
}

/*
USAGE EXAMPLE:

In your Activity XML layout:
<ao.co.isptec.aplm.sca.ui.SyncStatusView
    android:id="@+id/syncStatusView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="8dp"
    android:background="@color/light_gray" />

In your Activity Java/Kotlin code:
class MainActivity : AppCompatActivity() {
    private lateinit var syncStatusView: SyncStatusView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        syncStatusView = findViewById(R.id.syncStatusView)
        syncStatusView.startObserving(this)
    }
}

Or add programmatically:
val syncStatusView = SyncStatusView(this)
someLayout.addView(syncStatusView)
syncStatusView.startObserving(this)
*/
