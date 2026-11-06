package com.allermeal.application.port.out.result;

import java.time.Instant;

public record RefreshOperationReservationResult(String status, String familyId, Instant expiresAt) {
}
