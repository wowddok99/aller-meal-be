package com.allermeal.application.port.out.command;

import com.allermeal.application.admin.AdminCollectionRequestType;
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
	Instant createdAt,
	AdminCollectionRequestType requestType
) {
	public AdminRecollectionRequestCommand(UUID id, String key, UserId actor, CollectionJobId original,
		CollectionJobId target, Instant createdAt) {
		this(id, key, actor, original, target, createdAt, AdminCollectionRequestType.RECOLLECTION);
	}
}
