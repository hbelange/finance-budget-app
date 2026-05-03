package com.hbelange.financebudgetapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hbelange.financebudgetapp.dto.AllocationRequest;
import com.hbelange.financebudgetapp.dto.BudgetViewDTO;
import com.hbelange.financebudgetapp.service.BudgetService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/budget")
public class BudgetController {

    private final BudgetService budgetService;

    @Autowired
    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public BudgetViewDTO getBudget(@RequestParam String month) {
        return budgetService.getBudget(month);
    }

    @PutMapping("/allocations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertAllocation(@Valid @RequestBody AllocationRequest req) {
        budgetService.upsertAllocation(req);
    }
}
