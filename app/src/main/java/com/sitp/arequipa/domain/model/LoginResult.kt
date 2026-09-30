package com.sitp.arequipa.domain.model

sealed class LoginResult {
    object Exito : LoginResult()
    object EmailNoVerificado : LoginResult()
}