package com.allermeal.application.port.out.command;

public record EmailVerificationMailCommand(String email, String token) {
}
