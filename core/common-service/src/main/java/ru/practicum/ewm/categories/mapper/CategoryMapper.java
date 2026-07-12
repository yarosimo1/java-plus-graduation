package ru.practicum.ewm.categories.mapper;

import ru.practicum.ewm.categories.model.Category;
import lombok.experimental.UtilityClass;
import ru.practicum.ewm.categories.dto.CategoryDto;
import ru.practicum.ewm.categories.dto.NewCategoryDto;

@UtilityClass
public class CategoryMapper {
    public Category toCategory(NewCategoryDto newCategoryDto) {
        Category category = new Category();
        category.setName(newCategoryDto.getName());
        return category;
    }

    public CategoryDto toCategoryDto(Category category) {
        return new CategoryDto(category.getId(), category.getName());
    }
}
