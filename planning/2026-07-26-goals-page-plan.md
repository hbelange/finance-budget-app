# Goals Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `/goals` page where the user can create, view, edit, and delete one savings/spending goal per budget category, displayed in the same group/category order as the Budget page, with system-managed categories excluded.

**Architecture:** Backend gets one new bulk-read endpoint (`GET /api/goals`) added to the already-complete `Goal` CRUD slice. Frontend gets a new `GoalService`, a `GoalDialogComponent` (create/edit form, modeled on `AccountDialogComponent`), and a `GoalsComponent` page that merges the existing `GET /api/budget` group/category structure with the new bulk goals list, client-side, by `categoryId`.

**Tech Stack:** Java 21 / Spring Boot 3.5 / Maven (backend); Angular 21 standalone components, signals, Angular Material (frontend).

## Global Constraints

- Backend: Maven build (`./mvnw`), package root `com.hbelange.financebudgetapp`. Money fields are `BigDecimal`, never `float`/`double`.
- Frontend: standalone components only, no `standalone: true` in decorators (default in v20+), `ChangeDetectionStrategy.OnPush` on every component, signals for local state, `input()`/`output()` functions (not decorators), native control flow (`@if`/`@for`), no `ngClass`/`ngStyle`, Reactive Forms only.
- Accessibility: must pass AXE checks and WCAG AA (focus management, color contrast, ARIA labels on icon-only buttons).
- Optimistic UI on the frontend where feasible, matching the existing pattern in `budget.component.ts` (local state updated immediately, rolled back with a snackbar on error) for delete; dialogs (create/edit) wait for the server response before closing, matching `AccountDialogComponent`.
- No code comments except where a genuinely non-obvious constraint requires one.
- Do not add `Co-Authored-By` to any commit message (project convention).
- Do not run destructive git commands; commit only what each task's steps stage.

---

### Task 1: Backend — `GET /api/goals` bulk endpoint

**Files:**
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/repository/GoalRepository.java`
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/service/GoalService.java`
- Modify: `backend/src/main/java/com/hbelange/financebudgetapp/controller/GoalController.java`
- Test: `backend/src/test/java/com/hbelange/financebudgetapp/service/GoalServiceTest.java`
- Test: `backend/src/test/java/com/hbelange/financebudgetapp/controller/GoalControllerTest.java`

**Interfaces:**
- Produces: `GET /api/goals` → `200 OK` with `GoalDTO[]` (fields: `id: string`, `categoryId: string`, `amount: number`, `dayOfMonth: number`, `rolloverType: "REFILL" | "ACCUMULATE"`, matching the JSON shape already produced by the existing single-goal endpoints). Consumed by frontend Task 2's `GoalService.getAllGoals()`.

- [ ] **Step 1: Add the repository query method**

Modify `GoalRepository.java` to add a `List<Goal>` import and a new derived query method:

```java
package com.hbelange.financebudgetapp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.Goal;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    Goal findByCategory(BudgetCategory category);
    List<Goal> findByCategory_Group_UserSub(String userSub);
}
```

This traverses `Goal.category` (`BudgetCategory`) → `BudgetCategory.group` (`CategoryGroup`) → `CategoryGroup.userSub`. Spring Data generates the implementation; there is no method body to write.

- [ ] **Step 2: Write the failing service tests**

Add `import java.util.List;` to the top of `GoalServiceTest.java`'s import block (alongside the existing `java.math.BigDecimal`, `java.util.Optional`, `java.util.UUID` imports), then add these two test methods inside the class, after the existing `deleteGoal_throwsNotFound_whenNoGoalForCategory` test:

```java
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
```

