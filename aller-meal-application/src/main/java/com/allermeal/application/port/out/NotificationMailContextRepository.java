package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.NotificationMailContextResult;
import com.allermeal.domain.notification.NotificationRequest;
import java.util.Optional;

public interface NotificationMailContextRepository {

	/** request 전이 전에 owner→child→target 순서로 잠그고, 발송 트랜잭션 종료까지 문맥 변경을 막는다. */
	Optional<NotificationMailContextResult> findForDelivery(NotificationRequest request);
}
