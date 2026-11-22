package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.DecimalCalculation;
import com.claySoftware.MathExpAssistant.helpers.EquationBuilder;
import com.claySoftware.MathExpAssistant.helpers.FormulaExecutor;
import com.claySoftware.MathExpAssistant.helpers.FormulaValidator;
import com.claySoftware.MathExpAssistant.helpers.FormulaTreeCompiler;
import com.claySoftware.MathExpAssistant.helpers.VariableBuilder;
import com.claySoftware.MathExpAssistant.models.ExecuteFormulaRequest;
import com.claySoftware.MathExpAssistant.models.CalculationRequest;
import com.claySoftware.MathExpAssistant.models.CalculationResult;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaCompilationResult;
import com.claySoftware.MathExpAssistant.models.FormulaInputMode;
import com.claySoftware.MathExpAssistant.models.FormulaPreviewRequest;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaSearchResponse;
import com.claySoftware.MathExpAssistant.models.FormulaSearchScope;
import com.claySoftware.MathExpAssistant.models.FormulaType;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaWrites;
import com.claySoftware.MathExpAssistant.utils.FormulaNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FormulaService {

    private final FormulaRepository formulaRepository;
    private final FormulaExecutor formulaExecutor;
    private final FormulaValidator validator;
    private final FormulaTreeCompiler treeCompiler;
    private final FormulaWrites writes;
    private final CalculationService calculations;

    public FormulaService(
            FormulaExecutor formulaExecutor,
            FormulaRepository formulaRepository,
            FormulaValidator validator,
            FormulaTreeCompiler treeCompiler,
            FormulaWrites writes, CalculationService calculations) {
        this.formulaExecutor = formulaExecutor;
        this.formulaRepository = formulaRepository;
        this.validator = validator;
        this.treeCompiler = treeCompiler;
        this.writes = writes;
        this.calculations = calculations;
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
        return formulaRepository.findAllByOwnerUserId(ownerUserId);
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
        return formulaRepository.findByIdAndOwnerUserId(id, ownerUserId);
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

    public CalculationResult calculate(CalculationRequest request, String owner) {
        Optional<FormulaEntity> formula;
        if (request.formulaId() != null && !request.formulaId().isBlank()) {
            formula = owner == null
                    ? formulaRepository.findByIdAndStatus(request.formulaId(), FormulaStatus.PUBLIC)
                    : formulaRepository.findByIdAndOwnerUserId(request.formulaId(), owner)
                            .filter(item -> item.getStatus() == FormulaStatus.PRIVATE);
        } else {
            if (request.formulaName() == null || request.formulaName().isBlank()) {
                throw new IllegalArgumentException("formulaId or formulaName is required");
            }
            String name = FormulaNormalizer.normalizeEquation(request.formulaName());
            formula = owner == null ? formulaRepository.findByNameAndStatus(name, FormulaStatus.PUBLIC)
                    : formulaRepository.findByNameAndOwnerUserIdAndStatus(name, owner, FormulaStatus.PRIVATE);
        }
        return calculations.calculate(formula.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Formula not found")), request);
    }

    public CalculationResult calculatePreview(FormulaEntity formula, CalculationRequest request, String id,
                                               String owner, boolean publicRevision) {
        if (formula == null || request == null) throw new IllegalArgumentException("Formula and inputs are required");
        FormulaEntity previous = null;
        if (id != null) {
            var existing = publicRevision ? formulaRepository.findByIdAndStatus(id, FormulaStatus.PUBLIC)
                    : getUserFormula(id, owner);
            previous = existing.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formula not found"));
        }
        String error = validateCalculationPreview(formula, previous, publicRevision);
        if (error != null) throw new IllegalArgumentException(error);
        return calculations.calculate(formula, request);
    }

    public String validatePublicRevision(FormulaEntity formula, String id) {
        var previous = formulaRepository.findByIdAndStatus(id, FormulaStatus.PUBLIC)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formula not found"));
        return validateCalculationPreview(formula, previous, true);
    }

    private String validateCalculationPreview(FormulaEntity formula, FormulaEntity previous, boolean publicRevision) {
        if (!publicRevision) return validateUserFormula(formula, previous);
        normalizeFormula(formula);
        String error = prepareFormulaInput(formula, previous);
        return error == null ? validatePublication(formula) : error;
    }

    public String insertNewFormula(FormulaEntity formulaEntity, String ownerUserId) {
        formulaEntity.setStatus(FormulaStatus.UNDER_REVIEW);
        formulaEntity.setOwnerUserId(ownerUserId);
        formulaEntity.setOwnerEmail(ownerUserId);
        normalizeFormula(formulaEntity);
        String preparationError = prepareFormulaInput(formulaEntity, null);
        if (preparationError != null) {
            return "validation_error: " + preparationError;
        }

        String validationError = validatePublication(formulaEntity);
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
        formulaRepository.insert(formulaEntity);
        return "new formula successful saved";
    }

    public String validateUserFormula(FormulaEntity formulaEntity) {
        return validateUserFormula(formulaEntity, null);
    }

    public String validateUserFormula(FormulaEntity formulaEntity, FormulaEntity previous) {
        normalizeFormula(formulaEntity);
        formulaEntity.setVariable(false);
        formulaEntity.setStatus(FormulaStatus.PRIVATE);
        String preparationError = prepareFormulaInput(formulaEntity, previous);
        if (preparationError != null) {
            return preparationError;
        }
        return validateCustomFormula(formulaEntity);
    }

    public Map<String, Object> previewUserFormula(FormulaPreviewRequest request, FormulaEntity previous) {
        if (request == null || request.getFormula() == null) {
            return Map.of("valid", false, "message", "formula is required");
        }
        FormulaEntity formula = request.getFormula();
        String validationError = validateUserFormula(formula, previous);
        if (validationError != null) {
            return Map.of("valid", false, "message", validationError);
        }

        if (CalculationService.measured(formula)) {
            return Map.of("valid", false, "message", "Use the calculation preview API for formulas with units");
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

        String preparationError = prepareFormulaInput(formulaEntity, null);
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "custom formula already exists for this name and group");
        }

        Instant now = Instant.now();
        formulaEntity.setCreatedAt(now);
        formulaEntity.setUpdatedAt(now);
        formulaRepository.insert(formulaEntity);
        return "new custom formula successful saved";
    }

    public String updateUserFormula(String id, FormulaEntity formulaEntity, String ownerUserId, String ownerEmail) {
        FormulaEntity existing = privateFormula(id, ownerUserId);

        normalizeFormula(formulaEntity);
        prepareCustomFormulaForOwner(formulaEntity, ownerUserId, ownerEmail);
        String preparationError = prepareFormulaInput(formulaEntity, existing);
        if (preparationError != null) {
            return "validation_error: " + preparationError;
        }
        formulaEntity.setId(id);
        formulaEntity.setCreatedAt(existing.getCreatedAt());
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "custom formula already exists for this name and group");
        }

        writes.replace(existing, formulaEntity);
        return "custom formula successful updated";
    }

    public String deleteUserFormula(String id, String ownerUserId) {
        writes.delete(privateFormula(id, ownerUserId));
        return "custom formula deleted with success";
    }

    public String publishUserFormula(String id, String ownerUserId) {
        FormulaEntity formula = privateFormula(id, ownerUserId);
        formula.setStatus(FormulaStatus.UNDER_REVIEW);
        formula.setUpdatedAt(Instant.now());
        writes.replaceFromStatus(formula, FormulaStatus.PRIVATE);
        return "custom formula submitted for review";
    }

    private FormulaEntity privateFormula(String id, String ownerUserId) {
        FormulaEntity formula = getUserFormula(id, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "custom formula not found for user"));
        if (formula.getStatus() != FormulaStatus.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "formula must be PRIVATE");
        }
        return formula;
    }

    private String executeExistingFormula(FormulaEntity formula, ExecuteFormulaRequest req) {
        if (CalculationService.measured(formula)) {
            return "validation_error: use the calculation API for formulas with units";
        }
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

    void normalizeFormula(FormulaEntity formula) {
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

    String prepareFormulaInput(FormulaEntity formula, FormulaEntity previous) {
        FormulaInputMode mode = formula.getInputMode();
        if (mode == null) {
            mode = formula.getExpressionTree() == null ? FormulaInputMode.TEXT : FormulaInputMode.BUILDER;
            formula.setInputMode(mode);
        }

        if (mode == FormulaInputMode.TEXT) {
            formula.setExpressionTree(null);
            formula.setSourceSnapshots(null);
            formula.setBuilderVersion(null);
            return prepareUnits(formula, previous);
        }
        if (formula.isVariable()) {
            return "builder formulas must be fixed";
        }
        if (formula.getExpressionTree() == null) {
            return "expressionTree is required in BUILDER mode";
        }

        try {
            FormulaCompilationResult compiled = previous == null
                    ? treeCompiler.compile(formula.getExpressionTree())
                    : treeCompiler.compile(formula.getExpressionTree(), previous);
            formula.setEquation(compiled.equation());
            formula.setDisplayEquation(compiled.displayEquation());
            formula.setParameters(compiled.parameters());
            formula.setSourceSnapshots(compiled.sourceSnapshots());
            formula.setBuilderVersion(1);
            return prepareUnits(formula, previous);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    private String prepareUnits(FormulaEntity formula, FormulaEntity previous) {
        try {
            calculations.prepare(formula, previous);
            return null;
        } catch (IllegalArgumentException | DecimalCalculation.Failure error) {
            return error.getMessage();
        }
    }

    String validatePublication(FormulaEntity formula) {
        String error = validator.validateFormulaForInsert(formula);
        if (error != null) {
            return error;
        }
        if (CalculationService.measured(formula)) return null;
        return formulaExecutor.validateSyntax(formula.isVariable()
                ? EquationBuilder.buildVariadicEquation(formula.getEquation(), 2)
                : formula.getEquation());
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

        if (CalculationService.measured(formula)) return null;

        return formulaExecutor.validateSyntax(formula.getEquation(), formula.getParameters());
    }
}
