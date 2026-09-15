package com.allermeal.api.admin.request;

import com.allermeal.application.admin.AdminUserSuspensionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminUserSuspensionRequest(
	@Schema(allowableValues = {"SUSPEND", "UNSUSPEND"}, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull AdminUserSuspensionAction action,
	@Schema(minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank @Size(min = 1, max = 500) String reason,
	@Schema(minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @PositiveOrZero Long expectedVersion
) {
}
