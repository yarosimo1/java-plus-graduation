package ru.practicum.ewm.categories.repository;

import ru.practicum.ewm.categories.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByName(String name);
}
