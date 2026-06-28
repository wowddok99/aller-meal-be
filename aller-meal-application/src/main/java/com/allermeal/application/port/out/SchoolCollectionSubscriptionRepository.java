package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.RegisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.command.UnregisterSchoolCollectionSubscriptionCommand;
import com.allermeal.application.port.out.result.ActiveSchoolCollectionSubscriptionResult;
import com.allermeal.application.port.out.result.SchoolCollectionSubscriptionActivationResult;
import java.util.List;

public interface SchoolCollectionSubscriptionRepository {

	SchoolCollectionSubscriptionActivationResult registerChild(RegisterSchoolCollectionSubscriptionCommand command);

	void unregisterChild(UnregisterSchoolCollectionSubscriptionCommand command);

	List<ActiveSchoolCollectionSubscriptionResult> findActiveSubscriptions();
}
