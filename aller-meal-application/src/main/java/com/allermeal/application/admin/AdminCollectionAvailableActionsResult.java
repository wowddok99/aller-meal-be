package com.allermeal.application.admin;

import java.time.Instant;

public record AdminCollectionAvailableActionsResult(boolean canRecollect, boolean canExecute, Instant executeAvailableAt) {}
