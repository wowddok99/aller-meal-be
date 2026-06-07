package com.allermeal.domain.common;

import java.util.UUID;

@FunctionalInterface
public interface DomainIdentifier {

	UUID value();
}
