package com.allermeal.application.port.out;

import com.allermeal.domain.user.EmailSearchHash;

public interface EmailSearchHasher {

	EmailSearchHash hash(String normalizedEmail);
}
