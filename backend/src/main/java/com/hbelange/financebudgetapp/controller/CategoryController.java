package com.hbelange.financebudgetapp.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.hbelange.financebudgetapp.dto.SortItem;
import com.hbelange.financebudgetapp.service.CategoryService;

import org.springframework.security.oauth2.jwt.Jwt;

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
    public List<CategoryGroupDTO> getAllGroups(@AuthenticationPrincipal Jwt jwt) {
        return categoryService.findAllGroups(jwt.getSubject());
    }

    @PostMapping("/category-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryGroupDTO createGroup(@Valid @RequestBody CategoryGroupRequest req, @AuthenticationPrincipal Jwt jwt) {
        return categoryService.createGroup(req, jwt.getSubject());
    }

    @PostMapping("/category-groups/{groupId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryGroupDTO addCategory(@PathVariable UUID groupId, @Valid @RequestBody BudgetCategoryRequest req, @AuthenticationPrincipal Jwt jwt) {
        return categoryService.addCategory(groupId, req, jwt.getSubject());
    }

    @PatchMapping("/category-groups/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderGroups(@RequestBody List<SortItem> items, @AuthenticationPrincipal Jwt jwt) {
        categoryService.reorderGroups(items, jwt.getSubject());
    }

    @PatchMapping("/category-groups/{groupId}/categories/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderCategories(@PathVariable UUID groupId, @RequestBody List<SortItem> items, @AuthenticationPrincipal Jwt jwt) {
        categoryService.reorderCategories(items, jwt.getSubject());
    }
    
    @PutMapping("/category-groups/{id}")
    public CategoryGroupDTO renameGroup(@PathVariable UUID id, @Valid @RequestBody CategoryGroupRequest req, @AuthenticationPrincipal Jwt jwt) {
        return categoryService.renameGroup(id, req, jwt.getSubject());
    }

    @DeleteMapping("/category-groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        categoryService.deleteGroup(id, jwt.getSubject());
    }

    @PutMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renameCategory(@PathVariable UUID id, @Valid @RequestBody BudgetCategoryRequest req, @AuthenticationPrincipal Jwt jwt) {
        categoryService.renameCategory(id, req, jwt.getSubject());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        categoryService.deleteCategory(id, jwt.getSubject());
    }
}
