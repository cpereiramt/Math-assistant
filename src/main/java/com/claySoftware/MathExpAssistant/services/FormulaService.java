package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.EquationBuilder;
import com.claySoftware.MathExpAssistant.helpers.FormulaExecutor;
import com.claySoftware.MathExpAssistant.helpers.FormulaValidator;
import com.claySoftware.MathExpAssistant.helpers.FormulaTreeCompiler;
import com.claySoftware.MathExpAssistant.helpers.VariableBuilder;
import com.claySoftware.MathExpAssistant.models.ExecuteFormulaRequest;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaCompilationResult;
import com.claySoftware.MathExpAssistant.models.FormulaInputMode;
import com.claySoftware.MathExpAssistant.models.FormulaPreviewRequest;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaSearchResponse;
import com.claySoftware.MathExpAssistant.models.FormulaSearchScope;
import com.claySoftware.MathExpAssistant.models.FormulaType;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.utils.FormulaNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FormulaService {

    private final FormulaRepository formulaRepository;
    private final FormulaExecutor formulaExecutor;
    private final FormulaValidator validator;
    private final FormulaTreeCompiler treeCompiler;

    public FormulaService(
            FormulaExecutor formulaExecutor,
            FormulaRepository formulaRepository,
            FormulaValidator validator,
            FormulaTreeCompiler treeCompiler) {
        this.formulaExecutor = formulaExecutor;
        this.formulaRepository = formulaRepository;
        this.validator = validator;
        this.treeCompiler = treeCompiler;
    }

    public List<FormulaEntity> listPublicFormulas() {
        return formulaRepository.findAllByStatusOrderByUpvotesDescAverageRatingDescCreatedAtDesc(FormulaStatus.PUBLIC);
    }

    public List<FormulaEntity> listBuilderCatalog() {
        return listPublicFormulas().stream()
                .filter(formula -> !formula.isVariable())
                .toList();
    }

    public List<FormulaEntity> listUserFormulas(String ownerUserId) {
        return formulaRepository.findAllByOwnerUserIdAndStatus(ownerUserId, FormulaStatus.PRIVATE);
    }

    public FormulaSearchResponse searchFormulas(
            String query,
            List<FormulaGroup> groups,
            FormulaType type,
            FormulaSearchScope scope,
            String ownerUserId,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to zero");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        List<String> allowedSortFields = List.of(
                "name", "createdAt", "updatedAt", "upvotes", "downvotes", "ratingCount",
                "averageRating", "commentCount", "viewCount");
        if (!allowedSortFields.contains(sortBy)) {
            throw new IllegalArgumentException("sortBy must be one of: " + allowedSortFields);
        }

        FormulaSearchScope effectiveScope = scope == null ? FormulaSearchScope.PUBLIC : scope;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<FormulaEntity> result = formulaRepository.search(
                query, groups, type, effectiveScope, ownerUserId, pageable);

        return new FormulaSearchResponse(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(),
                sortBy + "," + direction.name().toLowerCase());
    }

    public Optional<FormulaEntity> getUserFormula(String id, String ownerUserId) {
        return formulaRepository.findByIdAndOwnerUserIdAndStatus(id, ownerUserId, FormulaStatus.PRIVATE);
    }

    // TODO : Put some form of cache, maybe using caffeine
    public String executeFormula(ExecuteFormulaRequest req) {
        if (req == null) {
            return "validation_error: request body is required";
        }
        if (req.getFormulaName() == null || req.getFormulaName().isBlank()) {
            return "validation_error: formulaName is required";
        }

        Optional<FormulaEntity> opt = formulaRepository.findByNameAndStatus(
                FormulaNormalizer.normalizeEquation(req.getFormulaName()),
                FormulaStatus.PUBLIC);

        if (opt.isEmpty()) {
            return "not_found: formula not found on database";
        }

        return executeExistingFormula(opt.get(), req);
    }

    public String executeUserFormula(ExecuteFormulaRequest req, String ownerUserId) {
        if (req == null) {
            return "validation_error: request body is required";
        }
        if (req.getFormulaName() == null || req.getFormulaName().isBlank()) {
            return "validation_error: formulaName is required";
        }

        Optional<FormulaEntity> opt = formulaRepository.findByNameAndOwnerUserIdAndStatus(
                FormulaNormalizer.normalizeEquation(req.getFormulaName()),
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (opt.isEmpty()) {
            return "not_found: custom formula not found for user";
        }

        return executeExistingFormula(opt.get(), req);
    }

    public String insertNewFormula(FormulaEntity formulaEntity) {
        normalizeFormula(formulaEntity);

        String validationError = validator.validateFormulaForInsert(formulaEntity);
        if (validationError != null) {
            return "validation_error: " + validationError;
        }

        FormulaEntity existingFormula = formulaRepository
                .findByNameAndStatus(formulaEntity.getName(), FormulaStatus.PUBLIC)
                .orElse(null);

        if (existingFormula != null && existingFormula.getId() != null) {
            return "Formula already exists";
        }

        Instant now = Instant.now();
        formulaEntity.setCreatedAt(now);
        formulaEntity.setUpdatedAt(now);
        formulaRepository.save(formulaEntity);
        return "new formula successful saved";
    }

    public String validateUserFormula(FormulaEntity formulaEntity) {
        normalizeFormula(formulaEntity);
        formulaEntity.setVariable(false);
        formulaEntity.setStatus(FormulaStatus.PRIVATE);
        String preparationError = prepareFormulaInput(formulaEntity);
        if (preparationError != null) {
            return preparationError;
        }
        return validateCustomFormula(formulaEntity);
    }

    public Map<String, Object> previewUserFormula(FormulaPreviewRequest request) {
        if (request == null || request.getFormula() == null) {
            return Map.of("valid", false, "message", "formula is required");
        }
        FormulaEntity formula = request.getFormula();
        String validationError = validateUserFormula(formula);
        if (validationError != null) {
            return Map.of("valid", false, "message", validationError);
        }

        String result = executeFixed(formula, request.getVariables());
        if (result.startsWith("validation_error:") || result.startsWith("calculation_error:")
                || result.startsWith("server_error:")) {
            return Map.of("valid", false, "message", result);
        }
        return Map.of(
                "valid", true,
                "equation", formula.getEquation(),
                "displayEquation", formula.getDisplayEquation(),
                "parameters", formula.getParameters(),
                "result", Double.valueOf(result));
    }

    public String insertUserFormula(FormulaEntity formulaEntity, String ownerUserId, String ownerEmail) {
        normalizeFormula(formulaEntity);
        prepareCustomFormulaForOwner(formulaEntity, ownerUserId, ownerEmail);

        String preparationError = prepareFormulaInput(formulaEntity);
        if (preparationError != null) {
            return "validation_error: " + preparationError;
        }

        String validationError = validateCustomFormula(formulaEntity);
        if (validationError != null) {
            return "validation_error: " + validationError;
        }

        Optional<FormulaEntity> existingFormula = formulaRepository.findByNameAndGroupAndOwnerUserIdAndStatus(
                formulaEntity.getName(),
                formulaEntity.getGroup(),
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (existingFormula.isPresent()) {
            return "validation_error: custom formula already exists for this name and group";
        }

        Instant now = Instant.now();
        formulaEntity.setCreatedAt(now);
        formulaEntity.setUpdatedAt(now);
        formulaRepository.save(formulaEntity);
        return "new custom formula successful saved";
    }

    public String updateUserFormula(String id, FormulaEntity formulaEntity, String ownerUserId, String ownerEmail) {
        Optional<FormulaEntity> existing = formulaRepository.findByIdAndOwnerUserIdAndStatus(
                id,
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (existing.isEmpty()) {
            return "not_found: custom formula not found for user";
        }

        normalizeFormula(formulaEntity);
        prepareCustomFormulaForOwner(formulaEntity, ownerUserId, ownerEmail);
        String preparationError = prepareFormulaInput(formulaEntity);
        if (preparationError != null) {
            return "validation_error: " + preparationError;
        }
        formulaEntity.setId(id);
        formulaEntity.setCreatedAt(existing.get().getCreatedAt());
        formulaEntity.setUpdatedAt(Instant.now());

        String validationError = validateCustomFormula(formulaEntity);
        if (validationError != null) {
            return "validation_error: " + validationError;
        }

        Optional<FormulaEntity> duplicate = formulaRepository.findByNameAndGroupAndOwnerUserIdAndStatus(
                formulaEntity.getName(),
                formulaEntity.getGroup(),
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (duplicate.isPresent() && !id.equals(duplicate.get().getId())) {
            return "validation_error: custom formula already exists for this name and group";
        }

        formulaRepository.save(formulaEntity);
        return "custom formula successful updated";
    }

    public String deleteUserFormula(String id, String ownerUserId) {
        Optional<FormulaEntity> existing = formulaRepository.findByIdAndOwnerUserIdAndStatus(
                id,
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (existing.isEmpty()) {
            return "not_found: custom formula not found for user";
        }

        formulaRepository.delete(existing.get());
        return "custom formula deleted with success";
    }

    public String publishUserFormula(String id, String ownerUserId) {
        Optional<FormulaEntity> existing = formulaRepository.findByIdAndOwnerUserIdAndStatus(
                id, ownerUserId, FormulaStatus.PRIVATE);
        if (existing.isEmpty()) {
            return "not_found: custom formula not found for user";
        }

        FormulaEntity formula = existing.get();
        formula.setStatus(FormulaStatus.PUBLIC);
        formula.setUpdatedAt(Instant.now());
        formulaRepository.save(formula);
        return "custom formula published with success";
    }

    private String executeExistingFormula(FormulaEntity formula, ExecuteFormulaRequest req) {
        boolean isVariadic = Boolean.TRUE.equals(formula.isVariable());

        if (!isVariadic) {
            return executeFixed(formula, req.getVariables());
        }

        return executeVariadic(formula, req.getValues());
    }

    private String executeFixed(FormulaEntity formula, Map<String, Double> variables) {
        if (variables == null || variables.isEmpty()) {
            return "validation_error: variables is required for fixed formulas";
        }

        if (formula.getParameters() == null || formula.getParameters().isEmpty()) {
            return "server_error: fixed formula missing parameters configuration";
        }

        if (formula.getEquation() == null || formula.getEquation().isBlank()) {
            return "server_error: fixed formula missing equation configuration";
        }

        if (!variables.keySet().containsAll(formula.getParameters())) {
            return "validation_error: some parameters are not provided. Expected: " + formula.getParameters();
        }

        if (!formula.getParameters().containsAll(variables.keySet())) {
            return "validation_error: extra parameters provided. Expected only: " + formula.getParameters();
        }

        try {
            Map<String, Double> normalizedVars = FormulaNormalizer.normalizeVariables(variables);
            double result = formulaExecutor.executeFormula(formula.getEquation(), normalizedVars);
            return String.valueOf(result);
        } catch (Exception e) {
            return "calculation_error: " + e.getMessage();
        }
    }

    private String executeVariadic(FormulaEntity formula, List<Double> values) {
        if (values == null || values.isEmpty()) {
            return "validation_error: values is required for variadic formulas";
        }

        if (formula.getEquation() == null || formula.getEquation().isBlank()) {
            return "server_error: variadic formula missing equation configuration";
        }

        Integer min = formula.getMinParameters();
        Integer max = formula.getMaxParameters();

        if (min != null && values.size() < min) {
            return "validation_error: at least " + min + " values are required";
        }
        if (max != null && values.size() > max) {
            return "validation_error: at most " + max + " values are allowed";
        }

        String equation;
        Map<String, Double> vars;

        try {
            equation = EquationBuilder.buildVariadicEquation(formula.getEquation(), values.size());
            vars = VariableBuilder.buildVariables(values);
        } catch (Exception e) {
            return "validation_error: " + e.getMessage();
        }

        try {
            double result = formulaExecutor.executeFormula(equation, vars);
            return String.valueOf(result);
        } catch (Exception e) {
            return "calculation_error: " + e.getMessage();
        }
    }

    private void normalizeFormula(FormulaEntity formula) {
        if (formula == null) {
            return;
        }

        if (formula.getName() != null) {
            formula.setName(formula.getName().trim().toUpperCase());
        }

        if (formula.getEquation() != null) {
            formula.setEquation(FormulaNormalizer.normalizeEquation(formula.getEquation()));
        }

        if (formula.getDisplayEquation() == null || formula.getDisplayEquation().isBlank()) {
            formula.setDisplayEquation(formula.getEquation());
        } else {
            formula.setDisplayEquation(formula.getDisplayEquation().trim());
        }

        if (formula.getParameters() != null) {
            formula.setParameters(FormulaNormalizer.normalizeParameters(formula.getParameters()));
        }
    }

    private void prepareCustomFormulaForOwner(FormulaEntity formula, String ownerUserId, String ownerEmail) {
        formula.setStatus(FormulaStatus.PRIVATE);
        formula.setOwnerUserId(ownerUserId);
        formula.setOwnerEmail(ownerEmail);
        formula.setVariable(false);
        formula.setMinParameters(null);
        formula.setMaxParameters(null);
    }

    private String prepareFormulaInput(FormulaEntity formula) {
        FormulaInputMode mode = formula.getInputMode();
        if (mode == null) {
            mode = formula.getExpressionTree() == null ? FormulaInputMode.TEXT : FormulaInputMode.BUILDER;
            formula.setInputMode(mode);
        }

        if (mode == FormulaInputMode.TEXT) {
            formula.setExpressionTree(null);
            formula.setSourceSnapshots(null);
            formula.setBuilderVersion(null);
            return null;
        }
        if (formula.getExpressionTree() == null) {
            return "expressionTree is required in BUILDER mode";
        }

        try {
            FormulaCompilationResult compiled = treeCompiler.compile(formula.getExpressionTree());
            formula.setEquation(compiled.equation());
            formula.setDisplayEquation(compiled.displayEquation());
            formula.setParameters(compiled.parameters());
            formula.setSourceSnapshots(compiled.sourceSnapshots());
            formula.setBuilderVersion(1);
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    private String validateCustomFormula(FormulaEntity formula) {
        if (formula == null) {
            return "Payload is required";
        }

        if (formula.isVariable()) {
            return "custom formulas currently support fixed equations only";
        }

        String validationError = validator.validateFormulaForInsert(formula);
        if (validationError != null) {
            return validationError;
        }

        try {
            Map<String, Double> testVariables = new HashMap<>();
            for (String parameter : formula.getParameters()) {
                testVariables.put(parameter, 1.0);
            }
            formulaExecutor.executeFormula(formula.getEquation(), testVariables);
            return null;
        } catch (Exception e) {
            return "invalid equation syntax: " + e.getMessage();
        }
    }
}
