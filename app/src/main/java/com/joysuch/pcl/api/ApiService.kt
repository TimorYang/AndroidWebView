package com.joysuch.pcl.api

import com.joysuch.pcl.model.LoginRequest
import com.joysuch.pcl.model.LoginResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    
    @POST("appCenter/mobile/user/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>
    
} 