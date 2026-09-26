package com.allermeal.api.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allermeal.api.admin.AdminCollectionFailureController;
import com.allermeal.api.admin.AdminNotificationFailureController;
import com.allermeal.application.admin.AdminCollectionFailureService;
import com.allermeal.application.admin.AdminNotificationFailureService;
import com.allermeal.application.port.out.AccessTokenIssuer;
import com.allermeal.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

@WebMvcTest(controllers = {AdminCollectionFailureController.class, AdminNotificationFailureController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(OpenApiConfiguration.class)
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@ImportAutoConfiguration(classes = {
	SpringDocConfiguration.class,
	SpringDocWebMvcConfiguration.class,
	MultipleOpenApiSupportConfiguration.class
})
final class AdminOperationOpenApiContractTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private AdminCollectionFailureService collectionFailureService;

	@MockitoBean
	private AdminNotificationFailureService notificationFailureService;

	@MockitoBean
	private AccessTokenIssuer accessTokenIssuer;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private StringRedisTemplate redisTemplate;

	@Test
	void adminDocsExposeTypedListQueryContracts() throws Exception {
		mvc.perform(get("/v3/api-docs/admin"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/admin/collection-jobs'].get.operationId").value("listAdminCollectionJobs"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/collection-jobs'].get.parameters[?(@.name == 'status')].schema.enum").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/collection-jobs'].get.parameters[?(@.name == 'status')].schema.enum[3]").value("FAILED"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/collection-jobs'].get.parameters[?(@.name == 'schoolId')].schema.format").value("uuid"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/collection-jobs'].get.parameters[?(@.name == 'mealDate')].schema.format").value("date"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/collection-jobs'].get.parameters[?(@.name == 'mealType')].schema.enum[2]").value("DINNER"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/collection-jobs'].get.parameters[?(@.name == 'query')].schema.maxLength").value(100))
			.andExpect(jsonPath("$.paths['/api/v1/admin/meal-item-labelings'].get.parameters[?(@.name == 'status')].schema.enum").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/meal-item-labelings'].get.parameters[?(@.name == 'status')].schema.enum[3]").value("LABELING_FAILED"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/outbox-events'].get.parameters[?(@.name == 'status')].schema.enum").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/outbox-events'].get.parameters[?(@.name == 'status')].schema.enum[1]").value("PUBLISHED"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/notification-dlq-events'].get.parameters[?(@.name == 'status')].schema.enum").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/notification-dlq-events'].get.parameters[?(@.name == 'status')].schema.enum[1]").value("REPROCESSED"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/notification-dlq-events'].get.parameters[?(@.name == 'eventType')].schema.maxLength").value(100))
			.andExpect(jsonPath("$.paths['/api/v1/admin/notification-requests'].get.parameters[?(@.name == 'status')].schema.enum[5]").value("CANCELED"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/notification-requests'].get.parameters[?(@.name == 'channel')].schema.enum[0]").value("EMAIL"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/notification-requests'].get.parameters[?(@.name == 'reason')].schema.enum[5]").value("NO_MEAL"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/notification-requests'].get.parameters[?(@.name == 'query')].schema.format").value("uuid"))
			.andExpect(jsonPath("$.paths['/api/v1/admin/external-api-logs'].get.parameters[?(@.name == 'provider')].schema.maxLength").value(100))
			.andExpect(jsonPath("$.paths['/api/v1/admin/external-api-logs'].get.parameters[?(@.name == 'query')].schema.maxLength").value(100));
	}
}
