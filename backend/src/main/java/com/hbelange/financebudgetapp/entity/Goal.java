package com.hbelange.financebudgetapp.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.hbelange.financebudgetapp.enums.RolloverType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;

@Entity
@Table(name = "goals")
@Setter
@Getter
@NoArgsConstructor
public class Goal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id", nullable = false)
    private BudgetCategory category;

    @PositiveOrZero
    private BigDecimal amount;

    @Min(1)
    @Max(31)
    private Integer dayOfMonth;

    @Enumerated(EnumType.STRING)
    private RolloverType rolloverType;
}
