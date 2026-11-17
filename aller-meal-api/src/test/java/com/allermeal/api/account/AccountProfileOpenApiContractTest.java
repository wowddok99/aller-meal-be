package com.allermeal.api.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.allermeal.api.openapi.OpenApiConfiguration;
import com.allermeal.application.account.AccountProfileService;
import com.allermeal.application.auth.RefreshService;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

@WebMvcTest(controllers = AccountProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OpenApiConfiguration.class)
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@ImportAutoConfiguration(classes = {SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, MultipleOpenApiSupportConfiguration.class})
final class AccountProfileOpenApiContractTest {
	@Autowired MockMvc mvc;
	@MockitoBean AccountProfileService service;
	@MockitoBean AccessTokenIssuer issuer;
	@MockitoBean UserRepository users;
	@MockitoBean RefreshService refresh;
	@MockitoBean StringRedisTemplate redis;

	@MockitoBean java.time.Clock clock;

	@Test void memberDocumentIncludesAuthenticatedProfileAndNullableName() throws Exception {
		mvc.perform(get("/v3/api-docs/member"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/account/profile'].get.operationId").value("getAccountProfile"))
			.andExpect(jsonPath("$.paths['/api/v1/account/profile'].get.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/v1/account/profile'].get.security[1].accessCookieAuth").isArray())
			.andExpect(jsonPath("$.components.schemas.AccountProfileResponse.properties.displayName.type[0]").value("string"))
			.andExpect(jsonPath("$.components.schemas.AccountProfileResponse.properties.displayName.type[1]").value("null"))
			.andExpect(jsonPath("$.components.schemas.AccountProfileResponse.properties.userStatus.enum", org.hamcrest.Matchers.containsInAnyOrder("ACTIVE", "WITHDRAWAL_PENDING")))
			.andExpect(jsonPath("$.components.schemas.AccountProfileResponse.properties.role.enum", org.hamcrest.Matchers.containsInAnyOrder("MEMBER", "ADMIN")))
			.andExpect(jsonPath("$.components.schemas.AccountProfileResponse.required", org.hamcrest.Matchers.containsInAnyOrder("displayName", "email", "userStatus", "role")));
	}
}
