package ao.co.isptec.aplm.sca.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.tasks.Task;

import com.google.android.gms.maps.model.LatLng;

import java.util.concurrent.TimeUnit;

/**
 * Classe auxiliar para gerenciar a localização do dispositivo de forma eficiente.
 * Fornece métodos para obter a localização atual com diferentes níveis de precisão.
 */
public class LocationHelper {
    private static final String TAG = "LocationHelper";
    private static final long LOCATION_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(5);
    private static final long LOCATION_FASTEST_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(2);
    private static final long LOCATION_MAX_WAIT_TIME = TimeUnit.SECONDS.toMillis(15);
    private static final float MIN_ACCURACY_METERS = 50.0f; // 50 metros de precisão mínima
    public static final int REQUEST_CHECK_SETTINGS = 1001;
    
    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationResultListener locationResultListener;
    private boolean isLocationRequested = false;
    
    public interface LocationResultListener {
        void onLocationSuccess(LatLng location, float accuracy);
        void onLocationError(String errorMessage);
    }
    
    public LocationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }
    
    /**
     * Verifica se as permissões de localização foram concedidas
     */
    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Verifica se o GPS está ativado
     */
    public boolean isGpsEnabled() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
    
    /**
     * Tenta resolver as configurações de localização usando a API do Google (SettingsClient)
     * para garantir alta precisão quando necessário.
     */
    public void ensureLocationSettings(Activity activity, boolean highAccuracy, Runnable onReady, java.util.function.Consumer<String> onError) {
        LocationRequest settingsRequest = new LocationRequest.Builder(
                highAccuracy ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                LOCATION_UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_UPDATE_INTERVAL)
            .build();

        LocationSettingsRequest request = new LocationSettingsRequest.Builder()
                .addLocationRequest(settingsRequest)
                .setAlwaysShow(true)
                .build();

        SettingsClient client = LocationServices.getSettingsClient(activity);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(request);

        task.addOnSuccessListener(response -> onReady.run())
            .addOnFailureListener(e -> {
                if (e instanceof ResolvableApiException) {
                    try {
                        ((ResolvableApiException) e).startResolutionForResult(activity, REQUEST_CHECK_SETTINGS);
                    } catch (Exception sendEx) {
                        Log.e(TAG, "Erro ao solicitar resolução de configurações de localização", sendEx);
                        onError.accept("Falha ao abrir configurações de localização");
                    }
                } else {
                    Log.w(TAG, "Configurações de localização não satisfatórias e não resolúveis: " + e.getMessage());
                    onError.accept("Configurações de localização não satisfatórias");
                }
            });
    }
    
    /**
     * Obtém a localização atual do dispositivo
     * @param activity A atividade que está solicitando a localização
     * @param highAccuracy Se true, tenta obter uma localização de alta precisão
     * @param listener Listener para receber os resultados
     */
    public void getCurrentLocation(Activity activity, boolean highAccuracy, LocationResultListener listener) {
        this.locationResultListener = listener;
        
        // Verifica permissões
        if (!hasLocationPermission()) {
            listener.onLocationError("Permissão de localização não concedida");
            return;
        }
        
        // Garante que as configurações de localização estejam adequadas (alta precisão quando solicitado)
        ensureLocationSettings(activity, highAccuracy, () -> {
            // Previne múltiplas solicitações simultâneas
            if (isLocationRequested) {
                Log.d(TAG, "Location request already in progress");
                return;
            }
            isLocationRequested = true;
            
            // Configura o callback de localização
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult == null) {
                        cleanup();
                        listener.onLocationError("Não foi possível obter a localização");
                        return;
                    }
                    
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                        
                        // Se a precisão for boa o suficiente ou não estivermos em alta precisão, retorna
                        if (!highAccuracy || location.getAccuracy() <= MIN_ACCURACY_METERS) {
                            cleanup();
                            listener.onLocationSuccess(latLng, location.getAccuracy());
                        }
                    }
                }
            };
            
            // Configura a solicitação de localização
            LocationRequest locationRequest = new LocationRequest.Builder(
                    highAccuracy ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    (long) LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_UPDATE_INTERVAL)
                .setMaxUpdates(highAccuracy ? 5 : 3) // Número máximo de atualizações
                .setWaitForAccurateLocation(highAccuracy) // Aguarda uma localização precisa se true
                .build();
            
            // Solicita atualizações de localização
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                );
                
                // Configura um timeout para cancelar a solicitação
                new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isLocationRequested) {
                        Log.w(TAG, "Location request timed out");
                        cleanup();
                        // Tenta obter a última localização conhecida
                        getLastKnownLocation(listener);
                    }
                }, LOCATION_MAX_WAIT_TIME);
                
            } catch (SecurityException e) {
                Log.e(TAG, "Erro ao solicitar atualizações de localização", e);
                cleanup();
                listener.onLocationError("Erro ao acessar a localização: " + e.getMessage());
            }
        }, errorMsg -> {
            // Caso não seja possível resolver automaticamente, mostra diálogo simples como fallback
            new AlertDialog.Builder(activity)
                .setTitle("Ativar Localização")
                .setMessage("Precisamos da sua localização para uma melhor precisão. Deseja ativá-la agora?")
                .setPositiveButton("Sim", (dialog, which) -> activity.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Não", (dialog, which) -> {
                    if (locationResultListener != null) {
                        locationResultListener.onLocationError("Localização desativada pelo usuário");
                    }
                })
                .setCancelable(false)
                .show();
        });
    }
    
    /**
     * Obtém a última localização conhecida
     */
    public void getLastKnownLocation(LocationResultListener listener) {
        if (!hasLocationPermission()) {
            listener.onLocationError("Permissão de localização não concedida");
            return;
        }
        
        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    listener.onLocationSuccess(latLng, location.getAccuracy());
                } else {
                    listener.onLocationError("Nenhuma localização conhecida");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Erro ao obter última localização conhecida", e);
                listener.onLocationError("Erro ao obter localização: " + e.getMessage());
            });
    }
    
    /**
     * Limpa os recursos de localização
     */
    public void cleanup() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
        isLocationRequested = false;
    }
    
    /**
     * Deve ser chamado quando a atividade for destruída
     */
    public void onDestroy() {
        cleanup();
    }
}
