package com.allermeal.infra.user;

import com.allermeal.application.port.out.AdminUserQueryRepository;
import com.allermeal.application.port.out.result.AdminUserQueryPageResult;
import com.allermeal.application.port.out.result.AdminUserQueryResult;
import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.EncryptedEmail;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAdminUserQueryRepository implements AdminUserQueryRepository {

	private static final List<UserStatus> VISIBLE_STATUSES = List.of(
		UserStatus.ACTIVE, UserStatus.WITHDRAWAL_PENDING, UserStatus.SUSPENDED);

	private final SpringDataUserRepository repository;

	public JpaAdminUserQueryRepository(SpringDataUserRepository repository) {
		this.repository = repository;
	}

	@Override
	public AdminUserQueryPageResult findVisibleUsers(
		UserStatus status,
		UserId userId,
		EmailSearchHash emailSearchHash,
		int page,
		int pageSize
	) {
		List<UserStatus> statuses = status == null ? VISIBLE_STATUSES : List.of(status);
		PageRequest pageable = PageRequest.of(page - 1, pageSize,
			Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Page<AdminUserQueryProjection> result;
		if (userId != null) {
			Optional<AdminUserQueryProjection> item = repository.findAdminUserByIdAndStatusIn(userId.value(), statuses);
			return new AdminUserQueryPageResult(
				page == 1 ? item.map(this::toResult).stream().toList() : List.of(),
				page, pageSize, item.isPresent() ? 1 : 0);
		}
		if (emailSearchHash != null) {
			result = repository.findAdminUsersByEmailSearchHashAndStatusIn(emailSearchHash.value(), statuses, pageable);
		} else {
			result = repository.findAdminUsersByStatusIn(statuses, pageable);
		}
		return new AdminUserQueryPageResult(result.getContent().stream().map(this::toResult).toList(),
			page, pageSize, result.getTotalElements());
	}

	@Override
	public Optional<AdminUserQueryResult> findVisibleUserById(UserId userId) {
		return repository.findAdminUserByIdAndStatusIn(userId.value(), VISIBLE_STATUSES).map(this::toResult);
	}

	private AdminUserQueryResult toResult(AdminUserQueryProjection projection) {
		return new AdminUserQueryResult(
			new UserId(projection.getUserId()),
			new EncryptedEmail(projection.getEncryptedEmail()),
			projection.getRole(),
			projection.getStatus(),
			projection.getEmailVerificationStatus(),
			projection.getCreatedAt(),
			projection.getWithdrawalDueAt(),
			projection.getVersion());
	}
}
