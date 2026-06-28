package com.allermeal.application.port.out.command;

public record PasswordResetMailCommand(String email, String token) {
}
