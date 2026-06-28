package com.allermeal.api.child.request;

import java.util.UUID;

public record CreateChildProfileRequest(String name, int grade, int classNumber, UUID schoolId) {
}
