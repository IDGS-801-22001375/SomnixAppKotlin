package com.example.somnixapp.network

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object PythonApiProvider {

    private const val BASE_URL =
        "https://monitoreosomnixpython.onrender.com/"

    /*
     * Dispatcher exclusivo para comandos:
     * iniciar, pausar, reanudar, terminar y apagar.
     */
    private val controlDispatcher =
        Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 4
        }

    /*
     * Dispatcher separado para imágenes.
     * Una imagen lenta nunca ocupará el cliente de control.
     */
    private val frameDispatcher =
        Dispatcher().apply {
            maxRequests = 2
            maxRequestsPerHost = 2
        }

    private val controlClient =
        OkHttpClient.Builder()
            .dispatcher(controlDispatcher)
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                25,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                20,
                TimeUnit.SECONDS
            )
            .callTimeout(
                30,
                TimeUnit.SECONDS
            )
            .retryOnConnectionFailure(true)
            .build()

    private val frameClient =
        OkHttpClient.Builder()
            .dispatcher(frameDispatcher)
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                45,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                30,
                TimeUnit.SECONDS
            )
            .callTimeout(
                50,
                TimeUnit.SECONDS
            )
            .retryOnConnectionFailure(true)
            .build()

    val controlApi: PythonApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(controlClient)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(PythonApiService::class.java)
    }

    val frameApi: PythonApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(frameClient)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(PythonApiService::class.java)
    }
}