- [ ] **Step 3: Run the service tests to verify they fail**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ./mvnw -q test -Dtest=GoalServiceTest`
Expected: compile error — `goalService.getAllGoals` and `goalRepository.findByCategory_Group_UserSub` do not exist yet (the repository method from Step 1 exists, but `GoalService.getAllGoals` does not).

- [ ] **Step 4: Implement `toDto` extraction and `getAllGoals` in `GoalService`**

Replace the full contents of `GoalService.java` with:

```java
package com.hbelange.financebudgetapp.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.GoalDTO;
import com.hbelange.financebudgetapp.dto.GoalRequest;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.Goal;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.GoalRepository;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;

    public GoalService(GoalRepository goalRepository, BudgetCategoryRepository budgetCategoryRepository) {
        this.goalRepository = goalRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
    }

    public GoalDTO createGoal(GoalRequest goalRequest, String userSub) {
        BudgetCategory category = budgetCategoryRepository.findById(goalRequest.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to add a goal to this category");
        }

        Goal goal = new Goal();
        goal.setCategory(category);
        goal.setAmount(goalRequest.amount());
        goal.setDayOfMonth(goalRequest.dayOfMonth());
        goal.setRolloverType(goalRequest.rolloverType());
        goal = goalRepository.save(goal);

        return toDto(goal);
    }

    public GoalDTO getGoalByCategoryId(UUID categoryId, String userSub) {
        BudgetCategory category = budgetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view the goal for this category");
        }

        Goal goal = goalRepository.findByCategory(category);

        if (goal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No goal found for this category");
        }

        return toDto(goal);
    }

    public GoalDTO updateGoal(UUID categoryId, GoalRequest goalRequest, String userSub) {

        BudgetCategory category = budgetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to update the goal for this category");
        }

        Goal goal = goalRepository.findByCategory(category);

        if (goal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No goal found for this category");
        }

        goal.setAmount(goalRequest.amount());
        goal.setDayOfMonth(goalRequest.dayOfMonth());
        goal.setRolloverType(goalRequest.rolloverType());
        goal = goalRepository.save(goal);

        return toDto(goal);
    }

    public void deleteGoal(UUID categoryId, String userSub) {

        BudgetCategory category = budgetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete the goal for this category");
        }

        Goal goal = goalRepository.findByCategory(category);

        if (goal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No goal found for this category");
        }

        goalRepository.delete(goal);
    }

    public List<GoalDTO> getAllGoals(String userSub) {
        return goalRepository.findByCategory_Group_UserSub(userSub).stream().map(this::toDto).toList();
    }

    private GoalDTO toDto(Goal goal) {
        return new GoalDTO(goal.getId(), goal.getCategory().getId(), goal.getAmount(), goal.getDayOfMonth(), goal.getRolloverType());
    }
}
```

- [ ] **Step 5: Run the service tests to verify they pass**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ./mvnw -q test -Dtest=GoalServiceTest`
Expected: PASS, 17 tests run (15 existing + 2 new), 0 failures.

- [ ] **Step 6: Write the failing controller test**

Add `import java.util.List;` to `GoalControllerTest.java`'s imports (alongside `java.math.BigDecimal`, `java.util.UUID`), then add inside the class, after the existing `deleteGoal_returns404_whenGoalMissing` test:

```java
    @Test
    void getAllGoals_returns200WithList() throws Exception {
        GoalDTO dto = new GoalDTO(GOAL_ID, CATEGORY_ID, new BigDecimal("200.00"), 15, RolloverType.REFILL);
        when(goalService.getAllGoals(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/goals").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(GOAL_ID.toString()))
            .andExpect(jsonPath("$[0].categoryId").value(CATEGORY_ID.toString()));
    }

    @Test
    void getAllGoals_returns200WithEmptyList_whenNoGoals() throws Exception {
        when(goalService.getAllGoals(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/goals").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }
```

- [ ] **Step 7: Run the controller tests to verify they fail**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ./mvnw -q test -Dtest=GoalControllerTest`
Expected: compile error or 404 — `GoalService.getAllGoals` now exists (from Step 4) but `GET /api/goals` has no controller mapping yet, so MockMvc returns 404 instead of 200.

- [ ] **Step 8: Add the controller endpoint**

Modify `GoalController.java`: add `import java.util.List;` directly below the existing `import java.util.UUID;` line, and add this method as the first method in the class, before `createGoal`:

```java
    @GetMapping
    public List<GoalDTO> getAllGoals(@AuthenticationPrincipal Jwt jwt) {
        return goalService.getAllGoals(jwt.getSubject());
    }

