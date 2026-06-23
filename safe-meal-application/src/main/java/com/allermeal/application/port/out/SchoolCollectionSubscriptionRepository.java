package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.RegisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.command.UnregisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.result.SchoolCollectionSubscriptionActivationResult;

public interface SchoolCollectionSubscriptionRepository {

	SchoolCollectionSubscriptionActivationResult registerChild(RegisterSchoolCollectionSubscriptionCommand command);

	void unregisterChild(UnregisterSchoolCollectionSubscriptionCommand command);
}
