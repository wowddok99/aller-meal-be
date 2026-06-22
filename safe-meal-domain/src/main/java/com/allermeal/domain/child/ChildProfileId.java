package com.allermeal.domain.child;

import com.allermeal.domain.common.DomainIdentifier;
import java.util.Objects;
import java.util.UUID;

public record ChildProfileId(UUID value) implements DomainIdentifier {

	public ChildProfileId {
		Objects.requireNonNull(value, "자녀 ID는 null일 수 없습니다.");
	}
}
