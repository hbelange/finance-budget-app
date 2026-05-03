package com.hbelange.financebudgetapp.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hbelange.financebudgetapp.dto.BudgetCategoryRequest;
import com.hbelange.financebudgetapp.dto.CategoryGroupDTO;
import com.hbelange.financebudgetapp.dto.CategoryGroupRequest;
import com.hbelange.financebudgetapp.service.CategoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CategoryController {

    private final CategoryService categoryService;

    @Autowired
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/category-groups")
    public List<CategoryGroupDTO> getAllGroups() {
        return categoryService.findAllGroups();
    }

    @PostMapping("/category-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryGroupDTO createGroup(@Valid @RequestBody CategoryGroupRequest req) {
        return categoryService.createGroup(req);
    }

    @PostMapping("/category-groups/{groupId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryGroupDTO addCategory(@PathVariable UUID groupId, @Valid @RequestBody BudgetCategoryRequest req) {
        return categoryService.addCategory(groupId, req);
    }

    @PutMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renameCategory(@PathVariable UUID id, @Valid @RequestBody BudgetCategoryRequest req) {
        categoryService.renameCategory(id, req);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
    }
}
