package com.allermeal.infra.config;

import com.allermeal.application.auth.EmailVerificationConfirmer;
import com.allermeal.application.auth.EmailVerificationRequester;
import com.allermeal.application.auth.LoginService;
import com.allermeal.application.auth.PasswordResetConfirmer;
import com.allermeal.application.auth.PasswordResetRequester;
import com.allermeal.application.auth.RefreshService;
import com.allermeal.application.auth.SignupService;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.EmailEncryptor;
import com.allermeal.application.port.out.EmailSearchHasher;
import com.allermeal.application.port.out.EmailVerificationMailSender;
import com.allermeal.application.port.out.EmailVerificationTokenHasher;
import com.allermeal.application.port.out.EmailVerificationTokenStore;
import com.allermeal.application.port.out.PasswordHasher;
import com.allermeal.application.port.out.PasswordResetMailSender;
import com.allermeal.application.port.out.PasswordResetTokenStore;
import com.allermeal.application.port.out.RefreshTokenStore;
import com.allermeal.application.port.out.UserRepository;
import com.allermeal.application.port.out.VerificationTokenGenerator;
import com.allermeal.infra.auth.AesGcmEmailEncryptor;
import com.allermeal.infra.auth.HmacSha256AccessTokenIssuer;
import com.allermeal.infra.auth.Pbkdf2PasswordHasher;
import com.allermeal.infra.auth.RedisEmailVerificationTokenStore;
import com.allermeal.infra.auth.RedisPasswordResetTokenStore;
import com.allermeal.infra.auth.RedisRefreshTokenStore;
import com.allermeal.infra.auth.SecureRandomVerificationTokenGenerator;
import com.allermeal.infra.auth.Sha256EmailSearchHasher;
import com.allermeal.infra.auth.Sha256EmailVerificationTokenHasher;
import com.allermeal.infra.auth.SmtpEmailVerificationMailSender;
import com.allermeal.infra.auth.SmtpPasswordResetMailSender;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class AuthConfiguration {

	@Bean
	SecureRandom secureRandom() {
		return new SecureRandom();
	}

	@Bean
	EmailSearchHasher emailSearchHasher() {
		return new Sha256EmailSearchHasher();
	}

	@Bean
	EmailEncryptor emailEncryptor(
		@Value("${safe-meal.auth.email-encryption-key}") String encodedKey,
		@Value("${safe-meal.auth.email-encryption-key-version:v1-local}") String keyVersion,
		SecureRandom secureRandom
	) {
		return new AesGcmEmailEncryptor(Base64.getDecoder().decode(encodedKey), keyVersion, secureRandom);
	}

	@Bean
	PasswordHasher passwordHasher(
		SecureRandom secureRandom,
		@Value("${safe-meal.auth.password-hash-iterations:210000}") int iterations
	) {
		return new Pbkdf2PasswordHasher(secureRandom, iterations);
	}

	@Bean
	VerificationTokenGenerator verificationTokenGenerator(SecureRandom secureRandom) {
		return new SecureRandomVerificationTokenGenerator(secureRandom);
	}

	@Bean
	EmailVerificationTokenHasher emailVerificationTokenHasher() {
		return new Sha256EmailVerificationTokenHasher();
	}

	@Bean
	EmailVerificationTokenStore emailVerificationTokenStore(StringRedisTemplate redisTemplate) {
		return new RedisEmailVerificationTokenStore(redisTemplate);
	}

	@Bean
	PasswordResetTokenStore passwordResetTokenStore(StringRedisTemplate redisTemplate) {
		return new RedisPasswordResetTokenStore(redisTemplate);
	}

	@Bean
	RefreshTokenStore refreshTokenStore(StringRedisTemplate redisTemplate) {
		return new RedisRefreshTokenStore(redisTemplate);
	}

	@Bean
	AccessTokenIssuer accessTokenIssuer(
		@Value("${safe-meal.auth.access-token-signing-key}") String encodedKey,
		Clock clock,
		ObjectMapper objectMapper
	) {
		return new HmacSha256AccessTokenIssuer(Base64.getDecoder().decode(encodedKey), clock, objectMapper);
	}

	@Bean
	EmailVerificationMailSender emailVerificationMailSender(
		JavaMailSender mailSender,
		@Value("${safe-meal.auth.email-from:no-reply@allermeal.local}") String from,
		@Value("${safe-meal.auth.email-verification-base-url}") String verificationBaseUrl
	) {
		return new SmtpEmailVerificationMailSender(mailSender, from, verificationBaseUrl);
	}

	@Bean
	PasswordResetMailSender passwordResetMailSender(
		JavaMailSender mailSender,
		@Value("${safe-meal.auth.email-from:no-reply@allermeal.local}") String from,
		@Value("${safe-meal.auth.password-reset-base-url}") String passwordResetBaseUrl
	) {
		return new SmtpPasswordResetMailSender(mailSender, from, passwordResetBaseUrl);
	}

	@Bean
	EmailVerificationRequester emailVerificationRequester(
		UserRepository userRepository,
		EmailSearchHasher emailSearchHasher,
		VerificationTokenGenerator tokenGenerator,
		EmailVerificationTokenHasher tokenHasher,
		EmailVerificationTokenStore tokenStore,
		EmailVerificationMailSender mailSender,
		@Value("${safe-meal.auth.email-verification-token-ttl:30m}") Duration tokenTtl
	) {
		return new EmailVerificationRequester(
			userRepository, emailSearchHasher, tokenGenerator, tokenHasher, tokenStore, mailSender, tokenTtl);
	}

	@Bean
	EmailVerificationConfirmer emailVerificationConfirmer(
		UserRepository userRepository,
		EmailVerificationTokenHasher tokenHasher,
		EmailVerificationTokenStore tokenStore,
		Clock clock
	) {
		return new EmailVerificationConfirmer(userRepository, tokenHasher, tokenStore, clock);
	}

	@Bean
	LoginService loginService(
		UserRepository userRepository,
		EmailSearchHasher emailSearchHasher,
		PasswordHasher passwordHasher,
		AccessTokenIssuer accessTokenIssuer,
		VerificationTokenGenerator verificationTokenGenerator,
		EmailVerificationTokenHasher tokenHasher,
		RefreshTokenStore refreshTokenStore,
		@Value("${safe-meal.auth.access-token-ttl:15m}") Duration accessTokenTtl,
		@Value("${safe-meal.auth.refresh-token-ttl:14d}") Duration refreshTokenTtl,
		Clock clock
	) {
		return new LoginService(
			userRepository, emailSearchHasher, passwordHasher, accessTokenIssuer, verificationTokenGenerator,
			tokenHasher, refreshTokenStore, accessTokenTtl, refreshTokenTtl, clock);
	}

	@Bean
	RefreshService refreshService(
		UserRepository userRepository,
		AccessTokenIssuer accessTokenIssuer,
		VerificationTokenGenerator verificationTokenGenerator,
		EmailVerificationTokenHasher tokenHasher,
		RefreshTokenStore refreshTokenStore,
		@Value("${safe-meal.auth.access-token-ttl:15m}") Duration accessTokenTtl,
		@Value("${safe-meal.auth.refresh-token-ttl:14d}") Duration refreshTokenTtl,
		Clock clock
	) {
		return new RefreshService(
			userRepository, accessTokenIssuer, verificationTokenGenerator, tokenHasher, refreshTokenStore,
			accessTokenTtl, refreshTokenTtl, clock);
	}

	@Bean
	PasswordResetRequester passwordResetRequester(
		UserRepository userRepository,
		EmailSearchHasher emailSearchHasher,
		VerificationTokenGenerator tokenGenerator,
		EmailVerificationTokenHasher tokenHasher,
		PasswordResetTokenStore tokenStore,
		PasswordResetMailSender mailSender,
		@Value("${safe-meal.auth.password-reset-token-ttl:30m}") Duration tokenTtl
	) {
		return new PasswordResetRequester(
			userRepository, emailSearchHasher, tokenGenerator, tokenHasher, tokenStore, mailSender, tokenTtl);
	}

	@Bean
	PasswordResetConfirmer passwordResetConfirmer(
		UserRepository userRepository,
		PasswordHasher passwordHasher,
		EmailVerificationTokenHasher tokenHasher,
		PasswordResetTokenStore tokenStore,
		RefreshTokenStore refreshTokenStore,
		@Value("${safe-meal.auth.refresh-token-ttl:14d}") Duration refreshTokenTtl,
		Clock clock
	) {
		return new PasswordResetConfirmer(
			userRepository, passwordHasher, tokenHasher, tokenStore, refreshTokenStore, refreshTokenTtl, clock);
	}

	@Bean
	SignupService signupService(
		UserRepository userRepository,
		EmailEncryptor emailEncryptor,
		EmailSearchHasher emailSearchHasher,
		PasswordHasher passwordHasher,
		EmailVerificationRequester verificationRequester,
		Clock clock
	) {
		return new SignupService(
			userRepository, emailEncryptor, emailSearchHasher, passwordHasher, verificationRequester, clock);
	}
}
