package com.allermeal.application.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.allermeal.application.port.out.*;
import com.allermeal.application.port.out.command.NotificationMailCommand;
import com.allermeal.application.port.out.result.*;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.notification.*;
import com.allermeal.domain.risk.MealRiskLevel;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class NotificationDeliveryServiceContractTest {
	private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");
	private final User user = User.create(new UserId(UUID.randomUUID()),
		new EncryptedEmail("v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="),
		new EmailSearchHash("0".repeat(64)), new PasswordHash("password-hash"), NOW).verifyEmail(NOW);
	private final ChildProfileId child = new ChildProfileId(UUID.randomUUID());
	private final SchoolId school = new SchoolId(UUID.randomUUID());
	private final NotificationRequestRepository requests = mock(NotificationRequestRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final EmailDecryptor decryptor = mock(EmailDecryptor.class);
	private final NotificationMailSender mail = mock(NotificationMailSender.class);
	private final NotificationMailContextRepository contexts = mock(NotificationMailContextRepository.class);
	private final NotificationMealSummaryService summaries = mock(NotificationMealSummaryService.class);
	private final NotificationDeliveryService service = new NotificationDeliveryService(requests, users, decryptor,
		mail, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5), contexts, summaries);
	private NotificationRequest request;
	private NotificationMailContextResult context;

	private void prepare(NotificationReason reason) {
		var target = new PendingNotificationTargetResult(UUID.randomUUID(), child, user.id(), LocalDate.of(2026, 11, 13),
			reason, reason == NotificationReason.NO_MEAL ? null : MealRiskLevel.PENDING, null, 0);
		request = NotificationRequest.pending(new NotificationId(UUID.randomUUID()), target.notificationTargetId(), child,
			user.id(), target.notificationDate(), NotificationChannel.EMAIL, reason, "a".repeat(64), "b".repeat(64),
			NotificationRequestKeyFactory.contentVersion(target), false, null, 2, NOW);
		context = new NotificationMailContextResult(target, school, "소유 자녀", "알림 학교");
		when(requests.findById(request.id())).thenReturn(Optional.of(request));
		when(users.findById(user.id())).thenReturn(Optional.of(user));
		when(decryptor.decrypt(user.encryptedEmail())).thenReturn("owner@example.test");
		when(requests.startSendingIfOwnerActive(eq(NotificationStatus.PENDING), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
		when(requests.save(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
		when(contexts.findForDelivery(any())).thenReturn(Optional.of(context));
		when(summaries.summarize(any())).thenReturn(List.of());
	}

	@ParameterizedTest
	@EnumSource(NotificationReason.class)
	void allExistingReasonsStillSendIncludingNoRiskAndUncertainResults(NotificationReason reason) {
		prepare(reason);
		assertEquals(NotificationStatus.SENT, service.deliver(request.id()).status());
		verify(mail).send(argThat(command -> command.recipientEmail().equals("owner@example.test")));
		verify(mail).send(argThat(command -> command.childProfileId().equals(child)
			&& command.childName().equals("소유 자녀") && command.schoolName().equals("알림 학교")
			&& command.notificationDate().equals(request.notificationDate()) && command.reason() == reason
			&& command.items().isEmpty()));
		verify(contexts).findForDelivery(argThat(locked -> locked.status() == NotificationStatus.PENDING));
	}

	@Test
	void missingDeletedTransferredOrChangedSchoolContextCancelsWithoutSending() {
		prepare(NotificationReason.NO_RISK);
		when(contexts.findForDelivery(any())).thenReturn(Optional.empty());
		assertEquals(NotificationStatus.CANCELED, service.deliver(request.id()).status());
		verify(requests).save(eq(NotificationStatus.SENDING), argThat(canceled -> canceled.failureCode().equals("NOTIFICATION_CONTEXT_CHANGED")));
		verifyNoInteractions(mail, decryptor, summaries);
	}

	@Test
	void targetReasonOrContentVersionMismatchCancelsInsteadOfCombiningDifferentSnapshots() {
		prepare(NotificationReason.NO_RISK);
		var target = context.target();
		var changed = new PendingNotificationTargetResult(target.notificationTargetId(), child, user.id(), target.notificationDate(),
			NotificationReason.RISK_DETECTED, MealRiskLevel.RISKY, "c".repeat(64), 1);
		when(contexts.findForDelivery(any())).thenReturn(Optional.of(new NotificationMailContextResult(changed, school, "자녀", "학교")));
		assertEquals(NotificationStatus.CANCELED, service.deliver(request.id()).status());
		verifyNoInteractions(mail, decryptor, summaries);
	}

	@Test
	void smtpFailureRetainsRetryAndMaximumAttemptTransitionsAndSentRequestDoesNotResend() {
		prepare(NotificationReason.RISK_PENDING);
		doThrow(new IllegalStateException("SMTP 일시 실패")).when(mail).send(any());
		var first = service.deliver(request.id());
		assertEquals(NotificationStatus.RETRY_PENDING, first.status());
		assertEquals(1, first.attemptCount());
		var retry = request.startSending(NOW).markFailed("SMTP_SEND_FAILED", "일시 실패", NOW, Duration.ZERO);
		when(requests.findById(request.id())).thenReturn(Optional.of(retry));
		when(requests.startSendingIfOwnerActive(eq(NotificationStatus.RETRY_PENDING), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
		assertEquals(NotificationStatus.FAILED, service.deliver(request.id()).status());
		reset(mail);
		when(requests.findById(request.id())).thenReturn(Optional.of(request.startSending(NOW).markSent(NOW)));
		service.deliver(request.id());
		verifyNoInteractions(mail);
	}

	@Test
	void pendingOwnerPreservesPersonalDataMaskingAndOwnerRaceDoesNotSend() {
		prepare(NotificationReason.NO_RISK);
		when(users.findById(user.id())).thenReturn(Optional.of(user.requestWithdrawal(NOW, NOW.plusSeconds(60))));
		assertEquals(NotificationStatus.CANCELED, service.deliver(request.id()).status());
		verify(requests).save(eq(NotificationStatus.PENDING), argThat(canceled -> canceled.failureCode().equals("PERSONAL_DATA_MASKED")));
		verifyNoInteractions(mail, contexts, decryptor);
	}

	@Test
	void ownerStatusChangedAfterReadOrSuspendedOwnerNeverSendsMail() {
		prepare(NotificationReason.NO_RISK);
		when(requests.startSendingIfOwnerActive(any(), any()))
			.thenReturn(Optional.of(request.cancelForPersonalDataMasking(NOW)));
		assertEquals(NotificationStatus.CANCELED, service.deliver(request.id()).status());
		verify(contexts).findForDelivery(request);
		verifyNoInteractions(mail, decryptor);
		when(users.findById(user.id())).thenReturn(Optional.of(user.suspend(NOW)));
		assertEquals(NotificationStatus.CANCELED, service.deliver(request.id()).status());
		verify(requests).cancelPendingAndRetryForSuspendedOwner(user.id(), NOW);
		verify(requests).save(eq(NotificationStatus.PENDING), argThat(canceled -> canceled.failureCode().equals("OWNER_ACCESS_RESTRICTED")));
	}

	@Test
	void decryptionOrSummaryPreparationFailureRetainsRetryWithoutSending() {
		prepare(NotificationReason.RISK_PENDING);
		when(decryptor.decrypt(any())).thenThrow(new IllegalStateException("복호화 실패"));
		assertEquals(NotificationStatus.RETRY_PENDING, service.deliver(request.id()).status());
		verifyNoInteractions(mail);
		doReturn("owner@example.test").when(decryptor).decrypt(any());
		when(summaries.summarize(any())).thenThrow(new IllegalStateException("상세 조회 실패"));
		assertEquals(NotificationStatus.RETRY_PENDING, service.deliver(request.id()).status());
		verifyNoInteractions(mail);
	}

	@Test
	void preparationAndSmtpFailuresNeverExposeInternalDetailsInStoredFailure() {
		prepare(NotificationReason.RISK_PENDING);
		when(summaries.summarize(any())).thenThrow(new IllegalStateException("private SQL details"));
		assertEquals(NotificationStatus.RETRY_PENDING, service.deliver(request.id()).status());
		verify(requests).save(eq(NotificationStatus.SENDING), argThat(failed ->
			"NOTIFICATION_PREPARATION_FAILED".equals(failed.failureCode())
				&& "알림 내용 준비에 실패했습니다.".equals(failed.failureMessage())));
		verifyNoInteractions(mail);

		doReturn(List.of()).when(summaries).summarize(any());
		doThrow(new IllegalStateException("private SMTP details")).when(mail).send(any());
		assertEquals(NotificationStatus.RETRY_PENDING, service.deliver(request.id()).status());
		verify(requests).save(eq(NotificationStatus.SENDING), argThat(failed ->
			"SMTP_SEND_FAILED".equals(failed.failureCode())
				&& "알림 메일 발송에 실패했습니다.".equals(failed.failureMessage())));
	}
}
