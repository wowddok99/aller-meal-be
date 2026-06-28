package com.allermeal.application.notification;

import com.allermeal.application.child.ChildProfileNotFoundException;
import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.user.UserId;

public class NotificationHistoryService {

	private static final int MAX_PAGE_SIZE = 100;

	private final ChildProfileRepository childProfileRepository;
	private final NotificationRequestRepository notificationRequestRepository;

	public NotificationHistoryService(
		ChildProfileRepository childProfileRepository,
		NotificationRequestRepository notificationRequestRepository
	) {
		this.childProfileRepository = childProfileRepository;
		this.notificationRequestRepository = notificationRequestRepository;
	}

	public NotificationHistoryResult findByChild(UserId ownerId, ChildProfileId childProfileId, int page, int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
			|| (long) page > ((long) Integer.MAX_VALUE / pageSize) + 1) {
			throw new InvalidNotificationHistoryRequestException();
		}
		if (childProfileRepository.findByIdAndOwnerId(childProfileId, ownerId).isEmpty()) {
			throw new ChildProfileNotFoundException();
		}
		return notificationRequestRepository.findHistoryByChild(ownerId, childProfileId, page, pageSize);
	}
}
