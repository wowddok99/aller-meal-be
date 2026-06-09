package com.allermeal.domain.school;

import com.allermeal.domain.common.DomainIdentifier;
import java.util.Objects;
import java.util.UUID;

public record SchoolId(UUID value) implements DomainIdentifier {

	public SchoolId {
		Objects.requireNonNull(value, "학교 ID는 null일 수 없습니다.");
	}
}
