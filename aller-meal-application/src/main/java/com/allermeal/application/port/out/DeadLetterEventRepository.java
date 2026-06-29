package com.allermeal.application.port.out;

import com.allermeal.application.admin.AdminDeadLetterEventItemResult;
import com.allermeal.application.admin.AdminDeadLetterEventPageResult;
import com.allermeal.application.port.out.command.DeadLetterEventCommand;
import com.allermeal.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DeadLetterEventRepository {

	void save(DeadLetterEventCommand command);

	AdminDeadLetterEventPageResult findRecent(int page, int pageSize);

	Optional<AdminDeadLetterEventItemResult> findById(UUID deadLetterEventId);

	boolean markReprocessed(UUID deadLetterEventId, UserId actorUserId, Instant reprocessedAt);
}
