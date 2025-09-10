package ao.co.isptec.aplm.sca;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import ao.co.isptec.aplm.sca.utils.LocationHelper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


import ao.co.isptec.aplm.sca.dto.NewIncidentRequest;
import ao.co.isptec.aplm.sca.dto.IncidentResponse;
import ao.co.isptec.aplm.sca.service.ApiService;
import ao.co.isptec.aplm.sca.service.SessionManager;
import ao.co.isptec.aplm.sca.service.EmailService;

// Offline-first imports
import ao.co.isptec.aplm.sca.offline.OfflineFirstHelper;
import ao.co.isptec.aplm.sca.sync.SyncManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class RegistarOcorrencia extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_LOCATION_PERMISSION = 101;
    
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<Intent> takeVideoLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private EditText editDescricao;
    private RadioGroup radioGroupUrgencia;
    private Button btnTirarFoto, btnGravarVideo, btnSalvar;
    private ImageView imageViewFoto;
    private VideoView videoViewVideo;
    private TextView textLocationStatus;

    private LocationHelper locationHelper;
    // Coordenadas somente após obter do GPS (sem padrão)
    private Double latitude = null;
    private Double longitude = null;
    private String fotoPath;
    private String videoPath;
    private Uri photoUri;
    private Uri videoUri;

    private ApiService apiService;
    private SessionManager sessionManager;
    private EmailService emailService;
    private String currentUsername;
    private String urgenciaTexto;
    
    // Offline-first components
    private OfflineFirstHelper offlineHelper;
    private SyncManager syncManager;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registar_ocorrencia);

        initializeViews();
        setupActivityResultLaunchers();
        setupApiService();
        setupLocation();
        loadUserSession();
    }

    private void initializeViews() {
        editDescricao = findViewById(R.id.editDescricao);
        radioGroupUrgencia = findViewById(R.id.radioGroupUrgencia);
        btnTirarFoto = findViewById(R.id.btnTirarFoto);
        btnGravarVideo = findViewById(R.id.btnGravarVideo);
        btnSalvar = findViewById(R.id.btnSalvar);
        imageViewFoto = findViewById(R.id.imageViewFoto);
        videoViewVideo = findViewById(R.id.videoViewVideo);
        textLocationStatus = findViewById(R.id.textLocationStatus);

        btnTirarFoto.setOnClickListener(v -> checkCameraPermissionAndCapture());
        btnGravarVideo.setOnClickListener(v -> checkCameraPermissionAndRecord());
        btnSalvar.setOnClickListener(v -> salvarOcorrencia());
        
        // Initialize location status (no default location)
        updateLocationStatus("🛰 Obtendo localização precisa...", false);
    }

    private void setupApiService() {
        apiService = new ApiService(this);
        sessionManager = new SessionManager(this);
        emailService = new EmailService(this);
        
        // Initialize offline-first components
        offlineHelper = new OfflineFirstHelper(this);
        syncManager = SyncManager.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        
        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            // Redirect to login if not authenticated
            Intent intent = new Intent(this, Login.class);
            startActivity(intent);
            finish();
            return;
        }
        
        Log.d("RegistarOcorrencia", "ApiService and SessionManager initialized");
    }

    private void setupLocation() {
        locationHelper = new LocationHelper(this);
        checkLocationPermissionAndGetLocation();
    }
    
    /**
     * Update location status display for user feedback
     */
    private void updateLocationStatus(String message, boolean isLocationFound) {
        if (textLocationStatus != null) {
            textLocationStatus.setText(message);
            
            // Change text color based on status
            if (isLocationFound) {
                textLocationStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            } else {
                textLocationStatus.setTextColor(getColor(R.color.gray_text));
            }
        }
    }

    private void setupActivityResultLaunchers() {
        // Photo capture launcher
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            try {
                                Bitmap imageBitmap = (Bitmap) result.getData().getExtras().get("data");
                                if (imageBitmap != null) {
                                    imageViewFoto.setImageBitmap(imageBitmap);
                                    fotoPath = saveBitmapToFile(imageBitmap);
                                    Toast.makeText(RegistarOcorrencia.this, "Foto capturada com sucesso!", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e("RegistarOcorrencia", "Erro ao processar foto", e);
                                Toast.makeText(RegistarOcorrencia.this, "Erro ao processar foto", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

        // Video capture launcher
        takeVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            try {
                                // For video capture, we use the pre-defined videoUri
                                if (videoUri != null) {
                                    videoViewVideo.setVideoURI(videoUri);
                                    videoViewVideo.setVisibility(android.view.View.VISIBLE);
                                    videoViewVideo.start(); // Auto-play the captured video
                                    videoPath = videoUri.getPath();
                                    Log.d("RegistarOcorrencia", "Video captured successfully: " + videoPath);
                                    Toast.makeText(RegistarOcorrencia.this, "Vídeo capturado com sucesso!", Toast.LENGTH_SHORT).show();
                                } else {
                                    Log.e("RegistarOcorrencia", "Video URI is null");
                                    Toast.makeText(RegistarOcorrencia.this, "Erro: URI do vídeo não encontrado", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e("RegistarOcorrencia", "Erro ao processar vídeo", e);
                                Toast.makeText(RegistarOcorrencia.this, "Erro ao processar vídeo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.w("RegistarOcorrencia", "Video capture cancelled or failed. Result code: " + result.getResultCode());
                            Toast.makeText(RegistarOcorrencia.this, "Captura de vídeo cancelada", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean isGranted) {
                        if (isGranted) {
                            Toast.makeText(RegistarOcorrencia.this, "Permissão concedida", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(RegistarOcorrencia.this, "Permissão negada. Não é possível usar a câmera.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LocationHelper.REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                // Usuário habilitou as configurações necessárias, tentar novamente
                requestLocationUpdates();
            } else {
                Log.w("RegistarOcorrencia", "Usuário não habilitou alta precisão de localização");
                updateLocationStatus("Precisão reduzida. Alguns recursos podem não funcionar corretamente.", false);
            }
        }
    }

    private void loadUserSession() {
        // Get current user information from session
        currentUsername = sessionManager.getUsername();
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            Log.e("RegistarOcorrencia", "No username found in session");
            // Redirect to login
            Intent intent = new Intent(this, Login.class);
            startActivity(intent);
            finish();
            return;
        }
        
        Log.d("RegistarOcorrencia", "User session loaded for: " + currentUsername);
    }

    private void checkCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            dispatchTakePictureIntent();
        }
    }

    private void checkCameraPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            dispatchTakeVideoIntent();
        }
    }

    private void checkLocationPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            updateLocationStatus("🔒 Solicitando permissão de localização...", false);
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            // Try to get last known location first for faster response
            getLastKnownLocation();
        }
    }
    
    /**
     * Try to get the last known location for immediate feedback
     */
    private void getLastKnownLocation() {
        locationHelper.getLastKnownLocation(new LocationHelper.LocationResultListener() {
            @Override
            public void onLocationSuccess(com.google.android.gms.maps.model.LatLng location, float accuracy) {
                latitude = location.latitude;
                longitude = location.longitude;
                
                String locationText = String.format("📍 Localização obtida\n%.6f, %.6f\n(Última localização conhecida, precisão: %.0fm)", 
                    latitude, longitude, accuracy);
                updateLocationStatus(locationText, true);
                
                Log.d("RegistarOcorrencia", "Using last known location: " + latitude + ", " + longitude);
            }
            
            @Override
            public void onLocationError(String errorMessage) {
                Log.w("RegistarOcorrencia", "Error getting last known location: " + errorMessage);
                updateLocationStatus("Procurando localização atual...", false);
                // Se não conseguir a última localização conhecida, tenta obter uma nova
                requestLocationUpdates();
            }
        });
    }

    private void requestLocationUpdates() {
        updateLocationStatus("🛰 Obtendo localização via GPS...", false);
        
        locationHelper.getCurrentLocation(this, true, new LocationHelper.LocationResultListener() {
            @Override
            public void onLocationSuccess(com.google.android.gms.maps.model.LatLng location, float accuracy) {
                latitude = location.latitude;
                longitude = location.longitude;
                
                String providerIcon = accuracy < 50 ? "🛰" : "📡";
                String locationText = String.format("%s Localização obtida\n%.6f, %.6f\nPrecisão: %.0fm", 
                    providerIcon, latitude, longitude, accuracy);
                
                updateLocationStatus(locationText, true);
                Log.d("RegistarOcorrencia", "Location updated: " + latitude + ", " + longitude + 
                    " (accuracy: " + accuracy + "m)");
                
                // Para atualizações para economizar bateria
                locationHelper.cleanup();
            }
            
            @Override
            public void onLocationError(String errorMessage) {
                Log.e("RegistarOcorrencia", "Error getting location: " + errorMessage);
                updateLocationStatus("❌ " + errorMessage, false);
            }
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            // On Android 11+, we can't reliably use resolveActivity due to package visibility
            // Instead, we'll try to launch the intent and handle any exceptions
            takePictureLauncher.launch(takePictureIntent);
            Log.d("RegistarOcorrencia", "Camera intent launched successfully");
        } catch (Exception e) {
            Log.e("RegistarOcorrencia", "Error launching camera intent", e);
            Toast.makeText(this, "Nenhum aplicativo de câmera encontrado. Por favor, instale um aplicativo de câmera.", Toast.LENGTH_LONG).show();
        }
    }

    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        try {
            // Create a video file to store the captured video
            File videoFile = createVideoFile();
            if (videoFile != null) {
                videoUri = FileProvider.getUriForFile(this,
                        "ao.co.isptec.aplm.sca.fileprovider",
                        videoFile);
                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                videoPath = videoFile.getAbsolutePath();
                
                Log.d("RegistarOcorrencia", "Video file created: " + videoPath);
                Log.d("RegistarOcorrencia", "Video URI: " + videoUri.toString());
                
                takeVideoLauncher.launch(takeVideoIntent);
                Log.d("RegistarOcorrencia", "Video intent launched successfully");
            } else {
                Toast.makeText(this, "Erro ao criar arquivo de vídeo", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("RegistarOcorrencia", "Error launching video intent", e);
            Toast.makeText(this, "Nenhum aplicativo de vídeo encontrado. Por favor, instale um aplicativo de câmera.", Toast.LENGTH_LONG).show();
        }
    }
    
    private File createVideoFile() throws IOException {
        // Create a video file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String videoFileName = "VIDEO_" + timeStamp + "_";
        File storageDir = getExternalFilesDir("Videos");
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }
        return File.createTempFile(
                videoFileName,  /* prefix */
                ".mp4",         /* suffix */
                storageDir      /* directory */
        );
    }



    private String saveBitmapToFile(Bitmap bitmap) {
        try {
            // Create a unique filename with timestamp
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "OCORRENCIA_" + timeStamp + ".jpg";
            
            // Use app's private directory for better security
            File storageDir = new File(getExternalFilesDir(null), "ocorrencias");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            
            File imageFile = new File(storageDir, fileName);
            FileOutputStream out = new FileOutputStream(imageFile);
            
            // Compress with good quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            out.flush();
            out.close();
            
            Log.d("RegistarOcorrencia", "Imagem salva em: " + imageFile.getAbsolutePath());
            return imageFile.getAbsolutePath();
            
        } catch (IOException e) {
            Log.e("RegistarOcorrencia", "Erro ao salvar imagem", e);
            Toast.makeText(this, "Erro ao salvar imagem", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void salvarOcorrencia() {
        String descricao = editDescricao.getText().toString().trim();
        
        if (descricao.isEmpty()) {
            Toast.makeText(this, "Por favor, insira uma descrição", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fotoPath == null && videoPath == null) {
            Toast.makeText(this, "Por favor, tire uma foto ou grave um vídeo", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedUrgenciaId = radioGroupUrgencia.getCheckedRadioButtonId();
        if (selectedUrgenciaId == -1) {
            Toast.makeText(this, "Por favor, selecione o nível de urgência", Toast.LENGTH_SHORT).show();
            return;
        }
        
        RadioButton selectedRadioButton = findViewById(selectedUrgenciaId);
        urgenciaTexto = selectedRadioButton.getText().toString();
        boolean isUrgent = false;
        if (urgenciaTexto != null) {
            String u = urgenciaTexto.toLowerCase(Locale.ROOT);
            isUrgent = u.contains("urg") || u.contains("alta") || u.contains("alto") || u.contains("high");
        }
        
        // Exigir coordenadas reais antes de salvar
        if (latitude == null || longitude == null) {
            Toast.makeText(this, "Ative o GPS e aguarde obter a localização para continuar.", Toast.LENGTH_LONG).show();
            // Tentar novamente obter localização
            requestLocationUpdates();
            return;
        }

        // Get location name for symbolic location (sem padrão)
        String localizacaoSimbolica = ""; // Pode ser preenchido futuramente via geocodificação reversa
        
        Log.d("RegistarOcorrencia", "Saving occurrence offline-first");
        
        // Save using offline-first approach
        salvarOcorrenciaOfflineFirst(descricao, localizacaoSimbolica, latitude, longitude, isUrgent, fotoPath, videoPath);
    }
    
    /**
     * Saves occurrence using offline-first approach
     */
    private void salvarOcorrenciaOfflineFirst(String descricao, String localizacaoSimbolica, 
                                            double latitude, double longitude, boolean urgente, 
                                            String fotoPath, String videoPath) {
        
        executorService.execute(() -> {
            try {
                offlineHelper.saveOcorrenciaOfflineFirstAsync(
                    descricao,
                    localizacaoSimbolica,
                    latitude,
                    longitude,
                    urgente,
                    fotoPath,
                    videoPath,
                    (LongConsumer) localId -> runOnUiThread(() -> {
                        Toast.makeText(RegistarOcorrencia.this,
                            "Ocorrência salva localmente! ID: " + localId,
                            Toast.LENGTH_SHORT).show();

                        Log.d("RegistarOcorrencia", "Occurrence saved locally with ID: " + localId);

                        // Show sync status
                        showSyncStatus();

                        // Send email notification if needed
                        enviarEmailNotificacao(createNewIncidentRequest(descricao, localizacaoSimbolica, latitude, longitude, urgente),
                                              urgente ? "URGENTE" : "NORMAL");

                        finish();
                    }),
                    (Consumer<String>) error -> runOnUiThread(() -> {
                        Toast.makeText(RegistarOcorrencia.this,
                            "Erro ao salvar ocorrência: " + error,
                            Toast.LENGTH_LONG).show();

                        Log.e("RegistarOcorrencia", "Error saving occurrence: " + error);
                    })
                );
            } catch (Exception e) {
                Log.e("RegistarOcorrencia", "Exception in offline save", e);
                runOnUiThread(() -> {
                    Toast.makeText(RegistarOcorrencia.this, 
                        "Erro interno: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * Shows sync status to user
     */
    private void showSyncStatus() {
        offlineHelper.getSyncInfoAsync(syncInfo -> {
            String statusMessage;
            if (syncInfo.getUnsyncedCount() == 0) {
                statusMessage = "Todas as ocorrências estão sincronizadas";
            } else if (syncManager.isNetworkAvailable()) {
                statusMessage = "Sincronizando " + syncInfo.getUnsyncedCount() + " ocorrência(s)...";
            } else {
                statusMessage = syncInfo.getUnsyncedCount() + " ocorrência(s) aguardando sincronização (sem rede)";
            }
            Toast.makeText(RegistarOcorrencia.this, statusMessage, Toast.LENGTH_LONG).show();
            Log.d("RegistarOcorrencia", "Sync status: " + statusMessage);
        });
    }

    /**
     * Cria um NewIncidentRequest a partir dos dados da ocorrência
     * @param descricao Descrição da ocorrência
     * @param localizacaoSimbolica Localização simbólica
     * @param latitude Latitude
     * @param longitude Longitude
     * @param urgente Se é urgente
     * @return NewIncidentRequest configurado
     */
    private NewIncidentRequest createNewIncidentRequest(String descricao, String localizacaoSimbolica, 
                                                       double latitude, double longitude, boolean urgente) {
        // Formato de data ISO para a API
        String dataHoraISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .format(new Date());
        
        // Obter ID do usuário da sessão
        Long userId = sessionManager.getUserId();
        if (userId == null) {
            userId = 1L; // Fallback para ID padrão
        }
        
        return new NewIncidentRequest(
            "Ocorrência", // título padrão
            descricao,
            latitude,
            longitude,
            dataHoraISO,
            urgente,
            userId
        );
    }

/**
 * Envia notificação por email quando uma nova ocorrência é registrada
 * @param request Dados da ocorrência
 * @param urgencia Nível de urgência da ocorrência
 */
private void enviarEmailNotificacao(NewIncidentRequest request, String urgencia) {
    if (emailService == null) {
        Log.e("RegistarOcorrencia", "EmailService não inicializado");
        return;
    }

    // Obter data/hora formatada
    String dataHoraFormatada = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
            .format(new Date());

    // Obter nome do usuário logado
    String nomeUsuario = sessionManager.getUsername();
    if (nomeUsuario == null || nomeUsuario.isEmpty()) {
        nomeUsuario = "Usuário não identificado";
    }

    // Enviar email usando o serviço
    emailService.enviarEmailNovaOcorrencia(
            request.getTitle(),
            request.getDescription(),
            urgencia,
            dataHoraFormatada,
            request.getLatitude(),
            request.getLongitude(),
            nomeUsuario,
            new EmailService.EmailCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> 
                        Toast.makeText(RegistarOcorrencia.this, 
                            "Notificação enviada para o Estado", 
                            Toast.LENGTH_SHORT).show()
                    );
                }

                @Override
                public void onError(String error) {
                    Log.e("RegistarOcorrencia", "Erro ao enviar email: " + error);
                    runOnUiThread(() -> 
                        Toast.makeText(RegistarOcorrencia.this, 
                            "Ocorrência salva, mas não foi possível notificar o Estado", 
                            Toast.LENGTH_LONG).show()
                    );
                }
            }
    );
}
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, can use camera
            } else {
                Toast.makeText(this, "Permissão de câmera necessária", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateLocationStatus("✅ Permissão concedida - Obtendo localização...", false);
                Toast.makeText(this, "Permissão de localização concedida", Toast.LENGTH_SHORT).show();
                // Tenta obter a localização novamente
                getLastKnownLocation();
            } else {
                updateLocationStatus("❌ Permissão de localização negada. Ative o GPS para continuar.", false);
                Toast.makeText(this, "Permissão de localização negada. Ative o GPS para continuar.", Toast.LENGTH_LONG).show();
                Log.w("RegistarOcorrencia", "Location permission denied by user");
                // Não definir coordenadas padrão
            }
        }
    }

    // Métodos de LocationListener não são mais necessários
    // pois o LocationHelper agora gerencia a localização
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Limpa os recursos do LocationHelper
        if (locationHelper != null) {
            locationHelper.cleanup();
        }
        
        // Shutdown email service
        if (emailService != null) {
            emailService.shutdown();
        }
        
        // Shutdown executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        Log.d("RegistarOcorrencia", "Activity destroyed, resources cleaned up");
    }
}
