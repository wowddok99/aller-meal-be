package com.allermeal.domain.user;

import com.allermeal.domain.common.DomainIdentifier;
import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) implements DomainIdentifier {

	public UserId {
		Objects.requireNonNull(value, "사용자 ID는 null일 수 없습니다.");
	}
}
