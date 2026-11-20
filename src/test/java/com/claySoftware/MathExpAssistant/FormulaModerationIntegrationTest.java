package com.claySoftware.MathExpAssistant;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaVersion;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.repositories.FormulaVersionStore;
import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"ADMIN_CREDS=reviewer@example.com", "spring.data.mongodb.auto-index-creation=true"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FORMULA_TEST_MONGO_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormulaModerationIntegrationTest {
    private static final String API = "/api/formulas";
    private static final String DATABASE = "formula_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String OWNER = "owner@example.com";
    private static final String ADMIN = "reviewer@example.com";
    private static final String REVIEW = """
            {"reason":"Checked against the reference"}
            """;

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired FormulaVersionStore versions;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("FORMULA_TEST_MONGO_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @AfterAll
    void cleanDatabase() {
        mongo.getDb().drop();
    }

    @Test
    void securelyModeratesAndRecordsApprovalsAndRejections() throws Exception {
        String id = draft("Moderate", """
                ,"status":"PUBLIC","version":99,"reviewedBy":"forged","reviewReason":"forged",
                "reviewedAt":"2026-01-01T00:00:00Z","ownerUserId":"forged"
                """);
        call(get(API + "/mine/" + id), OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRIVATE")).andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.reviewedBy").doesNotExist()).andExpect(jsonPath("$.ownerUserId").value(OWNER));
        call(get(API + "/status/PRIVATE"), "other@example.com").andExpect(status().isForbidden());
        call(get(API + "/status/UNDER_REVIEW"), OWNER).andExpect(status().isForbidden());
        call(get(API + "/status/unknown"), OWNER).andExpect(status().isBadRequest());
        call(post(API + "/mine/" + id + "/publish"), "other@example.com").andExpect(status().isNotFound());
        call(post(API + "/admin/" + id + "/approve").content(REVIEW), ADMIN).andExpect(status().isConflict());
        submit(id);
        call(post(API + "/mine/" + id + "/publish"), OWNER).andExpect(status().isConflict());
        call(put(API + "/mine/" + id).content(textFormula("Moderate", "X+2")), OWNER)
                .andExpect(status().isConflict());
        call(get(API + "/admin/pending"), OWNER).andExpect(status().isForbidden());
        mvc.perform(get(API + "/admin/pending")).andExpect(status().isUnauthorized());
        call(get(API + "/admin/pending"), ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isNotEmpty());
        call(post(API + "/admin/" + id + "/approve").content(REVIEW), OWNER).andExpect(status().isForbidden());
        call(post(API + "/admin/" + id + "/reject").content(REVIEW), OWNER).andExpect(status().isForbidden());
        call(get(API + "/" + id + "/versions"), OWNER).andExpect(status().isNotFound());
        call(post(API + "/admin/" + id + "/reject").content("{\"reason\":\" \"}"), ADMIN)
                .andExpect(status().isBadRequest());
        approve(id);
        call(post(API + "/admin/" + id + "/reject").content(REVIEW), ADMIN).andExpect(status().isConflict());
        call(get(API + "/" + id + "/versions/1"), OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewReason").value("Checked against the reference"));
        call(get(API + "/" + id + "/versions/2"), OWNER).andExpect(status().isNotFound());

        String rejected = draft("Rejected example", "");
        submit(rejected);
        call(post(API + "/admin/" + rejected + "/reject").content(REVIEW), ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedBy").value(ADMIN))
                .andExpect(jsonPath("$.reviewedAt").exists());
        assertThat(versions.history(rejected)).isEmpty();
        call(post(API + "/admin/" + rejected + "/approve").content(REVIEW), ADMIN).andExpect(status().isConflict());
        call(get(API + "/" + rejected + "/versions"), OWNER).andExpect(status().isNotFound());
        call(post(API + "/admin/missing/approve").content(REVIEW), ADMIN).andExpect(status().isNotFound());
    }

    @Test
    void pinsDependenciesBeforeAndAfterRevisionAndDeletion() throws Exception {
        String source = draft("Versioned source", "");
        submit(source);
        approve(source);

        String composite = createDraft(builder("Pinned composite", source, ""));
        assertPinned(composite, 1);
        assertResult("PINNED COMPOSITE", "3.0");
        String published = createDraft(builder("Published composite", source, ""));
        submit(published);
        approve(published);

        call(put(API + "/admin/" + source + "/revision").content(revision(1, textFormula("Versioned source", "X+"))),
                ADMIN).andExpect(status().isBadRequest());
        call(put(API + "/admin/" + source + "/revision").content(revision(1, textFormula("Versioned source", "X+10"))),
                ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.reviewedBy").value(ADMIN));
        call(put(API + "/admin/" + source + "/revision").content(revision(1, textFormula("Versioned source", "X+20"))),
                ADMIN).andExpect(status().isConflict());
        assertResult("PINNED COMPOSITE", "3.0");
        call(post(API + "/execute").content("""
                {"formulaName":"PUBLISHED COMPOSITE","variables":{"X":2}}
                """), OWNER).andExpect(status().isOk()).andExpect(content().string("3.0"));
        call(put(API + "/mine/" + composite).content(builder("Pinned composite", source, "")), OWNER)
                .andExpect(status().isOk());
        assertPinned(composite, 1);
        assertResult("PINNED COMPOSITE", "3.0");
        call(get(API + "/" + source + "/versions"), OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2)).andExpect(jsonPath("$[1].equation").value("X+1"));

        call(put(API + "/mine/" + composite).content(builder("Pinned composite", source, ",\"sourceVersion\":2")), OWNER)
                .andExpect(status().isOk());
        assertPinned(composite, 2);
        assertResult("PINNED COMPOSITE", "12.0");
        call(delete(API + "/delete/" + source), ADMIN).andExpect(status().isOk());
        call(get(API + "/" + source + "/versions/1"), OWNER).andExpect(status().isOk());
        call(put(API + "/mine/" + composite).content(builder("Pinned composite", source, "")), OWNER)
                .andExpect(status().isOk());
        assertResult("PINNED COMPOSITE", "12.0");

        String historical = createDraft(builder("Historical composite", source, ",\"sourceVersion\":1"));
        assertPinned(historical, 1);
        assertResult("HISTORICAL COMPOSITE", "3.0");
    }

    @Test
    void migratesLegacyPublicationAndReusesLocalLegacySnapshot() throws Exception {
        String source = mongo.getCollection("formulas").insertOne(new Document("name", "LEGACY SOURCE")
                .append("group", "ALGEBRA").append("status", "PUBLIC").append("equation", "X+4")
                .append("parameters", List.of("X"))).getInsertedId().asObjectId().getValue().toHexString();
        Document tree = Document.parse(json.readTree(builder("Legacy composite", source, ""))
                .get("expressionTree").toString());
        String composite = mongo.getCollection("formulas").insertOne(new Document("name", "LEGACY COMPOSITE")
                .append("group", "ALGEBRA").append("status", "PRIVATE").append("ownerUserId", OWNER)
                .append("inputMode", "BUILDER").append("expressionTree", tree)
                .append("equation", "X+2").append("parameters", List.of("X"))
                .append("sourceSnapshots", List.of(new Document("formulaId", source).append("name", "LEGACY SOURCE")
                        .append("equation", "X+2").append("parameters", List.of("X")))))
                .getInsertedId().asObjectId().getValue().toHexString();
        assertResult("LEGACY COMPOSITE", "4.0");
        call(get(API + "/" + source + "/versions/1"), OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.equation").value("X+4")).andExpect(jsonPath("$.reviewedBy").doesNotExist());
        assertThat(versions.history(source)).isEmpty();
        call(put(API + "/admin/" + source + "/revision").content(revision(1, textFormula("Legacy source", "X+8"))),
                ADMIN).andExpect(status().isOk());
        assertThat(versions.history(source)).extracting(FormulaVersion::version).containsExactly(2, 1);
        call(put(API + "/mine/" + composite).content(builder("Legacy composite", source, "")), OWNER)
                .andExpect(status().isOk());
        assertPinned(composite, 0);
        assertResult("LEGACY COMPOSITE", "4.0");
        call(put(API + "/mine/" + composite).content(builder("Legacy composite", source, ",\"sourceVersion\":1")), OWNER)
                .andExpect(status().isOk());
        assertResult("LEGACY COMPOSITE", "6.0");
        call(post(API + "/mine").content(builder("Forged legacy", source, ",\"sourceVersion\":0")), OWNER)
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentApprovalsCannotPublishTheSameNameTwice() throws Exception {
        String first = draft("Concurrent name", "");
        submit(first);
        String second = mongo.getCollection("formulas").insertOne(new Document("name", "CONCURRENT NAME")
                .append("ownerUserId", "another@example.com").append("group", "ALGEBRA")
                .append("status", "UNDER_REVIEW").append("equation", "X+1").append("parameters", List.of("X")))
                .getInsertedId().asObjectId().getValue().toHexString();

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstResult = executor.submit(() -> approveAfter(start, first));
            var secondResult = executor.submit(() -> approveAfter(start, second));
            start.countDown();
            assertThat(List.of(firstResult.get(), secondResult.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(versions.history(first).size() + versions.history(second).size()).isEqualTo(1);
    }

    private int approveAfter(CountDownLatch start, String id) throws Exception {
        start.await();
        return call(post(API + "/admin/" + id + "/approve").content(REVIEW), ADMIN)
                .andReturn().getResponse().getStatus();
    }

    @Test
    void rollsBackPublicationWhenHistoryInsertFails() throws Exception {
        String id = draft("Rollback check", "");
        submit(id);
        mongo.getCollection("formula_versions")
                .insertOne(new Document("_id", FormulaVersion.key(id, 1)).append("formulaId", "test-conflict").append("version", 1));
        call(post(API + "/admin/" + id + "/approve").content(REVIEW), ADMIN).andExpect(status().isConflict());
        assertThat(mongo.findById(id, FormulaEntity.class).getStatus()).isEqualTo(FormulaStatus.UNDER_REVIEW);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String user) throws Exception {
        String token = Jwts.builder().setSubject(user).claim("email", user).claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(SignatureAlgorithm.HS512, tokens.getJwtSecret()).compact();
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
    }

    private String draft(String name, String extraFields) throws Exception {
        String text = textFormula(name, "X+1").strip();
        return createDraft(text.substring(0, text.length() - 1) + extraFields + "}");
    }

    private String createDraft(String body) throws Exception {
        call(post(API + "/mine").content(body), OWNER).andExpect(status().isOk());
        String raw = call(get(API + "/mine"), OWNER).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String name = json.readTree(body).get("name").asText();
        for (JsonNode formula : json.readTree(raw)) {
            if (formula.get("name").asText().equalsIgnoreCase(name)) {
                return formula.get("id").asText();
            }
        }
        throw new AssertionError("Draft not found: " + name);
    }

    private void submit(String id) throws Exception {
        call(post(API + "/mine/" + id + "/publish"), OWNER).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    private void approve(String id) throws Exception {
        call(post(API + "/admin/" + id + "/approve").content(REVIEW), ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLIC")).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.reviewedBy").value(ADMIN));
    }

    private void assertPinned(String composite, int version) throws Exception {
        call(get(API + "/mine/" + composite), OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.expressionTree.sourceVersion").value(version));
    }

    private void assertResult(String name, String result) throws Exception {
        call(post(API + "/mine/execute").content("""
                {"formulaName":"%s","variables":{"X":2}}
                """.formatted(name)), OWNER).andExpect(status().isOk()).andExpect(content().string(result));
    }

    private String textFormula(String name, String equation) {
        return """
                {"name":"%s","group":"ALGEBRA","equation":"%s","parameters":["X"]}
                """.formatted(name, equation);
    }

    private String builder(String name, String source, String version) {
        return """
                {"name":"%s","group":"ALGEBRA","inputMode":"BUILDER","expressionTree":{
                    "id":"reference","type":"FORMULA","sourceFormulaId":"%s"%s,
                    "bindings":{"X":{"id":"binding","type":"VARIABLE","variableName":"X"}}
                }}
                """.formatted(name, source, version);
    }

    private String revision(int expected, String body) {
        return """
                {"expectedVersion":%d,"reason":"Corrected the source","formula":%s}
                """.formatted(expected, body);
    }
}
