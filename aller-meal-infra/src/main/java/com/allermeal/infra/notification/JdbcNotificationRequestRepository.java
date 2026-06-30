package com.allermeal.infra.notification;

import com.allermeal.application.admin.AdminFailedNotificationItemResult;
import com.allermeal.application.admin.AdminFailedNotificationPageResult;
import com.allermeal.application.notification.NotificationHistoryItemResult;
import com.allermeal.application.notification.NotificationHistoryResult;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.result.NotificationRequestSaveResult;
import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationRequest;
import com.allermeal.domain.notification.NotificationStatus;
import com.allermeal.domain.risk.MealRiskLevel;
import com.allermeal.domain.user.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcNotificationRequestRepository implements NotificationRequestRepository {

	private final JdbcClient jdbcClient;

	public JdbcNotificationRequestRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<PendingNotificationTargetResult> findPendingTargets(int limit) {
		if (limit < 1 || limit > 500) {
			throw new IllegalArgumentException("알림 대상 조회 limit은 1 이상 500 이하여야 합니다.");
		}
		return jdbcClient.sql("""
				SELECT target.notification_target_id, target.child_id, target.user_id, target.notification_date,
				       target.reason, target.risk_level, target.risk_version, target.meal_count
				FROM notification_targets target
				JOIN users account ON account.user_id = target.user_id
				WHERE NOT EXISTS (
				    SELECT 1
				    FROM notification_requests request
				    WHERE request.notification_target_id = target.notification_target_id
				)
				  AND account.status = 'ACTIVE'
				ORDER BY target.created_at, target.notification_target_id
				LIMIT :limit
				""")
			.param("limit", limit)
			.query(this::mapTarget)
			.list();
	}

	@Override
	@Transactional
	public NotificationRequestSaveResult saveCorrectionAware(NotificationRequest request) {
		Optional<NotificationRequest> latest = findLatestByCorrectionKey(request.correctionKey());
		if (latest.map(existing -> existing.dedupKey().equals(request.dedupKey())).orElse(false)) {
			return NotificationRequestSaveResult.duplicate();
		}

		NotificationRequest toSave = request;
		if (latest.isPresent()) {
			NotificationRequest superseded = latest.get();
			int canceled = superseded.isActive() ? cancelActiveSuperseded(superseded, request) : 0;
			toSave = new NotificationRequest(
				request.id(),
				request.notificationTargetId(),
				request.childProfileId(),
				request.ownerId(),
				request.notificationDate(),
				request.channel(),
				request.reason(),
				request.dedupKey(),
				request.correctionKey(),
				request.contentVersion(),
				true,
				superseded.id(),
				request.status(),
				request.attemptCount(),
				request.maxAttempts(),
				request.nextAttemptAt(),
				request.sentAt(),
				request.failureCode(),
				request.failureMessage(),
				request.timestamps());
			return insert(toSave, canceled);
		}
		return insert(toSave, 0);
	}

	@Override
	public Optional<NotificationRequest> findById(NotificationId notificationId) {
		return jdbcClient.sql("""
				SELECT notification_id, notification_target_id, child_id, user_id, notification_date,
				       channel, reason, dedup_key, correction_key, content_version, is_correction,
				       supersedes_notification_id, status, attempt_count, max_attempts, next_attempt_at,
				       sent_at, failure_code, failure_message, created_at, updated_at
				FROM notification_requests
				WHERE notification_id = :notificationId
				""")
			.param("notificationId", notificationId.value())
			.query(this::mapRequest)
			.optional();
	}

	@Override
	public Optional<NotificationRequest> startSendingIfOwnerActive(
		NotificationStatus expectedStatus,
		NotificationRequest request
	) {
		return jdbcClient.sql("""
				WITH active_account AS (
				    SELECT user_id
				    FROM users
				    WHERE user_id = :userId AND status = 'ACTIVE'
				    FOR UPDATE
				),
				updated AS (
				    UPDATE notification_requests
				    SET status = :status,
				        attempt_count = :attemptCount,
				        next_attempt_at = :nextAttemptAt,
				        sent_at = :sentAt,
				        failure_code = :failureCode,
				        failure_message = :failureMessage,
				        updated_at = :updatedAt
				    WHERE notification_id = :notificationId
				      AND status = :expectedStatus
				      AND EXISTS (SELECT 1 FROM active_account)
				    RETURNING notification_id, notification_target_id, child_id, user_id, notification_date,
				              channel, reason, dedup_key, correction_key, content_version, is_correction,
				              supersedes_notification_id, status, attempt_count, max_attempts, next_attempt_at,
				              sent_at, failure_code, failure_message, created_at, updated_at
				)
				SELECT notification_id, notification_target_id, child_id, user_id, notification_date,
				       channel, reason, dedup_key, correction_key, content_version, is_correction,
				       supersedes_notification_id, status, attempt_count, max_attempts, next_attempt_at,
				       sent_at, failure_code, failure_message, created_at, updated_at
				FROM updated
				""")
			.param("status", request.status().name())
			.param("attemptCount", request.attemptCount())
			.param("nextAttemptAt", request.nextAttemptAt() == null ? null : Timestamp.from(request.nextAttemptAt()))
			.param("sentAt", request.sentAt() == null ? null : Timestamp.from(request.sentAt()))
			.param("failureCode", request.failureCode())
			.param("failureMessage", request.failureMessage())
			.param("updatedAt", Timestamp.from(request.timestamps().updatedAt()))
			.param("notificationId", request.id().value())
			.param("userId", request.ownerId().value())
			.param("expectedStatus", expectedStatus.name())
			.query(this::mapRequest)
			.optional();
	}

	@Override
	public NotificationRequest save(NotificationStatus expectedStatus, NotificationRequest request) {
		int rows = jdbcClient.sql("""
				UPDATE notification_requests
				SET status = :status,
				    attempt_count = :attemptCount,
				    next_attempt_at = :nextAttemptAt,
				    sent_at = :sentAt,
				    failure_code = :failureCode,
				    failure_message = :failureMessage,
				    updated_at = :updatedAt
				WHERE notification_id = :notificationId AND status = :expectedStatus
				""")
			.param("status", request.status().name())
			.param("attemptCount", request.attemptCount())
			.param("nextAttemptAt", request.nextAttemptAt() == null ? null : Timestamp.from(request.nextAttemptAt()))
			.param("sentAt", request.sentAt() == null ? null : Timestamp.from(request.sentAt()))
			.param("failureCode", request.failureCode())
			.param("failureMessage", request.failureMessage())
			.param("updatedAt", Timestamp.from(request.timestamps().updatedAt()))
			.param("notificationId", request.id().value())
			.param("expectedStatus", expectedStatus.name())
			.update();
		if (rows != 1) {
			throw new IllegalStateException("알림 요청 상태 전이에 실패했습니다.");
		}
		return findById(request.id()).orElseThrow();
	}

	@Override
	public NotificationHistoryResult findHistoryByChild(
		UserId ownerId,
		ChildProfileId childProfileId,
		int page,
		int pageSize
	) {
		int offset = offset(page, pageSize);
		long totalCount = jdbcClient.sql("""
				SELECT count(*)
				FROM notification_requests
				WHERE user_id = :ownerId AND child_id = :childId
				""")
			.param("ownerId", ownerId.value())
			.param("childId", childProfileId.value())
			.query(Long.class)
			.single();
		List<NotificationHistoryItemResult> notifications = jdbcClient.sql("""
				SELECT notification_id, notification_date, channel, reason, status, attempt_count,
				       sent_at, failure_code, created_at, updated_at
				FROM notification_requests
				WHERE user_id = :ownerId AND child_id = :childId
				ORDER BY notification_date DESC, created_at DESC, notification_id DESC
				LIMIT :limit OFFSET :offset
				""")
			.param("ownerId", ownerId.value())
			.param("childId", childProfileId.value())
			.param("limit", pageSize)
			.param("offset", offset)
			.query(this::mapHistoryItem)
			.list();
		return new NotificationHistoryResult(notifications, page, pageSize, Math.toIntExact(totalCount));
	}

	@Override
	public AdminFailedNotificationPageResult findFailed(int page, int pageSize) {
		int offset = offset(page, pageSize);
		long totalCount = jdbcClient.sql("""
				SELECT count(*)
				FROM notification_requests
				WHERE status = 'FAILED'
				""")
			.query(Long.class)
			.single();
		List<AdminFailedNotificationItemResult> notifications = jdbcClient.sql("""
				SELECT notification_id, notification_target_id, child_id, user_id, notification_date,
				       channel, reason, status, attempt_count, max_attempts, failure_code,
				       failure_message, created_at, updated_at
				FROM notification_requests
				WHERE status = 'FAILED'
				ORDER BY updated_at DESC, notification_id DESC
				LIMIT :limit OFFSET :offset
				""")
			.param("limit", pageSize)
			.param("offset", offset)
			.query(this::mapFailedNotification)
			.list();
		return new AdminFailedNotificationPageResult(notifications, page, pageSize, totalCount);
	}

	private int offset(int page, int pageSize) {
		try {
			return Math.multiplyExact(page - 1, pageSize);
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException("알림 이력 페이지 offset이 허용 범위를 초과했습니다.", exception);
		}
	}

	private NotificationRequestSaveResult insert(NotificationRequest request, int canceledSupersededCount) {
		int created = jdbcClient.sql("""
				INSERT INTO notification_requests (
				    notification_id, notification_target_id, child_id, user_id, notification_date,
				    channel, reason, dedup_key, correction_key, content_version, is_correction,
				    supersedes_notification_id, status, attempt_count, max_attempts, next_attempt_at,
				    sent_at, failure_code, failure_message, created_at, updated_at
				)
				VALUES (
				    :notificationId, :targetId, :childId, :userId, :notificationDate,
				    :channel, :reason, :dedupKey, :correctionKey, :contentVersion, :correction,
				    :supersedesNotificationId, :status, :attemptCount, :maxAttempts, :nextAttemptAt,
				    :sentAt, :failureCode, :failureMessage, :createdAt, :updatedAt
				)
				ON CONFLICT (dedup_key) DO NOTHING
				""")
			.param("notificationId", request.id().value())
			.param("targetId", request.notificationTargetId())
			.param("childId", request.childProfileId().value())
			.param("userId", request.ownerId().value())
			.param("notificationDate", request.notificationDate())
			.param("channel", request.channel().name())
			.param("reason", request.reason().name())
			.param("dedupKey", request.dedupKey())
			.param("correctionKey", request.correctionKey())
			.param("contentVersion", request.contentVersion())
			.param("correction", request.correction())
			.param("supersedesNotificationId", request.supersedesNotificationId() == null
				? null : request.supersedesNotificationId().value())
			.param("status", request.status().name())
			.param("attemptCount", request.attemptCount())
			.param("maxAttempts", request.maxAttempts())
			.param("nextAttemptAt", request.nextAttemptAt() == null ? null : Timestamp.from(request.nextAttemptAt()))
			.param("sentAt", request.sentAt() == null ? null : Timestamp.from(request.sentAt()))
			.param("failureCode", request.failureCode())
			.param("failureMessage", request.failureMessage())
			.param("createdAt", Timestamp.from(request.timestamps().createdAt()))
			.param("updatedAt", Timestamp.from(request.timestamps().updatedAt()))
			.update();
		if (created == 0) {
			return NotificationRequestSaveResult.duplicate();
		}
		if (created != 1) {
			throw new IllegalStateException("알림 요청 저장에 실패했습니다.");
		}
		return new NotificationRequestSaveResult(
			true, request.correction(), canceledSupersededCount, request);
	}

	private int cancelActiveSuperseded(NotificationRequest superseded, NotificationRequest nextRequest) {
		NotificationRequest canceled = superseded.cancelAsSuperseded(nextRequest.timestamps().createdAt());
		return jdbcClient.sql("""
				UPDATE notification_requests
				SET status = :status,
				    next_attempt_at = NULL,
				    failure_code = :failureCode,
				    failure_message = :failureMessage,
				    updated_at = :updatedAt
				WHERE notification_id = :notificationId
				  AND status IN ('PENDING', 'SENDING', 'RETRY_PENDING')
				  AND dedup_key <> :nextDedupKey
				""")
			.param("status", canceled.status().name())
			.param("failureCode", canceled.failureCode())
			.param("failureMessage", canceled.failureMessage())
			.param("updatedAt", Timestamp.from(canceled.timestamps().updatedAt()))
			.param("notificationId", canceled.id().value())
			.param("nextDedupKey", nextRequest.dedupKey())
			.update();
	}

	private Optional<NotificationRequest> findLatestByCorrectionKey(String correctionKey) {
		return jdbcClient.sql("""
				SELECT notification_id, notification_target_id, child_id, user_id, notification_date,
				       channel, reason, dedup_key, correction_key, content_version, is_correction,
				       supersedes_notification_id, status, attempt_count, max_attempts, next_attempt_at,
				       sent_at, failure_code, failure_message, created_at, updated_at
				FROM notification_requests
				WHERE correction_key = :correctionKey
				ORDER BY updated_at DESC, notification_id DESC
				LIMIT 1
				""")
			.param("correctionKey", correctionKey)
			.query(this::mapRequest)
			.optional();
	}

	private PendingNotificationTargetResult mapTarget(ResultSet resultSet, int rowNum) throws SQLException {
		String riskLevel = resultSet.getString("risk_level");
		return new PendingNotificationTargetResult(
			resultSet.getObject("notification_target_id", UUID.class),
			new ChildProfileId(resultSet.getObject("child_id", UUID.class)),
			new UserId(resultSet.getObject("user_id", UUID.class)),
			resultSet.getObject("notification_date", java.time.LocalDate.class),
			NotificationReason.valueOf(resultSet.getString("reason")),
			riskLevel == null ? null : MealRiskLevel.valueOf(riskLevel),
			resultSet.getString("risk_version"),
			resultSet.getInt("meal_count"));
	}

	private NotificationRequest mapRequest(ResultSet resultSet, int rowNum) throws SQLException {
		UUID supersedesId = resultSet.getObject("supersedes_notification_id", UUID.class);
		OffsetDateTime nextAttemptAt = resultSet.getObject("next_attempt_at", OffsetDateTime.class);
		OffsetDateTime sentAt = resultSet.getObject("sent_at", OffsetDateTime.class);
		return new NotificationRequest(
			new NotificationId(resultSet.getObject("notification_id", UUID.class)),
			resultSet.getObject("notification_target_id", UUID.class),
			new ChildProfileId(resultSet.getObject("child_id", UUID.class)),
			new UserId(resultSet.getObject("user_id", UUID.class)),
			resultSet.getObject("notification_date", java.time.LocalDate.class),
			NotificationChannel.valueOf(resultSet.getString("channel")),
			NotificationReason.valueOf(resultSet.getString("reason")),
			resultSet.getString("dedup_key"),
			resultSet.getString("correction_key"),
			resultSet.getString("content_version"),
			resultSet.getBoolean("is_correction"),
			supersedesId == null ? null : new NotificationId(supersedesId),
			NotificationStatus.valueOf(resultSet.getString("status")),
			resultSet.getInt("attempt_count"),
			resultSet.getInt("max_attempts"),
			nextAttemptAt == null ? null : nextAttemptAt.toInstant(),
			sentAt == null ? null : sentAt.toInstant(),
			resultSet.getString("failure_code"),
			resultSet.getString("failure_message"),
			new EntityTimestamps(
				resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
				resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()));
	}

	private NotificationHistoryItemResult mapHistoryItem(ResultSet resultSet, int rowNum) throws SQLException {
		OffsetDateTime sentAt = resultSet.getObject("sent_at", OffsetDateTime.class);
		return new NotificationHistoryItemResult(
			new NotificationId(resultSet.getObject("notification_id", UUID.class)),
			resultSet.getObject("notification_date", java.time.LocalDate.class),
			NotificationChannel.valueOf(resultSet.getString("channel")),
			NotificationReason.valueOf(resultSet.getString("reason")),
			NotificationStatus.valueOf(resultSet.getString("status")),
			resultSet.getInt("attempt_count"),
			sentAt == null ? null : sentAt.toInstant(),
			resultSet.getString("failure_code"),
			resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
			resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
	}

	private AdminFailedNotificationItemResult mapFailedNotification(ResultSet resultSet, int rowNum) throws SQLException {
		return new AdminFailedNotificationItemResult(
			new NotificationId(resultSet.getObject("notification_id", UUID.class)),
			resultSet.getObject("notification_target_id", UUID.class),
			new ChildProfileId(resultSet.getObject("child_id", UUID.class)),
			new UserId(resultSet.getObject("user_id", UUID.class)),
			resultSet.getObject("notification_date", java.time.LocalDate.class),
			NotificationChannel.valueOf(resultSet.getString("channel")),
			NotificationReason.valueOf(resultSet.getString("reason")),
			NotificationStatus.valueOf(resultSet.getString("status")),
			resultSet.getInt("attempt_count"),
			resultSet.getInt("max_attempts"),
			resultSet.getString("failure_code"),
			resultSet.getString("failure_message"),
			resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
			resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
	}
}
