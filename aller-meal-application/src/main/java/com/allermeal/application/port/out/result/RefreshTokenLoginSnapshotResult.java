package com.allermeal.application.port.out.result;

import java.time.Instant;

public record RefreshTokenLoginSnapshotResult(Instant startedAt, String generation, String revokedAfter) {
}
