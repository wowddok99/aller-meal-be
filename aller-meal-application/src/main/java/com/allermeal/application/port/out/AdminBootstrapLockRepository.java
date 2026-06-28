package com.allermeal.application.port.out;

public interface AdminBootstrapLockRepository {

	void acquireTransactionLock();
}
