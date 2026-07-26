# Goals Page — Design

## Overview

A new `/goals` page letting the user set, view, edit, and delete a savings/spending goal per budget category. Goals are not month-scoped (unlike allocations) — one goal per category, period.

Backend entity/DTOs/service/controller for single-category CRUD already exist (`Goal`, `GoalDTO`, `GoalRequest`, `GoalService`, `GoalController`, `GoalRepository`) and are tested. This design adds the one missing backend piece (a bulk list endpoint) and the entire frontend page.

## Backend addition

No bulk "all goals for this user" endpoint exists today — only per-category CRUD. Add:

```java
// GoalController
@GetMapping
public List<GoalDTO> getAllGoals(@AuthenticationPrincipal Jwt jwt) {
    return goalService.getAllGoals(jwt.getSubject());
}

// GoalService
public List<GoalDTO> getAllGoals(String userSub) {
    return goalRepository.findByCategory_Group_UserSub(userSub).stream().map(this::toDto).toList();
}

// GoalRepository
List<Goal> findByCategory_Group_UserSub(String userSub);
```

`findByCategory_Group_UserSub` is a Spring Data derived query traversing `Goal.category` (`BudgetCategory`) → `BudgetCategory.group` (`CategoryGroup`) → `CategoryGroup.userSub`.

**Refactor while touching this file:** `createGoal`, `getGoalByCategoryId`, and `updateGoal` in `GoalService` each independently construct `new GoalDTO(goal.getId(), goal.getCategory().getId(), goal.getAmount(), goal.getDayOfMonth(), goal.getRolloverType())`. Adding `getAllGoals` makes this a 5th duplicate. Extract a private `toDto(Goal goal)` method and use it in all five places.

## Frontend: `GoalService`

New file `frontend/src/app/core/services/goal.service.ts`, matching the style of `budget.service.ts` / `account.service.ts`:

```typescript
export interface Goal {
  id: string;
  categoryId: string;
  amount: number;
  dayOfMonth: number;
  rolloverType: 'REFILL' | 'ACCUMULATE';
}

export interface GoalRequest {
  categoryId: string;
  amount: number;
  dayOfMonth: number;
  rolloverType: 'REFILL' | 'ACCUMULATE';
}

getAllGoals(): Observable<Goal[]>          // GET  /api/goals
createGoal(req: GoalRequest): Observable<Goal>          // POST /api/goals
updateGoal(categoryId: string, req: GoalRequest): Observable<Goal>  // PUT  /api/goals/{categoryId}
deleteGoal(categoryId: string): Observable<void>         // DELETE /api/goals/{categoryId}
```

## Frontend: data source and merge

The Goals page needs the same group/category structure and ordering as the Budget page, including which categories are `systemManaged` (e.g. the auto-generated CC-payment category), so those can be excluded.

- Reuse the existing `budgetService.getBudget(month)` call (same one the Budget page uses) purely for its `groups[].categories[]` structure, order, and `systemManaged` flag. The `assigned`/`spent`/`available`/`readyToAssign` fields it also returns are ignored. `month` comes from the existing `BudgetStateService.month$` (no visible month selector on this page — see Routing section).
- Call `goalService.getAllGoals()` in parallel.
- Merge client-side: for each category, look up a matching goal by `categoryId`.
- Filter out any category where `systemManaged` is `true`. If a group has zero categories remaining after filtering, hide the group entirely.

This means zero backend changes are needed to know which categories are system-managed — it's already in the existing budget response.

## Frontend: page component

New folder `frontend/src/app/goals/`:

- `goals.component.ts` / `.html` / `.scss` — default-exported standalone component, lazy-loaded, `ChangeDetectionStrategy.OnPush`.
- Structure mirrors `budget.component.html`: one `MatExpansionPanel` per group (expanded by default), containing a table of its categories. No drag-and-drop reordering (out of scope — goals just follow existing category order).
- Table columns: **Category** | **Goal**. The Goal column is contextual per row:
  - No goal for this category → single right-aligned "Add Goal" button (`mat-button` + `add` icon, same visual language as "Add Group"/"Add Category" on the Budget page).
  - Has a goal → right-aligned: formatted currency amount, then edit (pencil) icon button, then delete (trash) icon button.
- Loading state: reuse `AppLoadingSpinnerComponent` (same as Budget page), waiting on both the budget-structure call and the goals call.
- Error handling: reuse the `MatSnackBar` pattern from `budget.component.ts` — on load failure, show a snackbar ("Failed to load goals."); on save/delete failure, roll back any optimistic UI update and show a snackbar, consistent with how `budget.component.ts` handles allocation/category mutations.

## Frontend: `GoalDialogComponent`

New file, `frontend/src/app/goals/goal-dialog.component.ts`, structured like `AccountDialogComponent` (reactive form, `MAT_DIALOG_DATA` for edit-vs-create, closes with the saved `Goal`):

- **Amount** — numeric input, required, `Validators.min(0)` (matches backend `@PositiveOrZero`).
- **Day of Month** — numeric input, required, `Validators.min(1)`, `Validators.max(31)`.
- **Rollover Type** — `mat-select` with two options: "Refill each month" (`REFILL`), "Accumulate over time" (`ACCUMULATE`).
- Dialog data includes `categoryId`, `categoryName` (for the dialog title, e.g. "Add Goal: Groceries" / "Edit Goal: Groceries"), and the existing `Goal` when editing (prefills the form) or `null` when creating.
- Submit calls `goalService.createGoal(...)` or `goalService.updateGoal(categoryId, ...)` depending on whether an existing goal was passed in, then `dialogRef.close(goal)`.

Delete reuses the existing shared `ConfirmDialogComponent` (`frontend/src/app/accounts/confirm-dialog.component.ts`), same as Budget page's category/group deletion.

## Routing & navigation

- `app.routes.ts`: add `{ path: 'goals', loadComponent: () => import('./goals/goals.component'), canActivate: [authGuard] }`.
- `app.html`: add a "Goals" nav link in `mat-nav-list`, positioned after "Budget".
- `app.ts`: `showMonthSelector()` and `pageTitle()` computed signals updated so `/goals` behaves like `/accounts` — title "Goals", month selector hidden (goals aren't month-scoped even though the page internally fetches one month's category structure).

## Testing

- **Backend:** extend `GoalServiceTest` with cases for `getAllGoals` (returns mapped DTOs for the user's goals; returns empty list when none exist) and extend `GoalControllerTest` for `GET /api/goals` (200 with list).
- **Frontend:** no existing spec-file convention was found for other feature pages (e.g. no `budget.component.spec.ts`) — so no new frontend unit tests will be added for the Goals page or its dialog, consistent with the rest of the codebase. Manual verification (dev server, click through create/edit/delete) will be used to confirm the page works.

## Out of scope

- Drag-and-drop reordering on the Goals page.
- Any change to how `systemManaged` is computed or exposed elsewhere.
- Progress-toward-goal visualization (e.g. a progress bar comparing current balance to goal amount) — this design only covers CRUD display, not goal-tracking analytics.
