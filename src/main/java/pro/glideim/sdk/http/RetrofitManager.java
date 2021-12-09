package pro.glideim.sdk.http;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

public class RetrofitManager {
    private Retrofit retrofit;
    private OkHttpClient httpClient;
    private Gson gson;

    private static RetrofitManager sInstance;

    public static <T> T create(Class<T> c) {
        return sInstance.retrofit.create(c);
    }

    public static String toJson(Object obj) {
        return sInstance.gson.toJson(obj);
    }

    public static <T> T fromJson(Type t, String json) throws JsonSyntaxException {
        return sInstance.gson.fromJson(json, t);
    }

    public static void init(String baseUrl) {

        RetrofitManager m = new RetrofitManager();
//        File cacheDir = context.getExternalCacheDir();
        m.gson = new GsonBuilder()
                .setLenient()
                .setFieldNamingStrategy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .serializeNulls()
                .create();

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .hostnameVerifier((hostname, session) -> true);

//        if (cacheDir != null) {
//            httpClient.cache(new Cache(cacheDir, 1024 * 1024 * 10));
//        }
        m.httpClient = httpClient.build();
        m.retrofit = new Retrofit.Builder()
                .client(m.httpClient)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(m.gson))
                .baseUrl(baseUrl)
                .build();
        sInstance = m;
    }

    public static WebSocket newWebSocket(String url, WebSocketListener l) {
        Request request = new Request.Builder().get().url(url).build();
        return sInstance.httpClient.newWebSocket(request, l);
    }
}
