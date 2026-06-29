package com.allermeal.application.port.out.command;

import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;

public record AdminRecollectionRequestCommand(
	UUID recollectionRequestId,
	String idempotencyKey,
	UserId actorUserId,
	CollectionJobId originalCollectionJobId,
	CollectionJobId collectionJobId,
	Instant createdAt
) {
}
