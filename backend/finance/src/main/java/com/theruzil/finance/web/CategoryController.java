package com.theruzil.finance.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.theruzil.finance.category.CategoryService;
import com.theruzil.finance.category.dto.CategoryRequest;
import com.theruzil.finance.category.dto.CategoryResponse;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestBody CategoryRequest request) {
        return categoryService.create(userId, request);
    }

    @GetMapping
    public List<CategoryResponse> list(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId) {
        return categoryService.list(userId);
    }

    @PutMapping("/{id}")
    public CategoryResponse edit(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId, @PathVariable Long id,
            @RequestBody CategoryRequest request) {
        return categoryService.edit(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId, @PathVariable Long id) {
        categoryService.delete(userId, id);
    }
}
