package com.allermeal.application.port.out;

import com.allermeal.domain.allergen.Allergen;
import java.util.List;

public interface AllergenRepository {

	List<Allergen> findAll();
}
