package com.claySoftware.MathExpAssistant;

import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JWTIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    public void testJwtAuthentication() {
        // Generate a JWT token for testing
        String token = Jwts.builder()
                .setSubject("testuser")
                .claim("email", "testuser@example.com")
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + 3600000))  // 1 hour expiration
                .signWith(SignatureAlgorithm.HS512, "YOUR_SECRET_KEY")
                .compact();

        // Create headers with the generated token
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        // Send a request to a protected endpoint
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/login", String.class, headers);

        // Validate the response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Google");
    }
}