```

- [ ] **Step 9: Run the controller tests to verify they pass**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ./mvnw -q test -Dtest=GoalServiceTest,GoalControllerTest`
Expected: PASS, 31 tests run total (17 service + 14 controller), 0 failures.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/hbelange/financebudgetapp/repository/GoalRepository.java \
        backend/src/main/java/com/hbelange/financebudgetapp/service/GoalService.java \
        backend/src/main/java/com/hbelange/financebudgetapp/controller/GoalController.java \
        backend/src/test/java/com/hbelange/financebudgetapp/service/GoalServiceTest.java \
        backend/src/test/java/com/hbelange/financebudgetapp/controller/GoalControllerTest.java
git commit -m "Add GET /api/goals bulk endpoint for listing a user's goals"
```

---

### Task 2: Frontend — `GoalService`

**Files:**
- Create: `frontend/src/app/core/services/goal.service.ts`

**Interfaces:**
- Consumes: `GET /api/goals`, `POST /api/goals`, `PUT /api/goals/{categoryId}`, `DELETE /api/goals/{categoryId}` from Task 1.
- Produces: `Goal` interface (`id`, `categoryId`, `amount`, `dayOfMonth`, `rolloverType`), `GoalRequest` interface (`categoryId`, `amount`, `dayOfMonth`, `rolloverType`), `RolloverType` type (`'REFILL' | 'ACCUMULATE'`), `ROLLOVER_TYPE_LABELS` record, and `GoalService` with `getAllGoals()`, `createGoal(req)`, `updateGoal(categoryId, req)`, `deleteGoal(categoryId)`. Consumed by Task 3 (`GoalDialogComponent`) and Task 4 (`GoalsComponent`).

- [ ] **Step 1: Create the service file**

```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type RolloverType = 'REFILL' | 'ACCUMULATE';

export const ROLLOVER_TYPE_LABELS: Record<RolloverType, string> = {
  REFILL: 'Refill each month',
  ACCUMULATE: 'Accumulate over time',
};

export interface Goal {
  id: string;
  categoryId: string;
  amount: number;
  dayOfMonth: number;
  rolloverType: RolloverType;
}

export interface GoalRequest {
  categoryId: string;
  amount: number;
  dayOfMonth: number;
  rolloverType: RolloverType;
}

@Injectable({ providedIn: 'root' })
export class GoalService {
  private readonly http = inject(HttpClient);

  getAllGoals(): Observable<Goal[]> {
    return this.http.get<Goal[]>('/api/goals');
  }

  createGoal(req: GoalRequest): Observable<Goal> {
    return this.http.post<Goal>('/api/goals', req);
  }

  updateGoal(categoryId: string, req: GoalRequest): Observable<Goal> {
    return this.http.put<Goal>(`/api/goals/${categoryId}`, req);
  }

