package ao.co.isptec.aplm.sca.base;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ao.co.isptec.aplm.sca.P2PCommManager;
import ao.co.isptec.aplm.sca.SimWifiP2pBroadcastReceiver;
import ao.co.isptec.aplm.sca.model.Ocorrencia;
import ao.co.isptec.aplm.sca.offline.OfflineFirstHelper;
import ao.co.isptec.aplm.sca.security.CryptoUtils;
import ao.co.isptec.aplm.sca.service.SessionManager;
import pt.inesc.termite.wifidirect.SimWifiP2pBroadcast;
import pt.inesc.termite.wifidirect.SimWifiP2pManager;
import pt.inesc.termite.wifidirect.service.SimWifiP2pService;

/**
 * Classe base para activities que precisam de funcionalidade P2P
 * Implementa recepção de ocorrências via Wi-Fi Direct
 */
public abstract class BaseP2PActivity extends AppCompatActivity implements P2PCommManager.P2PMessageListener {

    private static final String TAG = "BaseP2PActivity";
    
    // WiFi Direct P2P components
    protected SimWifiP2pManager mManager = null;
    protected SimWifiP2pManager.Channel mChannel = null;
    protected Messenger mService = null;
    protected boolean mBound = false;
    protected SimWifiP2pBroadcastReceiver mReceiver;
    protected P2PCommManager mCommManager;
    
    // Helper services
    protected OfflineFirstHelper offlineHelper;
    protected SessionManager sessionManager;
    protected String sharePassphrase; // Used to encrypt/decrypt P2P payloads

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enable back button (Up) in the ActionBar by default
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // Initialize helper services
        offlineHelper = new OfflineFirstHelper(this);
        sessionManager = new SessionManager(this);
        
