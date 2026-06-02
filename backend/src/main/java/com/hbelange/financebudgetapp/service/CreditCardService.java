package com.hbelange.financebudgetapp.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@Service
public class CreditCardService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public CreditCardService(CategoryGroupRepository categoryGroupRepository,
                              BudgetCategoryRepository budgetCategoryRepository,
                              AccountRepository accountRepository) {
        this.categoryGroupRepository = categoryGroupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void ensureCCPaymentCategory(Account account, String userSub) {
        CategoryGroup group = categoryGroupRepository
            .findByUserSubAndName(userSub, "Credit Card Payments")
            .orElseGet(() -> {
                int nextOrder = categoryGroupRepository
                    .findTopByUserSubOrderBySortOrderDesc(userSub)
                    .map(g -> g.getSortOrder() + 1)
                    .orElse(0);
                CategoryGroup newGroup = new CategoryGroup();
                newGroup.setName("Credit Card Payments");
                newGroup.setSortOrder(nextOrder);
                newGroup.setUserSub(userSub);
                return categoryGroupRepository.save(newGroup);
            });

        BudgetCategory category = new BudgetCategory();
        category.setGroup(group);
        category.setName(account.getName());
        category.setSortOrder(0);
        BudgetCategory saved = budgetCategoryRepository.save(category);

        account.setCcPaymentCategoryId(saved.getId());
        accountRepository.save(account);
    }

    @Transactional
    public void syncCategoryName(Account account) {
        budgetCategoryRepository.findById(account.getCcPaymentCategoryId()).ifPresent(cat -> {
            cat.setName(account.getName());
            budgetCategoryRepository.save(cat);
        });
    }

    @Transactional
    public void deleteCCPaymentCategory(Account account) {
        if (account.getCcPaymentCategoryId() == null) return;

        budgetCategoryRepository.findById(account.getCcPaymentCategoryId()).ifPresent(cat -> {
            CategoryGroup group = cat.getGroup();

            // Clear the FK in the DB BEFORE deleting the category — FK RESTRICT prevents
            // deleting budget_categories rows that accounts still reference.
            account.setCcPaymentCategoryId(null);
            accountRepository.save(account);

            budgetCategoryRepository.deleteById(cat.getId());

            List<BudgetCategory> remaining = budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group);
            if (remaining.isEmpty()) {
                categoryGroupRepository.deleteById(group.getId());
            }
        });
    }
}
