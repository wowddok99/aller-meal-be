package com.allermeal.application.admin;

public record AdminExternalApiLogQuery(
	int page, int pageSize, String provider, String method, String outcome, String query
) {
}
