package com.allermeal.application.admin;

import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;

public record AdminRecollectionResult(
	CollectionJobId originalCollectionJobId,
	CollectionJobId collectionJobId,
	CollectionJobStatus status,
	boolean duplicate
) {
}
