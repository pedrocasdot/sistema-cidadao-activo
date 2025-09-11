package ao.co.isptec.aplm.sca;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import ao.co.isptec.aplm.sca.base.BaseP2PActivity;

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

import java.util.ArrayList;
import java.util.List;

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

public class TelaMapaOcorrencias extends BaseP2PActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tela_mapa_ocorrencias);

        initializeViews();
        setupApiServices();
        setupMap();
        setupButtons();
        
        locationHelper = new LocationHelper(this);
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
        // offlineHelper is initialized in BaseP2PActivity
        
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



    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup location helper
        if (locationHelper != null) {
            locationHelper.onDestroy();
        }
        
        // Cleanup executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        Log.d(TAG, "Activity destroyed, resources cleaned up");
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
    
    @Override
    protected void onP2PStatusUpdated(String status, boolean isEnabled) {
        super.onP2PStatusUpdated(status, isEnabled);
        // Show P2P status on map activity
        if (isEnabled) {
            Toast.makeText(this, "Wi-Fi Direct ativado - Pronto para receber ocorrências", Toast.LENGTH_SHORT).show();
        }
    }
}
