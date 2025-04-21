package com.teemoyang.androidwebview.model

data class LoginRequest(
    val role: String,
    val userName: String? = null,
    val password: String? = null,
    val nickName: String? = null,
    val idCard: String? = null
)

data class LoginResponse(
    val errorCode: String,
    val errorMsg: List<String>,
    val data: UserData?,
    val valid: Boolean,
    val success: Boolean
)

data class UserData(
    val userName: String?,
    val nickName: String?,
    val phone: String?,
    val buildingId: String?,
    val role: String,
    val loginTime: String,
    val permissionId: String,
    val deviceId: String,
    val userType: String? = null
) 