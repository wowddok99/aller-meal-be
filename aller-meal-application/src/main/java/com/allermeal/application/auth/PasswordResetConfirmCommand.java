package com.allermeal.application.auth;

public record PasswordResetConfirmCommand(String token, String password) {
}
