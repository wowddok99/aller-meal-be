package com.allermeal.api.child.request;

import java.util.List;

public record ReplaceChildAllergensRequest(List<Integer> allergenCodes) {
}
