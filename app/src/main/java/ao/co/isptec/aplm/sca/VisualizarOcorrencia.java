package ao.co.isptec.aplm.sca;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.graphics.Typeface;
import android.graphics.Color;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.util.Base64;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import ao.co.isptec.aplm.sca.model.Ocorrencia;
import ao.co.isptec.aplm.sca.service.ApiService;
import ao.co.isptec.aplm.sca.service.SessionManager;
import ao.co.isptec.aplm.sca.service.EmailService;
import ao.co.isptec.aplm.sca.security.CryptoUtils;

import com.squareup.picasso.Picasso;
import ao.co.isptec.aplm.sca.offline.OfflineFirstHelper;
import ao.co.isptec.aplm.sca.database.entity.OcorrenciaEntity;

public class VisualizarOcorrencia extends AppCompatActivity implements OnMapReadyCallback, 
        PeerListListener, GroupInfoListener, P2PCommManager.P2PMessageListener {

    private TextView textDescricao, textDataHora, textUrgencia, textContadorPartilha, textNoEvidence;
    private ImageView imageViewFoto;
    private VideoView videoViewVideo;
    private Button btnPartilhar, btnDispositivosProximos;
    private SupportMapFragment mapFragment;
    private GoogleMap mMap;
    
    // P2P Status UI
    private TextView textStatusP2P;
    private ImageView imgStatusP2P;
    
    private Ocorrencia ocorrencia;
    private SimpleDateFormat dateFormat;
    
    // API integration
    private ApiService apiService;
    private SessionManager sessionManager;
    private static final String TAG = "VisualizarOcorrencia";
    
    // WiFi Direct P2P components
    private SimWifiP2pManager mManager = null;
    private Channel mChannel = null;
    private Messenger mService = null;
    private boolean mBound = false;
    private SimWifiP2pBroadcastReceiver mReceiver;
    private P2PCommManager mCommManager;
    private List<SimWifiP2pDevice> mPeers = new ArrayList<>();
    private OfflineFirstHelper offlineHelper;
    private EmailService emailService;
    private String lastRecipientName;
    private String sharePassphrase; // Used to encrypt/decrypt P2P payloads

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visualizar_ocorrencia);
        // Setup toolbar as ActionBar and enable back (Up)
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        sessionManager = new SessionManager(this);
        apiService = new ApiService(this);
        
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, Login.class);
            startActivity(intent);
            finish();
            return;
        }

        // Offline-first helper to persist received shares locally
        offlineHelper = new OfflineFirstHelper(this);
        // Email service for notifications
        emailService = new EmailService(this);

        initializeViews();
        
        ocorrencia = (Ocorrencia) getIntent().getSerializableExtra("ocorrencia");
        
        dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        
        if (ocorrencia != null) {
            // Reload from database to get updated share counter
            reloadOcorrenciaFromDatabase();
        } else {
            Toast.makeText(this, "Ocorrência não encontrada", Toast.LENGTH_SHORT).show();
            finish();
        }
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
    private void initializeP2P() {
        Log.d(TAG, "Initializing P2P components");
        
        // Initialize SimWifiP2pSocketManager
        SimWifiP2pSocketManager.Init(getApplicationContext());
        
        // Initialize communication manager
        mCommManager = new P2PCommManager(this);
        
        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_NETWORK_MEMBERSHIP_CHANGED_ACTION);
        filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_GROUP_OWNERSHIP_CHANGED_ACTION);
        
        // Start WiFi P2P service
        Intent intent = new Intent(this, SimWifiP2pService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mBound = true;
        
        // Start communication server
        mCommManager.startServer();
        
        updateP2PStatus("Inicializando WiFi P2P...", false);
    }
    
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
                mReceiver = new SimWifiP2pBroadcastReceiver(mManager, mChannel, VisualizarOcorrencia.this);
                IntentFilter filter = new IntentFilter();
                filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_STATE_CHANGED_ACTION);
                filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_PEERS_CHANGED_ACTION);
                filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_NETWORK_MEMBERSHIP_CHANGED_ACTION);
                filter.addAction(SimWifiP2pBroadcast.WIFI_P2P_GROUP_OWNERSHIP_CHANGED_ACTION);
                registerReceiver(mReceiver, filter);
            }
            
            updateP2PStatus("WiFi P2P conectado", true);
            Log.d(TAG, "P2P Service connected");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
            mManager = null;
            mChannel = null;
            mBound = false;
            updateP2PStatus("WiFi P2P desconectado", false);
            Log.d(TAG, "P2P Service disconnected");
        }
    };
    
    /**
     * Atualiza o status do P2P na interface
     */
    public void updateP2PStatus(String status, boolean isConnected) {
        runOnUiThread(() -> {
            if (textStatusP2P != null) {
                textStatusP2P.setText(status);
            }
            if (imgStatusP2P != null) {
                imgStatusP2P.setImageResource(isConnected ? 
                    android.R.drawable.presence_online : android.R.drawable.presence_offline);
            }
        });
    }

    // ====== Encryption helpers ======
    private void promptForPassphrase(java.util.function.Consumer<String> onOk) {
        EditText input = new EditText(this);
        input.setHint("Palavra-passe de partilha");
        new AlertDialog.Builder(this)
            .setTitle("Proteção da Partilha")
            .setMessage("Defina/insira a palavra-passe para encriptar a ocorrência.")
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                String pass = input.getText() != null ? input.getText().toString().trim() : "";
                if (pass.isEmpty()) {
                    Toast.makeText(this, "Palavra-passe não pode ser vazia", Toast.LENGTH_SHORT).show();
                } else {
                    onOk.accept(pass);
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void sendEncryptedOccurrence() {
        try {
            JSONObject ocorrenciaJson = createOcorrenciaJson();
            String plaintext = ocorrenciaJson.toString();
            String encrypted = CryptoUtils.encryptToBase64(plaintext, sharePassphrase);
            mCommManager.sendMessage(encrypted);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao encriptar/enviar ocorrência", e);
            Toast.makeText(this, "Erro ao encriptar a ocorrência para partilha", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Mostra diálogo com dispositivos próximos
     */
    private void showNearbyDevicesDialog() {
        if (mBound && mManager != null && mChannel != null) {
            // Request peers
            mManager.requestPeers(mChannel, this);
        } else {
            Toast.makeText(this, "WiFi P2P não está disponível", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Envia mensagem P2P para um dispositivo
     */
    private void sendP2PMessage(String ipAddress, String message) {
        if (mCommManager != null) {
            // Connect and send message
            mCommManager.connectToDevice(ipAddress);
            // Message will be sent when connection is established
        } else {
            Toast.makeText(this, "Gerenciador P2P não disponível", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViews() {
        // Initialize basic views
        textDescricao = findViewById(R.id.textDescricao);
        textDataHora = findViewById(R.id.textDataHora);
        textUrgencia = findViewById(R.id.textUrgencia);
        textContadorPartilha = findViewById(R.id.textContadorPartilha);
        imageViewFoto = findViewById(R.id.imageViewFoto);
        videoViewVideo = findViewById(R.id.videoViewVideo);
        textNoEvidence = findViewById(R.id.textNoEvidence);
        btnPartilhar = findViewById(R.id.btnPartilhar);
        btnDispositivosProximos = findViewById(R.id.btnDispositivosProximos);
        
        // Initialize P2P status views
        textStatusP2P = findViewById(R.id.textStatusP2P);
        imgStatusP2P = findViewById(R.id.imgStatusP2P);
    }

    private void setupOccurrenceData() {
        populateViews();
    }

    private void populateViews() {
        textDescricao.setText(ocorrencia.getDescricao());
        textDataHora.setText(dateFormat.format(ocorrencia.getDataHora()));
        textUrgencia.setText(ocorrencia.getUrgenciaTexto());
        textContadorPartilha.setText("Partilhado " + ocorrencia.getContadorPartilha() + " vez(es)");

        // Set urgency color and style
        if (ocorrencia.isUrgente()) {
            textUrgencia.setTextColor(getColor(R.color.urgent_color));
            textUrgencia.setTypeface(null, Typeface.BOLD);
        } else {
            textUrgencia.setTextColor(getColor(R.color.normal_color));
            textUrgencia.setTypeface(null, Typeface.NORMAL);
        }

        // Load media
        loadMedia();
    }

    private void loadMedia() {
        // Initially hide all media components
        imageViewFoto.setVisibility(View.GONE);
        videoViewVideo.setVisibility(View.GONE);
        textNoEvidence.setVisibility(View.VISIBLE);
        
        boolean hasMedia = false;
        
        // Load image
        if (ocorrencia.getFotoPath() != null && !ocorrencia.getFotoPath().isEmpty()) {
            try {
                String imagePath = ocorrencia.getFotoPath();
                Log.d(TAG, "Loading image from path: " + imagePath);
                
                // Check if it's a URL or local file path
                if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                    Log.d(TAG, "Attempting to load image from URL: " + imagePath);
                    
                    // Load from URL using Picasso
                    Picasso.get()
                        .load(imagePath)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .into(imageViewFoto, new com.squareup.picasso.Callback() {
                            @Override
                            public void onSuccess() {
                                imageViewFoto.setVisibility(View.VISIBLE);
                                textNoEvidence.setVisibility(View.GONE);
                                Log.d(TAG, "Image loaded successfully from URL: " + imagePath);
                                Toast.makeText(VisualizarOcorrencia.this, "Imagem carregada com sucesso", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Exception e) {
                                Log.e(TAG, "Error loading image from URL: " + imagePath, e);
                                Log.e(TAG, "Error details: " + e.getMessage());
                                
                                // Show error image instead of hiding
                                imageViewFoto.setImageResource(R.drawable.ic_image_error);
                                imageViewFoto.setVisibility(View.VISIBLE);
                                textNoEvidence.setVisibility(View.GONE);
                                
                                Toast.makeText(VisualizarOcorrencia.this, "Erro ao carregar imagem da URL", Toast.LENGTH_SHORT).show();
                            }
                        });
                    hasMedia = true;
                } else {
                    // Load from local file path
                    try {
                        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                        if (bitmap != null) {
                            imageViewFoto.setImageBitmap(bitmap);
                            imageViewFoto.setVisibility(View.VISIBLE);
                            textNoEvidence.setVisibility(View.GONE);
                            hasMedia = true;
                            Log.d(TAG, "Image loaded successfully from local file");
                        } else {
                            Log.w(TAG, "Could not decode image from local file: " + imagePath);
                            // Show error placeholder
                            imageViewFoto.setImageResource(R.drawable.ic_image_error);
                            imageViewFoto.setVisibility(View.VISIBLE);
                            textNoEvidence.setVisibility(View.GONE);
                            hasMedia = true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading image from local file", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting up image loading", e);
            }
        }

        // Load video
        if (ocorrencia.getVideoPath() != null && !ocorrencia.getVideoPath().isEmpty()) {
            try {
                String videoPath = ocorrencia.getVideoPath();
                Log.d(TAG, "Attempting to load video from path: " + videoPath);
                
                Uri videoUri = Uri.parse(videoPath);
                videoViewVideo.setVideoURI(videoUri);
                videoViewVideo.setVisibility(View.VISIBLE);
                textNoEvidence.setVisibility(View.GONE);
                hasMedia = true;
                
                // Set up video controls
                videoViewVideo.setOnPreparedListener(mp -> {
                    Log.d(TAG, "Video prepared successfully: " + videoPath);
                    Toast.makeText(VisualizarOcorrencia.this, "Vídeo carregado com sucesso", Toast.LENGTH_SHORT).show();
                    
                    // Enable media controller for better user experience
                    android.widget.MediaController mediaController = new android.widget.MediaController(VisualizarOcorrencia.this);
                    videoViewVideo.setMediaController(mediaController);
                    mediaController.setAnchorView(videoViewVideo);
                });
                
                videoViewVideo.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "Video error for path: " + videoPath + ", what=" + what + ", extra=" + extra);
                    videoViewVideo.setVisibility(View.GONE);
                    
                    // Show error feedback to user
                    Toast.makeText(VisualizarOcorrencia.this, "Erro ao carregar vídeo", Toast.LENGTH_SHORT).show();
                    
                    // If no image is showing, show the no evidence text
                    if (imageViewFoto.getVisibility() != View.VISIBLE) {
                        textNoEvidence.setVisibility(View.VISIBLE);
                        textNoEvidence.setText("Erro ao carregar mídia");
                    }
                    return true;
                });
                
                // Add completion listener
                videoViewVideo.setOnCompletionListener(mp -> {
                    Log.d(TAG, "Video playback completed");
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error setting up video loading for path: " + ocorrencia.getVideoPath(), e);
                Toast.makeText(this, "Erro ao configurar reprodução de vídeo", Toast.LENGTH_SHORT).show();
            }
        }
        
        // If no media was found or loaded, ensure placeholder is visible
        if (!hasMedia) {
            textNoEvidence.setVisibility(View.VISIBLE);
            textNoEvidence.setText("Nenhuma evidência disponível");
            Log.d(TAG, "No media available for this occurrence");
        } else {
            // Log what type of media was loaded
            boolean imageVisible = imageViewFoto.getVisibility() == View.VISIBLE;
            boolean videoVisible = videoViewVideo.getVisibility() == View.VISIBLE;
            
            if (imageVisible && videoVisible) {
                Log.d(TAG, "Both image and video are available - video takes priority");
                // If both are visible, hide image to prioritize video
                imageViewFoto.setVisibility(View.GONE);
            } else if (imageVisible) {
                Log.d(TAG, "Image media loaded and displayed");
            } else if (videoVisible) {
                Log.d(TAG, "Video media loaded and displayed");
            }
        }
    }
    
    /**
     * Test method to verify media display functionality (both image and video)
     * This method can be called to test media loading with sample data
     */
    private void testMediaDisplay() {
        Log.d(TAG, "Testing media display functionality");
        
        // Determine what type of media to test based on current occurrence
        boolean hasImage = ocorrencia.getFotoPath() != null && !ocorrencia.getFotoPath().isEmpty();
        boolean hasVideo = ocorrencia.getVideoPath() != null && !ocorrencia.getVideoPath().isEmpty();
        
        if (hasImage) {
            // Test image display
            Log.d(TAG, "Testing image display with sample drawable");
            imageViewFoto.setImageResource(R.drawable.sample_image);
            imageViewFoto.setVisibility(View.VISIBLE);
            videoViewVideo.setVisibility(View.GONE);
            textNoEvidence.setVisibility(View.GONE);
            Toast.makeText(this, "Testando exibição de imagem", Toast.LENGTH_SHORT).show();
        } else if (hasVideo) {
            // Test video display placeholder
            Log.d(TAG, "Testing video display with placeholder");
            imageViewFoto.setImageResource(R.drawable.ic_video_placeholder);
            imageViewFoto.setVisibility(View.VISIBLE);
            videoViewVideo.setVisibility(View.GONE);
            textNoEvidence.setVisibility(View.GONE);
            Toast.makeText(this, "Testando exibição de vídeo (placeholder)", Toast.LENGTH_SHORT).show();
        } else {
            // Test with sample image when no media is available
            Log.d(TAG, "Testing with sample image (no media in occurrence)");
            imageViewFoto.setImageResource(R.drawable.sample_image);
            imageViewFoto.setVisibility(View.VISIBLE);
            videoViewVideo.setVisibility(View.GONE);
            textNoEvidence.setVisibility(View.GONE);
            Toast.makeText(this, "Testando exibição de mídia (sem mídia na ocorrência)", Toast.LENGTH_LONG).show();
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
        // Botão para partilhar ocorrência
        btnPartilhar.setOnClickListener(v -> {
            partilharOcorrencia();
        });
        
        // Botão para encontrar dispositivos próximos
        btnDispositivosProximos.setOnClickListener(v -> {
            showNearbyDevicesDialog();
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        
        // Configure map settings
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(true);
        
        if (ocorrencia != null) {
            // Get the location from the occurrence
            double latitude = ocorrencia.getLatitude();
            double longitude = ocorrencia.getLongitude();
            LatLng location = new LatLng(latitude, longitude);
            
            // Add a marker at the occurrence location
            MarkerOptions markerOptions = new MarkerOptions()
                .position(location)
                .title("Local da Ocorrência")
                .snippet(ocorrencia.getDescricao());
            
            // Customize marker based on urgency
//            if (ocorrencia.isUrgente()) {
//                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
//            } else {
//                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
//            }
//
            mMap.addMarker(markerOptions);
            
            // Add a circle to show the approximate area (500m radius)
            mMap.addCircle(new CircleOptions()
                .center(location)
                .radius(500) // 500 meters
                .strokeColor(Color.argb(100, 0, 0, 255))
                .fillColor(Color.argb(30, 0, 0, 255)));
            
            // Move camera to the marker with animation
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        } else {
            // Não usar localização padrão; manter estado atual do mapa
            Toast.makeText(this, "Ocorrência não disponível", Toast.LENGTH_SHORT).show();
        }
    }

    private void partilharOcorrencia() {
        if (ocorrencia == null) {
            Toast.makeText(this, "Erro: Ocorrência não disponível para partilha", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!mBound || mManager == null) {
            Toast.makeText(this, "WiFi P2P não está disponível", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Request peers to show available devices for sharing
        mManager.requestPeers(mChannel, new SimWifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(SimWifiP2pDeviceList peers) {
                showShareDeviceDialog(peers);
            }
        });
    }
    
    /**
     * Mostra diálogo para selecionar dispositivo para partilha
     */
    private void showShareDeviceDialog(SimWifiP2pDeviceList peers) {
        List<SimWifiP2pDevice> deviceList = new ArrayList<>(peers.getDeviceList());
        
        if (deviceList.isEmpty()) {
            Toast.makeText(this, "Nenhum dispositivo encontrado", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create device names array with user information
        String[] deviceNames = new String[deviceList.size()];
        String currentUser = sessionManager.getFullName() != null ? 
            sessionManager.getFullName() : sessionManager.getUsername();
        
        for (int i = 0; i < deviceList.size(); i++) {
            SimWifiP2pDevice device = deviceList.get(i);
            String deviceInfo = device.deviceName + " (" + device.getVirtIp() + ")";
            String userInfo = "Usuário: " + (currentUser != null ? currentUser : "Desconhecido");
            deviceNames[i] = deviceInfo + "\n" + userInfo;
        }
        
        new AlertDialog.Builder(this)
            .setTitle("Selecionar Dispositivo para Partilha")
            .setItems(deviceNames, (dialog, which) -> {
                SimWifiP2pDevice selectedDevice = deviceList.get(which);
                // Keep recipient info for email notification (include user info)
                String currentUserName = sessionManager.getFullName() != null ? 
                    sessionManager.getFullName() : sessionManager.getUsername();
                lastRecipientName = (selectedDevice.deviceName != null ? selectedDevice.deviceName : selectedDevice.getVirtIp()) + 
                    " (" + (currentUserName != null ? currentUserName : "Usuário Desconhecido") + ")";
                // Ask for passphrase before starting connection
                promptForPassphrase(pass -> {
                    sharePassphrase = pass;
                    partilharComDispositivo(selectedDevice.getVirtIp());
                });
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }
    
    private static final int SEND_TIMEOUT_MS = 15000; // 15 seconds timeout for sending
    
    private void partilharComDispositivo(String ipAddress) {
        if (ocorrencia == null) {
            showErrorDialog("Erro", "Ocorrência não disponível para partilha");
            return;
        }
        
        if (ipAddress == null || ipAddress.isEmpty()) {
            showErrorDialog("Erro", "Endereço do dispositivo inválido");
            return;
        }
        
        // Show progress dialog with cancel option
        AlertDialog progressDialog = createProgressDialog();
        progressDialog.show();
        
        // Start a new thread for network operations
        new Thread(() -> {
            boolean success = false;
            String errorMessage = null;
            
            try {
                // Convert occurrence to JSON string
                JSONObject ocorrenciaJson = createOcorrenciaJson();
                String message = ocorrenciaJson.toString();
                
                Log.d("P2P_Share", "Tentando enviar para " + ipAddress + ": " + message);
                
                // Send via P2P
                sendP2PMessage(ipAddress, message);
                
                // Wait a bit for the message to be sent
                Thread.sleep(2000);
                success = true;
                
                if (!success) {
                    errorMessage = "Falha ao enviar a mensagem. Verifique a conexão com o dispositivo.";
                    Log.e("P2P_Share", "Falha ao enviar mensagem para " + ipAddress);
                } else {
                    Log.d("P2P_Share", "Mensagem enviada com sucesso para " + ipAddress);
                }
                
            } catch (JSONException e) {
                errorMessage = "Erro ao formatar os dados da ocorrência.";
                Log.e("P2P_Share", "Erro ao criar JSON", e);
            } catch (Exception e) {
                errorMessage = "Erro inesperado: " + e.getMessage();
                Log.e("P2P_Share", "Erro ao partilhar ocorrência", e);
            }
            
            final boolean finalSuccess = success;
            final String finalErrorMessage = errorMessage;
            
            runOnUiThread(() -> {
                try {
                    progressDialog.dismiss();
                    
                    if (finalSuccess) {
                // Note: Share counter will be updated in onMessageSent callback
                // to avoid double counting
                
                // Show success message
                showSuccessDialog("Sucesso", "Ocorrência partilhada com sucesso!");
                } else {
                    // Show error message
                    showErrorDialog("Falha na Partilha", 
                            finalErrorMessage != null ? finalErrorMessage : 
                            "Não foi possível partilhar a ocorrência. Tente novamente.");
                    }
                } catch (Exception e) {
                    Log.e("P2P_Share", "Erro na atualização da UI", e);
                }
            });
            
        }).start();
    }
    
    private JSONObject createOcorrenciaJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", ocorrencia.getId());
        json.put("titulo", ocorrencia.getDescricao());
        json.put("descricao", ocorrencia.getDescricao());
        json.put("tipo", ocorrencia.isUrgente());
        json.put("urgencia", ocorrencia.isUrgente());
        json.put("dataHora", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(ocorrencia.getDataHora()));
        json.put("latitude", ocorrencia.getLatitude());
        json.put("longitude", ocorrencia.getLongitude());
        
        // Add media information if available
        if (ocorrencia.getFotoPath() != null && !ocorrencia.getFotoPath().isEmpty()) {
            json.put("fotoPath", ocorrencia.getFotoPath());
            
            // Convert photo to Base64 and include in JSON
            String photoBase64 = convertPhotoToBase64(ocorrencia.getFotoPath());
            if (photoBase64 != null) {
                json.put("fotoBase64", photoBase64);
                Log.d(TAG, "Photo converted to Base64 for sharing (size: " + photoBase64.length() + " chars)");
            }
        }
        
        if (ocorrencia.getVideoPath() != null && !ocorrencia.getVideoPath().isEmpty()) {
            json.put("videoPath", ocorrencia.getVideoPath());
            // Note: Video files are typically too large for P2P sharing via JSON
            // Consider implementing separate file transfer for videos if needed
        }
        
        return json;
    }
    
    private AlertDialog createProgressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(R.layout.dialog_loading);
        builder.setTitle("A Partilhar...");
        builder.setMessage("A enviar ocorrência para o dispositivo. Por favor aguarde...");
        builder.setCancelable(true);
        
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        
        // Handle back button press
        dialog.setOnCancelListener(dialogInterface -> {
            // User cancelled the operation
            Toast.makeText(this, "Partilha cancelada", Toast.LENGTH_SHORT).show();
        });
        
        return dialog;
    }
    
    private void updateShareCounterUI() {
        if (textContadorPartilha != null) {
            textContadorPartilha.setText("Partilhado " + ocorrencia.getContadorPartilha() + " vez(es)");
        }
    }
    
    /**
     * Reload ocorrencia from database to get updated share counter
     */
    private void reloadOcorrenciaFromDatabase() {
        if (ocorrencia != null && ocorrencia.getId() > 0) {
            // Try to reload from database to get updated counter
            offlineHelper.getAllOcorrenciasAsync(ocorrenciasList -> {
                for (OcorrenciaEntity entity : ocorrenciasList) {
                    if (entity.getId() == (long) ocorrencia.getId()) {
                        // Update the current ocorrencia with database values
                        ocorrencia.setContadorPartilha(entity.getContadorPartilha());
                        Log.d(TAG, "Reloaded share counter from database: " + entity.getContadorPartilha());
                        break;
                    }
                }
                
                // Continue with normal initialization
                populateViews();
                setupMap();
                setupButtons();
                initializeP2P();
            });
        } else {
            // If no ID, continue with normal initialization
            populateViews();
            setupMap();
            setupButtons();
            initializeP2P();
        }
    }
    
    private void showSuccessDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
    }
    
    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
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
                    Log.e("VisualizarOcorrencia", "Error parsing date", e);
                }
                
                // Set location if available
                if (ocorrenciaJson.has("latitude") && ocorrenciaJson.has("longitude")) {
                    double lat = ocorrenciaJson.getDouble("latitude");
                    double lng = ocorrenciaJson.getDouble("longitude");
                    // Assuming Ocorrencia has setLatitude and setLongitude methods
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
                        }),
                        err -> runOnUiThread(() -> {
                            Toast.makeText(this, "Falha ao salvar ocorrência recebida: " + err, Toast.LENGTH_LONG).show();
                        })
                    );
                }

                // Show a dialog to confirm adding the received occurrence
                new AlertDialog.Builder(this)
                    .setTitle("Nova Ocorrência Recebida")
                    .setMessage(String.format(Locale.getDefault(),
                        "Deseja visualizar a ocorrência recebida?\n\n" +
                        "Título: %s\n" +
                        "Tipo: %s\n" +
                        "Urgente: %s",
                        ocorrenciaRecebida.getDescricao(),
                        ocorrenciaRecebida.isUrgente() ? "Sim" : "Não"))
                    .setPositiveButton("Visualizar", (dialog, which) -> {
                        // Open the received occurrence in a new activity
                        Intent intent = new Intent(VisualizarOcorrencia.this, VisualizarOcorrencia.class);
                        intent.putExtra("ocorrencia", ocorrenciaRecebida);
                        startActivity(intent);
                    })
                    .setNegativeButton("Fechar", null)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();
                
            } catch (JSONException e) {
                Log.e("VisualizarOcorrencia", "Error parsing received message", e);
                Toast.makeText(this, "Erro ao processar ocorrência recebida", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("VisualizarOcorrencia", "Unexpected error processing message", e);
                Toast.makeText(this, "Erro inesperado: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    
    // ========== Interface Methods ==========
    
    /**
     * Callback quando a lista de peers é atualizada
     */
    @Override
    public void onPeersAvailable(SimWifiP2pDeviceList peers) {
        mPeers.clear();
        mPeers.addAll(peers.getDeviceList());
        
        Log.d(TAG, "Peers available: " + mPeers.size());
        
        // Show devices dialog
        showDevicesDialog(peers);
    }
    
    /**
     * Callback quando informações do grupo são atualizadas
     */
    @Override
    public void onGroupInfoAvailable(SimWifiP2pDeviceList devices, SimWifiP2pInfo groupInfo) {
        Log.d(TAG, "Group info available");
        
        // Update group information
        StringBuilder groupStr = new StringBuilder();
        String currentUser = sessionManager.getFullName() != null ? 
            sessionManager.getFullName() : sessionManager.getUsername();
            
        for (String deviceName : groupInfo.getDevicesInNetwork()) {
            SimWifiP2pDevice device = devices.getByName(deviceName);
            String deviceInfo = deviceName + " (" + 
                ((device == null) ? "??" : device.getVirtIp()) + ")";
            String userInfo = "Usuário: " + (currentUser != null ? currentUser : "Desconhecido");
            String devstr = deviceInfo + "\n" + userInfo + "\n\n";
            groupStr.append(devstr);
        }
        
        // Show group info dialog
        new AlertDialog.Builder(this)
            .setTitle("Dispositivos na Rede WiFi")
            .setMessage(groupStr.toString())
            .setNeutralButton("Fechar", null)
            .show();
    }
    
    /**
     * Mostra diálogo com dispositivos disponíveis
     */
    private void showDevicesDialog(SimWifiP2pDeviceList peers) {
        List<SimWifiP2pDevice> deviceList = new ArrayList<>(peers.getDeviceList());
        
        if (deviceList.isEmpty()) {
            Toast.makeText(this, "Nenhum dispositivo encontrado", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder peersStr = new StringBuilder();
        String currentUser = sessionManager.getFullName() != null ? 
            sessionManager.getFullName() : sessionManager.getUsername();
        
        for (SimWifiP2pDevice device : deviceList) {
            // Incluir informações do usuário junto com o dispositivo
            String deviceInfo = device.deviceName + " (" + device.getVirtIp() + ")";
            String userInfo = "Usuário: " + (currentUser != null ? currentUser : "Desconhecido");
            String devstr = deviceInfo + "\n" + userInfo + "\n\n";
            peersStr.append(devstr);
        }
        
        new AlertDialog.Builder(this)
            .setTitle("Dispositivos WiFi Próximos")
            .setMessage(peersStr.toString())
            .setNeutralButton("Fechar", null)
            .show();
    }
    
    // ========== P2P Communication Callbacks ==========
    
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
            
            // Send the occurrence data (encrypted)
            if (sharePassphrase == null || sharePassphrase.isEmpty()) {
                promptForPassphrase(pass -> {
                    sharePassphrase = pass;
                    sendEncryptedOccurrence();
                });
            } else {
                sendEncryptedOccurrence();
            }
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
            // Update share counter
            ocorrencia.incrementarPartilha();
            updateShareCounterUI();
            
            // Save updated counter to database
            if (ocorrencia.getId() > 0 && offlineHelper != null) {
                offlineHelper.updateShareCounterAsync(
                    ocorrencia.getId(),
                    ocorrencia.getContadorPartilha(),
                    () -> Log.d(TAG, "Share counter updated in database: " + ocorrencia.getContadorPartilha()),
                    error -> Log.e(TAG, "Error updating share counter: " + error)
                );
            }
            
            Toast.makeText(this, "Ocorrência partilhada com sucesso!", Toast.LENGTH_SHORT).show();

            // Notify via email about the share
            String titulo = ocorrencia.getDescricao();
            String descricao = ocorrencia.getDescricao();
            String nomeRemetente = sessionManager != null && sessionManager.getFullName() != null
                    ? sessionManager.getFullName()
                    : (sessionManager != null && sessionManager.getUsername() != null ? sessionManager.getUsername() : "Utilizador");
            String nomeDestinatario = (lastRecipientName != null && !lastRecipientName.isEmpty())
                    ? lastRecipientName
                    : "Dispositivo P2P";

            if (emailService != null) {
                emailService.enviarEmailPartilhaOcorrencia(
                    titulo,
                    descricao,
                    nomeRemetente,
                    nomeDestinatario,
                    new EmailService.EmailCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Email de partilha enviado com sucesso");
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Falha ao enviar email de partilha: " + error);
                        }
                    }
                );
            }
            
            // Disconnect after sending
            mCommManager.disconnect();
        });
    }
    
    @Override
    public void onMessageSendFailed(String error) {
        Log.e(TAG, "Message send failed: " + error);
        runOnUiThread(() -> {
            Toast.makeText(this, "Falha ao enviar: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    // ========== Lifecycle Methods ==========
    
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
        
        if (emailService != null) {
            emailService.shutdown();
        }

        Log.d(TAG, "P2P resources cleaned up");
    }
    
    /**
     * Converte uma foto para Base64 para envio via P2P
     */
    private String convertPhotoToBase64(String photoPath) {
        if (photoPath == null || photoPath.isEmpty()) {
            return null;
        }
        
        try {
            // Check if it's a URL (from server) - skip conversion for URLs
            if (photoPath.startsWith("http://") || photoPath.startsWith("https://")) {
                Log.d(TAG, "Skipping Base64 conversion for URL: " + photoPath);
                return null;
            }
            
            // Load bitmap from local file
            File photoFile = new File(photoPath);
            if (!photoFile.exists()) {
                Log.w(TAG, "Photo file does not exist: " + photoPath);
                return null;
            }
            
            // Decode with size limit to avoid memory issues
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photoPath, options);
            
            // Calculate sample size to reduce memory usage
            int sampleSize = 1;
            int maxSize = 800; // Max width/height for P2P sharing
            while (options.outWidth / sampleSize > maxSize || options.outHeight / sampleSize > maxSize) {
                sampleSize *= 2;
            }
            
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            
            Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);
            if (bitmap == null) {
                Log.w(TAG, "Could not decode bitmap from: " + photoPath);
                return null;
            }
            
            // Convert to byte array
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream); // Compress to reduce size
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            
            // Convert to Base64
            String base64String = Base64.encodeToString(byteArray, Base64.DEFAULT);
            
            Log.d(TAG, "Photo converted to Base64 - Original size: " + photoFile.length() + 
                  " bytes, Base64 size: " + base64String.length() + " chars");
            
            return base64String;
            
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory converting photo to Base64", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error converting photo to Base64", e);
            return null;
        }
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
