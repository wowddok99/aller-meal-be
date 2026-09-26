package com.allermeal.application.admin;

public record AdminDeadLetterEventQuery(int page, int pageSize, AdminDeadLetterEventStatus status, String eventType, String query) {
}
