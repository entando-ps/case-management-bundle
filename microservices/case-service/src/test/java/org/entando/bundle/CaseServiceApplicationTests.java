package org.entando.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class CaseServiceApplicationTests {

	@MockBean
	JwtDecoder jwtDecoder;

	@Test
	void contextLoads() {
	}

}
