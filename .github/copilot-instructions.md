## Math-assistant — Copilot agent quick guide

Purpose: help AI coding agents be productive quickly in this Spring Boot (Java 21) backend that stores and executes mathematical formulas.

Essentials

- Entry point: `MathExpAssistantApplication` (Spring Boot). Main packages under `src/main/java/com/claySoftware/MathExpAssistant/` — `controllers`, `services`, `repositories`, `entities`, `helpers`, `config`, `models`.
- DB: MongoDB via Spring Data. Config in `src/main/resources/application.properties` (defaults to localhost:27017, DB `mathAssistant`).

Architecture & dataflow (practical)

- HTTP -> `controllers` (e.g. `FormulaController`) -> `services` (business logic) -> `repositories` (MongoDB `formulas` collection) -> `entities`.
- Formula evaluation: `FormulaExecutor.executeFormula(equation, variables)` uses exp4j; variables keys must match `FormulaEntity.parameters`.
- Security: OAuth2 login + JWT. Key classes: `JwtTokenProvider` and `JwtAuthenticationFilter` (expect `Authorization: Bearer <token>`).

Developer workflows (commands you will run)

- Build: `gradlew.bat build` (Windows) or `./gradlew build` (Unix).
- Run locally: `gradlew.bat bootRun`.
- Tests: `gradlew.bat test`. Integration tests exist under `src/test/java` and a compiled `test/` folder.
- Docker: project includes `Dockerfile` and `docker-compose.yml` for containerized runs; use Docker Desktop on Windows.

Project-specific conventions (must-follow)

- Repositories return `Optional<T>`; always check presence before `.get()` — many existing controllers do not, so prefer safe handling.
- Controllers usually return plain human-readable strings (not structured JSON). Preserve this shape unless you coordinate breaking changes with consumers.
- Entities use Jakarta Validation (`@NotBlank`, `@NotEmpty`, `@Pattern`). Prefer keeping validation annotations and add `@Valid` usage in controllers when adding endpoints.

Key files to inspect for context and examples

- Controller flow: [src/main/java/com/claySoftware/MathExpAssistant/controllers/FormulaController.java](src/main/java/com/claySoftware/MathExpAssistant/controllers/FormulaController.java)
- Business logic: [src/main/java/com/claySoftware/MathExpAssistant/services/FormulaService.java](src/main/java/com/claySoftware/MathExpAssistant/services/FormulaService.java)
- Persistence: [src/main/java/com/claySoftware/MathExpAssistant/repositories/FormulaRepository.java](src/main/java/com/claySoftware/MathExpAssistant/repositories/FormulaRepository.java)
- Evaluation helper: [src/main/java/com/claySoftware/MathExpAssistant/helpers/FormulaExecutor.java](src/main/java/com/claySoftware/MathExpAssistant/helpers/FormulaExecutor.java)
- Security: [src/main/java/com/claySoftware/MathExpAssistant/config/JwtTokenProvider.java](src/main/java/com/claySoftware/MathExpAssistant/config/JwtTokenProvider.java)

Quick code examples (copyable)

- Evaluate: call `FormulaExecutor.executeFormula("a + b * c", Map.of("a",1,"b",2,"c",3))` — variable names must match `FormulaEntity.parameters`.
- Repo lookup: `formulaRepository.findByName(name)` returns `Optional<FormulaEntity>` — handle `isPresent()`.

Safety notes

- Never commit secrets (OAuth client ids, `jwt.secret`) — prefer env variables or `application-local.properties` and document in `README.md`.

When editing or adding endpoints

- Place controllers in `controllers/`, services in `services/` annotated with `@Service`.
- Keep API parameter names and `/api/formulas` paths consistent (existing clients expect `formulaName` on `/execute`).
- Add unit tests under `src/test/java` and run `gradlew.bat test` before pushing.

If anything is unclear or you want stronger opinions (error-shape changes, validation tightening, JWT behavior), tell me which area to expand.
