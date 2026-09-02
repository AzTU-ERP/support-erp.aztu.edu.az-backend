package com.aztu.support_erp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context smoke test. Disabled by default because the context needs a live Postgres with the
 * support schema migrated; run it with {@code -Dtest=SupportErpApplicationTests} once
 * {@code compose.yaml} is up.
 */
@SpringBootTest
@org.junit.jupiter.api.Disabled("Requires a running PostgreSQL — start compose.yaml first")
class SupportErpApplicationTests {

	@Test
	void contextLoads() {
	}

}
