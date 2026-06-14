package com.allermeal.application.port.out;

import com.allermeal.application.port.out.result.SchoolSearchResult;

public interface NeisSchoolClient {

	SchoolSearchResult search(String keyword, int page, int pageSize);
}
