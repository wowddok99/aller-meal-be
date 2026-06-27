package com.allermeal.domain.notification;

import com.allermeal.domain.common.DomainIdentifier;
import java.util.Objects;
import java.util.UUID;

public record NotificationId(UUID value) implements DomainIdentifier {

	public NotificationId {
		Objects.requireNonNull(value, "알림 ID는 null일 수 없습니다.");
	}
}
