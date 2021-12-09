package pro.glideim.sdk.api.auth;

import io.reactivex.Observable;
import pro.glideim.sdk.api.Response;
import pro.glideim.sdk.http.RetrofitManager;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface UserApi {

    UserApi API = RetrofitManager.create(UserApi.class);

    @POST("auth/register")
    Observable<Response<Object>> register(@Body RegisterDto d);

    @POST("auth/signin")
    Observable<Response<TokenBean>> login(@Body LoginDto d);

    @POST("auth/logout")
    Observable<Response<Object>> logout();
}
