package com.allermeal.api.auth.request;

import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public record SignupRequest(
	String email,
	String password,
	@Schema(type = "string", nullable = true, description = "양 끝 공백 제거 후 1~50 Unicode code point. C0/C1 제어문자와 짝 없는 surrogate 불가.") JsonNode displayName,
	@Schema(type = "string", nullable = true, allowableValues = {"STUDENT", "PARENT"}) JsonNode usageType
) {
	@Override public String toString() { return "SignupRequest[REDACTED]"; }
}
