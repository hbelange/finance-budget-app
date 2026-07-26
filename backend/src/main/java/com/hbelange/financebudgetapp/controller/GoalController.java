package com.hbelange.financebudgetapp.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hbelange.financebudgetapp.dto.GoalDTO;
import com.hbelange.financebudgetapp.dto.GoalRequest;
import com.hbelange.financebudgetapp.service.GoalService;

import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;


@RestController
@RequestMapping("/api/goals")
public class GoalController {
    
    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public GoalDTO createGoal(@Valid @RequestBody GoalRequest req, @AuthenticationPrincipal Jwt jwt) {
        return goalService.createGoal(req, jwt.getSubject());
    }

    @GetMapping("/{categoryId}")
    public GoalDTO getGoalByCategoryId(@PathVariable UUID categoryId, @AuthenticationPrincipal Jwt jwt) {
        return goalService.getGoalByCategoryId(categoryId, jwt.getSubject());
    }

    @PutMapping("/{categoryId}")
    public GoalDTO updateGoal(@PathVariable UUID categoryId, @Valid @RequestBody GoalRequest req, @AuthenticationPrincipal Jwt jwt) {
        return goalService.updateGoal(categoryId, req, jwt.getSubject());
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(@PathVariable UUID categoryId, @AuthenticationPrincipal Jwt jwt) {
        goalService.deleteGoal(categoryId, jwt.getSubject());
    }

}
