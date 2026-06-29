package com.allermeal.api.admin.response;

import com.allermeal.application.admin.AdminRecollectionResult;
import com.allermeal.domain.collection.CollectionJobStatus;
import java.util.UUID;

public record AdminRecollectionResponse(
	UUID originalCollectionJobId,
	UUID collectionJobId,
	CollectionJobStatus status,
	boolean duplicate
) {

	public static AdminRecollectionResponse from(AdminRecollectionResult result) {
		return new AdminRecollectionResponse(
			result.originalCollectionJobId().value(),
			result.collectionJobId().value(),
			result.status(),
			result.duplicate());
	}
}
