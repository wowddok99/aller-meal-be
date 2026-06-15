package com.allermeal.api.school.response;

import java.util.List;

public record SchoolSearchResponse(List<SchoolResponse> schools, int page, int pageSize, int totalCount) {
}
