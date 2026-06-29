package com.allermeal.application.port.out;

import com.allermeal.domain.user.EmailSearchHash;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import com.allermeal.domain.user.UserRole;
import java.util.Optional;

public interface UserRepository {

	User save(User user);

	Optional<User> findById(UserId userId);

	Optional<User> findByEmailSearchHash(EmailSearchHash emailSearchHash);

	boolean existsByEmailSearchHash(EmailSearchHash emailSearchHash);

	boolean existsByRole(UserRole role);
}
