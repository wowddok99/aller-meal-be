package com.allermeal.application.child;

import com.allermeal.domain.school.SchoolId;

public record UpdateChildProfileCommand(String name, int grade, int classNumber, SchoolId schoolId) {
}
