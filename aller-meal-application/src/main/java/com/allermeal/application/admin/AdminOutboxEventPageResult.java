package com.allermeal.application.admin;

import java.util.List;

public record AdminOutboxEventPageResult(List<AdminOutboxEventItemResult> items, int page, int pageSize, long totalCount) {
}
