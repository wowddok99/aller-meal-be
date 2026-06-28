package com.allermeal.application.port.out;

import com.allermeal.domain.user.EncryptedEmail;

public interface EmailDecryptor {

	String decrypt(EncryptedEmail encryptedEmail);
}
