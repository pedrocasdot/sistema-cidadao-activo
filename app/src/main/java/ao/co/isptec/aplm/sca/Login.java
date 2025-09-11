package ao.co.isptec.aplm.sca;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import ao.co.isptec.aplm.sca.dto.LoginRequest;
import ao.co.isptec.aplm.sca.dto.LoginResponse;
import ao.co.isptec.aplm.sca.dto.IncidentResponse;
import ao.co.isptec.aplm.sca.service.ApiService;
import ao.co.isptec.aplm.sca.service.SessionManager;
import ao.co.isptec.aplm.sca.offline.OfflineFirstHelper;
import java.util.List;

public class Login extends AppCompatActivity {

    private static final String TAG = "TelaLogin";
    
    private EditText etSenha;
    private EditText etUserName;
    private TextView tvLinkRegistrar;
    private ApiService apiService;
    private SessionManager sessionManager;
    private OfflineFirstHelper offlineHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tela_login);
        // Setup toolbar as ActionBar and enable back (Up)
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }
        
        setupWindowInsets();
        initializeViews();
        setupApiService();
        setupUI();
        checkExistingSession();
        
        Log.d(TAG, "TelaLogin initialized successfully");
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    
    private void initializeViews() {
        etSenha = findViewById(R.id.senhaLogin);
        etUserName = findViewById(R.id.userNameLogin);
        tvLinkRegistrar = findViewById(R.id.linkRegistar);
    }
    
    private void setupApiService() {
        apiService = new ApiService(this);
        sessionManager = apiService.getSessionManager();
        offlineHelper = new OfflineFirstHelper(this);
        Log.d(TAG, "ApiService initialized");
    }
    
    private void checkExistingSession() {
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "User already logged in, redirecting to main activity");
            navigateToMainActivity();
        }
    }
    
    private void setupUI() {
        tvLinkRegistrar.setText(Html.fromHtml("<u>Registe-se</u>"));
    }

    /**
     * Navega para a tela de registro
     */
    public void irRegistrar(View view) {
        Log.d(TAG, "Navegando para tela de registro");
        Intent intent = new Intent(this, Registro.class);
        startActivity(intent);
    }

    /**
     * Navega diretamente para o mapa (método de desenvolvimento)
     */
    public void irUsuario(View view) {
        Log.d(TAG, "Navegando diretamente para o mapa");
        Intent intent = new Intent(this, TelaMapaOcorrencias.class);
        startActivity(intent);
    }

    /**
     * Realiza o processo de login com validação e autenticação
     */
    public void fazerLogin(View view) {
        Log.d(TAG, "Iniciando processo de login");
        
        String userNameLogin = etUserName.getText().toString().trim();
        String senhaLogin = etSenha.getText().toString().trim();

        // Validação dos campos de entrada
        if (!validateLoginInputs(userNameLogin, senhaLogin)) {
            return;
        }

        // Autenticação usando API
        authenticateUser(userNameLogin, senhaLogin);
    }
    
    /**
     * Valida os campos de entrada do login
     */
    private boolean validateLoginInputs(String username, String senha) {
        if (TextUtils.isEmpty(username)) {
            showErrorMessage("Campo Username é obrigatório");
            etUserName.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(senha)) {
            showErrorMessage("Campo Senha é obrigatório");
            etSenha.requestFocus();
            return false;
        }
        
        if (username.length() < 3) {
            showErrorMessage("Username deve ter pelo menos 3 caracteres");
            etUserName.requestFocus();
            return false;
        }

        return true;
    }
    
    /**
     * Autentica o utilizador usando API
     */
    private void authenticateUser(String username, String senha) {
        try {
            // Get device info for login request
            String deviceInfo = android.os.Build.MODEL + " (" + android.os.Build.VERSION.RELEASE + ")";
            String ipAddress = "127.0.0.1"; // Will be determined by server
            
            LoginRequest loginRequest = new LoginRequest(username, senha, deviceInfo, ipAddress);
            
            apiService.login(loginRequest, new ApiService.ApiCallback<LoginResponse>() {
                @Override
                public void onSuccess(LoginResponse loginResponse) {
                    runOnUiThread(() -> handleSuccessfulLogin(loginResponse));
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> handleFailedLogin(error));
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante autenticação", e);
            showErrorMessage("Erro interno. Tente novamente.");
        }
    }
    
    /**
     * Processa login bem-sucedido
     */
    private void handleSuccessfulLogin(LoginResponse loginResponse) {
        Log.d(TAG, "Login bem-sucedido para utilizador: " + loginResponse.getUsername());
        
        // Session is already saved by ApiService, just show success message
        showSuccessMessage("Bem-vindo, " + loginResponse.getFullName() + "!");
        
        // Sincronizar dados do usuário do servidor para a Room database
        syncUserDataFromServer(loginResponse.getId());
    }
    
    /**
     * Processa login falhado
     */
    private void handleFailedLogin(String error) {
        Log.w(TAG, "Tentativa de login falhada: " + error);
        showErrorMessage(error != null ? error : "Credenciais inválidas. Verifique username e senha.");
        
        // Limpar campo de senha por segurança
        etSenha.setText("");
        etUserName.requestFocus();
    }
    
    /**
     * Navega para a tela principal da aplicação
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(Login.this, TelaMapaOcorrencias.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Mostra mensagem de erro padronizada
     */
    private void showErrorMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Mostra mensagem de sucesso padronizada
     */
    private void showSuccessMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Sincroniza dados do usuário do servidor para a Room database
     */
    private void syncUserDataFromServer(long userId) {
        Log.d(TAG, "Iniciando sincronização de dados do usuário: " + userId);
        
        try {
            // Buscar ocorrências do usuário no servidor
            apiService.getIncidentsByUser(userId, new ApiService.ApiCallback<List<IncidentResponse>>() {
                @Override
                public void onSuccess(List<IncidentResponse> incidents) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "Recebidas " + incidents.size() + " ocorrências do servidor");
                        
                        // Salvar cada ocorrência na Room database
                        for (IncidentResponse incident : incidents) {
                            try {
                                // Converter IncidentResponse para Ocorrencia
                                ao.co.isptec.aplm.sca.model.Ocorrencia ocorrencia = convertIncidentResponseToOcorrencia(incident);
                                
                                // Salvar usando OfflineFirstHelper (que salva na Room)
                                offlineHelper.saveReceivedSharedOcorrenciaAsync(
                                    ocorrencia.getDescricao(),
                                    ocorrencia.getLocalizacaoSimbolica(),
                                    ocorrencia.getLatitude(),
                                    ocorrencia.getLongitude(),
                                    ocorrencia.isUrgente(),
                                    ocorrencia.getFotoPath(),
                                    ocorrencia.getVideoPath(),
                                    localId -> Log.d(TAG, "Ocorrência sincronizada: " + incident.getDescription()),
                                    error -> Log.e(TAG, "Erro ao sincronizar ocorrência: " + error)
                                );
                            } catch (Exception e) {
                                Log.e(TAG, "Erro ao converter ocorrência: " + e.getMessage());
                            }
                        }
                        
                        // Após sincronização, navegar para tela principal
                        navigateToMainActivity();
                        
                        if (incidents.size() > 0) {
                            Toast.makeText(Login.this, 
                                "Sincronizadas " + incidents.size() + " ocorrências", 
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.w(TAG, "Erro ao sincronizar dados do usuário: " + error);
                        // Mesmo com erro na sincronização, permitir login
                        navigateToMainActivity();
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Erro durante sincronização", e);
            // Em caso de erro, continuar com o login
            navigateToMainActivity();
        }
    }
    
    /**
     * Converte IncidentResponse do servidor para Ocorrencia local
     */
    private ao.co.isptec.aplm.sca.model.Ocorrencia convertIncidentResponseToOcorrencia(IncidentResponse incident) {
        ao.co.isptec.aplm.sca.model.Ocorrencia ocorrencia = new ao.co.isptec.aplm.sca.model.Ocorrencia();
        
        // Mapear campos do servidor para modelo local
        ocorrencia.setId(incident.getId().intValue()); // Convert Long to int
        String description = "";
        if (incident.getTitle() != null) {
            description += incident.getTitle();
        }
        if (incident.getDescription() != null) {
            if (!description.isEmpty()) description += "\n";
            description += incident.getDescription();
        }
        ocorrencia.setDescricao(description);
        ocorrencia.setLocalizacaoSimbolica(""); // No location field in IncidentResponse
        ocorrencia.setLatitude(incident.getLatitude());
        ocorrencia.setLongitude(incident.getLongitude());
        
        // Parse datetime string to Date
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            ocorrencia.setDataHora(sdf.parse(incident.getDatetime()));
        } catch (Exception e) {
            ocorrencia.setDataHora(new java.util.Date()); // Fallback to current date
        }
        
        ocorrencia.setUrgente(incident.getUrgency());
        ocorrencia.setContadorPartilha(0); // Resetar contador local
        
        // Paths de mídia (se disponíveis)
        if (incident.getDbPhotoFilename() != null && !incident.getDbPhotoFilename().isEmpty()) {
            ocorrencia.setFotoPath(incident.getDbPhotoFilename());
        }
        if (incident.getDbVideoFilename() != null && !incident.getDbVideoFilename().isEmpty()) {
            ocorrencia.setVideoPath(incident.getDbVideoFilename());
        }
        
        return ocorrencia;
    }

}