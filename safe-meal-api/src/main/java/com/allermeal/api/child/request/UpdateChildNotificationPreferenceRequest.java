package com.allermeal.api.child.request;

import java.time.LocalTime;

public record UpdateChildNotificationPreferenceRequest(Boolean emailEnabled, LocalTime notificationTime, String timezone) {
}
