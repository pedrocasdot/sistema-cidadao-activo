package ao.co.isptec.aplm.sca

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import ao.co.isptec.aplm.sca.database.SCADatabase
import ao.co.isptec.aplm.sca.sync.SyncManager

class SCAApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize any application-wide components here
        
        // Initialize database
        SCADatabase.getInstance(this)
        
        // Initialize sync manager
        SyncManager.getInstance(this)
    }
}
