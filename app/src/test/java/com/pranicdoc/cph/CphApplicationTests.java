package com.pranicdoc.cph;

import org.junit.jupiter.api.Test;
import com.pranicdoc.cph.support.TestRedisConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class CphApplicationTests {

	@Test
	void contextLoads() {
	}

}
