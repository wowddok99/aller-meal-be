package com.allermeal.application.port.out.result;

import com.allermeal.domain.collection.CollectionJobId;

public record AdminRecollectionRequestResult(
	CollectionJobId collectionJobId,
	boolean duplicate,
	boolean conflict
) {
}
