package ao.co.isptec.aplm.sca.service;

import java.util.List;

import ao.co.isptec.aplm.sca.dto.IncidentResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public interface IncidentApi {
    @GET("incidents")
    Call<List<IncidentResponse>> getAllIncidents();

    @GET("incidents/user/{userId}")
    Call<List<IncidentResponse>> getIncidentsByUser(@Path("userId") long userId);

    @GET("incidents/{id}")
    Call<IncidentResponse> getIncidentById(@Path("id") long id);

    @Multipart
    @POST("incidents")
    Call<IncidentResponse> reportIncident(
        @Part("incident") RequestBody incidentJson,
        @Part MultipartBody.Part photo,
        @Part MultipartBody.Part video
    );
}
