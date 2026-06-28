package com.allermeal.application.port.out;

import com.allermeal.application.auth.AccessTokenClaims;
import com.allermeal.domain.user.User;
import java.time.Duration;

public interface AccessTokenIssuer {

	String issue(User user, Duration ttl);

	AccessTokenClaims verify(String token);
}
