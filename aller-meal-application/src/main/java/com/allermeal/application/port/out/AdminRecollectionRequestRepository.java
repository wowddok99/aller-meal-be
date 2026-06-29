package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.AdminRecollectionRequestCommand;
import com.allermeal.application.port.out.result.AdminRecollectionRequestResult;
import com.allermeal.domain.collection.CollectionJobId;
import java.util.Optional;

public interface AdminRecollectionRequestRepository {

	AdminRecollectionRequestResult save(AdminRecollectionRequestCommand command);

	Optional<AdminRecollectionRequestResult> findByIdempotencyKey(
		String idempotencyKey,
		CollectionJobId originalCollectionJobId
	);
}
