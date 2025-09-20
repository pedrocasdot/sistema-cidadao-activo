package ao.co.isptec.aplm.sca;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import ao.co.isptec.aplm.sca.utils.LocationHelper;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.util.Base64;

import ao.co.isptec.aplm.sca.model.Ocorrencia;
import ao.co.isptec.aplm.sca.service.ApiService;
import ao.co.isptec.aplm.sca.service.SessionManager;
import ao.co.isptec.aplm.sca.dto.IncidentResponse;

// Offline-first imports
import ao.co.isptec.aplm.sca.database.repository.OcorrenciaRepository;
import ao.co.isptec.aplm.sca.database.entity.OcorrenciaEntity;
import ao.co.isptec.aplm.sca.sync.SyncManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import ao.co.isptec.aplm.sca.offline.OfflineFirstHelper;

// WiFi Direct P2P imports
import pt.inesc.termite.wifidirect.SimWifiP2pBroadcast;
import pt.inesc.termite.wifidirect.SimWifiP2pDevice;
import pt.inesc.termite.wifidirect.SimWifiP2pDeviceList;
import pt.inesc.termite.wifidirect.SimWifiP2pInfo;
import pt.inesc.termite.wifidirect.SimWifiP2pManager;
import pt.inesc.termite.wifidirect.SimWifiP2pManager.Channel;
import pt.inesc.termite.wifidirect.SimWifiP2pManager.PeerListListener;
import pt.inesc.termite.wifidirect.SimWifiP2pManager.GroupInfoListener;
import pt.inesc.termite.wifidirect.service.SimWifiP2pService;
import pt.inesc.termite.wifidirect.sockets.SimWifiP2pSocketManager;

import ao.co.isptec.aplm.sca.security.CryptoUtils;

import org.json.JSONObject;
import org.json.JSONException;

