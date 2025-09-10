package ao.co.isptec.aplm.sca;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// WiFi Direct P2P imports
import pt.inesc.termite.wifidirect.SimWifiP2pBroadcast;
import pt.inesc.termite.wifidirect.SimWifiP2pDeviceList;
import pt.inesc.termite.wifidirect.SimWifiP2pInfo;
import pt.inesc.termite.wifidirect.SimWifiP2pManager;
import pt.inesc.termite.wifidirect.SimWifiP2pManager.Channel;
import pt.inesc.termite.wifidirect.service.SimWifiP2pService;
import pt.inesc.termite.wifidirect.sockets.SimWifiP2pSocketManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    // WiFi Direct P2P components
    private SimWifiP2pManager mManager = null;
    private Channel mChannel = null;
    private Messenger mService = null;
    private boolean mBound = false;
    private SimWifiP2pBroadcastReceiver mReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Inicializar Wi-Fi Direct P2P
        initializeWifiDirect();
    }
    
    /**
     * Inicializa o serviço Wi-Fi Direct P2P
     */
    private void initializeWifiDirect() {
        Log.d(TAG, "Inicializando Wi-Fi Direct P2P...");
        
        // Inicializar o socket manager
        SimWifiP2pSocketManager.Init(getApplicationContext());
        
        // Conectar ao serviço Wi-Fi Direct
        Intent intent = new Intent(this, SimWifiP2pService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
    
    /**
     * ServiceConnection para o serviço Wi-Fi Direct
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Serviço Wi-Fi Direct conectado");
            mService = new Messenger(service);
            
            // Inicializar o manager com o serviço conectado
            mManager = new SimWifiP2pManager(mService);
            mChannel = mManager.initialize(getApplication(), getMainLooper(), null);
            mBound = true;
            
            // Configurar o broadcast receiver após a inicialização
            setupBroadcastReceiver();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Serviço Wi-Fi Direct desconectado");
            mService = null;
            mBound = false;
        }
    };
    
    /**
     * Configura o broadcast receiver para eventos Wi-Fi Direct
     */
    private void setupBroadcastReceiver() {
        // Criar e registrar o broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_NETWORK_MEMBERSHIP_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_GROUP_OWNERSHIP_CHANGED_ACTION);
        
        // Criar um callback genérico para a MainActivity
        SimWifiP2pBroadcastReceiver.P2PStatusCallback callback = new SimWifiP2pBroadcastReceiver.P2PStatusCallback() {
            @Override
            public void updateP2PStatus(String status, boolean isEnabled) {
                Log.d(TAG, "Status P2P atualizado: " + status + ", Ativado: " + isEnabled);
                if (isEnabled) {
                    Toast.makeText(MainActivity.this, "Wi-Fi Direct ativado", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Wi-Fi Direct não ativado", Toast.LENGTH_SHORT).show();
                }
            }
        };
        
        mReceiver = new SimWifiP2pBroadcastReceiver(mManager, mChannel, callback);
        registerReceiver(mReceiver, filter);
        Log.d(TAG, "BroadcastReceiver registrado");
        
        // Solicitar informações do grupo para ativar o Wi-Fi Direct
        mManager.requestGroupInfo(mChannel, new SimWifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(SimWifiP2pDeviceList devices, SimWifiP2pInfo groupInfo) {
                Log.d(TAG, "Informações do grupo disponíveis");
            }
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (mReceiver != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_STATE_CHANGED_ACTION);
            filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_PEERS_CHANGED_ACTION);
            filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_NETWORK_MEMBERSHIP_CHANGED_ACTION);
            filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_GROUP_OWNERSHIP_CHANGED_ACTION);
            registerReceiver(mReceiver, filter);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mReceiver != null) {
            try {
                unregisterReceiver(mReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver já foi desregistrado");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Desconectar do serviço
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        
        // Desregistrar o receiver se ainda estiver registrado
        if (mReceiver != null) {
            try {
                unregisterReceiver(mReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver já foi desregistrado");
            }
        }
        
        Log.d(TAG, "MainActivity destruída - Wi-Fi Direct desativado");
    }

    public void ir(View view){
        Intent intent = new Intent(MainActivity.this , Login.class);
        startActivity(intent);
    }
}