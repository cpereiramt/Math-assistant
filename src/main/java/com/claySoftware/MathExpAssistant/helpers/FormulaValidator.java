package com.claySoftware.MathExpAssistant.helpers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import org.springframework.stereotype.Component;

@Component
public class FormulaValidator {

    public String validateFormulaForInsert(FormulaEntity f) {
        if (f == null)
            return "Payload is required";
        if (f.getName() == null || f.getName().isBlank())
            return "name is required";
        if (f.getGroup() == null || f.getGroup().name().isBlank())
            return "group is required";
        if (f.getEquation() == null || f.getEquation().isBlank())
            return "equation is required";

        boolean isVariadic = Boolean.TRUE.equals(f.isVariable());

        if (!isVariadic) {
            if (f.getParameters() == null || f.getParameters().isEmpty()) {
                return "parameters is required for fixed formulas";
            }

            var set = new java.util.HashSet<String>();
            for (String p : f.getParameters()) {
                if (p == null || p.isBlank())
                    return "parameters cannot contain empty values";
                if (!p.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
                    return "invalid parameter name: " + p;
                }
                if (!set.add(p))
                    return "duplicate parameter: " + p;
            }

        } else {
            Integer min = f.getMinParameters();
            Integer max = f.getMaxParameters();

            if (min != null && min < 1)
                return "minParams must be >= 1";
            if (max != null && max < 1)
                return "maxParams must be >= 1";
            if (min != null && max != null && max < min)
                return "maxParams must be >= minParams";

            if (f.getParameters() != null && !f.getParameters().isEmpty()) {
                return "parameters must be empty for variadic formulas";
            }
        }

        return null;
    }
}
