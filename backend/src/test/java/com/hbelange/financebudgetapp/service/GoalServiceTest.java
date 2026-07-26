package com.hbelange.financebudgetapp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.GoalDTO;
import com.hbelange.financebudgetapp.dto.GoalRequest;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.entity.Goal;
import com.hbelange.financebudgetapp.enums.RolloverType;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.GoalRepository;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private BudgetCategoryRepository budgetCategoryRepository;

    @InjectMocks
    private GoalService goalService;

    private static final String USER_SUB = "auth0|test-user";
    private static final String OTHER_SUB = "auth0|other-user";

    private UUID categoryId;
    private BudgetCategory category;
    private Goal goal;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();

        CategoryGroup group = new CategoryGroup();
        group.setUserSub(USER_SUB);

        category = new BudgetCategory();
        category.setId(categoryId);
        category.setGroup(group);
        category.setName("Groceries");

        goal = new Goal();
        goal.setId(UUID.randomUUID());
        goal.setCategory(category);
        goal.setAmount(new BigDecimal("200.00"));
        goal.setDayOfMonth(15);
        goal.setRolloverType(RolloverType.REFILL);
    }

    private GoalRequest goalRequest() {
        return new GoalRequest(categoryId, new BigDecimal("200.00"), 15, RolloverType.REFILL);
    }

    @Test
    void createGoal_savesAndReturnsDto() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(goalRepository.save(any(Goal.class))).thenReturn(goal);

        GoalDTO result = goalService.createGoal(goalRequest(), USER_SUB);

        assertEquals(goal.getId(), result.id());
        assertEquals(categoryId, result.categoryId());
        assertEquals(new BigDecimal("200.00"), result.amount());
        assertEquals(15, result.dayOfMonth());
        assertEquals(RolloverType.REFILL, result.rolloverType());
        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    void createGoal_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.createGoal(goalRequest(), USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(goalRepository, never()).save(any());
    }

    @Test
    void createGoal_throwsForbidden_whenCategoryBelongsToOtherUser() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.createGoal(goalRequest(), OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(goalRepository, never()).save(any());
    }

    @Test
    void getGoalByCategoryId_returnsDto() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(goalRepository.findByCategory(category)).thenReturn(goal);

        GoalDTO result = goalService.getGoalByCategoryId(categoryId, USER_SUB);

        assertEquals(goal.getId(), result.id());
        assertEquals(categoryId, result.categoryId());
    }

    @Test
    void getGoalByCategoryId_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.getGoalByCategoryId(categoryId, USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getGoalByCategoryId_throwsForbidden_whenCategoryBelongsToOtherUser() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.getGoalByCategoryId(categoryId, OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getGoalByCategoryId_throwsNotFound_whenNoGoalForCategory() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(goalRepository.findByCategory(category)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.getGoalByCategoryId(categoryId, USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateGoal_updatesAndReturnsDto() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(goalRepository.findByCategory(category)).thenReturn(goal);
        when(goalRepository.save(any(Goal.class))).thenReturn(goal);

        GoalRequest req = new GoalRequest(categoryId, new BigDecimal("300.00"), 20, RolloverType.ACCUMULATE);
        GoalDTO result = goalService.updateGoal(categoryId, req, USER_SUB);

        assertEquals(new BigDecimal("300.00"), goal.getAmount());
        assertEquals(20, goal.getDayOfMonth());
        assertEquals(RolloverType.ACCUMULATE, goal.getRolloverType());
        assertEquals(goal.getId(), result.id());
        verify(goalRepository).save(goal);
    }

    @Test
    void updateGoal_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.updateGoal(categoryId, goalRequest(), USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(goalRepository, never()).save(any());
    }

    @Test
    void updateGoal_throwsForbidden_whenCategoryBelongsToOtherUser() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.updateGoal(categoryId, goalRequest(), OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(goalRepository, never()).save(any());
    }

    @Test
    void updateGoal_throwsNotFound_whenNoGoalForCategory() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(goalRepository.findByCategory(category)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.updateGoal(categoryId, goalRequest(), USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(goalRepository, never()).save(any());
    }

    @Test
    void deleteGoal_deletesGoal() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(goalRepository.findByCategory(category)).thenReturn(goal);

        goalService.deleteGoal(categoryId, USER_SUB);

        verify(goalRepository).delete(goal);
    }

    @Test
    void deleteGoal_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.deleteGoal(categoryId, USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(goalRepository, never()).delete(any());
    }

    @Test
    void deleteGoal_throwsForbidden_whenCategoryBelongsToOtherUser() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.deleteGoal(categoryId, OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(goalRepository, never()).delete(any());
    }

    @Test
    void deleteGoal_throwsNotFound_whenNoGoalForCategory() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(goalRepository.findByCategory(category)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> goalService.deleteGoal(categoryId, USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(goalRepository, never()).delete(any());
    }

    @Test
    void getAllGoals_returnsMappedDtos() {
        when(goalRepository.findByCategory_Group_UserSub(USER_SUB)).thenReturn(List.of(goal));

        List<GoalDTO> result = goalService.getAllGoals(USER_SUB);

        assertEquals(1, result.size());
        assertEquals(goal.getId(), result.get(0).id());
        assertEquals(categoryId, result.get(0).categoryId());
        assertEquals(new BigDecimal("200.00"), result.get(0).amount());
        assertEquals(15, result.get(0).dayOfMonth());
        assertEquals(RolloverType.REFILL, result.get(0).rolloverType());
    }

    @Test
    void getAllGoals_returnsEmptyList_whenUserHasNoGoals() {
        when(goalRepository.findByCategory_Group_UserSub(USER_SUB)).thenReturn(List.of());

        List<GoalDTO> result = goalService.getAllGoals(USER_SUB);

        assertTrue(result.isEmpty());
    }
}
