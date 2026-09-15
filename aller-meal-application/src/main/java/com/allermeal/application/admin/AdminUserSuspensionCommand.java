package com.allermeal.application.admin;

public record AdminUserSuspensionCommand(
	AdminUserSuspensionAction action,
	String reason,
	Long expectedVersion
) {

	public AdminUserSuspensionCommand {
		if (action == null || reason == null || expectedVersion == null) {
			throw new AdminInvalidUserChangeRequestException();
		}
		reason = reason.trim();
		if (reason.isBlank() || reason.length() > 500 || expectedVersion < 0) {
			throw new AdminInvalidUserChangeRequestException();
		}
	}
}
