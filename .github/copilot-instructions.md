## Math-assistant — quick agent guide

This project is a Spring Boot backend (Java 21) that provides REST endpoints for storing and executing mathematical formulas. The frontend was originally planned as Flutter but has changed: the web frontend will be a ReactJS app and the mobile app will use React Native (not included here). Use these notes to make practical, codebase-specific suggestions and edits.

Key places to look

- `src/main/java/com/claySoftware/MathExpAssistant/` — primary Java packages: `controllers`, `services`, `repositories`, `entities`, `helpers`, `config`, `models`.
- `src/main/resources/application.properties` — environment values (MongoDB, OAuth, JWT).
- `build.gradle` — build, dependencies (Spring Boot 3.2.5, MongoDB, exp4j, jjwt).

High-level architecture

- Spring Boot REST API. Entry point: `MathExpAssistantApplication`.
- Controllers handle HTTP, delegate business logic to `services`.
- `services` use `repositories` (Spring Data MongoDB) to persist `entities`.
- `helpers` contain pure logic utilities (example: `FormulaExecutor` using exp4j to evaluate expressions).
- Security: OAuth2 login + JWT creation (`JwtTokenProvider`) and a custom `JwtAuthenticationFilter` that extracts bearer tokens from `Authorization` header.

Data & flows (example)

- Formula lifecycle: `FormulaController` -> `FormulaService` -> `FormulaRepository` (MongoDB `formulas` collection). Example methods: `executeFormula`, `insertNewFormula`, `getFormulaByName`.
- Evaluation: `FormulaExecutor.executeFormula(equation, variables)` uses exp4j ExpressionBuilder and expects variables keys to match `FormulaEntity.parameters`.

Project-specific conventions and patterns

- MongoDB repositories return Optionals: use `Optional<T>` checks (e.g. `findByName`, `findAllByStatus`). Prefer returning clear error strings (current controllers return plain strings; follow existing style when editing).
- Entities use Jakarta Validation annotations (`@NotBlank`, `@NotEmpty`, `@Pattern`) and map `group` string to enum `FormulaGroup` via `FormulaEntity.getGroup()`.
- Controller endpoints are under `/api/formulas`. Keep path and parameter names consistent (e.g. `formulaName` request param on `/execute`).
- Simple approach to errors and responses: current code returns human-readable strings rather than structured JSON for many endpoints. New changes should maintain compatibility unless you update API consumers.

Build / test / run notes (developer workflows)

- Build: Gradle wrapper. On Windows (cmd.exe) run:
  - gradlew.bat build
  - gradlew.bat bootRun
- Tests: `gradlew.bat test` (uses JUnit Platform). Unit/integration tests live under `src/test/java` and some integration tests exist in `test/` compiled classes.
- DB: App expects MongoDB (default in `application.properties` points to localhost:27017, database `mathAssistant`). For local dev, run a MongoDB instance or adjust `spring.data.mongodb.*` entries.
- OAuth / JWT: `application.properties` contains OAuth client ids and `jwt.secret` used by `JwtTokenProvider`. Do NOT leak or change secrets in PRs; instead reference environment variables or `application-local.properties` when adding samples.

Frontend notes

- The frontend has moved to ReactJS for web. Use `fetch` or `axios` for HTTP; client should call the REST endpoints under `/api/formulas`.
- Mobile app will use React Native when implemented. Keep API stable (plain-string responses currently) or add compatibility layers if changing response shapes.

Security and tokens

- JWT creation happens in `JwtTokenProvider.createToken(Authentication)`. Tokens are HS512-signed with `jwt.secret` from properties.
- `JwtAuthenticationFilter` expects header `Authorization: Bearer <token>` and sets a `UsernamePasswordAuthenticationToken` with an empty authority list. If you change authentication shape, update both provider and filter.

Quick code examples to follow (copyable patterns)

- Evaluating a formula (use `FormulaExecutor`):

  - equation: `"a + b * c"`
  - variables map must contain keys listed in `FormulaEntity.parameters` (e.g. `["a","b","c"]`).

- Repository query examples:
  - `formulaRepository.findByName(name)` returns `Optional<FormulaEntity>`
  - `formulaRepository.findAllByStatus("public")` returns `Optional<List<FormulaEntity>>`

Editing guidance for agents

- Preserve existing API shapes and return types unless task explicitly requires a breaking change.
- When adding new endpoints, place controllers under `controllers/` and register any new beans in `services/` with `@Service`.
- Prefer small, testable changes: add unit tests under `src/test/java` and run `gradlew.bat test`.
- Use `FormulaExecutor` for expression evaluation rather than reinventing parsing/evaluation.

What to watch for (edge cases found)

- Many controller methods return raw strings or call `Optional.get()` without presence checks (e.g. `getAllFormula` assumes `Optional<List<FormulaEntity>>.get()` is present). Fixes should add safe Optional handling.
- Validation: entities use annotations but controllers don't always validate presence of Optional values — maintain defensive checks.
- Security: `JwtAuthenticationFilter` prints token errors to stdout. Use logging or standard error handling when improving security code.

Files to reference when implementing tasks

- `FormulaController.java`, `FormulaService.java`, `FormulaRepository.java`, `FormulaEntity.java`, `FormulaExecutor.java`, `JwtTokenProvider.java`, `JwtAuthenticationFilter.java`, `application.properties`, `build.gradle`.

If something is missing

- If frontend behavior or message formats are unclear, prefer preserving current string-returning behavior and ask for clarification before changing API shapes.
- If secrets or environment-specific values are needed, use environment variables and document in `README.md` rather than editing `application.properties` in repo.

If you update this file, keep it concise and cite concrete files and line examples rather than broad recommendations.

---

Please review these notes and tell me if you'd like me to: (a) tighten error handling across controllers, (b) add small unit tests for `FormulaExecutor`, or (c) convert controller responses to structured JSON. I'll iterate on your preference.
