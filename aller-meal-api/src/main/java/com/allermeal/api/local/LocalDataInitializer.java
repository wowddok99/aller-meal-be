package com.allermeal.api.local;

import com.allermeal.application.admin.AdminBootstrapProperties;
import com.allermeal.application.admin.AdminCollectionJobQuery;
import com.allermeal.application.admin.AdminCollectionRequestType;
import com.allermeal.application.auth.EmailVerificationRequester;
import com.allermeal.application.meal.MealLabelingEvents;
import com.allermeal.application.notification.NotificationRequestEvents;
import com.allermeal.application.notification.NotificationRequestKeyFactory;
import com.allermeal.application.notification.NotificationTargetReason;
import com.allermeal.application.port.out.AdminAuditLogRepository;
import com.allermeal.application.port.out.AdminNotificationReprocessRequestRepository;
import com.allermeal.application.port.out.AdminRecollectionRequestRepository;
import com.allermeal.application.port.out.AdminUserAccessAuditRepository;
import com.allermeal.application.port.out.AllergenRepository;
import com.allermeal.application.port.out.ChildAllergenRepository;
import com.allermeal.application.port.out.ChildNotificationPreferenceRepository;
import com.allermeal.application.port.out.ChildProfileRepository;
import com.allermeal.application.port.out.CollectionJobRepository;
import com.allermeal.application.port.out.DeadLetterEventRepository;
import com.allermeal.application.port.out.EmailEncryptor;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.ExternalApiLogRepository;
import com.allermeal.application.port.out.MealCollectionPersistence;
import com.allermeal.application.port.out.MealRepository;
import com.allermeal.application.port.out.NotificationRequestRepository;
import com.allermeal.application.port.out.NotificationTargetRepository;
import com.allermeal.application.port.out.OutboxEventRepository;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.RawPayloadStorage;
import com.allermeal.application.port.out.SchoolCollectionSubscriptionRepository;
import com.allermeal.application.port.out.SchoolRepository;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.command.AdminAuditLogCommand;
import com.allermeal.application.port.out.command.AdminNotificationReprocessRequestCommand;
import com.allermeal.application.port.out.command.AdminRecollectionRequestCommand;
import com.allermeal.application.port.out.command.AdminUserAccessAuditCommand;
import com.allermeal.application.port.out.command.DeadLetterEventCommand;
import com.allermeal.application.port.out.command.ExternalApiLogCommand;
import com.allermeal.application.port.out.command.NotificationTargetCommand;
import com.allermeal.application.port.out.command.RawPayload;
import com.allermeal.application.port.out.command.RegisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.result.MealQueryResult;
import com.allermeal.application.port.out.result.PendingNotificationTargetResult;
import com.allermeal.domain.allergen.Allergen;
import com.allermeal.domain.child.ChildProfile;
import com.allermeal.domain.child.ChildProfileId;
import com.allermeal.domain.child.NotificationPreference;
import com.allermeal.domain.collection.CollectionJob;
import com.allermeal.domain.collection.CollectionJobId;
import com.allermeal.domain.collection.CollectionJobStatus;
import com.allermeal.domain.common.EntityTimestamps;
import com.allermeal.domain.meal.Meal;
import com.allermeal.domain.meal.MealId;
import com.allermeal.domain.meal.MealItem;
import com.allermeal.domain.meal.MealItemId;
import com.allermeal.domain.meal.MealItemLabelingStatus;
import com.allermeal.domain.meal.MealLabelingStatus;
import com.allermeal.domain.meal.MealType;
import com.allermeal.domain.notification.NotificationChannel;
import com.allermeal.domain.notification.NotificationId;
import com.allermeal.domain.notification.NotificationReason;
import com.allermeal.domain.notification.NotificationRequest;
import com.allermeal.domain.notification.NotificationStatus;
import com.allermeal.domain.outbox.OutboxEvent;
import com.allermeal.domain.raw.RawObjectMetadata;
import com.allermeal.domain.risk.ChildMealRisk;
import com.allermeal.domain.risk.ChildMealRiskCalculator;
import com.allermeal.domain.risk.MealRiskLevel;
import com.allermeal.domain.school.School;
import com.allermeal.domain.school.SchoolId;
import com.allermeal.domain.user.EmailVerificationStatus;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.PasswordHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import com.allermeal.domain.user.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("local & !prod & !production")
@ConditionalOnProperty(prefix = "aller-meal.local-data-initializer", name = "enabled", havingValue = "true")
final class LocalDataInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LocalDataInitializer.class);
	private static final UserId COMPLETION_USER_ID = userId("admin");
	private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<School> SCHOOLS = List.of(
		new School(new SchoolId(id("school:7010059")), "7010059", "B10", "경기고등학교",
			"서울특별시 강남구 영동대로 643", "서울특별시"),
		new School(new SchoolId(id("school:7061128")), "7061128", "B10", "서울대학교사범대학부설초등학교",
			"서울특별시 종로구 대학로 64", "서울특별시"));
	private static final List<SeedChild> CHILDREN = List.of(
		new SeedChild("milk-child", "member", 0, "김하늘", 1, 2, List.of(1, 2), true, LocalTime.of(7, 30)),
		new SeedChild("safe-child", "member", 0, "김바다", 2, 3, List.of(), false, LocalTime.of(8, 0)),
		new SeedChild("primary-child", "member", 1, "김별", 4, 1, List.of(5, 6), true, LocalTime.of(8, 30)),
		new SeedChild("other-child", "other-member", 1, "이새봄", 3, 2, List.of(1), true, LocalTime.of(7, 45)),
		new SeedChild("restricted-child", "suspended", 1, "박노을", 5, 4, List.of(2), true, LocalTime.of(8, 15)));
	private static final List<SeedUser> USERS = List.of(
		new SeedUser("admin", true, UserStatus.ACTIVE),
		new SeedUser("member", true, UserStatus.ACTIVE),
		new SeedUser("other-member", true, UserStatus.ACTIVE),
		new SeedUser("unverified", false, UserStatus.ACTIVE),
		new SeedUser("suspended", true, UserStatus.SUSPENDED),
		new SeedUser("withdrawal", true, UserStatus.WITHDRAWAL_PENDING),
		new SeedUser("promoted-admin", true, UserStatus.ACTIVE),
		new SeedUser("promote-target", true, UserStatus.ACTIVE),
		new SeedUser("suspend-target", false, UserStatus.ACTIVE),
		new SeedUser("unsuspend-target", true, UserStatus.SUSPENDED),
		new SeedUser("unsuspended", true, UserStatus.ACTIVE),
		new SeedUser("new-member", true, UserStatus.ACTIVE),
		new SeedUser("disabled", true, UserStatus.DISABLED));

	private final UserRepository userRepository;
	private final EmailEncryptor emailEncryptor;
	private final EmailSearchHasher emailSearchHasher;
	private final PasswordHasher passwordHasher;
	private final SchoolRepository schoolRepository;
	private final ChildProfileRepository childProfileRepository;
	private final AllergenRepository allergenRepository;
	private final ChildAllergenRepository childAllergenRepository;
	private final ChildNotificationPreferenceRepository preferenceRepository;
	private final SchoolCollectionSubscriptionRepository subscriptionRepository;
	private final MealRepository mealRepository;
	private final CollectionJobRepository collectionJobRepository;
	private final MealCollectionPersistence collectionPersistence;
	private final RawPayloadStorage rawPayloadStorage;
	private final NotificationTargetRepository targetRepository;
	private final NotificationRequestRepository notificationRepository;
	private final OutboxEventRepository outboxRepository;
	private final DeadLetterEventRepository deadLetterRepository;
	private final AdminNotificationReprocessRequestRepository notificationReprocessRepository;
	private final AdminAuditLogRepository auditLogRepository;
	private final AdminRecollectionRequestRepository recollectionRepository;
	private final ExternalApiLogRepository externalLogRepository;
	private final AdminUserAccessAuditRepository accessAuditRepository;
	private final AdminBootstrapProperties bootstrapProperties;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;
	private final String password;

	LocalDataInitializer(
		UserRepository userRepository,
		EmailEncryptor emailEncryptor,
		EmailSearchHasher emailSearchHasher,
		PasswordHasher passwordHasher,
		SchoolRepository schoolRepository,
		ChildProfileRepository childProfileRepository,
		AllergenRepository allergenRepository,
		ChildAllergenRepository childAllergenRepository,
		ChildNotificationPreferenceRepository preferenceRepository,
		SchoolCollectionSubscriptionRepository subscriptionRepository,
		MealRepository mealRepository,
		CollectionJobRepository collectionJobRepository,
		MealCollectionPersistence collectionPersistence,
		RawPayloadStorage rawPayloadStorage,
		NotificationTargetRepository targetRepository,
		NotificationRequestRepository notificationRepository,
		OutboxEventRepository outboxRepository,
		DeadLetterEventRepository deadLetterRepository,
		AdminNotificationReprocessRequestRepository notificationReprocessRepository,
		AdminAuditLogRepository auditLogRepository,
		AdminRecollectionRequestRepository recollectionRepository,
		ExternalApiLogRepository externalLogRepository,
		AdminUserAccessAuditRepository accessAuditRepository,
		AdminBootstrapProperties bootstrapProperties,
		PlatformTransactionManager transactionManager,
		Clock clock,
		@Value("${aller-meal.local-data-initializer.password}") String password
	) {
		this.userRepository = userRepository;
		this.emailEncryptor = emailEncryptor;
		this.emailSearchHasher = emailSearchHasher;
		this.passwordHasher = passwordHasher;
		this.schoolRepository = schoolRepository;
		this.childProfileRepository = childProfileRepository;
		this.allergenRepository = allergenRepository;
		this.childAllergenRepository = childAllergenRepository;
		this.preferenceRepository = preferenceRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.mealRepository = mealRepository;
		this.collectionJobRepository = collectionJobRepository;
		this.collectionPersistence = collectionPersistence;
		this.rawPayloadStorage = rawPayloadStorage;
		this.targetRepository = targetRepository;
		this.notificationRepository = notificationRepository;
		this.outboxRepository = outboxRepository;
		this.deadLetterRepository = deadLetterRepository;
		this.notificationReprocessRepository = notificationReprocessRepository;
		this.auditLogRepository = auditLogRepository;
		this.recollectionRepository = recollectionRepository;
		this.externalLogRepository = externalLogRepository;
		this.accessAuditRepository = accessAuditRepository;
		this.bootstrapProperties = bootstrapProperties;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		Instant now = clock.instant();
		Boolean initialized = transactionTemplate.execute(status -> {
			if (userRepository.findById(COMPLETION_USER_ID).isPresent()) {
				return false;
			}
			requireAvailableUserKeys();
			Map<String, User> users = seedUsers(now);
			User exampleAdmin = exampleAdmin(users.get("admin"));
			List<School> schools = seedSchools();
			Map<String, ChildProfile> children = seedChildren(users, schools, now);
			LocalDate week = weekStart(now);
			List<Meal> meals = seedMeals(schools, week, now);
			Map<String, NotificationRequest> notifications = seedNotifications(children, schools, week, now);
			seedNotificationEvents(notifications, users.get("admin"), now);
			seedAdminExamples(exampleAdmin, schools, week, now);
			Meal labeled = meals.stream().filter(meal -> meal.labelingStatus() == MealLabelingStatus.LABELED).findFirst().orElseThrow();
			outboxRepository.save(OutboxEvent.pending(id("meal-labeled-event:" + labeled.id().value()),
				MealLabelingEvents.MEAL_LABELED, MealLabelingEvents.mealLabeledPayload(labeled), now));
			List<CollectionJob> operations = seedCollectionOperations(schools.getFirst(), users.get("admin"), week, now);
			for (Meal meal : meals.stream().limit(4).toList()) {
				operations.add(collectedJob(meal.schoolId(), meal.mealDate(), meal.mealType()));
			}
			seedExternalLogs(operations);
			seedUserAudits(users, now);
			return true;
		});
		if (Boolean.TRUE.equals(initialized)) {
			log.info("로컬 데이터 초기화 완료.");
		} else {
			log.info("로컬 데이터 초기화 생략. 기존 완료 기준 회원 존재.");
		}
	}

	private void requireAvailableUserKeys() {
		for (SeedUser seed : USERS) {
			if (userRepository.findById(userId(seed.key())).isPresent()
				|| userRepository.existsByEmailSearchHash(emailSearchHasher.hash(seed.email()))) {
				throw new IllegalStateException("로컬 데이터 초기화 회원 고유키 충돌. 대상=" + seed.key());
			}
		}
	}

	private Map<String, User> seedUsers(Instant now) {
		if (password.isBlank() || password.length() < 8) {
			throw new IllegalArgumentException("로컬 초기화 비밀번호는 8자 이상이어야 합니다.");
		}
		Map<String, User> users = new LinkedHashMap<>();
		Instant createdAt = now.minus(Duration.ofDays(30));
		Instant verifiedAt = createdAt.plus(Duration.ofHours(1));
		Instant changedAt = now.minus(Duration.ofDays(1));
		for (SeedUser seed : USERS) {
			EncryptedEmail encryptedEmail = new EncryptedEmail(emailEncryptor.encrypt(seed.email()));
			PasswordHash passwordHash = new PasswordHash(passwordHasher.hash(password));
			User user = seed.key().equals("admin")
				? User.createAdmin(userId(seed.key()), encryptedEmail,
					emailSearchHasher.hash(seed.email()), passwordHash, createdAt)
				: User.create(userId(seed.key()), encryptedEmail,
					emailSearchHasher.hash(seed.email()), passwordHash, createdAt);
			if (seed.verified()) {
				user = user.verifyEmail(verifiedAt);
			}
			if (seed.key().equals("promoted-admin")) {
				user = user.promoteToAdmin(changedAt);
			} else if (seed.key().equals("unsuspended")) {
				user = user.suspend(changedAt.minus(Duration.ofDays(1))).unsuspend(changedAt);
			}
			user = switch (seed.status()) {
				case ACTIVE -> user;
				case SUSPENDED -> user.suspend(changedAt);
				case WITHDRAWAL_PENDING -> user.requestWithdrawal(changedAt, changedAt.plus(Duration.ofDays(7)));
				case DISABLED -> user.disable(changedAt);
			};
			users.put(seed.key(), userRepository.save(user));
		}
		return users;
	}

	private List<School> seedSchools() {
		List<School> schools = new ArrayList<>();
		for (School school : SCHOOLS) {
			School existing = schoolRepository.findById(school.id()).orElse(null);
			if (existing != null) {
				if (!existing.equals(school)) {
					throw new IllegalStateException("로컬 데이터 초기화 학교 정보 충돌.");
				}
				schools.add(existing);
			} else {
				School saved = schoolRepository.saveAll(List.of(school)).getFirst();
				if (!saved.id().equals(school.id())) {
					throw new IllegalStateException("로컬 데이터 초기화 학교 코드 충돌.");
				}
				schools.add(saved);
			}
		}
		return schools;
	}

	private Map<String, ChildProfile> seedChildren(Map<String, User> users, List<School> schools, Instant now) {
		var availableAllergens = allergenRepository.findAll().stream().map(Allergen::code).collect(Collectors.toSet());
		if (!availableAllergens.containsAll(List.of(1, 2, 5, 6, 15))) {
			throw new IllegalStateException("로컬 데이터 초기화에 필요한 알레르기 기준정보 누락.");
		}
		Instant createdAt = now.minus(Duration.ofDays(14));
		Map<String, ChildProfile> children = new LinkedHashMap<>();
		for (SeedChild seed : CHILDREN) {
			children.put(seed.key(), seedChild(seed, users.get(seed.ownerKey()).id(), schools, createdAt));
		}
		return children;
	}

	private ChildProfile seedChild(SeedChild seed, UserId ownerId, List<School> schools, Instant createdAt) {
		ChildProfile child = childProfileRepository.save(ChildProfile.create(
			new ChildProfileId(id("child:" + seed.key())), ownerId, seed.name(), seed.grade(), seed.classNumber(),
			schools.get(seed.schoolIndex()).id(), createdAt));
		subscriptionRepository.registerChild(new RegisterSchoolCollectionSubscriptionCommand(child.schoolId(), createdAt));
		if (!childAllergenRepository.replaceAll(ownerId, child.id(), seed.allergenCodes())) {
			throw new IllegalStateException("로컬 데이터 초기화 자녀 알레르기 저장 실패.");
		}
		preferenceRepository.upsert(ownerId, NotificationPreference.create(child.id(), seed.emailEnabled(),
			seed.notificationTime(), NotificationPreference.SUPPORTED_TIMEZONE, EntityTimestamps.createdAt(createdAt)))
			.orElseThrow(() -> new IllegalStateException("로컬 데이터 초기화 알림 설정 저장 실패."));
		return child;
	}

	private User exampleAdmin(User fallback) {
		if (bootstrapProperties.email() == null || bootstrapProperties.email().isBlank()) {
			return fallback;
		}
		String email = EmailVerificationRequester.normalizeEmail(bootstrapProperties.email());
		User admin = userRepository.findByEmailSearchHash(emailSearchHasher.hash(email))
			.orElseThrow(() -> new IllegalStateException("설정된 초기 관리자 계정을 찾을 수 없습니다."));
		if (admin.role() != UserRole.ADMIN || admin.status() != UserStatus.ACTIVE
			|| admin.emailVerificationStatus() != EmailVerificationStatus.VERIFIED) {
			throw new IllegalStateException("설정된 초기 관리자 계정은 활성·인증 완료 관리자여야 합니다.");
		}
		return admin;
	}

	private void seedAdminExamples(User admin, List<School> schools, LocalDate week, Instant now) {
		Map<String, ChildProfile> children = new LinkedHashMap<>();
		for (SeedChild template : CHILDREN) {
			if (!template.ownerKey().equals("member")) {
				continue;
			}
			SeedChild seed = new SeedChild("admin-" + template.key(), "admin", template.schoolIndex(), template.name(),
				template.grade(), template.classNumber(), template.allergenCodes(), template.emailEnabled(), template.notificationTime());
			children.put(seed.key(), seedChild(seed, admin.id(), schools, now.minus(Duration.ofDays(14))));
		}
		Map<String, NotificationRequest> notifications = seedNotificationHistory(children.get("admin-milk-child"), week, now);
		seedNotificationEvents(notifications, admin, now);
	}

	private List<Meal> seedMeals(List<School> schools, LocalDate week, Instant now) {
		requireAvailableMealTargets(schools, week);
		if (!mealRepository.findCollectedInRange(schools.getFirst().id(), week.plusDays(7), week.plusDays(7)).isEmpty()) {
			throw new IllegalStateException("로컬 데이터 초기화 수집 중 대상 충돌.");
		}
		List<Meal> meals = new ArrayList<>();
		for (School school : schools) {
			for (int day = 0; day < 7; day++) {
				for (MealType type : MealType.values()) {
					boolean hasMeal = day < 5 && (type == MealType.LUNCH
						|| type == MealType.DINNER && school.equals(schools.getFirst()));
					MealLabelingStatus labelingStatus = !hasMeal ? null : switch (day) {
						case 2 -> MealLabelingStatus.UNKNOWN;
						case 3 -> MealLabelingStatus.LABELING_FAILED;
						case 4 -> MealLabelingStatus.PENDING;
						default -> MealLabelingStatus.LABELED;
					};
					Meal meal = seedCollectedMeal(school, week.plusDays(day), type, labelingStatus, now);
					if (meal != null) {
						meals.add(meal);
					}
				}
			}
		}
		School school = schools.getFirst();
		createPendingJob("collecting:" + school.id().value(), school, week.plusDays(7), MealType.LUNCH, now.minusSeconds(30));
		return meals;
	}

	private void requireAvailableMealTargets(List<School> schools, LocalDate week) {
		for (School school : schools) {
			if (!mealRepository.findCollectedInRange(school.id(), week, week.plusDays(6)).isEmpty()) {
				throw new IllegalStateException("로컬 데이터 초기화 급식 수집 버전 충돌.");
			}
			for (int day = 0; day < 7; day++) {
				for (MealType type : MealType.values()) {
					LocalDate date = week.plusDays(day);
					if (mealRepository.findByNaturalKey(school.id(), date, type).isPresent()
						|| collectionJobRepository.findAdminPage(new AdminCollectionJobQuery(
							1, 1, null, school.id().value(), date, type, null)).totalCount() > 0) {
						throw new IllegalStateException("로컬 데이터 초기화 급식 또는 수집 대상 충돌.");
					}
				}
			}
		}
	}

	private Meal seedCollectedMeal(School school, LocalDate date, MealType type, MealLabelingStatus status, Instant now) {
		if (mealRepository.findByNaturalKey(school.id(), date, type).isPresent()
			|| mealRepository.findCollectedInRange(school.id(), date, date).stream()
				.anyMatch(result -> result.mealType() == type)) {
			throw new IllegalStateException("로컬 데이터 초기화 급식 또는 수집 버전 충돌.");
		}
		String key = school.id().value() + ":" + date + ":" + type;
		Instant startedAt = now.minus(Duration.ofMinutes(10));
		CollectionJob pending = createPendingJob("meal:" + key, school, date, type, startedAt.minusSeconds(1));
		CollectionJob running = collectionJobRepository.save(CollectionJobStatus.PENDING,
			pending.start(startedAt, startedAt.plus(Duration.ofMinutes(2))));
		List<SeedMenu> menus = status == null ? List.of() : menus(date);
		RawObjectMetadata raw = storeRawMeal(school, date, type, menus, startedAt.plusMillis(80));
		Meal pendingMeal = status == null ? null : new Meal(new MealId(id("meal:" + key)), school.id(), date, type,
			raw.sha256Hash(), raw.receivedAt(), MealLabelingStatus.PENDING,
			date.getDayOfWeek() == DayOfWeek.TUESDAY ? null : "열량 645 kcal / 단백질 24 g / 칼슘 210 mg",
			date.getDayOfWeek() == DayOfWeek.TUESDAY ? null : "쌀: 국내산 / 닭고기: 국내산 / 배추: 국내산",
			IntStream.range(0, menus.size()).mapToObj(index -> new MealItem(
				new MealItemId(id("menu:" + key + ":" + index)), menus.get(index).name(), menus.get(index).rawText(),
				index, MealItemLabelingStatus.PENDING)).toList());
		var completed = collectionPersistence.complete(running,
			running.succeed(80, 120, raw.id(), startedAt.plusMillis(120)), raw,
			pendingMeal == null ? List.of() : List.of(pendingMeal));
		if (pendingMeal == null) {
			if (!completed.meals().isEmpty()) {
				throw new IllegalStateException("로컬 데이터 초기화 무급식 저장 결과 불일치.");
			}
			return null;
		}
		if (completed.meals().size() != 1 || !completed.meals().getFirst().applied()
			|| !completed.meals().getFirst().meal().id().equals(pendingMeal.id())) {
			throw new IllegalStateException("로컬 데이터 초기화 급식 저장 결과 불일치.");
		}
		Meal saved = completed.meals().getFirst().meal();
		if (status == MealLabelingStatus.PENDING) {
			return saved;
		}
		Meal labeled = new Meal(saved.id(), saved.schoolId(), saved.mealDate(), saved.mealType(), saved.sourceVersion(),
			saved.sourceReceivedAt(), status, saved.nutritionInfo(), saved.originInfo(), saved.items().stream()
				.map(item -> new MealItem(item.id(), item.name(), item.rawText(), item.displayOrder(),
					item.displayOrder() == 2 ? MealItemLabelingStatus.valueOf(status.name()) : MealItemLabelingStatus.LABELED))
				.toList());
		Map<MealItemId, List<Integer>> labels = new LinkedHashMap<>();
		for (MealItem item : labeled.items()) {
			labels.put(item.id(), item.labelingStatus() == MealItemLabelingStatus.LABELED
				? menus.get(item.displayOrder()).allergenCodes() : List.of());
		}
		if (!mealRepository.saveAllergenLabels(labeled, labels)) {
			throw new IllegalStateException("로컬 데이터 초기화 메뉴 라벨 저장 실패.");
		}
		return labeled;
	}

	private CollectionJob createPendingJob(String key, School school, LocalDate date, MealType type, Instant createdAt) {
		CollectionJobId jobId = new CollectionJobId(id("collection:" + key));
		if (collectionJobRepository.findById(jobId).isPresent()
			|| collectionJobRepository.findAdminPage(new AdminCollectionJobQuery(
				1, 1, CollectionJobStatus.PENDING, school.id().value(), date, type, null)).totalCount() > 0
			|| collectionJobRepository.findAdminPage(new AdminCollectionJobQuery(
				1, 1, CollectionJobStatus.RUNNING, school.id().value(), date, type, null)).totalCount() > 0) {
			throw new IllegalStateException("로컬 데이터 초기화 수집 작업 충돌.");
		}
		CollectionJob saved = collectionJobRepository.createOrGetActive(
			CollectionJob.pending(jobId, school.id(), date, type, createdAt), createdAt);
		if (!saved.id().equals(jobId)) {
			throw new IllegalStateException("로컬 데이터 초기화 활성 수집 작업 충돌.");
		}
		return saved;
	}

	private RawObjectMetadata storeRawMeal(School school, LocalDate date, MealType type, List<SeedMenu> menus,
		Instant receivedAt) {
		String mealCode = switch (type) { case BREAKFAST -> "1"; case LUNCH -> "2"; case DINNER -> "3"; };
		String rows = menus.isEmpty() ? "[]" : """
			[{"ATPT_OFCDC_SC_CODE":"%s","SD_SCHUL_CODE":"%s","SCHUL_NM":"%s","MMEAL_SC_CODE":"%s",
			"MLSV_YMD":"%s","DDISH_NM":"%s","CAL_INFO":"645 Kcal","NTR_INFO":"단백질 24 g","ORPLC_INFO":"쌀: 국내산"}]
			""".formatted(school.educationOfficeCode(), school.neisSchoolCode(), school.name(), mealCode,
			date.format(DateTimeFormatter.BASIC_ISO_DATE), menus.stream().map(SeedMenu::rawText).collect(Collectors.joining("<br/>"))).trim();
		String json = """
			{"localSeed":true,"schoolCode":"%s","mealDate":"%s","mealType":"%s",
			"mealServiceDietInfo":[{"head":[{"list_total_count":%d},{"RESULT":{"CODE":"%s","MESSAGE":"로컬 확인용 원문"}}]},{"row":%s}]}
			""".formatted(school.neisSchoolCode(), date, type, menus.isEmpty() ? 0 : 1,
			menus.isEmpty() ? "INFO-200" : "INFO-000", rows);
		if (menus.isEmpty()) {
			json = """
				{"localSeed":true,"schoolCode":"%s","mealDate":"%s","mealType":"%s",
				"RESULT":{"CODE":"INFO-200","MESSAGE":"로컬 확인용 무급식 원문"}}
				""".formatted(school.neisSchoolCode(), date, type);
		}
		return rawPayloadStorage.store(new RawPayload("local-data", json.getBytes(StandardCharsets.UTF_8),
			"application/json", receivedAt, receivedAt.plus(Duration.ofDays(30))));
	}

	private List<SeedMenu> menus(LocalDate date) {
		return switch (date.getDayOfWeek()) {
			case MONDAY -> List.of(new SeedMenu("현미밥", "현미밥", List.of()),
				new SeedMenu("달걀말이", "달걀말이(1)", List.of(1)), new SeedMenu("우유", "우유(2)", List.of(2)),
				new SeedMenu("배추김치", "배추김치", List.of()));
			case TUESDAY -> List.of(new SeedMenu("잡곡밥", "잡곡밥", List.of()),
				new SeedMenu("닭가슴살구이", "닭가슴살구이(15)", List.of(15)), new SeedMenu("맑은무국", "맑은무국", List.of()),
				new SeedMenu("사과", "사과", List.of()));
			case WEDNESDAY -> List.of(new SeedMenu("혼합곡밥", "혼합곡밥", List.of()),
				new SeedMenu("계절채소무침", "계절채소무침", List.of()), new SeedMenu("학교특식", "학교특식", List.of()),
				new SeedMenu("배", "배", List.of()));
			case THURSDAY -> List.of(new SeedMenu("쌀밥", "쌀밥", List.of()),
				new SeedMenu("채소찜", "채소찜", List.of()), new SeedMenu("확인 중 소스", "확인 중 소스", List.of()),
				new SeedMenu("깍두기", "깍두기", List.of()));
			default -> List.of(new SeedMenu("쌀밥", "쌀밥", List.of()),
				new SeedMenu("두부조림", "두부조림(5.6)", List.of(5, 6)), new SeedMenu("된장국", "된장국(5)", List.of(5)),
				new SeedMenu("깍두기", "깍두기", List.of()));
		};
	}

	private LocalDate weekStart(Instant now) {
		return now.atZone(LOCAL_ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	private Map<String, NotificationRequest> seedNotifications(Map<String, ChildProfile> children,
		List<School> schools, LocalDate week, Instant now) {
		ChildProfile child = children.get("milk-child");
		for (int offset = -5; offset < 0; offset++) {
			LocalDate date = week.plusDays(offset);
			for (MealType type : MealType.values()) {
				boolean hasMeal = date.getDayOfWeek().getValue() <= 5 && type == MealType.LUNCH;
				MealLabelingStatus status = !hasMeal ? null : switch (date.getDayOfWeek()) {
					case WEDNESDAY -> MealLabelingStatus.UNKNOWN;
					case THURSDAY -> MealLabelingStatus.LABELING_FAILED;
					default -> MealLabelingStatus.PENDING;
				};
				if (mealRepository.findByNaturalKey(child.schoolId(), date, type).isPresent()
					|| !mealRepository.findCollectedInRange(child.schoolId(), date, date).stream()
						.filter(result -> result.mealType() == type).toList().isEmpty()) {
					throw new IllegalStateException("로컬 데이터 초기화 과거 급식 대상 충돌.");
				}
				seedCollectedMeal(schools.getFirst(), date, type, status, now.minus(Duration.ofDays(3)));
			}
		}
		Map<String, NotificationRequest> notifications = seedNotificationHistory(child, week, now);
		for (int offset = 0; offset < 3; offset++) {
			ChildProfile other = children.get("other-child");
			LocalDate date = week.plusDays(offset);
			Instant createdAt = now.minus(Duration.ofMinutes(7)).plusSeconds(offset);
			notifications.put("other:" + date, createNotification("current:" + other.id().value() + ":" + date,
				seedNotificationTarget(other, date, createdAt), createdAt));
		}
		ChildProfile restricted = children.get("restricted-child");
		Instant beforeRestriction = now.minus(Duration.ofDays(2));
		NotificationRequest request = createNotification("restricted:" + restricted.id().value(),
			seedNotificationTarget(restricted, week.minusDays(2), beforeRestriction), beforeRestriction);
		notifications.put("restricted", notificationRepository.save(NotificationStatus.PENDING,
			request.cancelForOwnerAccessRestriction(now.minus(Duration.ofDays(1)))));
		return notifications;
	}

	private Map<String, NotificationRequest> seedNotificationHistory(ChildProfile child, LocalDate week, Instant now) {
		Map<String, NotificationRequest> notifications = new LinkedHashMap<>();
		for (int offset = -5; offset < 7; offset++) {
			LocalDate date = week.plusDays(offset);
			Instant createdAt = now.minus(Duration.ofMinutes(8)).plusSeconds(offset + 5);
			PendingNotificationTargetResult target = seedNotificationTarget(child, date, createdAt);
			if (offset == 0) {
				PendingNotificationTargetResult beforeLabeling = notificationTarget(child, date, true);
				NotificationRequest previous = createNotification("before-labeling:" + child.id().value() + ":" + date,
					beforeLabeling, createdAt.minusSeconds(20));
				persistNotificationEvent(previous);
			}
			NotificationRequest request = createNotification("current:" + child.id().value() + ":" + date, target, createdAt);
			NotificationStatus status = switch (offset) {
				case 1, 2 -> NotificationStatus.RETRY_PENDING;
				case 3 -> NotificationStatus.SENDING;
				case 4 -> NotificationStatus.FAILED;
				case 5 -> NotificationStatus.SENT;
				default -> NotificationStatus.PENDING;
			};
			notifications.put("milk:" + date, changeNotificationStatus(request, status, offset == 2));
		}
		return notifications;
	}

	private PendingNotificationTargetResult seedNotificationTarget(ChildProfile child, LocalDate date, Instant createdAt) {
		PendingNotificationTargetResult target = notificationTarget(child, date, false);
		NotificationPreference preference = preferenceRepository.findByChildProfileIdAndOwnerId(child.id(), child.ownerId())
			.orElseThrow();
		var saved = targetRepository.saveAll(List.of(new NotificationTargetCommand(target.notificationTargetId(),
			child.id(), child.ownerId(), child.schoolId(), date, preference.notificationTime(), preference.timezone(),
			NotificationTargetReason.valueOf(target.reason().name()), target.riskLevel(), target.riskVersion(), target.mealCount(), createdAt)));
		if (saved.createdCount() != 1 || saved.duplicateCount() != 0) {
			throw new IllegalStateException("로컬 데이터 초기화 알림 대상 충돌.");
		}
		return target;
	}

	private PendingNotificationTargetResult notificationTarget(ChildProfile child, LocalDate date, boolean beforeLabeling) {
		List<MealQueryResult> collected = mealRepository.findCollectedInRange(child.schoolId(), date, date);
		List<Meal> meals = collected.stream().map(MealQueryResult::meal).filter(Objects::nonNull).toList();
		UUID targetId = id("notification-target:" + child.id().value() + ":" + date);
		if (meals.isEmpty()) {
			return new PendingNotificationTargetResult(targetId, child.id(), child.ownerId(), date,
				collected.isEmpty() ? NotificationReason.RISK_PENDING : NotificationReason.NO_MEAL,
				collected.isEmpty() ? MealRiskLevel.PENDING : null, null, 0);
		}
		Map<MealItemId, List<Integer>> labels = mealRepository.findAllergenCodesByMealIds(meals.stream().map(Meal::id).toList());
		List<Integer> childAllergens = childAllergenRepository.findAllergenCodes(child.ownerId(), child.id());
		List<ChildMealRisk> risks = new ArrayList<>();
		for (Meal source : meals) {
			Meal meal = beforeLabeling ? new Meal(source.id(), source.schoolId(), source.mealDate(), source.mealType(),
				source.sourceVersion(), source.sourceReceivedAt(), MealLabelingStatus.PENDING, source.nutritionInfo(), source.originInfo(),
				source.items().stream().map(item -> new MealItem(item.id(), item.name(), item.rawText(), item.displayOrder(),
					MealItemLabelingStatus.PENDING)).toList()) : source;
			Map<MealItemId, List<Integer>> mealLabels = meal.items().stream().collect(Collectors.toMap(MealItem::id,
				item -> beforeLabeling ? List.of() : labels.getOrDefault(item.id(), List.of())));
			risks.add(ChildMealRiskCalculator.calculate(child.id(), childAllergens, meal, mealLabels));
		}
		// 이 시드의 같은 날짜 급식은 같은 메뉴·라벨 상태로 구성한다.
		MealRiskLevel level = risks.getFirst().riskLevel();
		if (risks.stream().anyMatch(risk -> risk.riskLevel() != level)) {
			throw new IllegalStateException("로컬 데이터 초기화 알림 사례의 위험도 구성 불일치.");
		}
		String canonical = risks.stream().sorted(Comparator.comparing(risk -> risk.mealId().value()))
			.map(risk -> risk.mealId().value() + ":" + risk.riskVersion() + ":" + risk.riskLevel())
			.collect(Collectors.joining("\n"));
		NotificationReason reason = switch (level) {
			case RISKY -> NotificationReason.RISK_DETECTED;
			case SAFE -> NotificationReason.NO_RISK;
			case UNKNOWN -> NotificationReason.RISK_UNKNOWN;
			case LABELING_FAILED -> NotificationReason.RISK_LABELING_FAILED;
			case PENDING -> NotificationReason.RISK_PENDING;
		};
		return new PendingNotificationTargetResult(targetId, child.id(), child.ownerId(), date,
			reason, level, sha256(canonical), meals.size());
	}

	private NotificationRequest createNotification(String key, PendingNotificationTargetResult target, Instant createdAt) {
		NotificationChannel channel = NotificationChannel.EMAIL;
		var saved = notificationRepository.saveCorrectionAware(NotificationRequest.pending(new NotificationId(id("notification:" + key)),
			target.notificationTargetId(), target.childProfileId(), target.ownerId(), target.notificationDate(), channel, target.reason(),
			NotificationRequestKeyFactory.dedupKey(target, channel), NotificationRequestKeyFactory.correctionKey(target, channel),
			NotificationRequestKeyFactory.contentVersion(target), false, null, 3, createdAt));
		if (!saved.created()) {
			throw new IllegalStateException("로컬 데이터 초기화 알림 요청 충돌.");
		}
		return saved.notificationRequest();
	}

	private NotificationRequest changeNotificationStatus(NotificationRequest request, NotificationStatus status, boolean futureRetry) {
		if (status == NotificationStatus.PENDING) {
			return request;
		}
		Instant startedAt = request.timestamps().createdAt().plusSeconds(1);
		NotificationRequest sending = notificationRepository.save(request.status(), request.startSending(startedAt));
		return switch (status) {
			case SENDING -> sending;
			case SENT -> notificationRepository.save(NotificationStatus.SENDING, sending.markSent(startedAt.plusSeconds(1)));
			case RETRY_PENDING -> notificationRepository.save(NotificationStatus.SENDING, sending.markFailed(
				"SMTP_SEND_FAILED", "로컬 확인용 메일 전송 실패 이력.", startedAt.plusSeconds(1),
				futureRetry ? Duration.ofHours(1) : Duration.ofMinutes(1)));
			case FAILED -> {
				NotificationRequest failed = sending;
				for (int attempt = 0; attempt < request.maxAttempts(); attempt++) {
					failed = notificationRepository.save(NotificationStatus.SENDING, failed.markFailed(
						"SMTP_SEND_FAILED", "로컬 확인용 최대 시도 실패 이력.", startedAt.plusSeconds(attempt * 12L + 1), Duration.ofSeconds(10)));
					if (failed.status() == NotificationStatus.RETRY_PENDING) {
						failed = notificationRepository.save(NotificationStatus.RETRY_PENDING, failed.startSending(failed.nextAttemptAt()));
					}
				}
				yield failed;
			}
			default -> throw new IllegalArgumentException("지원하지 않는 로컬 알림 사례 상태.");
		};
	}

	private OutboxEvent persistNotificationEvent(NotificationRequest request) {
		OutboxEvent event = OutboxEvent.pending(id("notification-event:" + request.id().value()),
			NotificationRequestEvents.NOTIFICATION_REQUESTED, NotificationRequestEvents.notificationRequestedPayload(request),
			request.timestamps().createdAt().plusMillis(100));
		outboxRepository.save(event);
		outboxRepository.markPublished(event.markPublished(event.occurredAt().plusMillis(100)));
		return event;
	}

	private void seedNotificationEvents(Map<String, NotificationRequest> notifications, User admin, Instant now) {
		for (NotificationRequest request : notifications.values()) {
			OutboxEvent original = persistNotificationEvent(request);
			boolean actionable = request.status() == NotificationStatus.PENDING
				|| request.status() == NotificationStatus.RETRY_PENDING && !request.nextAttemptAt().isAfter(now);
			if (actionable || request.status() == NotificationStatus.SENT) {
				UUID dlqId = id("dlq:" + request.id().value());
				Instant failedAt = request.status() == NotificationStatus.SENT
					? original.occurredAt().plusMillis(200) : request.timestamps().updatedAt().plusSeconds(1);
				deadLetterRepository.save(new DeadLetterEventCommand(dlqId, original.id().toString(), original.type(),
					original.payload(), 3, failedAt));
				deadLetterRepository.findById(dlqId).orElseThrow(() -> new IllegalStateException("로컬 데이터 초기화 DLQ 저장 실패."));
				if (request.status() == NotificationStatus.SENT) {
					Instant reprocessedAt = request.sentAt().minusMillis(500);
					var result = notificationReprocessRepository.save(new AdminNotificationReprocessRequestCommand(
						id("dlq-reprocess:" + dlqId), "local-data-" + dlqId, admin.id(), dlqId, reprocessedAt));
					if (result.duplicate() || result.conflict() || !deadLetterRepository.markReprocessed(dlqId, admin.id(), reprocessedAt)) {
						throw new IllegalStateException("로컬 데이터 초기화 DLQ 재처리 이력 충돌.");
					}
					OutboxEvent replay = OutboxEvent.pending(id("dlq-replay:" + dlqId), original.type(), original.payload(), reprocessedAt);
					outboxRepository.save(replay);
					outboxRepository.markPublished(replay.markPublished(reprocessedAt.plusMillis(100)));
					auditLogRepository.save(new AdminAuditLogCommand(id("audit:dlq:" + dlqId), admin.id(), admin.id(),
						"REPROCESS_NOTIFICATION_DLQ", "SUCCEEDED", "deadLetterEventId=%s duplicate=false".formatted(dlqId), reprocessedAt));
				}
			}
		}
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
		}
	}

	private List<CollectionJob> seedCollectionOperations(School school, User admin, LocalDate week, Instant now) {
		List<CollectionJob> jobs = new ArrayList<>();
		LocalDate base = week.minusDays(21);
		jobs.add(createPendingJob("pending-recent", school, base, MealType.LUNCH, now.minusSeconds(30)));
		CollectionJob oldPending = createPendingJob("pending-old", school, base.plusDays(1), MealType.LUNCH,
			now.minus(Duration.ofMinutes(20)));
		jobs.add(oldPending);
		saveCollectionRequest("old-execution", admin, oldPending, oldPending,
			AdminCollectionRequestType.EXECUTION, now.minus(Duration.ofMinutes(10)));
		jobs.add(runningCollection("running-live", school, base.plusDays(2), now.minus(Duration.ofMinutes(1)), now.plus(Duration.ofMinutes(30))));
		jobs.add(runningCollection("running-expired", school, base.plusDays(3), now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofMinutes(10))));
		jobs.add(failedCollection("failed-unrequested", school, base.plusDays(4), "NEIS_TIMEOUT", false, now.minus(Duration.ofHours(1))));
		jobs.add(failedCollection("failed-raw", school, base.plusDays(5), "NEIS_INVALID_RESPONSE", true, now.minus(Duration.ofHours(1))));

		CollectionJob progressSource = failedCollection("progress-source", school, base.plusDays(6), "NEIS_IO_ERROR", false,
			now.minus(Duration.ofHours(3)));
		CollectionJob progressTarget = createPendingJob("progress-target", school, progressSource.mealDate(), MealType.LUNCH,
			now.minus(Duration.ofMinutes(5)));
		jobs.addAll(List.of(progressSource, progressTarget));
		saveCollectionRequest("recovery-progress", admin, progressSource, progressTarget,
			AdminCollectionRequestType.RECOLLECTION, progressTarget.timestamps().createdAt());

		CollectionJob failedSource = failedCollection("recovery-failed-source", school, base.plusDays(7), "NEIS_HTTP_5XX", false,
			now.minus(Duration.ofHours(4)));
		CollectionJob failedTarget = failedCollection("recovery-failed-target", school, failedSource.mealDate(), "NEIS_TIMEOUT", false,
			now.minus(Duration.ofHours(3)));
		jobs.addAll(List.of(failedSource, failedTarget));
		saveCollectionRequest("recovery-failed", admin, failedSource, failedTarget,
			AdminCollectionRequestType.RECOLLECTION, failedTarget.timestamps().createdAt());

		CollectionJob successSource = failedCollection("recovery-succeeded-source", school, base.plusDays(8), "NEIS_TIMEOUT", false,
			now.minus(Duration.ofHours(5)));
		seedCollectedMeal(school, successSource.mealDate(), MealType.LUNCH, null, now.minus(Duration.ofMinutes(230)));
		CollectionJob successTarget = collectedJob(school.id(), successSource.mealDate(), MealType.LUNCH);
		jobs.addAll(List.of(successSource, successTarget));
		saveCollectionRequest("recovery-succeeded", admin, successSource, successTarget,
			AdminCollectionRequestType.RECOLLECTION, successTarget.timestamps().createdAt());

		CollectionJob mixedSource = failedCollection("recovery-mixed-source", school, base.plusDays(9), "NEIS_IO_ERROR", false,
			now.minus(Duration.ofHours(7)));
		seedCollectedMeal(school, mixedSource.mealDate(), MealType.LUNCH, null, now.minus(Duration.ofMinutes(350)));
		CollectionJob mixedSuccess = collectedJob(school.id(), mixedSource.mealDate(), MealType.LUNCH);
		saveCollectionRequest("recovery-mixed-succeeded", admin, mixedSource, mixedSuccess,
			AdminCollectionRequestType.RECOLLECTION, mixedSuccess.timestamps().createdAt());
		CollectionJob mixedFailure = failedCollection("recovery-mixed-latest", school, mixedSource.mealDate(), "NEIS_HTTP_5XX", false,
			now.minus(Duration.ofHours(5)));
		saveCollectionRequest("recovery-mixed-latest", admin, mixedSource, mixedFailure,
			AdminCollectionRequestType.RECOLLECTION, mixedFailure.timestamps().createdAt());
		jobs.addAll(List.of(mixedSource, mixedSuccess, mixedFailure));

		CollectionJob expiredSource = failedCollection("recovery-expired-source", school, base.plusDays(10), "NEIS_TIMEOUT", false,
			now.minus(Duration.ofHours(3)));
		CollectionJob expiredTarget = runningCollection("recovery-expired-target", school, expiredSource.mealDate(),
			now.minus(Duration.ofHours(1)), now.minus(Duration.ofMinutes(30)));
		jobs.addAll(List.of(expiredSource, expiredTarget));
		saveCollectionRequest("recovery-expired", admin, expiredSource, expiredTarget,
			AdminCollectionRequestType.RECOLLECTION, expiredTarget.timestamps().createdAt());
		for (int index = 0; index < 3; index++) {
			jobs.add(failedCollection("failed-history-" + index, school, week.minusDays(35 + index),
				index == 0 ? "NEIS_HTTP_5XX" : "NEIS_TIMEOUT", false, now.minus(Duration.ofHours(6)).plusSeconds(index)));
		}
		return jobs;
	}

	private CollectionJob runningCollection(String key, School school, LocalDate date, Instant startedAt, Instant leaseUntil) {
		CollectionJob pending = createPendingJob(key, school, date, MealType.LUNCH, startedAt.minusSeconds(1));
		return collectionJobRepository.save(CollectionJobStatus.PENDING, pending.start(startedAt, leaseUntil));
	}

	private CollectionJob failedCollection(String key, School school, LocalDate date, String failureCode, boolean hasRaw, Instant createdAt) {
		CollectionJob running = runningCollection(key, school, date, createdAt.plusSeconds(1), createdAt.plusSeconds(121));
		RawObjectMetadata raw = null;
		if (hasRaw) {
			String json = """
				{"localSeed":true,"schoolCode":"%s","mealDate":"%s","mealType":"LUNCH",
				"mealServiceDietInfo":[{"head":[{"list_total_count":1},{"RESULT":{"CODE":"INFO-000","MESSAGE":"로컬 확인용 원문"}}]},
				{"row":[{"ATPT_OFCDC_SC_CODE":"%s","SD_SCHUL_CODE":"%s","MLSV_YMD":"%s"}]}]}
				""".formatted(school.neisSchoolCode(), date, school.educationOfficeCode(), school.neisSchoolCode(),
				date.format(DateTimeFormatter.BASIC_ISO_DATE));
			Instant receivedAt = createdAt.plusMillis(1250);
			raw = rawPayloadStorage.store(new RawPayload("local-data", json.getBytes(StandardCharsets.UTF_8),
				"application/json", receivedAt, receivedAt.plus(Duration.ofDays(30))));
		}
		return collectionJobRepository.save(CollectionJobStatus.RUNNING, running.fail(250, 300,
			raw == null ? null : raw.id(), failureCode, "로컬 확인용 수집 실패 이력.", createdAt.plusMillis(1300)));
	}

	private CollectionJob collectedJob(SchoolId schoolId, LocalDate date, MealType type) {
		return collectionJobRepository.findById(new CollectionJobId(id("collection:meal:" + schoolId.value() + ":" + date + ":" + type)))
			.orElseThrow();
	}

	private void saveCollectionRequest(String key, User admin, CollectionJob source, CollectionJob target,
		AdminCollectionRequestType type, Instant requestedAt) {
		var result = recollectionRepository.save(new AdminRecollectionRequestCommand(id("collection-request:" + key),
			"local-data-" + key, admin.id(), source.id(), target.id(), requestedAt, type));
		if (result.duplicate() || result.conflict() || !result.collectionJobId().equals(target.id())) {
			throw new IllegalStateException("로컬 데이터 초기화 수집 요청 이력 충돌.");
		}
		auditLogRepository.save(new AdminAuditLogCommand(id("audit:collection:" + key), admin.id(), admin.id(),
			type == AdminCollectionRequestType.EXECUTION ? "REQUEST_COLLECTION_EXECUTION" : "REQUEST_COLLECTION_RETRY", "SUCCEEDED",
			"requestAccepted=true action=%s originalCollectionJobId=%s collectionJobId=%s requestedAt=%s".formatted(
				type, source.id().value(), target.id().value(), requestedAt), requestedAt));
	}

	private void seedExternalLogs(List<CollectionJob> jobs) {
		for (CollectionJob job : jobs) {
			if (job.status() != CollectionJobStatus.SUCCEEDED && job.status() != CollectionJobStatus.FAILED) {
				continue;
			}
			boolean httpSucceeded = job.status() == CollectionJobStatus.SUCCEEDED || "NEIS_INVALID_RESPONSE".equals(job.failureCode());
			Integer httpStatus = httpSucceeded ? Integer.valueOf(200)
				: "NEIS_HTTP_5XX".equals(job.failureCode()) ? Integer.valueOf(503) : null;
			externalLogRepository.save(new ExternalApiLogCommand(id("external-log:" + job.id().value()), "NEIS", "MEAL_SERVICE_DIET_INFO",
				job.schoolId(), job.mealDate(), job.mealType(), "GET", "https://open.neis.go.kr/hub/mealServiceDietInfo",
				httpStatus, httpSucceeded ? "SUCCEEDED" : "FAILED", httpSucceeded ? null : job.failureCode(),
				job.responseTimeMillis(), job.timestamps().updatedAt()));
		}
	}

	private void seedUserAudits(Map<String, User> users, Instant now) {
		User admin = users.get("admin");
		Instant changedAt = now.minus(Duration.ofDays(1));
		saveAccessAudit("promoted", admin, users.get("promoted-admin"), "PROMOTE_USER_TO_ADMIN",
			UserRole.MEMBER, UserRole.ADMIN, UserStatus.ACTIVE, UserStatus.ACTIVE, "로컬 확인용 관리자 승격.", changedAt);
		for (String key : List.of("suspended", "unsuspend-target")) {
			saveAccessAudit(key, admin, users.get(key), "SUSPEND_USER", UserRole.MEMBER, UserRole.MEMBER,
				UserStatus.ACTIVE, UserStatus.SUSPENDED, "로컬 확인용 운영 제한.", changedAt);
		}
		saveAccessAudit("unsuspended-before", admin, users.get("unsuspended"), "SUSPEND_USER", UserRole.MEMBER, UserRole.MEMBER,
			UserStatus.ACTIVE, UserStatus.SUSPENDED, "로컬 확인용 운영 제한.", changedAt.minus(Duration.ofDays(1)));
		saveAccessAudit("unsuspended-final", admin, users.get("unsuspended"), "UNSUSPEND_USER", UserRole.MEMBER, UserRole.MEMBER,
			UserStatus.SUSPENDED, UserStatus.ACTIVE, "로컬 확인용 운영 제한 해제.", changedAt);
	}

	private void saveAccessAudit(String key, User admin, User target, String action, UserRole beforeRole, UserRole afterRole,
		UserStatus beforeStatus, UserStatus afterStatus, String reason, Instant changedAt) {
		UUID eventId = id("user-access:" + key);
		accessAuditRepository.save(new AdminUserAccessAuditCommand(eventId, admin.id(), target.id(), action, beforeRole, afterRole,
			beforeStatus, afterStatus, reason, changedAt));
		auditLogRepository.save(new AdminAuditLogCommand(id("audit:user:" + key), admin.id(), target.id(), action, "SUCCEEDED",
			"USER_ACCESS_AUDIT_EVENT:" + eventId, changedAt));
	}

	private static UserId userId(String key) {
		return new UserId(id("user:" + key));
	}

	private static UUID id(String key) {
		return UUID.nameUUIDFromBytes(("aller-meal:local-data:" + key).getBytes(StandardCharsets.UTF_8));
	}

	private record SeedUser(String key, boolean verified, UserStatus status) {

		private String email() {
			return key + "@local.allermeal.test";
		}
	}

	private record SeedChild(String key, String ownerKey, int schoolIndex, String name, int grade, int classNumber,
		List<Integer> allergenCodes, boolean emailEnabled, LocalTime notificationTime) {
	}

	private record SeedMenu(String name, String rawText, List<Integer> allergenCodes) {
	}
}
