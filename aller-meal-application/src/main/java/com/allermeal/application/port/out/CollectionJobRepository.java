package com.allermeal.application.port.out;

import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobStatus;
import java.time.Instant;

public interface CollectionJobRepository {

	CollectionJob createOrGetActive(CollectionJob job, Instant staleBefore);

	CollectionJob save(CollectionJobStatus expectedStatus, CollectionJob job);
}
