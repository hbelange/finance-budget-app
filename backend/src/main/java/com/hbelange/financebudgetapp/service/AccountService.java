package com.hbelange.financebudgetapp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AccountBalance;
import com.hbelange.financebudgetapp.dto.AccountDTO;
import com.hbelange.financebudgetapp.dto.AccountRequest;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.enums.AccountType;
import com.hbelange.financebudgetapp.repository.AccountRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CreditCardService creditCardService;

    @Autowired
    public AccountService(AccountRepository accountRepository, CreditCardService creditCardService) {
        this.accountRepository = accountRepository;
        this.creditCardService = creditCardService;
    }

    public List<AccountDTO> findAll(String userSub) {
        List<AccountBalance> balances = accountRepository.findBalancesByUserSub(userSub);
        return accountRepository.findByUserSub(userSub)
            .stream()
            .map(a -> {
                BigDecimal balance = balances.stream()
                    .filter(ab -> ab.accountId().equals(a.getId()))
                    .map(AccountBalance::balance)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
                return toDTO(a, balance);
            })
            .toList();
    }

    public AccountDTO create(AccountRequest req, String userSub) {
        Account account = new Account();
        account.setName(req.name());
        account.setType(req.type());
        account.setUserSub(userSub);
        Account saved = accountRepository.save(account);
        if (saved.getType() == AccountType.CREDIT_CARD) {
            creditCardService.ensureCCPaymentCategory(saved, userSub);
        }
        return toDTO(saved, BigDecimal.ZERO);
    }

    public AccountDTO update(UUID id, AccountRequest req, String userSub) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to update this account");
        }

        AccountType oldType = account.getType();
        String oldName = account.getName();

        if (oldType == AccountType.CREDIT_CARD && req.type() != AccountType.CREDIT_CARD) {
            creditCardService.deleteCCPaymentCategory(account);
        }

        account.setName(req.name());
        account.setType(req.type());
        Account saved = accountRepository.save(account);

        if (oldType != AccountType.CREDIT_CARD && req.type() == AccountType.CREDIT_CARD) {
            creditCardService.ensureCCPaymentCategory(saved, userSub);
        } else if (req.type() == AccountType.CREDIT_CARD && !oldName.equals(req.name())) {
            creditCardService.syncCategoryName(saved);
        }

        return toDTO(saved, accountRepository.findBalanceById(id));
    }

    public void delete(UUID id, String userSub) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this account");
        }

        if (accountRepository.existsTransactionsByAccountId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account has existing transactions");
        }
        accountRepository.deleteById(id);
    }

    private AccountDTO toDTO(Account account, BigDecimal balance) {
        return new AccountDTO(account.getId(), account.getName(), account.getType(), balance);
    }
}
