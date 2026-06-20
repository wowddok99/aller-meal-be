package com.allermeal.api.auth.request;

public record PasswordResetConfirmRequest(String token, String password) {
}
