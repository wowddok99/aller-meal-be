package com.allermeal.infra.admin;

import org.springframework.jdbc.core.simple.JdbcClient;

/** 수집 변경 transaction과 snapshot 재집계의 commit 순서를 직렬화합니다. */
public final class CollectionDashboardSnapshotInvalidator {
	static final long REFRESH_LOCK_KEY = 7_404_621_064L;
	private CollectionDashboardSnapshotInvalidator() {}

	public static void invalidate(JdbcClient jdbcClient) {
		jdbcClient.sql("SELECT pg_advisory_xact_lock(:key)").param("key", REFRESH_LOCK_KEY)
			.query((rs, row) -> true).single();
		jdbcClient.sql("DELETE FROM admin_dashboard_summary_snapshots").update();
	}
}
