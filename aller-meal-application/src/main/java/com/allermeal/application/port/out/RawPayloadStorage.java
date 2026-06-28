package com.allermeal.application.port.out;

import com.allermeal.application.port.out.command.RawPayload;
import com.allermeal.domain.raw.RawObjectMetadata;

public interface RawPayloadStorage {

	RawObjectMetadata store(RawPayload payload);
}
