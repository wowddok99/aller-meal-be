package com.allermeal.application.auth;

import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.EmailVerificationTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.domain.user.User;
import com.allermeal.domain.user.UserId;
import java.time.Clock;
import java.util.Objects;

public final class EmailVerificationConfirmer {

	private final UserRepository userRepository;
	private final EmailVerificationTokenHasher tokenHasher;
	private final EmailVerificationTokenStore tokenStore;
	private final Clock clock;

	public EmailVerificationConfirmer(
		UserRepository userRepository,
		EmailVerificationTokenHasher tokenHasher,
		EmailVerificationTokenStore tokenStore,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.tokenHasher = tokenHasher;
		this.tokenStore = tokenStore;
		this.clock = clock;
	}

	public EmailVerificationConfirmResult confirm(String token) {
		String requiredToken = requireToken(token);
		String tokenHash = tokenHasher.hash(requiredToken);
		UserId userId = tokenStore.consume(tokenHash)
			.orElseThrow(InvalidEmailVerificationTokenException::new);
		User user = userRepository.findById(userId)
			.orElseThrow(InvalidEmailVerificationTokenException::new);
		User verifiedUser = userRepository.save(user.verifyEmail(clock.instant()));
		return new EmailVerificationConfirmResult(verifiedUser.id(), verifiedUser.emailVerificationStatus());
	}

	private String requireToken(String token) {
		String requiredToken = Objects.requireNonNullElse(token, "").trim();
		if (requiredToken.isBlank() || requiredToken.length() > 512) {
			throw new InvalidEmailVerificationTokenException();
		}
		return requiredToken;
	}
}
