package com.allermeal.application.port.out;

import com.allermeal.application.admin.AdminFailedCollectionJobPageResult;
import com.allermeal.application.admin.AdminCollectionJobPageResult;
import com.allermeal.application.admin.AdminCollectionJobQuery;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import java.time.Instant;
import java.util.Optional;

public interface CollectionJobRepository {

	CollectionJob createOrGetActive(CollectionJob job, Instant staleBefore);

	CollectionJob save(CollectionJobStatus expectedStatus, CollectionJob job);

	Optional<CollectionJob> findById(CollectionJobId collectionJobId);

	AdminFailedCollectionJobPageResult findFailed(int page, int pageSize);

	AdminCollectionJobPageResult findAdminPage(AdminCollectionJobQuery query);
}
