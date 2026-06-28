package com.allermeal.application.child;

import java.time.LocalTime;

public record UpdateChildNotificationPreferenceCommand(boolean emailEnabled, LocalTime notificationTime, String timezone) {
}
