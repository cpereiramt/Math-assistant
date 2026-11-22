package com.claySoftware.MathExpAssistant;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaVersion;
import com.claySoftware.MathExpAssistant.entities.UserEntity;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private static final String MEASURED_OWNER = "measurement-owner@example.com";
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
                .andExpect(jsonPath("$.reviewedBy").doesNotExist()).andExpect(jsonPath("$.ownerUserId").doesNotExist());
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
                .andExpect(jsonPath("$.reviewedBy").doesNotExist())
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
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
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

    @Test
    void connectsPublicationProfilesAndFollowsAndDeduplicatesConcurrentViews() throws Exception {
        String ownerId = account(OWNER, "Formula author");
        String reader = "community-reader@example.com";
        account(reader, "Community reader");
        call(put("/api/authors/me").content("""
                {"name":"Algebra author","bio":"I write about algebra","website":"https://example.com"}
                """), OWNER).andExpect(status().isOk());
        String id = draft("Profile and concurrent views", "");
        submit(id);
        approve(id);

        mvc.perform(get("/api/authors/by-formula/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownerId))
                .andExpect(jsonPath("$.name").value("Algebra author"))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/authors/" + ownerId + "/formulas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.hasItem(id)))
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());

        for (int i = 0; i < 2; i++) {
            call(put("/api/authors/" + ownerId + "/follow"), reader).andExpect(status().isNoContent());
        }
        call(get("/api/authors/" + ownerId), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$.followers").value(1)).andExpect(jsonPath("$.following").value(true));
        call(put("/api/authors/" + ownerId + "/follow"), OWNER).andExpect(status().isBadRequest());
        call(delete("/api/authors/" + ownerId + "/follow"), reader).andExpect(status().isNoContent());
        mvc.perform(get("/api/authors/" + ownerId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.followers").value(0));

        call(get(API + "/" + id), reader).andExpect(jsonPath("$.viewCount").value(0));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = java.util.stream.IntStream.range(0, 4).mapToObj(ignored -> executor.submit(() -> {
                start.await();
                return call(post(API + "/" + id + "/views"), reader).andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            for (var request : requests) {
                assertThat(request.get()).isEqualTo(204);
            }
        }
        call(get(API + "/" + id), reader).andExpect(jsonPath("$.viewCount").value(1));

        String legacyId = mongo.getCollection("formulas").insertOne(new Document("name", "LEGACY AUTHOR")
                .append("status", "PUBLIC").append("ownerUserId", OWNER))
                .getInsertedId().asObjectId().getValue().toHexString();
        mvc.perform(get("/api/authors/by-formula/" + legacyId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownerId));
    }

    @Test
    void ranksEligibleVotesAndRestrictsModerationAndNotifications() throws Exception {
        account(OWNER, "Formula author");
        String reader = "moderation-reader@example.com";
        String readerId = account(reader, "Reviewer");
        String newcomer = "new-reader@example.com";
        account(newcomer, "New reviewer");
        mongo.getCollection("users").updateOne(new Document("email", newcomer),
                new Document("$set", new Document("createdAt", new Date())));

        String id = draft("Ranked formula with reports", "");
        submit(id);
        approve(id);
        String rejected = draft("Rejected community test", "");
        submit(rejected);
        call(post(API + "/admin/" + rejected + "/reject").content(REVIEW), ADMIN).andExpect(status().isOk());

        call(post(API + "/" + id + "/ratings").content("{\"value\":1}"), newcomer).andExpect(status().isOk());
        call(get(API + "/ranking?days=7"), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));
        call(post(API + "/" + id + "/ratings").content("{\"value\":1}"), reader).andExpect(status().isOk());
        call(get(API + "/ranking?days=7"), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.hasItem(id)));
        mongo.getCollection("formula_ratings").updateOne(new Document("formulaId", id).append("userId", reader),
                new Document("$set", new Document("createdAt", Date.from(Instant.now().minus(8, ChronoUnit.DAYS)))));
        call(delete(API + "/" + id + "/ratings"), reader).andExpect(status().isOk());
        call(post(API + "/" + id + "/ratings").content("{\"value\":1}"), reader).andExpect(status().isOk());
        call(get(API + "/ranking?days=7"), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));
        call(get(API + "/ranking?days=30"), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.hasItem(id)));

        var comment = payload(call(post(API + "/" + id + "/comments").content("{\"content\":\"Incorrect comment\"}"), reader)
                .andExpect(status().isOk()));
        String commentId = comment.get("id").asText();
        call(get(API + "/" + id + "/comments"), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].authorId").value(readerId))
                .andExpect(jsonPath("$.content[0].own").value(true))
                .andExpect(jsonPath("$.content[0].userId").doesNotExist());

        String mathReport = """
                {"reason":"MATH_ERROR","details":"With X=2 the expected result is 4"}
                """;
        call(post(API + "/" + id + "/reports").content(mathReport), OWNER).andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            call(post(API + "/comments/" + commentId + "/reports").content(mathReport), OWNER)
                    .andExpect(status().isAccepted());
        }
        call(get(API + "/admin/reports"), reader).andExpect(status().isForbidden());
        removeReported(commentId);
        call(get(API + "/" + id + "/comments"), reader).andExpect(jsonPath("$.totalElements").value(0));
        call(get(API + "/" + id), reader).andExpect(jsonPath("$.commentCount").value(0));

        call(post(API + "/" + id + "/reports").content(mathReport), reader).andExpect(status().isAccepted());
        String originalReport = openReport(id);
        call(put(API + "/admin/" + id + "/revision")
                .content(revision(1, textFormula("Ranked formula with reports", "X+2"))), ADMIN)
                .andExpect(status().isOk());
        call(put(API + "/admin/reports/" + originalReport).content("""
                {"decision":"REMOVED","reason":"This report concerns the earlier revision"}
                """), ADMIN).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("reported content changed or is no longer public"));
        call(put(API + "/admin/reports/" + originalReport).content("""
                {"decision":"DISMISSED","reason":"The reported revision was superseded"}
                """), ADMIN).andExpect(status().isNoContent());
        call(post(API + "/" + id + "/reports").content(mathReport), reader).andExpect(status().isAccepted());
        removeReported(id);
        call(get(API + "/" + id), reader).andExpect(status().isNotFound());
        call(get(API + "/" + id + "/versions/1"), reader).andExpect(status().isNotFound());
        call(get(API + "/ranking?days=30"), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));

        JsonNode inbox = payload(call(get("/api/notifications?size=100"), OWNER).andExpect(status().isOk()));
        var types = new java.util.ArrayList<String>();
        String approval = null;
        for (JsonNode notification : inbox.get("content")) {
            if (List.of(id, rejected).contains(notification.get("formulaId").asText())) {
                types.add(notification.get("type").asText());
                if ("APPROVED".equals(notification.get("type").asText())) approval = notification.get("id").asText();
            }
        }
        assertThat(types).containsExactlyInAnyOrder("APPROVED", "REJECTED", "COMMENT", "RATING", "RATING", "CONTENT_REMOVED");
        var notice = mongo.getCollection("community_notifications").find(new Document("_id", approval));
        call(put("/api/notifications/" + approval + "/read"), reader).andExpect(status().isNoContent());
        assertThat(notice.first().getDate("readAt")).isNull();
        call(put("/api/notifications/" + approval + "/read"), OWNER).andExpect(status().isNoContent());
        assertThat(notice.first().getDate("readAt")).isNotNull();
    }

    @Test
    void calculatesMeasuredFormulasAndPreservesPinnedUnitsAcrossCatalogChanges() throws Exception {
        call(put(API + "/admin/units/L").content("""
                {"name":"Liter","dimension":"Volume","reference":"L","factor":"1","active":true}
                """), ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.unit.dimension").value("CUSTOM:Volume"));
        call(put(API + "/admin/units/mL").content("""
                {"name":"Milliliter","reference":"L","factor":"0.001","active":true}
                """), ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.unit.dimension").value("CUSTOM:Volume"));
        String path = API + "/admin/units/cm";
        String centimeter = """
                {"name":"Centimeter","reference":"m","factor":"0.01","active":true}
                """;
        call(put(path).content(centimeter), MEASURED_OWNER).andExpect(status().isForbidden());
        long version = payload(call(put(path).content(centimeter), ADMIN).andExpect(status().isOk()))
                .get("version").asLong();

        String formula = """
                {"name":"Measured length","group":"PHYSICS","equation":"X+Y","parameters":["X","Y"],
                "inputUnits":{"X":"cm","Y":"m"},"outputUnit":"m"}
                """;
        String id = createDraft(formula, MEASURED_OWNER);
        String inputs = """
                {"formulaName":"Measured length","variables":{"X":{"value":150},"Y":{"value":"0.001","unit":"km"}}}
                """;
        call(post(API + "/mine/calculate").content(inputs), MEASURED_OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("2.5")).andExpect(jsonPath("$.unit").value("m"));
        call(post(API + "/mine/calculate").content(inputs.replace("km", "kg")), MEASURED_OWNER)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Y expects a unit compatible with m"));
        call(post(API + "/builder/calculate").param("formulaId", id).content(
                "{\"formula\":" + formula + ",\"inputs\":" + inputs + "}"), MEASURED_OWNER)
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("2.5"));
        submit(id, MEASURED_OWNER);
        approve(id);

        call(put(path).content("""
                {"name":"Centimeter","reference":"m","factor":"0.02","active":false,"version":%d}
                """.formatted(version)), ADMIN).andExpect(status().isOk());
        call(put(path).content(centimeter), ADMIN).andExpect(status().isConflict());
        call(get(API + "/units"), MEASURED_OWNER).andExpect(jsonPath("$[?(@.code == 'cm')]").isEmpty());
        call(post(API + "/calculate").content(inputs), MEASURED_OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("2.5"));
        call(get(API + "/" + id + "/versions/1"), MEASURED_OWNER)
                .andExpect(jsonPath("$.unitSnapshots.X.factor").value("0.01"));
    }

    @Test
    void returnsStructuredErrorsAndPreservesLegacyExecution() throws Exception {
        createDraft(textFormula("Zero divisor", "1/(X-1)"), MEASURED_OWNER);
        String endpoint = API + "/mine/calculate";
        call(post(endpoint).content("""
                {"formulaName":"Zero divisor","variables":{"X":{"value":1}}}
                """), MEASURED_OWNER).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("division_by_zero"));
        call(post(endpoint).content("""
                {"formulaName":"Missing formula","variables":{"X":{"value":1}}}
                """), MEASURED_OWNER).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
        call(post(endpoint).content("""
                {"formulaName":"Zero divisor","variables":{"X":{"value":"NaN"}}}
                """), MEASURED_OWNER).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_json"));
        assertResult("Zero divisor", "1.0", MEASURED_OWNER);
    }

    @Test
    void checksMeasuredBuilderConnectionsAndUsesPinnedDependencies() throws Exception {
        String source = createDraft("""
                {"name":"Measured source","group":"PHYSICS","equation":"X*2","parameters":["X"],
                "inputUnits":{"X":"kg"},"outputUnit":"kg"}
                """, MEASURED_OWNER);
        submit(source, MEASURED_OWNER);
        approve(source);
        var compiled = json.readTree(builder("Measured builder", source, ""));
        ((com.fasterxml.jackson.databind.node.ObjectNode) compiled).set("inputUnits",
                json.readTree("{\"X\":\"m\"}"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) compiled).put("outputUnit", "kg");
        call(post(API + "/mine").content(compiled.toString()), MEASURED_OWNER).andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode) compiled).set("inputUnits",
                json.readTree("{\"X\":\"g\"}"));
        String id = createDraft(compiled.toString(), MEASURED_OWNER);
        call(post(API + "/mine/calculate").content("""
                {"formulaId":"%s","variables":{"X":{"value":"750"}}}
                """.formatted(id)), MEASURED_OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("1.5")).andExpect(jsonPath("$.unit").value("kg"));
    }

    @Test
    void acceptsMixedCompatibleUnitsForVariadicFormulas() throws Exception {
        String definition = """
                {"name":"Measured aggregate","group":"PHYSICS","variable":true,"equation":"SUM({VALUES})",
                "minParameters":2,"maxParameters":10,"inputUnits":{"ITEM":"m"},"outputUnit":"m"}
                """;
        call(post(API + "/insert").content(definition), MEASURED_OWNER).andExpect(status().isOk());
        JsonNode pending = payload(call(get(API + "/admin/pending"), ADMIN).andExpect(status().isOk()));
        String id = java.util.stream.StreamSupport.stream(pending.spliterator(), false)
                .filter(item -> item.path("name").asText().equals("MEASURED AGGREGATE"))
                .findFirst().orElseThrow().path("id").asText();
        approve(id);

        String mixed = """
                {"formulaId":"%s","values":[{"value":2,"unit":"km"},{"value":"350"}]}
                """.formatted(id);
        call(post(API + "/calculate").content(mixed), MEASURED_OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("2350")).andExpect(jsonPath("$.unit").value("m"));
        call(post(API + "/admin/" + id + "/calculate-preview").content(
                "{\"formula\":" + definition + ",\"inputs\":" + mixed + "}"), ADMIN)
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("2350"));
    }

    private String openReport(String targetId) throws Exception {
        JsonNode queue = payload(call(get(API + "/admin/reports"), ADMIN).andExpect(status().isOk()));
        String reportId = null;
        int matches = 0;
        for (JsonNode report : queue.get("content")) {
            if (targetId.equals(report.get("targetId").asText())) {
                reportId = report.get("id").asText();
                matches++;
            }
        }
        assertThat(matches).isEqualTo(1);
        return reportId;
    }

    private void removeReported(String targetId) throws Exception {
        call(put(API + "/admin/reports/" + openReport(targetId)).content("""
                {"decision":"REMOVED","reason":"Confirmed against the reference"}
                """), ADMIN).andExpect(status().isNoContent());
    }

    private String account(String email, String name) {
        UserEntity user = mongo.findOne(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("email").is(email)), UserEntity.class);
        if (user != null) {
            return user.getId();
        }
        user = new UserEntity();
        user.setEmail(email);
        user.setName(name);
        user.setProvider("google");
        user.setProviderUserId(email);
        user.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
        return mongo.insert(user).getId();
    }

    private JsonNode payload(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
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
        return createDraft(body, OWNER);
    }

    private String createDraft(String body, String owner) throws Exception {
        call(post(API + "/mine").content(body), owner).andExpect(status().isOk());
        String raw = call(get(API + "/mine"), owner).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String name = json.readTree(body).get("name").asText();
        for (JsonNode formula : json.readTree(raw)) {
            if (formula.get("name").asText().equalsIgnoreCase(name)) {
                return formula.get("id").asText();
            }
        }
        throw new AssertionError("Draft not found: " + name);
    }

    private void submit(String id) throws Exception {
        submit(id, OWNER);
    }

    private void submit(String id, String owner) throws Exception {
        call(post(API + "/mine/" + id + "/publish"), owner).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    private void approve(String id) throws Exception {
        call(post(API + "/admin/" + id + "/approve").content(REVIEW), ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLIC")).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
    }

    private void assertPinned(String composite, int version) throws Exception {
        call(get(API + "/mine/" + composite), OWNER).andExpect(status().isOk())
                .andExpect(jsonPath("$.expressionTree.sourceVersion").value(version));
    }

    private void assertResult(String name, String result) throws Exception {
        assertResult(name, result, OWNER);
    }

    private void assertResult(String name, String result, String owner) throws Exception {
        call(post(API + "/mine/execute").content("""
                {"formulaName":"%s","variables":{"X":2}}
                """.formatted(name)), owner).andExpect(status().isOk()).andExpect(content().string(result));
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
