package com.allermeal.api.admin;

import com.allermeal.application.admin.AdminBootstrapResult;
import com.allermeal.application.admin.AdminBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
final class AdminBootstrapRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

	private final AdminBootstrapService bootstrapService;

	AdminBootstrapRunner(AdminBootstrapService bootstrapService) {
		this.bootstrapService = bootstrapService;
	}

	@Override
	public void run(ApplicationArguments args) {
		bootstrapService.bootstrap().ifPresent(this::logResult);
	}

	private void logResult(AdminBootstrapResult result) {
		log.info("관리자 bootstrap 처리 완료. action={}, changed={}, userId={}",
			result.action(), result.changed(), result.userId());
	}
}
