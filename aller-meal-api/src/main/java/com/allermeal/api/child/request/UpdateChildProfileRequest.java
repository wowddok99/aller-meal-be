package com.allermeal.api.child.request;

import java.util.UUID;

public record UpdateChildProfileRequest(String name, int grade, int classNumber, UUID schoolId) {
}