  deleteGoal(categoryId: string): Observable<void> {
    return this.http.delete<void>(`/api/goals/${categoryId}`);
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx ng build`
Expected: build succeeds with no TypeScript errors (this file isn't imported anywhere yet, so this just confirms the file itself is syntactically and structurally valid).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/core/services/goal.service.ts
git commit -m "Add frontend GoalService for goal CRUD"
```

---

### Task 3: Frontend — `GoalDialogComponent`

**Files:**
- Create: `frontend/src/app/goals/goal-dialog.component.ts`

**Interfaces:**
- Consumes: `Goal`, `GoalRequest`, `RolloverType`, `ROLLOVER_TYPE_LABELS`, `GoalService` from Task 2.
- Produces: `GoalDialogComponent`, `GoalDialogData` interface (`categoryId: string`, `categoryName: string`, `goal: Goal | null`). Opened via `MatDialog.open(GoalDialogComponent, { data: GoalDialogData })`; `afterClosed()` emits the saved `Goal` on success, or `undefined` if cancelled. Consumed by Task 4 (`GoalsComponent`).

- [ ] **Step 1: Create the dialog component**

```typescript
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel, MatError } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Goal, GoalRequest, GoalService, ROLLOVER_TYPE_LABELS, RolloverType } from '../core/services/goal.service';

const ROLLOVER_TYPES = Object.keys(ROLLOVER_TYPE_LABELS) as RolloverType[];

export interface GoalDialogData {
  categoryId: string;
  categoryName: string;
  goal: Goal | null;
}

@Component({
  selector: 'app-goal-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButton,
    MatFormField, MatLabel, MatError,
    MatInput,
    MatSelect, MatOption,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.goal ? 'Edit Goal: ' + data.categoryName : 'Add Goal: ' + data.categoryName }}</h2>
    <mat-dialog-content>
      <form id="goal-form" [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline">
          <mat-label>Amount</mat-label>
          <input matInput type="number" formControlName="amount" />
          @if (form.controls.amount.hasError('required')) {
            <mat-error>Amount is required</mat-error>
          }
          @if (form.controls.amount.hasError('min')) {
            <mat-error>Amount must be zero or greater</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Day of Month</mat-label>
          <input matInput type="number" formControlName="dayOfMonth" />
          @if (form.controls.dayOfMonth.hasError('required')) {
            <mat-error>Day of month is required</mat-error>
          }
          @if (form.controls.dayOfMonth.hasError('min') || form.controls.dayOfMonth.hasError('max')) {
            <mat-error>Day of month must be between 1 and 31</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Rollover Type</mat-label>
          <mat-select formControlName="rolloverType">
            @for (type of rolloverTypes; track type) {
              <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
            }
          </mat-select>
          @if (form.controls.rolloverType.hasError('required')) {
            <mat-error>Rollover type is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button form="goal-form" type="submit" [disabled]="form.invalid">Save</button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content form {
      display: flex;
      flex-direction: column;
      padding-top: 8px;
      min-width: min(320px, 85vw);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalDialogComponent {
  protected readonly data = inject<GoalDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<GoalDialogComponent>);
  private readonly goalService = inject(GoalService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly rolloverTypes = ROLLOVER_TYPES;
  protected readonly typeLabels: Record<string, string> = ROLLOVER_TYPE_LABELS;

  protected readonly form = new FormGroup({
    amount: new FormControl<number | null>(this.data.goal?.amount ?? null, [Validators.required, Validators.min(0)]),
    dayOfMonth: new FormControl<number | null>(this.data.goal?.dayOfMonth ?? null, [Validators.required, Validators.min(1), Validators.max(31)]),
    rolloverType: new FormControl<RolloverType | null>(this.data.goal?.rolloverType ?? null, [Validators.required]),
  });

  protected submit(): void {
    if (this.form.invalid) return;
    const { amount, dayOfMonth, rolloverType } = this.form.getRawValue();
    const req: GoalRequest = {
      categoryId: this.data.categoryId,
      amount: Number(amount!),
      dayOfMonth: Number(dayOfMonth!),
      rolloverType: rolloverType!,
    };
    const call$ = this.data.goal
      ? this.goalService.updateGoal(this.data.categoryId, req)
      : this.goalService.createGoal(req);
    call$.subscribe({
      next: goal => this.dialogRef.close(goal),
      error: () => this.snackBar.open('Failed to save goal. Please try again.', 'OK', { duration: 5000 }),
    });
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx ng build`
Expected: build succeeds with no TypeScript errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/goals/goal-dialog.component.ts
git commit -m "Add GoalDialogComponent for creating and editing a goal"
```

---

### Task 4: Frontend — `GoalsComponent` page

**Files:**
- Create: `frontend/src/app/goals/goals.component.ts`
- Create: `frontend/src/app/goals/goals.component.html`
- Create: `frontend/src/app/goals/goals.component.scss`

**Interfaces:**
- Consumes: `BudgetService.getBudget(month)` (existing, returns `BudgetView` with `groups[].categories[]`, each category having `id`, `name`, `systemManaged`), `BudgetStateService.month$` (existing), `GoalService` from Task 2, `GoalDialogComponent`/`GoalDialogData` from Task 3, `ConfirmDialogComponent` (existing, `frontend/src/app/accounts/confirm-dialog.component.ts`), `AppLoadingSpinnerComponent` (existing, `frontend/src/app/shared/app-loading-spinner.ts`).
- Produces: default-exported `GoalsComponent`, lazy-loadable via `import('./goals/goals.component')`. Consumed by Task 5's route registration.

- [ ] **Step 1: Create the component class**

```typescript
import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { forkJoin, timer } from 'rxjs';
import { MatIcon } from '@angular/material/icon';
import {
  MatExpansionPanel, MatExpansionPanelHeader,
  MatExpansionPanelTitle,
} from '@angular/material/expansion';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BudgetStateService } from '../core/services/budget-state.service';
import { BudgetService } from '../core/services/budget.service';
import { Goal, GoalService } from '../core/services/goal.service';
import { ConfirmDialogComponent } from '../accounts/confirm-dialog.component';
import { GoalDialogComponent, GoalDialogData } from './goal-dialog.component';
import { AppLoadingSpinnerComponent } from '../shared/app-loading-spinner';

interface GoalCategoryView {
  id: string;
  name: string;
  goal: Goal | null;
}

interface GoalGroupView {
  id: string;
  name: string;
  categories: GoalCategoryView[];
}

@Component({
  selector: 'app-goals',
  imports: [
    CurrencyPipe,
    MatIcon,
    MatExpansionPanel, MatExpansionPanelHeader,
    MatExpansionPanelTitle,
    MatIconButton, MatButton,
    AppLoadingSpinnerComponent,
  ],
  templateUrl: './goals.component.html',
  styleUrl: './goals.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class GoalsComponent {
  private readonly budgetService = inject(BudgetService);
  private readonly goalService = inject(GoalService);
  private readonly budgetState = inject(BudgetStateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  private readonly month = toSignal(this.budgetState.month$, { requireSync: true });

  protected readonly groups = signal<GoalGroupView[]>([]);
  protected isLoading = signal(false);
  protected isWakingUp = signal(false);

  private readonly _monthEffect = effect(() => this.loadGoals(this.month()));

  protected openAddGoal(cat: GoalCategoryView): void {
    const data: GoalDialogData = { categoryId: cat.id, categoryName: cat.name, goal: null };
    this.dialog.open(GoalDialogComponent, { data })
      .afterClosed()
      .subscribe((goal: Goal | undefined) => {
        if (!goal) return;
        this.applyGoal(cat.id, goal);
      });
  }

  protected openEditGoal(cat: GoalCategoryView): void {
    if (!cat.goal) return;
    const data: GoalDialogData = { categoryId: cat.id, categoryName: cat.name, goal: cat.goal };
    this.dialog.open(GoalDialogComponent, { data })
      .afterClosed()
      .subscribe((goal: Goal | undefined) => {
        if (!goal) return;
        this.applyGoal(cat.id, goal);
      });
  }

  protected confirmDeleteGoal(cat: GoalCategoryView): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete the goal for "${cat.name}"? This cannot be undone.` },
    }).afterClosed().subscribe((confirmed: boolean | undefined) => {
      if (!confirmed) return;
      const previousGoal = cat.goal;
      this.applyGoal(cat.id, null);
      this.goalService.deleteGoal(cat.id).subscribe({
        error: () => {
          this.applyGoal(cat.id, previousGoal);
          this.snackBar.open('Failed to delete goal.', 'OK', { duration: 5000 });
        },
      });
    });
  }

  private applyGoal(categoryId: string, goal: Goal | null): void {
    this.groups.update(groups => groups.map(g => ({
      ...g,
      categories: g.categories.map(c => c.id === categoryId ? { ...c, goal } : c),
    })));
  }

  private loadGoals(month: string): void {
    this.isLoading.set(true);

    forkJoin({
      budget: this.budgetService.getBudget(month),
      goals: this.goalService.getAllGoals(),
    }).subscribe({
      next: ({ budget, goals }) => {
        this.isLoading.set(false);
        this.isWakingUp.set(false);
        const goalsByCategoryId = new Map(goals.map(g => [g.categoryId, g]));
        this.groups.set(
          budget.groups
            .map(g => ({
              id: g.id,
              name: g.name,
              categories: g.categories
                .filter(c => !c.systemManaged)
                .map(c => ({ id: c.id, name: c.name, goal: goalsByCategoryId.get(c.id) ?? null })),
            }))
            .filter(g => g.categories.length > 0)
        );
      },
      error: () => {
        if (this.isWakingUp()) {
          this.loadGoals(month);
        } else {
          this.isLoading.set(false);
          this.isWakingUp.set(false);
          this.snackBar.open('Failed to load goals.', 'OK', { duration: 5000 });
        }
      },
    });

    timer(5000).subscribe(() => {
      if (this.isLoading()) {
        this.isWakingUp.set(true);
      }
    });
  }
}
```

- [ ] **Step 2: Create the template**

```html
@if (isLoading()) {
  <app-loading-spinner [isLoading]="isLoading()" [isWakingUp]="isWakingUp()" loadingText="Loading goals..."></app-loading-spinner>
} @else {
  <div class="goal-groups">
    @for (group of groups(); track group.id) {
      <mat-expansion-panel expanded>
        <mat-expansion-panel-header>
          <mat-panel-title>{{ group.name }}</mat-panel-title>
        </mat-expansion-panel-header>

        <table class="category-table">
          <thead>
            <tr>
              <th class="col-name">Category</th>
              <th class="col-goal">Goal</th>
            </tr>
          </thead>
          <tbody>
            @for (cat of group.categories; track cat.id) {
              <tr>
                <td class="col-name">{{ cat.name }}</td>
                <td class="col-goal">
                  @if (cat.goal; as goal) {
                    <span class="goal-amount">{{ goal.amount | currency }}</span>
                    <button mat-icon-button [attr.aria-label]="'Edit goal for ' + cat.name"
                      (click)="openEditGoal(cat)">
                      <mat-icon>edit</mat-icon>
                    </button>
                    <button mat-icon-button [attr.aria-label]="'Delete goal for ' + cat.name"
                      (click)="confirmDeleteGoal(cat)">
                      <mat-icon>delete</mat-icon>
                    </button>
                  } @else {
                    <button mat-button [attr.aria-label]="'Add goal for ' + cat.name"
                      (click)="openAddGoal(cat)">
                      <mat-icon>add</mat-icon>
                      Add Goal
                    </button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </mat-expansion-panel>
    }
  </div>
}
```

- [ ] **Step 3: Create the styles**

```scss
:host {
  display: block;
}

.goal-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.category-table {
  width: 100%;
  border-collapse: collapse;

  th {
    padding: 8px 12px;
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--mat-sys-on-surface-variant);
    text-align: left;
    border-bottom: 1px solid var(--mat-sys-outline-variant);
  }

  td {
    padding: 10px 12px;
    border-bottom: 1px solid var(--mat-sys-outline-variant);
    font-size: 0.875rem;
  }

  tr:last-child td {
    border-bottom: none;
  }
}

.col-name {
  width: 100%;
}

.col-goal {
  width: 260px;
  text-align: right;
  white-space: nowrap;

  button {
    width: 32px;
    height: 32px;
    line-height: 32px;

    mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
  }
}

.goal-amount {
  margin-right: 4px;
  font-weight: 500;
}

@media (max-width: 599px) {
  .category-table thead {
    display: none;
  }

  .category-table tr {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 0;
    border-bottom: 1px solid var(--mat-sys-outline-variant);
  }

  .category-table tbody tr:last-child {
    border-bottom: none;
  }

  .category-table td {
    border-bottom: none;
  }

  .col-name {
    width: auto;
  }

  .col-goal {
    width: auto;
  }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `cd frontend && npx ng build`
Expected: build succeeds with no TypeScript errors. The component isn't routed to yet, so this only confirms it compiles standalone.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/goals/goals.component.ts frontend/src/app/goals/goals.component.html frontend/src/app/goals/goals.component.scss
git commit -m "Add GoalsComponent page displaying goals grouped like the Budget page"
```

---

### Task 5: Frontend — Routing, navigation, and end-to-end verification

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/app.html`
- Modify: `frontend/src/app/app.ts`

**Interfaces:**
- Consumes: `GoalsComponent` from Task 4.
- Produces: `/goals` route reachable from the sidenav, with correct page title and no month selector.

- [ ] **Step 1: Register the route**

Modify `app.routes.ts` — insert a new route entry for `/goals` right after the `/budget` entry:

```typescript
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'accounts', loadComponent: () => import('./accounts/accounts-list.component'), canActivate: [authGuard] },
  { path: 'accounts/:id/transactions', loadComponent: () => import('./transactions/transaction-ledger.component'), canActivate: [authGuard] },
  { path: 'budget', loadComponent: () => import('./budget/budget.component'), canActivate: [authGuard] },
  { path: 'goals', loadComponent: () => import('./goals/goals.component'), canActivate: [authGuard] },
  { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard.component'), canActivate: [authGuard] },
  { path: '', redirectTo: 'budget', pathMatch: 'full' }
];
```

- [ ] **Step 2: Add the nav link**

Modify `app.html` — insert a "Goals" link into `mat-nav-list`, right after the "Budget" link:

```html
    <mat-nav-list>
      <a mat-list-item routerLink="/budget" routerLinkActive="active-link">Budget</a>
      <a mat-list-item routerLink="/goals" routerLinkActive="active-link">Goals</a>
      <a mat-list-item routerLink="/accounts" routerLinkActive="active-link">Accounts</a>
      <a mat-list-item routerLink="/dashboard" routerLinkActive="active-link">Dashboard</a>
    </mat-nav-list>
```

- [ ] **Step 3: Add the page title case**

Modify `app.ts` — the `pageTitle` computed signal currently falls through to `'Budget'` for any unmatched URL, which would mislabel `/goals`. Add a case for it. Note: `showMonthSelector` needs **no change** — its existing condition list (`/budget`, `/dashboard`, `/accounts/:id/transactions`) already evaluates to `false` for `/goals`, which is the desired behavior (goals aren't month-scoped), so leave that computed signal untouched.

```typescript
  protected readonly pageTitle = computed(() => {
    const url = this.currentUrl() ?? '/';
    if (url.startsWith('/dashboard')) return 'Dashboard';
    if (/^\/accounts\/[^/]+\/transactions/.test(url)) return 'Transactions';
    if (url.startsWith('/accounts')) return 'Accounts';
    if (url.startsWith('/goals')) return 'Goals';
    return 'Budget';
  });
```

- [ ] **Step 4: Verify the build**

Run: `cd frontend && npx ng build`
Expected: build succeeds with no errors.

- [ ] **Step 5: Manual end-to-end verification**

Run: `cd frontend && npx ng serve` (backend must also be running: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && cd backend && ./mvnw spring-boot:run`), then in a browser at `http://localhost:4200`:

1. Log in, click "Goals" in the sidenav — confirm the URL is `/goals`, the toolbar title reads "Goals", and no month selector is shown.
2. Confirm the groups and categories shown, and their order, match the `/budget` page for the same data — except any group/category that is entirely system-managed (e.g. a Credit Card Payments category) does not appear.
3. Click "Add Goal" on a category with no goal — fill in Amount, Day of Month (1–31), and Rollover Type, click Save. Confirm the dialog closes and that category's row now shows the amount plus edit/delete icons instead of the "Add Goal" button.
4. Refresh the page — confirm the goal persists (loaded from the backend, not just local state).
5. Click the edit (pencil) icon on that category — confirm the dialog opens pre-filled with the existing values, change the amount, save — confirm the row updates.
6. Click the delete (trash) icon — confirm a confirmation dialog appears; confirm — confirm the row reverts to showing the "Add Goal" button.
7. Trigger a save failure (e.g. stop the backend, then try adding a goal) — confirm a "Failed to save goal" snackbar appears and the dialog does not incorrectly close.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/app.routes.ts frontend/src/app/app.html frontend/src/app/app.ts
git commit -m "Wire up Goals page routing, navigation, and page title"
```
