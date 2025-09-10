package ao.co.isptec.aplm.sca.service;

import ao.co.isptec.aplm.sca.dto.LoginRequest;
import ao.co.isptec.aplm.sca.dto.LoginResponse;
import ao.co.isptec.aplm.sca.dto.SignupRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth/signup")
    Call<Void> signup(@Body SignupRequest request);
}
