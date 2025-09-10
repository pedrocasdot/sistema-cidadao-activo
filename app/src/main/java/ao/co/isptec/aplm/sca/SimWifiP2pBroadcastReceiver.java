package ao.co.isptec.aplm.sca;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import pt.inesc.termite.wifidirect.SimWifiP2pBroadcast;
import pt.inesc.termite.wifidirect.SimWifiP2pManager;

/**
 * Broadcast receiver para eventos WiFi Direct P2P
 * Baseado no projeto Termite-WifiP2P-MsgSender
 */
public class SimWifiP2pBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "SimWifiP2pBroadcastReceiver";
    
    // Interface para callback de status P2P
    public interface P2PStatusCallback {
        void updateP2PStatus(String status, boolean isEnabled);
    }
    
    private SimWifiP2pManager mManager;
    private SimWifiP2pManager.Channel mChannel;
    private VisualizarOcorrencia mActivity; // Manter compatibilidade com VisualizarOcorrencia
    private P2PStatusCallback mCallback; // Callback genérico

    // Construtor original para VisualizarOcorrencia
    public SimWifiP2pBroadcastReceiver(SimWifiP2pManager manager, 
            SimWifiP2pManager.Channel channel, VisualizarOcorrencia activity) {
        super();
        this.mManager = manager;
        this.mChannel = channel;
        this.mActivity = activity;
        this.mCallback = null;
    }
    
    // Novo construtor genérico
    public SimWifiP2pBroadcastReceiver(SimWifiP2pManager manager, 
            SimWifiP2pManager.Channel channel, P2PStatusCallback callback) {
        super();
        this.mManager = manager;
        this.mChannel = channel;
        this.mActivity = null;
        this.mCallback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Broadcast received: " + action);

        if (SimWifiP2pBroadcast.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi is enabled and notify appropriate activity
            int state = intent.getIntExtra(SimWifiP2pBroadcast.EXTRA_WIFI_STATE, -1);
            if (state == SimWifiP2pBroadcast.WIFI_P2P_STATE_ENABLED) {
                Log.d(TAG, "WiFi P2P is enabled");
                // Notificar usando o callback apropriado
                if (mActivity != null) {
                    mActivity.updateP2PStatus("WiFi P2P ativado", true);
                } else if (mCallback != null) {
                    mCallback.updateP2PStatus("WiFi P2P ativado", true);
                }
            } else {
                Log.d(TAG, "WiFi P2P is not enabled");
                // Notificar usando o callback apropriado
                if (mActivity != null) {
                    mActivity.updateP2PStatus("WiFi P2P desativado", false);
                } else if (mCallback != null) {
                    mCallback.updateP2PStatus("WiFi P2P desativado", false);
                }
            }
            
        } else if (SimWifiP2pBroadcast.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // Call WifiP2pManager.requestPeers() to get a list of current peers
            Log.d(TAG, "Peers changed - requesting peer list");
            if (mManager != null && mChannel != null && mActivity != null) {
                // Só chamar requestPeers se tiver a VisualizarOcorrencia (que implementa PeerListListener)
                mManager.requestPeers(mChannel, mActivity);
            }
            
        } else if (SimWifiP2pBroadcast.WIFI_P2P_NETWORK_MEMBERSHIP_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "Network membership changed");
            if (mManager != null && mChannel != null && mActivity != null) {
                // Só chamar requestGroupInfo se tiver a VisualizarOcorrencia (que implementa GroupInfoListener)
                mManager.requestGroupInfo(mChannel, mActivity);
            }
            
        } else if (SimWifiP2pBroadcast.WIFI_P2P_GROUP_OWNERSHIP_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "Group ownership changed");
            if (mManager != null && mChannel != null && mActivity != null) {
                // Só chamar requestGroupInfo se tiver a VisualizarOcorrencia (que implementa GroupInfoListener)
                mManager.requestGroupInfo(mChannel, mActivity);
            }
        }
    }
}