public class TelaMapaOcorrencias extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, 
        PeerListListener, GroupInfoListener, P2PCommManager.P2PMessageListener, SimWifiP2pBroadcastReceiver.P2PStatusCallback {

    private static final String TAG = "TelaMapaOcorrencias";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    
    private SupportMapFragment mapFragment;
    private GoogleMap mMap;
    private LocationHelper locationHelper;
    private FloatingActionButton fabNovaOcorrencia, fabMinhasOcorrencias, fabLogout;
    
    private ApiService apiService;
    private SessionManager sessionManager;
    private List<Ocorrencia> listaOcorrencias;
    
    // Offline-first components
    private OcorrenciaRepository repository;
    private SyncManager syncManager;
    private ExecutorService executorService;
    private OfflineFirstHelper offlineHelper;
    
    // WiFi Direct P2P components
    private SimWifiP2pManager mManager = null;
    private Channel mChannel = null;
    private Messenger mService = null;
    private boolean mBound = false;
    private SimWifiP2pBroadcastReceiver mReceiver;
    private P2PCommManager mCommManager;
    private List<SimWifiP2pDevice> mPeers = new ArrayList<>();
    private String sharePassphrase; // Used to encrypt/decrypt P2P payloads

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tela_mapa_ocorrencias);

        initializeViews();
        setupApiServices();
        setupMap();
        setupButtons();
        
        locationHelper = new LocationHelper(this);
        
        // Initialize P2P components
        initializeP2P();
    }

    private void initializeViews() {
        fabNovaOcorrencia = findViewById(R.id.fabNovaOcorrencia);
        fabMinhasOcorrencias = findViewById(R.id.fabMinhasOcorrencias);
        fabLogout = findViewById(R.id.fabLogout);
        listaOcorrencias = new ArrayList<>();
    }

    private void setupApiServices() {
        sessionManager = new SessionManager(this);
        apiService = new ApiService(this);
        
        // Initialize offline-first components
        repository = new OcorrenciaRepository(this);
        syncManager = SyncManager.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        offlineHelper = new OfflineFirstHelper(this);
        
        Log.d(TAG, "API services and offline components initialized for map");
        
        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, Login.class);
            startActivity(intent);
            finish();
        }
    }

    private void setupMap() {
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupButtons() {
        fabNovaOcorrencia.setOnClickListener(v -> {
            Intent intent = new Intent(TelaMapaOcorrencias.this, RegistarOcorrencia.class);
            startActivity(intent);
        });

        fabMinhasOcorrencias.setOnClickListener(v -> {
            Intent intent = new Intent(TelaMapaOcorrencias.this, ListarOcorrencias.class);
            startActivity(intent);
        });
        
        fabLogout.setOnClickListener(v -> {
            showLogoutConfirmationDialog();
        });
    }
    
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Tem certeza que deseja sair?")
                .setPositiveButton("Sim", (dialog, which) -> {
                    performLogout();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    
    private void performLogout() {
        // Clear session
        sessionManager.logout();
        
        // Show logout message
        Toast.makeText(this, "Logout realizado com sucesso", Toast.LENGTH_SHORT).show();
        
        // Navigate to login screen
        Intent intent = new Intent(this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        
        // Configure map settings
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        
        // Set up marker click listener
        mMap.setOnMarkerClickListener(this);
        
        // Set up info window click listener for detailed views
        setupMapInfoWindowClickListener();

        // Request location permission and enable location layer
        enableMyLocation();
        
        // Load and display incidents on map
        loadOcorrenciasOnMap();
        
        // Não utilizar localização padrão; aguardar GPS ou exibir ocorrências
    }





    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Enable the location layer
            try {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                getCurrentLocation();
            } catch (SecurityException e) {
                Log.e(TAG, "Error enabling location: " + e.getMessage());
            }
        } else {
            // Request permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    private void getCurrentLocation() {
        // Mostra mensagem de carregamento
        Toast.makeText(this, "🔍 Obtendo localização atual...", Toast.LENGTH_SHORT).show();
        
        // Usa o LocationHelper para obter a localização
        locationHelper.getCurrentLocation(this, true, new LocationHelper.LocationResultListener() {
            @Override
            public void onLocationSuccess(LatLng location, float accuracy) {
                // Move a câmera para a localização obtida
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
                
                // Mostra mensagem de sucesso
                
                Log.d(TAG, String.format("Location obtained: %.6f, %.6f (accuracy: %.0fm)", 
                    location.latitude, location.longitude, accuracy));
            }
            
            @Override
            public void onLocationError(String errorMessage) {
                Log.e(TAG, "Error getting location: " + errorMessage);
                // Não mover a câmera para localização padrão
            }
        });
    }

    private void loadOcorrenciasOnMap() {
        Log.d(TAG, "Loading occurrences on map from local database");
        loadOcorrenciasFromLocal();
    }
    
    /**
     * Load occurrences from local database (offline-first)
     */
    private void loadOcorrenciasFromLocal() {
        Log.d(TAG, "Requesting occurrences from OfflineFirstHelper");
        offlineHelper.getAllOcorrenciasAsync(new Consumer<List<OcorrenciaEntity>>() {
            @Override
            public void accept(List<OcorrenciaEntity> entities) {
                listaOcorrencias.clear();

                for (OcorrenciaEntity entity : entities) {
                    Ocorrencia ocorrencia = repository.entityToModel(entity);
                    listaOcorrencias.add(ocorrencia);
                }

                displayOcorrenciasOnMap();
                Log.d(TAG, "Loaded " + listaOcorrencias.size() + " occurrences from local database");

                showSyncStatusOnMap();
            }
        });
    }
    
    /**
     * Show sync status on map
     */
    private void showSyncStatusOnMap() {
        offlineHelper.getUnsyncedCountAsync(new IntConsumer() {
            @Override
            public void accept(int unsyncedCount) {
                boolean isNetworkAvailable = syncManager.isNetworkAvailable();
                if (unsyncedCount > 0) {
                    String statusMessage;
                    if (isNetworkAvailable) {
                        statusMessage = unsyncedCount + " ocorrência(s) sendo sincronizada(s)";
                    } else {
                        statusMessage = unsyncedCount + " ocorrência(s) aguardando sincronização (sem rede)";
                    }
                    Toast.makeText(TelaMapaOcorrencias.this, statusMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void displayOcorrenciasOnMap() {
        if (mMap == null) {
            Log.w(TAG, "Map not ready, cannot display occurrences");
            return;
        }

        Log.d(TAG, "Displaying " + listaOcorrencias.size() + " occurrences on map");
        
        // Clear existing markers
        mMap.clear();

        int urgentCount = 0;
        int resolvedCount = 0;
        int pendingCount = 0;
        
        for (Ocorrencia ocorrencia : listaOcorrencias) {
            LatLng position = new LatLng(ocorrencia.getLatitude(), ocorrencia.getLongitude());
            
            // Create enhanced title and snippet with more information
            String title = String.format("%s - ID: %d", 
                ocorrencia.getUrgenciaTexto(), ocorrencia.getId());
            
            String snippet = String.format("%s\nLocal: %s", 
                ocorrencia.getDescricao().length() > 50 ? 
                    ocorrencia.getDescricao().substring(0, 50) + "..." : ocorrencia.getDescricao(),
                ocorrencia.getLocalizacaoSimbolica() != null ? 
                    ocorrencia.getLocalizacaoSimbolica() : "Localização não especificada");
            
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position)
                    .title(title)
                    .snippet(snippet);

            // Marker colors based on urgency
            float markerColor;
            if (ocorrencia.isUrgente()) {
                urgentCount++;
                // Urgent - Red
                markerColor = BitmapDescriptorFactory.HUE_RED;
                pendingCount++;
            } else {
                // Not urgent - Blue
                markerColor = BitmapDescriptorFactory.HUE_BLUE;
                pendingCount++;
            }
            
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(markerColor));

            Marker marker = mMap.addMarker(markerOptions);
            if (marker != null) {
                marker.setTag(ocorrencia);
            }
        }

        // Enhanced feedback message with statistics
        String message = String.format("Mapa: %d ocorrências (%d urgentes, %d não urgentes)", 
            listaOcorrencias.size(), urgentCount, (listaOcorrencias.size() - urgentCount));
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        
        Log.d(TAG, String.format("Map display complete - Total: %d, Urgent: %d, Non-urgent: %d",
            listaOcorrencias.size(), urgentCount, (listaOcorrencias.size() - urgentCount)));
        
        // Adjust camera to show all markers if we have occurrences
        if (!listaOcorrencias.isEmpty()) {
            adjustCameraToShowAllMarkers();
        }
    }

    /**
     * Adjusts the camera to show all markers on the map
     */
    private void adjustCameraToShowAllMarkers() {
        if (mMap == null || listaOcorrencias.isEmpty()) {
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Ocorrencia ocorrencia : listaOcorrencias) {
            LatLng position = new LatLng(ocorrencia.getLatitude(), ocorrencia.getLongitude());
            builder.include(position);
        }

        try {
            LatLngBounds bounds = builder.build();
            int padding = 100; // Padding in pixels
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            mMap.animateCamera(cameraUpdate);
            Log.d(TAG, "Camera adjusted to show all " + listaOcorrencias.size() + " markers");
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting camera: " + e.getMessage());
            // Sem fallback para localização padrão
        }
    }

    /**
     * Sets up map info window click listener for occurrence details
     */
    private void setupMapInfoWindowClickListener() {
        if (mMap != null) {
            mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                @Override
                public void onInfoWindowClick(Marker marker) {
                    Ocorrencia ocorrencia = (Ocorrencia) marker.getTag();
                    if (ocorrencia != null) {
                        showOcorrenciaDetails(ocorrencia);
                    }
                }
            });
        }
    }

    /**
     * Shows detailed information about an occurrence
     */
    private void showOcorrenciaDetails(Ocorrencia ocorrencia) {
        String details = String.format(
            "ID: %d\n" +
            "Descrição: %s\n" +
            "Urgência: %s\n" +
            "Localização: %s\n" +
            "Coordenadas: %.6f, %.6f\n" +
            "Compartilhamentos: %d",
            ocorrencia.getId(),
            ocorrencia.getDescricao(),
            ocorrencia.getUrgenciaTexto(),
            ocorrencia.getLocalizacaoSimbolica() != null ? 
                ocorrencia.getLocalizacaoSimbolica() : "Não especificada",
            ocorrencia.getLatitude(),
            ocorrencia.getLongitude(),
            ocorrencia.getContadorPartilha()
        );

        new AlertDialog.Builder(this)
            .setTitle("Detalhes da Ocorrência")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("Ver no Mapa", (dialog, which) -> {
                LatLng position = new LatLng(ocorrencia.getLatitude(), ocorrencia.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16));
            })
            .show();

        Log.d(TAG, "Showing details for occurrence ID: " + ocorrencia.getId());
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        Ocorrencia ocorrencia = (Ocorrencia) marker.getTag();
        if (ocorrencia != null) {
            Intent intent = new Intent(TelaMapaOcorrencias.this, VisualizarOcorrencia.class);
            intent.putExtra("ocorrencia", ocorrencia);
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, enable location
                enableMyLocation();
            } else {
                Toast.makeText(this, "Permissão de localização negada. Ative o GPS para ver sua posição.", Toast.LENGTH_LONG).show();
                // Não utilizar fallback
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ao.co.isptec.aplm.sca.utils.LocationHelper.REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                // Usuário habilitou as configurações necessárias, tentar novamente
                enableMyLocation();
            } else {
                Log.w(TAG, "Usuário não habilitou alta precisão de localização");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload incidents when returning from other activities
        if (mMap != null) {
            loadOcorrenciasOnMap();
        }
    }
    
    // ========== P2P Implementation Methods ==========
    
    /**
     * Service connection for WiFi P2P
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            mManager = new SimWifiP2pManager(mService);
            mChannel = mManager.initialize(getApplication(), getMainLooper(), null);
            mBound = true;
            
            // Register broadcast receiver after service is connected
            if (mReceiver == null) {
                mReceiver = new SimWifiP2pBroadcastReceiver(mManager, mChannel, TelaMapaOcorrencias.this);
                IntentFilter filter = new IntentFilter();
                filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_STATE_CHANGED_ACTION);
                filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_PEERS_CHANGED_ACTION);
                filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_NETWORK_MEMBERSHIP_CHANGED_ACTION);
                filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_GROUP_OWNERSHIP_CHANGED_ACTION);
                registerReceiver(mReceiver, filter);
            }
            
            Log.d(TAG, "P2P Service connected");
            Toast.makeText(TelaMapaOcorrencias.this, "Wi-Fi Direct ativado - Pronto para receber ocorrências", Toast.LENGTH_SHORT).show();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
            mManager = null;
            mChannel = null;
            mBound = false;
            Log.d(TAG, "P2P Service disconnected");
        }
    };
    
    /**
     * Inicializa os componentes WiFi Direct P2P
     */
    private void initializeP2P() {
        Log.d(TAG, "Initializing P2P components");
        
        // Initialize SimWifiP2pSocketManager
        SimWifiP2pSocketManager.Init(getApplicationContext());
        
        // Initialize communication manager
        mCommManager = new P2PCommManager(this);
        
        // Start WiFi P2P service
        Intent intent = new Intent(this, SimWifiP2pService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mBound = true;
        
        // Start communication server
        mCommManager.startServer();
        
        Log.d(TAG, "P2P initialization completed");
    }
    
    // ========== P2P Listener Methods ==========
    
    @Override
    public void updateP2PStatus(String status, boolean isEnabled) {
        Log.d(TAG, "P2P Status updated: " + status + ", enabled: " + isEnabled);
        runOnUiThread(() -> {
            if (isEnabled) {
                Toast.makeText(this, status + " - Pronto para receber ocorrências", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void onPeersAvailable(SimWifiP2pDeviceList peers) {
        Log.d(TAG, "Peers available: " + peers.getDeviceList().size());
        mPeers.clear();
        mPeers.addAll(peers.getDeviceList());
    }
    
    @Override
    public void onGroupInfoAvailable(SimWifiP2pDeviceList devices, SimWifiP2pInfo groupInfo) {
        Log.d(TAG, "Group info available - devices: " + devices.getDeviceList().size());
    }
    
    @Override
    public void onMessageReceived(String message) {
        Log.d(TAG, "Message received: " + message);
        handleReceivedMessage(message);
    }
    
    @Override
    public void onConnectionEstablished() {
        Log.d(TAG, "P2P Connection established");
        runOnUiThread(() -> {
            Toast.makeText(this, "Conectado ao dispositivo", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onConnectionFailed(String error) {
        Log.e(TAG, "P2P Connection failed: " + error);
        runOnUiThread(() -> {
            Toast.makeText(this, "Falha na conexão: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    @Override
    public void onMessageSent() {
        Log.d(TAG, "Message sent successfully");
        runOnUiThread(() -> {
            Toast.makeText(this, "Mensagem enviada com sucesso!", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onMessageSendFailed(String error) {
        Log.e(TAG, "Message send failed: " + error);
        runOnUiThread(() -> {
            Toast.makeText(this, "Falha ao enviar: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    /**
     * Processa mensagem recebida via P2P
     */
    private void handleReceivedMessage(String message) {
        runOnUiThread(() -> {
            try {
                Log.d("P2P Message", "Received message: " + message);
                
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
                                String decrypted = CryptoUtils.decryptFromBase64(originalMsg, sharePassphrase);
                                handleReceivedMessage(decrypted);
                            } catch (Exception decErr) {
                                Log.e(TAG, "Falha ao desencriptar mensagem P2P", decErr);
                                sharePassphrase = null; // Clear for next attempt
                                Toast.makeText(this, "Falha na desencriptação da mensagem", Toast.LENGTH_LONG).show();
                            }
                        });
                        return; // Will continue after user provides passphrase
                    } else {
                        try {
                            String decrypted = CryptoUtils.decryptFromBase64(message, sharePassphrase);
                            ocorrenciaJson = new JSONObject(decrypted);
                        } catch (Exception decErr) {
                            Log.e(TAG, "Falha ao desencriptar mensagem P2P com senha armazenada, solicitando nova senha", decErr);
                            // Clear the stored passphrase and prompt for a new one
                            sharePassphrase = null;
                            final String originalMsg = message;
                            promptForPassphrase(pass -> {
                                sharePassphrase = pass;
                                try {
                                    String decrypted = CryptoUtils.decryptFromBase64(originalMsg, sharePassphrase);
                                    handleReceivedMessage(decrypted);
                                } catch (Exception decErr2) {
                                    Log.e(TAG, "Falha ao desencriptar mensagem P2P com nova senha", decErr2);
                                    sharePassphrase = null; // Clear again for next attempt
                                    Toast.makeText(this, "Mensagem inválida ou chave incorreta", Toast.LENGTH_LONG).show();
                                }
                            });
                            return;
                        }
                    }
                }
                
                // Create a new Ocorrencia object from the received data
                Ocorrencia ocorrenciaRecebida = new Ocorrencia();
                ocorrenciaRecebida.setId(Integer.parseInt(ocorrenciaJson.getString("id")));
                // Prefer descricao; fallback to titulo
                String descricao = ocorrenciaJson.optString("descricao",
                        ocorrenciaJson.optString("titulo", "Sem descrição"));
                ocorrenciaRecebida.setDescricao(descricao);
                // Optional local symbolic location
                if (ocorrenciaJson.has("localizacaoSimbolica")) {
                    ocorrenciaRecebida.setLocalizacaoSimbolica(
                            ocorrenciaJson.optString("localizacaoSimbolica", null));
                }
                ocorrenciaRecebida.setUrgente(ocorrenciaJson.optBoolean("urgencia", false));
                
                // Parse date if available
                try {
                    String dataHoraStr = ocorrenciaJson.optString("dataHora");
                    if (!dataHoraStr.isEmpty()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        ocorrenciaRecebida.setDataHora(sdf.parse(dataHoraStr));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing date", e);
                }
                
                // Set location if available
                if (ocorrenciaJson.has("latitude") && ocorrenciaJson.has("longitude")) {
                    double lat = ocorrenciaJson.getDouble("latitude");
                    double lng = ocorrenciaJson.getDouble("longitude");
                    ocorrenciaRecebida.setLatitude(lat);
                    ocorrenciaRecebida.setLongitude(lng);
                }
                
                // Media paths if available
                if (ocorrenciaJson.has("fotoPath")) {
                    ocorrenciaRecebida.setFotoPath(ocorrenciaJson.optString("fotoPath", null));
                }
                
                // Process Base64 photo if available
                if (ocorrenciaJson.has("fotoBase64")) {
                    String photoBase64 = ocorrenciaJson.getString("fotoBase64");
                    String savedPhotoPath = saveBase64Photo(photoBase64);
                    if (savedPhotoPath != null) {
                        ocorrenciaRecebida.setFotoPath(savedPhotoPath);
                        Log.d(TAG, "Photo saved from Base64 to: " + savedPhotoPath);
                    }
                }
                
                if (ocorrenciaJson.has("videoPath")) {
                    ocorrenciaRecebida.setVideoPath(ocorrenciaJson.optString("videoPath", null));
                }
                
                // Persist received occurrence locally but DO NOT sync (belongs to other user)
                if (offlineHelper != null) {
                    offlineHelper.saveReceivedSharedOcorrenciaAsync(
                        ocorrenciaRecebida.getDescricao(),
                        ocorrenciaRecebida.getLocalizacaoSimbolica(),
                        ocorrenciaRecebida.getLatitude(),
                        ocorrenciaRecebida.getLongitude(),
                        ocorrenciaRecebida.isUrgente(),
                        ocorrenciaRecebida.getFotoPath(),
                        ocorrenciaRecebida.getVideoPath(),
                        id -> runOnUiThread(() -> {
                            Toast.makeText(this, "Ocorrência recebida e salva localmente (não será sincronizada)", Toast.LENGTH_SHORT).show();
                            // Reload map to show new occurrence
                            if (mMap != null) {
                                loadOcorrenciasOnMap();
                            }
                        }),
                        err -> runOnUiThread(() -> {
                            Toast.makeText(this, "Falha ao salvar ocorrência recebida: " + err, Toast.LENGTH_LONG).show();
                        })
                    );
                }

                // Show a dialog to confirm viewing the received occurrence
                new AlertDialog.Builder(this)
                    .setTitle("Nova Ocorrência Recebida")
                    .setMessage(String.format(Locale.getDefault(),
                        "Deseja visualizar a ocorrência recebida?\n\n" +
                        "Título: %s\n" +
                        "Urgente: %s",
                        ocorrenciaRecebida.getDescricao(),
                        ocorrenciaRecebida.isUrgente() ? "Sim" : "Não"))
                    .setPositiveButton("Visualizar", (dialog, which) -> {
                        // Open the received occurrence in a new activity
                        Intent intent = new Intent(TelaMapaOcorrencias.this, VisualizarOcorrencia.class);
                        intent.putExtra("ocorrencia", ocorrenciaRecebida);
                        startActivity(intent);
                    })
                    .setNegativeButton("Fechar", null)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();
                
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing received message", e);
                Toast.makeText(this, "Erro ao processar ocorrência recebida", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Prompt user for passphrase
     */
    private void promptForPassphrase(java.util.function.Consumer<String> onOk) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Digite a palavra-passe");
        
        new AlertDialog.Builder(this)
            .setTitle("Palavra-passe necessária")
            .setMessage("Esta mensagem está encriptada. Digite a palavra-passe para desencriptar:")
            .setView(input)
            .setPositiveButton("OK", (dialog, which) -> {
                String passphrase = input.getText().toString().trim();
                if (!passphrase.isEmpty()) {
                    onOk.accept(passphrase);
                } else {
                    Toast.makeText(this, "Palavra-passe não pode estar vazia", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }
    
    // ========== Lifecycle Methods ==========
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup location helper
        if (locationHelper != null) {
            locationHelper.onDestroy();
        }
        
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
        
        // Cleanup executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        Log.d(TAG, "Activity destroyed, P2P and other resources cleaned up");
    }
    
    /**
     * Salva uma foto recebida em Base64 como arquivo local
     */
    private String saveBase64Photo(String base64Photo) {
        if (base64Photo == null || base64Photo.isEmpty()) {
            return null;
        }
        
        try {
            // Decode Base64 to byte array
            byte[] photoBytes = Base64.decode(base64Photo, Base64.DEFAULT);
            
            // Create unique filename for received photo
            String fileName = "received_photo_" + System.currentTimeMillis() + ".jpg";
            File photoFile = new File(getExternalFilesDir(null), fileName);
            
            // Write bytes to file
            java.io.FileOutputStream fos = new java.io.FileOutputStream(photoFile);
            fos.write(photoBytes);
            fos.close();
            
            Log.d(TAG, "Base64 photo saved to: " + photoFile.getAbsolutePath() + 
                  " (size: " + photoFile.length() + " bytes)");
            
            return photoFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving Base64 photo", e);
            return null;
        }
    }
}
