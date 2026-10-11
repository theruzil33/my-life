package com.theruzil.finance.category;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.finance.category.dto.CategoryRequest;
import com.theruzil.finance.category.dto.CategoryResponse;
import com.theruzil.finance.operation.OperationRepository;

@Service
public class CategoryService {

    private static final Comparator<Category> TYPE_THEN_ID = Comparator
            .comparingInt((Category category) -> category.getType() == CategoryType.INCOME ? 0 : 1)
            .thenComparing(Category::getId);

    private final CategoryRepository categoryRepository;

    private final OperationRepository operationRepository;

    public CategoryService(CategoryRepository categoryRepository, OperationRepository operationRepository) {
        this.categoryRepository = categoryRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        validateCreate(request);
        CategoryType type = parseType(request.type());
        Category category = new Category(userId, type, request.name().trim(), normalizeGroup(request.group()),
                Instant.now());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return categoryRepository.findByUserIdOrderById(userId).stream()
                .sorted(TYPE_THEN_ID)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse edit(Long userId, Long categoryId, CategoryRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new InvalidCategoryDataException("Category name must not be blank");
        }
        Category category = requireCategory(userId, categoryId);
        category.edit(request.name().trim(), normalizeGroup(request.group()), Instant.now());
        return toResponse(category);
    }

    @Transactional
    public void delete(Long userId, Long categoryId) {
        Category category = requireCategory(userId, categoryId);
        if (operationRepository.existsByCategoryId(categoryId)) {
            throw new CategoryInUseException("Category has operations");
        }
        categoryRepository.delete(category);
    }

    private void validateCreate(CategoryRequest request) {
        if (request == null) {
            throw new InvalidCategoryDataException("Category data is required");
        }
        if (request.type() == null || request.type().isBlank()) {
            throw new InvalidCategoryDataException("Category type is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidCategoryDataException("Category name must not be blank");
        }
    }

    private CategoryType parseType(String type) {
        try {
            return CategoryType.valueOf(type.trim());
        }
        catch (IllegalArgumentException ex) {
            throw new InvalidCategoryDataException("Category type must be INCOME or EXPENSE");
        }
    }

    private String normalizeGroup(String group) {
        if (group == null) {
            return null;
        }
        String trimmed = group.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Category requireCategory(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getType(), category.getName(),
                category.getGroupName());
    }
}
