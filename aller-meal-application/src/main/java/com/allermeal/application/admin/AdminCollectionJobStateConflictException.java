package com.allermeal.application.admin;

public final class AdminCollectionJobStateConflictException extends RuntimeException {
	public AdminCollectionJobStateConflictException() { super("수집 작업 상태가 액션 조건과 충돌함"); }
}
