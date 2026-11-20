package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaVersion;
import com.claySoftware.MathExpAssistant.services.FormulaModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/formulas")
public class FormulaModerationController {
    private final FormulaModerationService moderation;

    public FormulaModerationController(FormulaModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping("/admin/pending")
    public List<FormulaEntity> pending() {
        return moderation.pending();
    }

    @PostMapping("/admin/{id}/approve")
    public FormulaEntity approve(@PathVariable String id, @Valid @RequestBody ReviewRequest review,
                                 Authentication authentication) {
        return moderation.approve(id, authentication.getName(), review.reason());
    }

    @PostMapping("/admin/{id}/reject")
    public FormulaEntity reject(@PathVariable String id, @Valid @RequestBody ReviewRequest review,
                                Authentication authentication) {
        return moderation.reject(id, authentication.getName(), review.reason());
    }

    @PutMapping("/admin/{id}/revision")
    public FormulaEntity revise(@PathVariable String id, @Valid @RequestBody RevisionRequest request,
                                Authentication authentication) {
        return moderation.revise(id, request.expectedVersion(), request.formula(),
                authentication.getName(), request.reason());
    }

    @GetMapping("/{id}/versions")
    public List<FormulaVersion> history(@PathVariable String id) {
        return moderation.history(id);
    }

    @GetMapping("/{id}/versions/{version}")
    public FormulaVersion version(@PathVariable String id, @PathVariable int version) {
        return moderation.version(id, version);
    }

    @DeleteMapping("/delete/{id}")
    public String delete(@PathVariable String id) {
        moderation.delete(id);
        return "formula deleted with success !";
    }

    public record ReviewRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record RevisionRequest(@NotBlank @Size(max = 1000) String reason,
                                  @NotNull @Positive Integer expectedVersion,
                                  @NotNull FormulaEntity formula) {}
}
