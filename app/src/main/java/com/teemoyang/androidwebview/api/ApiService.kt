package com.teemoyang.androidwebview.api

import com.teemoyang.androidwebview.model.LoginRequest
import com.teemoyang.androidwebview.model.LoginResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    
    @POST("appCenter/mobile/user/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>
    
} 