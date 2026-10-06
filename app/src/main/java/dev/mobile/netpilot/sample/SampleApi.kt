package dev.mobile.netpilot.sample

import android.content.Context
import dev.mobile.netpilot.NetPilotInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

private interface JsonPlaceholderService {
    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: Int): Response<String>

    @GET("posts/{id}")
    suspend fun getPostWithAuth(@Path("id") id: Int, @Header("Authorization") token: String): Response<String>

    @POST("posts")
    suspend fun createPost(@Body body: RequestBody): Response<String>
}

/**
 * Demo calls that exercise NetPilot. Retrofit and Ktor share one OkHttpClient, so a
 * single interceptor captures traffic from both.
 */
class SampleApi(context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetPilotInterceptor(context))
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(JSON_PLACEHOLDER_URL)
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(JsonPlaceholderService::class.java)

    private val ktor = HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
    }

    suspend fun retrofitGet(): String = retrofit.getPost(1).describe()

    suspend fun retrofitPost(): String {
        val json = """{"title":"NetPilot","body":"Hello from the sample app","userId":1}"""
        return retrofit.createPost(json.toRequestBody(JSON)).describe()
    }

    suspend fun retrofitNotFound(): String = retrofit.getPost(NON_EXISTENT_POST_ID).describe()

    suspend fun retrofitWithAuthHeader(): String = retrofit.getPostWithAuth(1, "Bearer demo-token").describe()

    suspend fun ktorGet(): String = ktor.get("$HTTPBIN_URL/get?source=ktor").describe()

    suspend fun ktorServerError(): String = ktor.get("$HTTPBIN_URL/status/500").describe()

    suspend fun ktorUnknownHost(): String = ktor.get("https://netpilot-sample.invalid/").describe()

    private fun Response<String>.describe(): String = "${code()} ${message()}".trim()

    private fun HttpResponse.describe(): String = status.toString()

    private companion object {
        const val JSON_PLACEHOLDER_URL = "https://jsonplaceholder.typicode.com/"
        const val HTTPBIN_URL = "https://httpbin.org"
        const val NON_EXISTENT_POST_ID = 99_999
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
