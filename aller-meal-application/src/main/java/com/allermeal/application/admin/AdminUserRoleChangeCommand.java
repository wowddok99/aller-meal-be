package com.allermeal.application.admin;

public record AdminUserRoleChangeCommand(
	String reason,
	Long expectedVersion
) {

	public AdminUserRoleChangeCommand {
		if (reason == null || expectedVersion == null) {
			throw new AdminInvalidUserChangeRequestException();
		}
		reason = reason.trim();
		if (reason.isBlank() || reason.length() > 500 || expectedVersion < 0) {
			throw new AdminInvalidUserChangeRequestException();
		}
	}
}
