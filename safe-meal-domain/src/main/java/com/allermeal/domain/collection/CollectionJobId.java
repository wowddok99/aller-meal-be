package com.allermeal.domain.collection;

import com.allermeal.domain.common.DomainIdentifier;
import java.util.Objects;
import java.util.UUID;

public record CollectionJobId(UUID value) implements DomainIdentifier {

	public CollectionJobId {
		Objects.requireNonNull(value, "수집 작업 ID는 null일 수 없습니다.");
	}
}
