package com.hbelange.financebudgetapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
class FinancebudgetappApplicationTests {

	@Test
	void contextLoads() {
	}

}
