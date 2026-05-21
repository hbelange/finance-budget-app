package com.hbelange.financebudgetapp.entity;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Table(name = "transactions")
@Entity
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private LocalDate date;

    private String payee;

    private UUID categoryId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    private String memo;

    @Column(columnDefinition = "boolean default false")
    private Boolean cleared;

    private UUID transferId;
}
