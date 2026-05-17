package com.hbelange.financebudgetapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hbelange.financebudgetapp.entity.User;

public interface UserRepository extends JpaRepository<User, String> {
    
}
