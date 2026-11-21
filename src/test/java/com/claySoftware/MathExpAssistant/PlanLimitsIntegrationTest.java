package com.claySoftware.MathExpAssistant;

import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.models.UserPlan;
import com.claySoftware.MathExpAssistant.repositories.UserRepository;
import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;
import com.claySoftware.MathExpAssistant.services.PlanLimits;
import com.claySoftware.MathExpAssistant.services.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=true")
@AutoConfigureMockMvc
@ActiveProfiles("staging")
@EnabledIfEnvironmentVariable(named = "FORMULA_TEST_MONGO_URI", matches = ".+")
class PlanLimitsIntegrationTest {
    private static final String DATABASE = "plans_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String EMAIL = "plans@example.com";
    private static final String EXECUTION = """
            {"formulaName":"FIRST","variables":{"X":2}}
            """;

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired JwtTokenProvider tokens;
    @Autowired PlanLimits limits;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("FORMULA_TEST_MONGO_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @AfterEach
    void cleanDatabase() {
        mongo.getDb().drop();
    }

    @Test
    void preservesPremiumOnLoginAndEnforcesTheCurrentPlanForAnExistingToken() throws Exception {
        UserEntity account = userService.upsertOAuthUser("google-plans", EMAIL, "Plans", null, "USER");
        assertThat(account.getPlan()).isEqualTo(UserPlan.FREE);
        String originalToken = "Bearer " + Jwts.builder().setSubject(EMAIL).claim("plan", "FREE")
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(SignatureAlgorithm.HS512, tokens.getJwtSecret()).compact();

        assertThat(create("FIRST", originalToken)).isEqualTo(200);
        for (int index = 1; index < 9; index++) {
            assertThat(create("DRAFT" + index, originalToken)).isEqualTo(200);
        }

        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return create("RACEONE", originalToken);
            });
            var second = executor.submit(() -> {
                start.await();
                return create("RACETWO", originalToken);
            });
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }

        for (int count = 0; count < 49; count++) {
            limits.consumeExecution(EMAIL);
        }
        mvc.perform(post("/api/formulas/mine/execute").header("Authorization", originalToken)
                .contentType(MediaType.APPLICATION_JSON).content(EXECUTION)).andExpect(status().isOk());
        mvc.perform(post("/api/formulas/execute").header("Authorization", originalToken)
                .contentType(MediaType.APPLICATION_JSON).content(EXECUTION)).andExpect(status().isTooManyRequests());

        for (int count = 0; count < 20; count++) {
            limits.consumeBuilderPreview(EMAIL);
        }
        mvc.perform(post("/api/formulas/builder/preview").header("Authorization", originalToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isTooManyRequests());

        account = users.findByEmail(EMAIL).orElseThrow();
        account.setPlan(UserPlan.PREMIUM);
        users.save(account);

        Authentication authentication = mock(Authentication.class);
        OidcUser principal = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getSubject()).thenReturn("google-plans");
        when(principal.getEmail()).thenReturn(EMAIL);
        when(principal.getName()).thenReturn("Updated Plans");
        when(principal.getClaims()).thenReturn(Map.of());
        var claims = Jwts.parser().setSigningKey(tokens.getJwtSecret())
                .parseClaimsJws(tokens.createToken(authentication)).getBody();
        assertThat(claims.get("plan")).isEqualTo("PREMIUM");
        assertThat(claims.get("role")).isEqualTo("USER");

        assertThat(create("PREMIUMDRAFT", originalToken)).isEqualTo(200);
        mvc.perform(get("/api/users/me").header("Authorization", originalToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.usage.privateFormulas.used").value(11))
                .andExpect(jsonPath("$.usage.privateFormulas.limit").value(100))
                .andExpect(jsonPath("$.usage.dailyExecutions.used").value(50))
                .andExpect(jsonPath("$.usage.dailyBuilderPreviews.used").value(20));

        UserEntity downgraded = users.findByEmail(EMAIL).orElseThrow();
        downgraded.setPlan(UserPlan.FREE);
        users.save(downgraded);
        assertThat(create("AFTERDOWNGRADE", originalToken)).isEqualTo(409);
        mvc.perform(get("/api/formulas/mine").header("Authorization", originalToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(11));
    }

    private int create(String name, String token) throws Exception {
        return mvc.perform(post("/api/formulas/mine").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","group":"ALGEBRA","equation":"X+1","parameters":["X"]}
                        """.formatted(name))).andReturn().getResponse().getStatus();
    }
}
