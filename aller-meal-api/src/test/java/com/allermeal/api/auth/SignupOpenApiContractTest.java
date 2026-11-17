package com.allermeal.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.allermeal.api.openapi.OpenApiConfiguration;
import com.allermeal.application.auth.*;
import com.allermeal.application.port.out.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OpenApiConfiguration.class)
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@ImportAutoConfiguration(classes = {SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, MultipleOpenApiSupportConfiguration.class})
final class SignupOpenApiContractTest {
	@Autowired MockMvc mvc;
	@MockitoBean TransactionalSignupHandler signup;
	@MockitoBean EmailVerificationRequester requester;
	@MockitoBean EmailVerificationConfirmer confirmer;
	@MockitoBean LoginService login;
	@MockitoBean RefreshService refresh;
	@MockitoBean PasswordResetRequester reset;
	@MockitoBean PasswordResetConfirmer resetConfirm;
	@MockitoBean AuthCookieWriter cookies;
	@MockitoBean AccessTokenIssuer issuer;
	@MockitoBean UserRepository users;
	@MockitoBean StringRedisTemplate redis;

	@MockitoBean java.time.Clock clock;

	@Test void exposesOptionalTypedProfileFields() throws Exception {
		mvc.perform(get("/v3/api-docs/public"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.schemas.SignupRequest.properties.displayName.type[0]").value("string"))
			.andExpect(jsonPath("$.components.schemas.SignupRequest.properties.displayName.maxLength").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.SignupRequest.properties.displayName.type[1]").value("null"))
			.andExpect(jsonPath("$.components.schemas.SignupRequest.properties.usageType.type[0]").value("string"))
			.andExpect(jsonPath("$.components.schemas.SignupRequest.properties.usageType.type[1]").value("null"))
			.andExpect(jsonPath("$.components.schemas.SignupRequest.properties.usageType.enum[0]").value("STUDENT"))
			.andExpect(jsonPath("$.components.schemas.SignupRequest.properties.usageType.enum[1]").value("PARENT"))
			.andExpect(jsonPath("$.components.schemas.SignupRequest.properties.usageType.enum[2]").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.components.schemas.SignupRequest.required").doesNotExist());
	}
}
