package ao.co.isptec.aplm.sca;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ao.co.isptec.aplm.sca.base.BaseP2PActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import ao.co.isptec.aplm.sca.model.Ocorrencia;
import ao.co.isptec.aplm.sca.dto.IncidentResponse;
import ao.co.isptec.aplm.sca.service.ApiService;
import ao.co.isptec.aplm.sca.service.SessionManager;

// Offline-first imports
import ao.co.isptec.aplm.sca.database.repository.OcorrenciaRepository;
import ao.co.isptec.aplm.sca.database.entity.OcorrenciaEntity;
import ao.co.isptec.aplm.sca.offline.OfflineFirstHelper;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import ao.co.isptec.aplm.sca.sync.SyncManager;
import ao.co.isptec.aplm.sca.ui.SyncStatusView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListarOcorrencias extends BaseP2PActivity {

    private static final String TAG = "ListarOcorrencias";
    
    private RecyclerView recyclerViewOcorrencias;
    private ListaOcorrenciasAdapter adapter;
    private FloatingActionButton fabNovaOcorrencia;
    
    private ApiService apiService;
    private SessionManager sessionManager;
    private String currentUsername;
    private List<Ocorrencia> listaOcorrencias;
    
    // Offline-first components
    private OcorrenciaRepository repository;
    // offlineHelper is inherited from BaseP2PActivity
    private SyncManager syncManager;
    private ExecutorService executorService;
    private SyncStatusView syncStatusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listar_ocorrencias);

        // Setup toolbar as ActionBar and enable Up
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        initializeViews();
        setupApiService();
        setupRecyclerView();
        loadOcorrencias();
    }

    private void initializeViews() {
        recyclerViewOcorrencias = findViewById(R.id.recyclerViewOcorrencias);
        fabNovaOcorrencia = findViewById(R.id.fabNovaOcorrencia);
        
        fabNovaOcorrencia.setOnClickListener(v -> {
            Intent intent = new Intent(ListarOcorrencias.this, RegistarOcorrencia.class);
            startActivity(intent);
        });
    }

    private void setupApiService() {
        apiService = new ApiService(this);
        sessionManager = new SessionManager(this);
        
        // Initialize offline-first components
        repository = new OcorrenciaRepository(this);
        // offlineHelper is initialized in BaseP2PActivity
        syncManager = SyncManager.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        
        // Add sync status view
        syncStatusView = new SyncStatusView(this);
        // TODO: Add syncStatusView to layout programmatically or via XML

        // Check if user is authenticated
        if (!sessionManager.isLoggedIn()) {
            Log.w(TAG, "User not authenticated, redirecting to login");
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_SHORT).show();
            
            Intent intent = new Intent(this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        currentUsername = sessionManager.getUsername();
        Log.d(TAG, "API service initialized for user: " + currentUsername);
    }

    private void setupRecyclerView() {
        listaOcorrencias = new ArrayList<>();
        adapter = new ListaOcorrenciasAdapter(listaOcorrencias, this::onOcorrenciaClick);
        recyclerViewOcorrencias.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewOcorrencias.setAdapter(adapter);
    }

    private void loadOcorrencias() {
        if (currentUsername == null) {
            Toast.makeText(this, "Erro: Usuário não encontrado", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Cannot load occurrences - no user logged in");
            return;
        }

        Log.d(TAG, "Loading occurrences from local database for user: " + currentUsername);
        
        // Load occurrences from local database (offline-first)
        loadOcorrenciasFromLocal();
    }
    
    /**
     * Load occurrences from local database
     */
    private void loadOcorrenciasFromLocal() {
        offlineHelper.getAllOcorrenciasAsync((Consumer<List<OcorrenciaEntity>>) entities -> {
            listaOcorrencias.clear();
            for (OcorrenciaEntity entity : entities) {
                Ocorrencia ocorrencia = repository.entityToModel(entity);
                listaOcorrencias.add(ocorrencia);
            }
            adapter.notifyDataSetChanged();
            Log.d(TAG, "Loaded " + listaOcorrencias.size() + " occurrences from local database");
            if (listaOcorrencias.isEmpty()) {
                Toast.makeText(ListarOcorrencias.this, "Nenhuma ocorrência encontrada", Toast.LENGTH_SHORT).show();
            } else {
                String message = "Carregadas " + listaOcorrencias.size() + " ocorrências";
                Toast.makeText(ListarOcorrencias.this, message, Toast.LENGTH_SHORT).show();
                showSyncStatus();
            }
        });
    }
    
    /**
     * Show sync status to user
     */
    private void showSyncStatus() {
        int isNetwork = syncManager.isNetworkAvailable() ? 1 : 0;
        offlineHelper.getUnsyncedCountAsync((IntConsumer) unsyncedCount -> {
            
        });
    }
    
    /**
     * Convert IncidentResponse DTO to Ocorrencia model object
     */
    private Ocorrencia convertResponseToOcorrencia(IncidentResponse response) {
        return ApiService.mapToOcorrencia(response);
    }

    private void onOcorrenciaClick(Ocorrencia ocorrencia) {
        Intent intent = new Intent(ListarOcorrencias.this, VisualizarOcorrencia.class);
        intent.putExtra("ocorrencia", ocorrencia);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOcorrencias(); // Reload when returning from other activities
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        Log.d(TAG, "Activity destroyed, resources cleaned up");
    }
    
    @Override
    protected void onP2PStatusUpdated(String status, boolean isEnabled) {
        super.onP2PStatusUpdated(status, isEnabled);
        // Show P2P status in list activity
        if (isEnabled) {
            Toast.makeText(this, "Wi-Fi Direct ativado - Pronto para receber ocorrências", Toast.LENGTH_SHORT).show();
        }
    }
}
