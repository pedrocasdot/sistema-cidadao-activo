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

import ao.co.isptec.aplm.sca.dto.SignupRequest;
import ao.co.isptec.aplm.sca.service.ApiService;
import ao.co.isptec.aplm.sca.service.SessionManager;

public class Registro extends AppCompatActivity {

    private static final String TAG = "TelaRegisto";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MIN_USERNAME_LENGTH = 3;
    
    private EditText etNomeCompleto;
    private EditText etUserName;
    private EditText etSenha;
    private EditText etConfirmarSenha;
    private TextView tvLinkLogin;
    private ApiService apiService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tela_registo);
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
        
        Log.d(TAG, "TelaRegisto initialized successfully");
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
        etNomeCompleto = findViewById(R.id.nomeCompletoRegistar);
        etUserName = findViewById(R.id.userNameRegistar);
        etSenha = findViewById(R.id.senhaRegistar);
        etConfirmarSenha = findViewById(R.id.confirmarSenhaRegistar);
        tvLinkLogin = findViewById(R.id.linkLogin);
    }
    
    private void setupApiService() {
        apiService = new ApiService(this);
        sessionManager = new SessionManager(this);
        Log.d(TAG, "ApiService and SessionManager initialized");
        
        // Check for existing valid session
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "User already logged in, redirecting to main activity");
            navigateToMainActivity();
        }
    }
    
    private void setupUI() {
        tvLinkLogin.setText(Html.fromHtml("<u>Login</u>"));
    }

    /**
     * Volta para a tela de login
     */
    public void irLogin(View view) {
        Log.d(TAG, "Voltando para tela de login");
        finish();
    }

    /**
     * Realiza o processo de registro com validação completa
     */
    public void registrarUtilizador(View view) {
        Log.d(TAG, "Iniciando processo de registro");
        
        String nome = etNomeCompleto.getText().toString().trim();
        String username = etUserName.getText().toString().trim();
        String senhaUsuario = etSenha.getText().toString().trim();
        String confirmarSenhaUsuario = etConfirmarSenha.getText().toString().trim();

        // Validação completa dos campos
        if (!validateRegistrationInputs(nome, username, senhaUsuario, confirmarSenhaUsuario)) {
            return;
        }

        // Processo de registro
        registerUser(nome, username, senhaUsuario);
    }
    
    /**
     * Valida todos os campos de entrada do registro
     */
    private boolean validateRegistrationInputs(String nome, String username, String senha, String confirmarSenha) {
        // Validação do nome completo
        if (TextUtils.isEmpty(nome)) {
            showErrorMessage("Campo Nome Completo é obrigatório");
            etNomeCompleto.requestFocus();
            return false;
        }
        
        if (nome.length() < 2) {
            showErrorMessage("Nome deve ter pelo menos 2 caracteres");
            etNomeCompleto.requestFocus();
            return false;
        }

        // Validação do username
        if (TextUtils.isEmpty(username)) {
            showErrorMessage("Campo Username é obrigatório");
            etUserName.requestFocus();
            return false;
        }
        
        if (username.length() < MIN_USERNAME_LENGTH) {
            showErrorMessage("Username deve ter pelo menos " + MIN_USERNAME_LENGTH + " caracteres");
            etUserName.requestFocus();
            return false;
        }
        
        if (!isValidUsername(username)) {
            showErrorMessage("Username deve conter apenas letras, números e underscore");
            etUserName.requestFocus();
            return false;
        }

        // Validação da senha
        if (TextUtils.isEmpty(senha)) {
            showErrorMessage("Campo Senha é obrigatório");
            etSenha.requestFocus();
            return false;
        }
        
        if (senha.length() < MIN_PASSWORD_LENGTH) {
            showErrorMessage("Senha deve ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres");
            etSenha.requestFocus();
            return false;
        }

        // Validação da confirmação de senha
        if (TextUtils.isEmpty(confirmarSenha)) {
            showErrorMessage("Confirmação de senha é obrigatória");
            etConfirmarSenha.requestFocus();
            return false;
        }

        if (!senha.equals(confirmarSenha)) {
            showErrorMessage("Senhas não coincidem");
            etConfirmarSenha.requestFocus();
            return false;
        }

        return true;
    }
    
    /**
     * Verifica se o username é válido (apenas letras, números e underscore)
     */
    private boolean isValidUsername(String username) {
        return username.matches("^[a-zA-Z0-9_]+$");
    }
    
    /**
     * Registra um novo utilizador via API
     */
    private void registerUser(String nome, String username, String senha) {
        try {
            Log.d(TAG, "Iniciando registro via API para username: " + username);
            
            // Criar request DTO simples conforme nova API
            SignupRequest request = new SignupRequest(nome, username, senha);
            
            // Chamar API de registro (signup)
            apiService.signup(request, new ApiService.ApiCallback<Void>() {
                @Override
                public void onSuccess(Void ignored) {
                    runOnUiThread(() -> handleSuccessfulRegistration(username, nome));
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Erro no registro: " + error);
                        if (error != null && (error.contains("already") || error.contains("existe") || error.contains("409"))) {
                            handleUsernameAlreadyExists();
                        } else {
                            handleRegistrationError(error);
                        }
                    });
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Erro durante registro", e);
            showErrorMessage("Erro interno. Tente novamente.");
        }
    }
    
    /**
     * Processa username já existente
     */
    private void handleUsernameAlreadyExists() {
        Log.w(TAG, "Tentativa de registro com username existente");
        showErrorMessage("Username já existe. Escolha outro.");
        etUserName.requestFocus();
    }
    
    /**
     * Processa registro bem-sucedido via API
     */
    private void handleSuccessfulRegistration(String username, String nome) {
        Log.d(TAG, "Registro bem-sucedido para utilizador: " + username);
        
        // Limpar todos os campos
        clearAllFields();
        
        // Mostrar mensagem de sucesso
        showSuccessMessage("Utilizador registrado com sucesso! Bem-vindo, " + nome + "!");
        
        // Voltar para tela de login após delay
        etNomeCompleto.postDelayed(() -> {
            finish();
        }, 2000);
    }
    
    /**
     * Processa erro no registro via API
     */
    private void handleRegistrationError(String error) {
        Log.e(TAG, "Erro ao registrar utilizador: " + error);
        showErrorMessage("Erro ao registrar utilizador: " + error);
    }
    
    /**
     * Limpa todos os campos do formulário
     */
    private void clearAllFields() {
        etNomeCompleto.setText("");
        etUserName.setText("");
        etSenha.setText("");
        etConfirmarSenha.setText("");
        etNomeCompleto.requestFocus();
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    /**
     * Navega para a atividade principal (mapa de ocorrências)
     */
    private void navigateToMainActivity() {
        try {
            Intent intent = new Intent(this, TelaMapaOcorrencias.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Log.d(TAG, "Navegando para TelaMapaOcorrencias");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao navegar para atividade principal", e);
        }
    }
}