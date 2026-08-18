package com.hbelange.financebudgetapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinancebudgetappApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinancebudgetappApplication.class, args);
	}

}
