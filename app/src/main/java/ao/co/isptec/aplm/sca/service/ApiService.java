package ao.co.isptec.aplm.sca.service;

import android.content.Context;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ao.co.isptec.aplm.sca.dto.IncidentResponse;
import ao.co.isptec.aplm.sca.dto.LoginRequest;
import ao.co.isptec.aplm.sca.dto.LoginResponse;
import ao.co.isptec.aplm.sca.dto.NewIncidentRequest;
import ao.co.isptec.aplm.sca.dto.SignupRequest;
import ao.co.isptec.aplm.sca.model.Ocorrencia;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiService {
    private static final String TAG = "ApiService";

    private final SessionManager sessionManager;
    private final AuthApi authApi;
    private final IncidentApi incidentApi;

    public ApiService(Context context) {
        this.sessionManager = new SessionManager(context.getApplicationContext());
        this.authApi = ApiClient.getClient().create(AuthApi.class);
        this.incidentApi = ApiClient.getClient().create(IncidentApi.class);
    }

    // Signup
    public void signup(SignupRequest request, ApiCallback<Void> callback) {
        authApi.signup(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError("Falha no registro: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "signup error", t);
                callback.onError("Falha de rede: " + t.getMessage());
            }
        });
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public interface ApiCallback<T> {
        void onSuccess(T response);
        void onError(String error);
    }

    // Auth
    public void login(LoginRequest request, ApiCallback<LoginResponse> callback) {
        authApi.login(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse body = response.body();
                    // Save session locally
                    sessionManager.createLoginSession(body.getUsername(), body.getFullName());
                    sessionManager.setUserId(body.getId());
                    callback.onSuccess(body);
                } else {
                    callback.onError("Credenciais inválidas");
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Log.e(TAG, "login error", t);
                callback.onError("Falha de rede: " + t.getMessage());
            }
        });
    }

    public void getMyOccurrences(ApiCallback<List<IncidentResponse>> callback) {
        // For now, if we don't have a userId stored, fetch all incidents
        // The backend provides /incidents and /incidents/user/{userId}
        incidentApi.getAllIncidents().enqueue(new Callback<List<IncidentResponse>>() {
            @Override
            public void onResponse(Call<List<IncidentResponse>> call, Response<List<IncidentResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Erro ao carregar incidentes");
                }
            }

            @Override
            public void onFailure(Call<List<IncidentResponse>> call, Throwable t) {
                Log.e(TAG, "getMyOccurrences error", t);
                callback.onError("Falha de rede: " + t.getMessage());
            }
        });
    }

    // Create incident with optional photo/video
    public void createOccurrence(NewIncidentRequest request,
                                 java.io.File photoFile,
                                 java.io.File videoFile,
                                 ApiCallback<IncidentResponse> callback) {
        try {
            // Serialize request as JSON string body named "incident"
            String incidentJson = request.toJson();
            RequestBody incidentBody = RequestBody.create(MediaType.parse("application/json"), incidentJson);

            MultipartBody.Part photoPart = null;
            if (photoFile != null && photoFile.exists()) {
                RequestBody photoBody = RequestBody.create(MediaType.parse("image/*"), photoFile);
                photoPart = MultipartBody.Part.createFormData("photo", photoFile.getName(), photoBody);
            }

            MultipartBody.Part videoPart = null;
            if (videoFile != null && videoFile.exists()) {
                RequestBody videoBody = RequestBody.create(MediaType.parse("video/*"), videoFile);
                videoPart = MultipartBody.Part.createFormData("video", videoFile.getName(), videoBody);
            }

            incidentApi.reportIncident(incidentBody, photoPart, videoPart).enqueue(new Callback<IncidentResponse>() {
                @Override
                public void onResponse(Call<IncidentResponse> call, Response<IncidentResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onSuccess(response.body());
                    } else {
                        callback.onError("Erro ao criar incidente");
                    }
                }

                @Override
                public void onFailure(Call<IncidentResponse> call, Throwable t) {
                    Log.e(TAG, "createOccurrence error", t);
                    callback.onError("Falha de rede: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "createOccurrence exception", e);
            callback.onError("Erro interno: " + e.getMessage());
        }
    }

    // Helper to convert IncidentResponse to Ocorrencia for UI if needed
    public static Ocorrencia mapToOcorrencia(IncidentResponse response) {
        Ocorrencia o = new Ocorrencia();
        if (response.getId() != null) o.setId(response.getId().intValue());
        o.setDescricao(response.getDescription());
        if (response.getLatitude() != null) o.setLatitude(response.getLatitude());
        if (response.getLongitude() != null) o.setLongitude(response.getLongitude());

        // Parse datetime string (ISO or standard) to Date
        Date date = parseDate(response.getDatetime());
        if (date != null) {
            o.setDataHora(date);
        } else {
            o.setDataHora(new Date());
        }

        // Map urgency boolean from API
        Boolean urgency = response.getUrgency();
        boolean urgente = urgency != null && urgency;
        o.setUrgente(urgente);

        // Media
        if (response.getDbPhotoFilename() != null) {
            o.setFotoPath(buildFileUrl(response.getDbPhotoFilename()));
        }
        if (response.getDbVideoFilename() != null) {
            o.setVideoPath(buildFileUrl(response.getDbVideoFilename()));
        }

        return o;
    }

    private static Date parseDate(String s) {
        if (s == null) return null;
        // Try multiple formats: ISO_LOCAL_DATE_TIME or with Z
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                return new SimpleDateFormat(p, Locale.getDefault()).parse(s);
            } catch (ParseException ignored) { }
        }
        return null;
    }

    private static String buildFileUrl(String filename) {
        return "http://192.168.100.100:8081/incidents/files/" + filename;
    }
}
