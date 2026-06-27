package com.hbelange.financebudgetapp.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.BudgetCategoryDTO;
import com.hbelange.financebudgetapp.dto.BudgetCategoryRequest;
import com.hbelange.financebudgetapp.dto.CategoryGroupDTO;
import com.hbelange.financebudgetapp.dto.CategoryGroupRequest;
import com.hbelange.financebudgetapp.dto.SortItem;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@Service
public class CategoryService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public CategoryService(CategoryGroupRepository categoryGroupRepository,
                           BudgetCategoryRepository budgetCategoryRepository,
                           BudgetAllocationRepository budgetAllocationRepository,
                           AccountRepository accountRepository) {
        this.categoryGroupRepository = categoryGroupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.accountRepository = accountRepository;
    }

    public List<CategoryGroupDTO> findAllGroups(String userSub) {
        return categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(userSub).stream()
            .map(g -> {
                List<BudgetCategoryDTO> categories = budgetCategoryRepository
                    .findByGroupOrderBySortOrderAsc(g).stream()
                    .map(c -> new BudgetCategoryDTO(c.getId(), c.getName()))
                    .toList();
                return new CategoryGroupDTO(g.getId(), g.getName(), categories);
            })
            .toList();
    }

    public CategoryGroupDTO createGroup(CategoryGroupRequest req, String userSub) {
        int nextOrder = categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc(userSub)
            .map(g -> g.getSortOrder() + 1)
            .orElse(0);
        CategoryGroup group = new CategoryGroup();
        group.setName(req.name());
        group.setSortOrder(nextOrder);
        group.setUserSub(userSub);
        group = categoryGroupRepository.save(group);
        return new CategoryGroupDTO(group.getId(), group.getName(), List.of());
    }

    public CategoryGroupDTO addCategory(UUID groupId, BudgetCategoryRequest req, String userSub) {
        CategoryGroup group = categoryGroupRepository.findById(groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category group not found"));

        // If group doesn't belong to user, throw 403
        if (!group.getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to add a category to this group");
        }

        int nextOrder = budgetCategoryRepository.findTopByGroupOrderBySortOrderDesc(group)
            .map(c -> c.getSortOrder() + 1)
            .orElse(0);
        BudgetCategory category = new BudgetCategory();
        category.setGroup(group);
        category.setName(req.name());
        category.setSortOrder(nextOrder);
        budgetCategoryRepository.save(category);

        List<BudgetCategoryDTO> categories = budgetCategoryRepository
            .findByGroupOrderBySortOrderAsc(group).stream()
            .map(c -> new BudgetCategoryDTO(c.getId(), c.getName()))
            .toList();

        return new CategoryGroupDTO(group.getId(), group.getName(), categories);
    }

    public void renameCategory(UUID id, BudgetCategoryRequest req, String userSub) {
        BudgetCategory category = budgetCategoryRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        // If category doesn't belong to user, throw 403
        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to rename this category");
        }
        if (accountRepository.existsByCcPaymentCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This category is managed by a linked credit card account");
        }
        category.setName(req.name());
        budgetCategoryRepository.save(category);
    }

    public void reorderGroups(List<SortItem> items, String userSub) {
        Map<UUID, Integer> orderMap = items.stream()
            .collect(Collectors.toMap(SortItem::id, SortItem::sortOrder));
        List<CategoryGroup> groups = categoryGroupRepository.findAllById(orderMap.keySet());

        // If any group doesn't belong to user, throw 403
        if (groups.stream().anyMatch(g -> !g.getUserSub().equals(userSub))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to reorder one or more of these groups");
        }

        groups.forEach(g -> g.setSortOrder(orderMap.get(g.getId())));
        categoryGroupRepository.saveAll(groups);
    }

    public void reorderCategories(List<SortItem> items, String userSub) {
        Map<UUID, Integer> orderMap = items.stream()
            .collect(Collectors.toMap(SortItem::id, SortItem::sortOrder));
        List<BudgetCategory> categories = budgetCategoryRepository.findAllById(orderMap.keySet());

        // If any category doesn't belong to user, throw 403
        if (categories.stream().anyMatch(c -> !c.getGroup().getUserSub().equals(userSub))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to reorder one or more of these categories");
        }

        categories.forEach(c -> c.setSortOrder(orderMap.get(c.getId())));
        budgetCategoryRepository.saveAll(categories);
    }

    public CategoryGroupDTO renameGroup(UUID id, CategoryGroupRequest req, String userSub) {
        CategoryGroup group = categoryGroupRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category group not found"));

        if (!group.getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to rename this group");
        }

        group.setName(req.name());
        group = categoryGroupRepository.save(group);
        List<BudgetCategoryDTO> categories = budgetCategoryRepository
            .findByGroupOrderBySortOrderAsc(group).stream()
            .map(c -> new BudgetCategoryDTO(c.getId(), c.getName()))
            .toList();
        return new CategoryGroupDTO(group.getId(), group.getName(), categories);
    }

    @Transactional
    public void deleteGroup(UUID id, String userSub) {
        CategoryGroup group = categoryGroupRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category group not found"));

        if (!group.getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this group");
        }
        if (budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)
                .stream().anyMatch(c -> accountRepository.existsByCcPaymentCategoryId(c.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This group contains a category managed by a linked credit card account");
        }
        if (budgetCategoryRepository.existsTransactionsByGroupId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Group contains categories with existing transactions");
        }
        budgetAllocationRepository.deleteByGroupId(id);
        budgetCategoryRepository.deleteByGroupId(id);
        categoryGroupRepository.deleteById(id);
    }

    @Transactional
    public void deleteCategory(UUID id, String userSub) {

        BudgetCategory category = budgetCategoryRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this category");
        }
        if (accountRepository.existsByCcPaymentCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This category is managed by a linked credit card account");
        }
        if (budgetCategoryRepository.existsTransactionsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category has existing transactions");
        }
        budgetAllocationRepository.deleteByCategoryId(id);
        budgetCategoryRepository.deleteById(id);
    }
}