        // Initialize P2P after activity is fully created
        initializeP2P();
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Inicializa os componentes WiFi Direct P2P
     */
    protected void initializeP2P() {
        Log.d(TAG, "Inicializando P2P para " + getClass().getSimpleName());
        
        // Bind to WiFi Direct service
        Intent intent = new Intent(this, SimWifiP2pService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * ServiceConnection para o serviço Wi-Fi Direct
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "P2P Service connected in " + getClass().getSimpleName());
            mService = new Messenger(service);
            mBound = true;
            
            // Initialize manager and channel
            mManager = new SimWifiP2pManager(mService);
            mChannel = mManager.initialize(getApplication(), getMainLooper(), null);
            
            // Setup communication manager for receiving messages
            mCommManager = new P2PCommManager(BaseP2PActivity.this);
            
            // Setup broadcast receiver
            setupBroadcastReceiver();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "P2P Service disconnected in " + getClass().getSimpleName());
            mService = null;
            mBound = false;
        }
    };

    /**
     * Configura o broadcast receiver para eventos Wi-Fi Direct
     */
    private void setupBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_NETWORK_MEMBERSHIP_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_GROUP_OWNERSHIP_CHANGED_ACTION);
        
        // Create a generic callback for P2P status updates
        SimWifiP2pBroadcastReceiver.P2PStatusCallback callback = new SimWifiP2pBroadcastReceiver.P2PStatusCallback() {
            @Override
            public void updateP2PStatus(String status, boolean isEnabled) {
                Log.d(TAG, "P2P Status updated in " + getClass().getSimpleName() + ": " + status + ", Enabled: " + isEnabled);
                onP2PStatusUpdated(status, isEnabled);
            }
        };
        
        mReceiver = new SimWifiP2pBroadcastReceiver(mManager, mChannel, callback);
        registerReceiver(mReceiver, filter);
        Log.d(TAG, "BroadcastReceiver registered in " + getClass().getSimpleName());
    }

    /**
     * Callback para atualizações de status P2P - pode ser sobrescrito pelas subclasses
     */
    protected void onP2PStatusUpdated(String status, boolean isEnabled) {
        // Default implementation - subclasses can override
        if (isEnabled) {
            Log.d(TAG, "Wi-Fi Direct ativado em " + getClass().getSimpleName());
        } else {
            Log.d(TAG, "Wi-Fi Direct não ativado em " + getClass().getSimpleName());
        }
    }

    // ========== P2PMessageListener Implementation ==========

    @Override
    public void onMessageReceived(String message) {
        Log.d(TAG, "Message received in " + getClass().getSimpleName() + ": " + message);
        handleReceivedMessage(message);
    }
    
    @Override
    public void onConnectionEstablished() {
        Log.d(TAG, "P2P Connection established in " + getClass().getSimpleName());
        runOnUiThread(() -> {
            Toast.makeText(this, "Conectado ao dispositivo", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onConnectionFailed(String error) {
        Log.e(TAG, "P2P Connection failed in " + getClass().getSimpleName() + ": " + error);
        runOnUiThread(() -> {
            Toast.makeText(this, "Falha na conexão: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    @Override
    public void onMessageSent() {
        Log.d(TAG, "Message sent successfully from " + getClass().getSimpleName());
        runOnUiThread(() -> {
            Toast.makeText(this, "Mensagem enviada com sucesso!", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onMessageSendFailed(String error) {
        Log.e(TAG, "Message send failed in " + getClass().getSimpleName() + ": " + error);
        runOnUiThread(() -> {
            Toast.makeText(this, "Falha ao enviar: " + error, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * Processa mensagem recebida via P2P
     */
    protected void handleReceivedMessage(String message) {
        runOnUiThread(() -> {
            try {
                Log.d(TAG, "Processing received message in " + getClass().getSimpleName());
                
                JSONObject ocorrenciaJson;
                try {
                    // Try as plain JSON first
                    ocorrenciaJson = new JSONObject(message);
                } catch (JSONException ex) {
                    // If not JSON, request passphrase and try decryption
                    if (sharePassphrase == null || sharePassphrase.isEmpty()) {
                        final String originalMsg = message;
                        promptForPassphrase(pass -> {
                            sharePassphrase = pass;
                            try {
                                String decryptedMessage = CryptoUtils.decryptFromBase64(originalMsg, sharePassphrase);
                                handleReceivedMessage(decryptedMessage);
                            } catch (Exception e) {
                                Log.e(TAG, "Decryption failed", e);
                                Toast.makeText(this, "Falha na descriptografia. Senha incorreta?", Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    } else {
                        // Try decryption with existing passphrase
                        try {
                            String decryptedMessage = CryptoUtils.decryptFromBase64(message, sharePassphrase);
                            ocorrenciaJson = new JSONObject(decryptedMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Decryption failed with existing passphrase", e);
                            Toast.makeText(this, "Falha na descriptografia", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }
                
                // Parse the occurrence from JSON
                Ocorrencia ocorrenciaRecebida = parseOcorrenciaFromJson(ocorrenciaJson);
                
                if (ocorrenciaRecebida != null) {
                    // Show received occurrence dialog
                    showReceivedOccurrenceDialog(ocorrenciaRecebida);
                    
                    // Save the received occurrence locally (without syncing to server)
                    saveReceivedOccurrence(ocorrenciaRecebida);
                } else {
                    Toast.makeText(this, "Erro ao processar ocorrência recebida", Toast.LENGTH_LONG).show();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing received message", e);
                Toast.makeText(this, "Erro ao processar mensagem recebida", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Solicita senha para descriptografia
     */
    protected void promptForPassphrase(java.util.function.Consumer<String> onOk) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        new AlertDialog.Builder(this)
            .setTitle("Senha de Descriptografia")
            .setMessage("Digite a senha para descriptografar a ocorrência recebida:")
            .setView(input)
            .setPositiveButton("OK", (dialog, which) -> {
                String passphrase = input.getText().toString().trim();
                if (!passphrase.isEmpty()) {
                    onOk.accept(passphrase);
                } else {
                    Toast.makeText(this, "Senha não pode estar vazia", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /**
     * Converte JSON para objeto Ocorrencia
     */
    protected Ocorrencia parseOcorrenciaFromJson(JSONObject json) {
        try {
            Ocorrencia ocorrencia = new Ocorrencia();
            
            if (json.has("id")) ocorrencia.setId(json.getInt("id"));
            if (json.has("titulo")) ocorrencia.setDescricao(json.getString("titulo"));
            if (json.has("descricao")) ocorrencia.setDescricao(json.getString("descricao"));
            if (json.has("urgencia")) ocorrencia.setUrgente(json.getBoolean("urgencia"));
            if (json.has("latitude")) ocorrencia.setLatitude(json.getDouble("latitude"));
            if (json.has("longitude")) ocorrencia.setLongitude(json.getDouble("longitude"));
            if (json.has("fotoPath")) ocorrencia.setFotoPath(json.getString("fotoPath"));
            if (json.has("videoPath")) ocorrencia.setVideoPath(json.getString("videoPath"));
            
            // Parse date
            if (json.has("dataHora")) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    Date date = sdf.parse(json.getString("dataHora"));
                    ocorrencia.setDataHora(date != null ? date : new Date());
                } catch (ParseException e) {
                    Log.w(TAG, "Error parsing date, using current date", e);
                    ocorrencia.setDataHora(new Date());
                }
            } else {
                ocorrencia.setDataHora(new Date());
            }
            
            return ocorrencia;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing occurrence from JSON", e);
            return null;
        }
    }

    /**
     * Mostra diálogo com a ocorrência recebida
     */
    protected void showReceivedOccurrenceDialog(Ocorrencia ocorrencia) {
        String message = "Nova ocorrência recebida via P2P:\n\n" +
                "Descrição: " + ocorrencia.getDescricao() + "\n" +
                "Urgente: " + (ocorrencia.isUrgente() ? "Sim" : "Não") + "\n" +
                "Data/Hora: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(ocorrencia.getDataHora()) + "\n" +
                "Localização: " + ocorrencia.getLatitude() + ", " + ocorrencia.getLongitude();
        
        new AlertDialog.Builder(this)
            .setTitle("Ocorrência Recebida")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
    }

    /**
     * Salva a ocorrência recebida localmente (sem sincronizar com servidor)
     */
    protected void saveReceivedOccurrence(Ocorrencia ocorrencia) {
        // Save locally without syncing to server (P2P received occurrences should not be synced)
        offlineHelper.saveReceivedSharedOcorrenciaAsync(
            ocorrencia.getDescricao(),
            ocorrencia.getLocalizacaoSimbolica(),
            ocorrencia.getLatitude(),
            ocorrencia.getLongitude(),
            ocorrencia.isUrgente(),
            ocorrencia.getFotoPath(),
            ocorrencia.getVideoPath(),
            id -> runOnUiThread(() -> {
                Toast.makeText(this, "Ocorrência recebida e salva localmente", Toast.LENGTH_SHORT).show();
            }),
            err -> runOnUiThread(() -> {
                Toast.makeText(this, "Falha ao salvar ocorrência recebida: " + err, Toast.LENGTH_LONG).show();
            })
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup P2P resources
        if (mReceiver != null) {
            try {
                unregisterReceiver(mReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered", e);
            }
            mReceiver = null;
        }
        
        if (mCommManager != null) {
            mCommManager.cleanup();
            mCommManager = null;
        }
        
        if (mBound) {
            try {
                unbindService(mConnection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Service not bound", e);
            }
            mBound = false;
        }
        
        Log.d(TAG, "P2P resources cleaned up in " + getClass().getSimpleName());
    }
}
