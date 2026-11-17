package com.allermeal.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.allermeal.api.error.ApiExceptionHandler;
import com.allermeal.application.auth.*;
import com.allermeal.application.port.out.*;
import com.allermeal.domain.user.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

final class SignupProfileContractTest {
	private final UserRepository users = mock(UserRepository.class);
	private final EmailVerificationRequester mail = mock(EmailVerificationRequester.class);
	private final AtomicReference<User> saved = new AtomicReference<>();
	private final SignupService service;
	private final MockMvc mvc;

	SignupProfileContractTest() {
		when(users.save(any())).thenAnswer(invocation -> { User user = invocation.getArgument(0); saved.set(user); return user; });
		PasswordHasher passwords = mock(PasswordHasher.class);
		when(passwords.hash(anyString())).thenReturn("password-hash");
		service = new SignupService(users, email -> "v1:test:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA==",
			email -> new EmailSearchHash("0".repeat(64)), passwords, mail, Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
		mvc = MockMvcBuilders.standaloneSetup(new AuthController(new TransactionalSignupHandler(service), null, null,
			null, null, null, null, null)).setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@Test void oldAndPartialRequestsAndBothValuesAreAccepted() throws Exception {
		request("").andExpect(status().isCreated());
		assertNull(saved.get().displayName()); assertNull(saved.get().usageType());
		request(",\"displayName\":null,\"usageType\":null").andExpect(status().isCreated());
		assertNull(saved.get().displayName()); assertNull(saved.get().usageType());
		request(",\"displayName\":\"" + " ".repeat(51) + "a\"").andExpect(status().isCreated());
		assertEquals("a", saved.get().displayName());
		request(",\"displayName\":\" \\uFEFF😀한글 \",\"usageType\":\"STUDENT\"").andExpect(status().isCreated());
		assertEquals("😀한글", saved.get().displayName()); assertEquals("STUDENT", saved.get().usageType());
		assertEquals(UserRole.MEMBER, saved.get().role());
		request(",\"usageType\":\"PARENT\"").andExpect(status().isCreated());
		assertNull(saved.get().displayName()); assertEquals("PARENT", saved.get().usageType());
		request(",\"displayName\":\"이름\",\"usageType\":null").andExpect(status().isCreated());
		assertEquals("이름", saved.get().displayName()); assertNull(saved.get().usageType());
	}

	@Test void rejectsInvalidTypesAndValuesAtHttpBoundary() throws Exception {
		for (String extra : new String[] {",\"displayName\":42", ",\"displayName\":true", ",\"usageType\":12",
			",\"usageType\":\"student\"", ",\"usageType\":\"\"", ",\"displayName\":\"   \"",
			",\"displayName\":\"a\\u0001b\"", ",\"displayName\":\"\\uD800\"",
			",\"displayName\":\"" + "😀".repeat(51) + "\""}) {
			request(extra).andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("INVALID_REQUEST")));
		}
		request(",\"displayName\":\"" + "😀".repeat(50) + "\"").andExpect(status().isCreated());
	}

	@Test void transitionsPreserveProfileExceptFinalDeletion() {
		service.signup(new SignupCommand("test@example.com", "password123", "표시 이름", "PARENT"));
		User user = saved.get();
		Instant now = user.timestamps().createdAt();
		User pending = user.requestWithdrawal(now.plusSeconds(1), now.plusSeconds(10));
		assertEquals("표시 이름", pending.cancelWithdrawal(now.plusSeconds(2)).displayName());
		User deleted = pending.markPersonalDataDeleted(user.encryptedEmail(), user.emailSearchHash(), user.passwordHash(), now.plusSeconds(10));
		assertNull(deleted.displayName()); assertNull(deleted.usageType());
		assertFalse(user.toString().contains("표시 이름"));
	}

	private org.springframework.test.web.servlet.ResultActions request(String extra) throws Exception {
		return mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"test@example.com\",\"password\":\"password123\"" + extra + "}"));
	}
}
