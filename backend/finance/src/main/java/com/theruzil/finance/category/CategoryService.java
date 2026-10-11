package com.theruzil.finance.category;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.finance.category.dto.CategoryOrderRequest;
import com.theruzil.finance.category.dto.CategoryRequest;
import com.theruzil.finance.category.dto.CategoryResponse;
import com.theruzil.finance.operation.OperationRepository;

@Service
public class CategoryService {

    private static final Comparator<Category> TYPE_THEN_ORDER_THEN_ID = Comparator
            .comparingInt((Category category) -> category.getType() == CategoryType.INCOME ? 0 : 1)
            .thenComparing(Category::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
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
        validatePlannedAmount(request.plannedAmount());
        CategoryType type = parseType(request.type());
        int sortOrder = nextSortOrder(userId, type);
        Category category = new Category(userId, type, request.name().trim(), normalizeGroup(request.group()),
                request.plannedAmount(), sortOrder, Instant.now());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(userId).stream()
                .sorted(TYPE_THEN_ORDER_THEN_ID)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse edit(Long userId, Long categoryId, CategoryRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new InvalidCategoryDataException("Category name must not be blank");
        }
        validatePlannedAmount(request.plannedAmount());
        Category category = requireCategory(userId, categoryId);
        category.edit(request.name().trim(), normalizeGroup(request.group()), request.plannedAmount(), Instant.now());
        return toResponse(category);
    }

    @Transactional
    public void reorder(Long userId, CategoryOrderRequest request) {
        if (request == null) {
            throw new InvalidCategoryDataException("Category order data is required");
        }
        CategoryType type = parseType(request.type());
        List<Long> categoryIds = request.categoryIds();
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new InvalidCategoryDataException("Category ids are required");
        }
        Set<Long> uniqueIds = new HashSet<>(categoryIds);
        if (uniqueIds.size() != categoryIds.size()) {
            throw new InvalidCategoryDataException("Category ids must not contain duplicates");
        }

        Map<Long, Category> owned = new LinkedHashMap<>();
        for (Long categoryId : categoryIds) {
            Category category = requireCategory(userId, categoryId);
            if (category.getType() != type) {
                throw new InvalidCategoryDataException("Category type does not match the requested type");
            }
            owned.put(categoryId, category);
        }

        long categoriesOfType = categoryRepository.findByUserIdOrderBySortOrderAscIdAsc(userId).stream()
                .filter(category -> category.getType() == type)
                .count();
        if (categoriesOfType != categoryIds.size()) {
            throw new InvalidCategoryDataException("Category order must contain every category of the type");
        }

        for (int index = 0; index < categoryIds.size(); index++) {
            owned.get(categoryIds.get(index)).assignSortOrder(index + 1);
        }
    }

    @Transactional
    public void delete(Long userId, Long categoryId) {
        Category category = requireCategory(userId, categoryId);
        if (operationRepository.existsByCategoryId(categoryId)) {
            throw new CategoryInUseException("Category has operations");
        }
        categoryRepository.delete(category);
    }

    private int nextSortOrder(Long userId, CategoryType type) {
        return categoryRepository.findTopByUserIdAndTypeOrderBySortOrderDesc(userId, type)
                .map(Category::getSortOrder)
                .orElse(0) + 1;
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

    private void validatePlannedAmount(BigDecimal plannedAmount) {
        if (plannedAmount == null) {
            return;
        }
        if (plannedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidCategoryDataException("Planned amount must be greater than zero");
        }
        if (plannedAmount.scale() > 2) {
            throw new InvalidCategoryDataException("Planned amount must have at most two decimals");
        }
    }

    private CategoryType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new InvalidCategoryDataException("Category type must be INCOME or EXPENSE");
        }
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
                category.getGroupName(), category.getPlannedAmount());
    }
}
