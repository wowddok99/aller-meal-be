package com.allermeal.application.child;

import com.allermeal.domain.school.SchoolId;

public record CreateChildProfileCommand(String name, int grade, int classNumber, SchoolId schoolId) {
}
