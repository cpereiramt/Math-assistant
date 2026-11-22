package com.claySoftware.MathExpAssistant.helpers;

import com.claySoftware.MathExpAssistant.models.MeasurementUnit;
import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.functions.basic.AbsFunction;
import com.ezylang.evalex.functions.FunctionParameter;
import com.ezylang.evalex.functions.basic.IfFunction;
import com.ezylang.evalex.parser.ASTNode;
import com.ezylang.evalex.parser.ParseException;
import com.ezylang.evalex.parser.Token.TokenType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Evaluates the bounded, decimal-only subset of the EvalEx grammar used by the calculation API. */
public final class DecimalCalculation {
    public static final MathContext PRECISION = MathContext.DECIMAL128;
    public static final int MAX_VALUES = 100;
    private static final List<String> APPROXIMATE = List.of("SIN", "COS", "TAN", "SINR", "COSR", "TANR",
            "ASIN", "ACOS", "ATAN", "ASINR", "ACOSR", "ATANR", "LOG", "LOG10", "DEG", "RAD");
    public static final List<String> FUNCTIONS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of("ABS", "SUM", "AVERAGE", "MIN", "MAX", "SQRT", "ROUND", "FLOOR",
                    "CEILING", "FACT", "IF", "FX"), APPROXIMATE.stream()).toList();
    private static final ExpressionConfiguration PARSER = ExpressionConfiguration.defaultConfiguration()
            .withAdditionalFunctions(Map.entry("FX", new IfFunction()), Map.entry("UNITCHECK", new CheckSignature()));

    private DecimalCalculation() {}

    public static Quantity run(String expression, Map<String, Quantity> inputs, boolean checkOnly) {
        if (expression == null || expression.isBlank() || expression.length() > 4096) {
            throw new IllegalArgumentException("Expression must contain 1 to 4096 characters");
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
        try {
            ASTNode root = new Expression(expression, PARSER).getAbstractSyntaxTree();
            enforceBudget(root, 1, new int[1]);
            Quantity result = evaluate(root, inputs, checkOnly, deadline);
            checkDeadline(deadline);
            return result;
        } catch (ParseException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static void enforceBudget(ASTNode node, int depth, int[] count) {
        if (depth > 64 || ++count[0] > 512) {
            throw new IllegalArgumentException("Expression exceeds 64 levels or 512 nodes");
        }
        for (ASTNode child : node.getParameters()) {
            enforceBudget(child, depth + 1, count);
        }
    }

    private static void checkDeadline(long deadline) {
        if (System.nanoTime() - deadline >= 0) {
            throw new Failure("time_budget_exceeded", "Expression exceeded the evaluation time budget");
        }
    }

    private static Quantity evaluate(ASTNode node, Map<String, Quantity> inputs, boolean checkOnly, long deadline) {
        checkDeadline(deadline);
        var token = node.getToken();
        var children = node.getParameters();
        String symbol = token.getValue().toUpperCase(java.util.Locale.ROOT);
        return switch (token.getType()) {
            case NUMBER_LITERAL -> scalar(decimal(new BigDecimal(symbol)));
            case VARIABLE_OR_CONSTANT -> {
                Quantity value = inputs.get(symbol);
                if (value != null) yield value;
                yield switch (symbol) {
                    case "PI" -> scalar(new BigDecimal("3.141592653589793238462643383279503"));
                    case "E" -> scalar(new BigDecimal("2.718281828459045235360287471352662"));
                    default -> throw new IllegalArgumentException("Unknown variable: " + symbol);
                };
            }
            case PREFIX_OPERATOR -> {
                Quantity input = evaluate(children.getFirst(), inputs, checkOnly, deadline);
                yield switch (symbol) {
                    case "+" -> input;
                    case "-" -> new Quantity(input.value().negate(), input.dimension());
                    default -> throw unsupported(symbol);
                };
            }
            case INFIX_OPERATOR -> operator(symbol, evaluate(children.get(0), inputs, checkOnly, deadline),
                    evaluate(children.get(1), inputs, checkOnly, deadline), children.get(1), checkOnly);
            case FUNCTION -> function(symbol, children, inputs, checkOnly, deadline);
            default -> throw unsupported(symbol);
        };
    }

    private static Quantity operator(String symbol, Quantity left, Quantity right, ASTNode exponent,
                                     boolean checkOnly) {
        BigDecimal a = left.value();
        BigDecimal b = right.value();
        return switch (symbol) {
            case "+" -> result(checkOnly ? BigDecimal.ONE : a.add(b, PRECISION), same(left, right));
            case "-" -> result(checkOnly ? BigDecimal.ONE : a.subtract(b, PRECISION), same(left, right));
            case "*" -> result(checkOnly ? BigDecimal.ONE : a.multiply(b, PRECISION), combine(left, right, 1));
            case "/" -> result(checkOnly ? BigDecimal.ONE : divide(a, b), combine(left, right, -1));
            case "%" -> {
                Map<String, Integer> dimension = same(left, right);
                if (!checkOnly && b.signum() == 0) throw new Failure("division_by_zero", "Division by zero");
                yield result(checkOnly ? BigDecimal.ONE : a.remainder(b), dimension);
            }
            case "=", "==", "!=", "<>", "<", ">", "<=", ">=" -> {
                same(left, right);
                int order = a.compareTo(b);
                boolean comparison = switch (symbol) {
                    case "=", "==" -> order == 0;
                    case "!=", "<>" -> order != 0;
                    case "<" -> order < 0;
                    case ">" -> order > 0;
                    case "<=" -> order <= 0;
                    default -> order >= 0;
                };
                yield scalar(comparison ? BigDecimal.ONE : BigDecimal.ZERO);
            }
            case "^" -> {
                requireDimension(right, Map.of(), "exponent");
                if (!left.dimension().isEmpty() && exponent.getToken().getType() != TokenType.NUMBER_LITERAL) {
                    throw new IllegalArgumentException("A measured base requires a literal integer exponent");
                }
                int power = checkOnly && left.dimension().isEmpty() ? 1 : boundedInteger(b, 100, "exponent");
                Map<String, Integer> dimensions = new HashMap<>();
                left.dimension().forEach((key, value) -> {
                    if (power != 0) dimensions.put(key, dimensionExponent(value * power));
                });
                if (!checkOnly && a.signum() == 0 && power < 0) {
                    throw new Failure("division_by_zero", "Division by zero");
                }
                yield result(checkOnly ? BigDecimal.ONE : a.pow(power, PRECISION), dimensions);
            }
            default -> throw unsupported(symbol);
        };
    }

    private static Quantity function(String name, List<ASTNode> args, Map<String, Quantity> inputs, boolean checkOnly, long deadline) {
        if (args.isEmpty() || (List.of("FX", "IF").contains(name) && args.size() != 3)
                || (List.of("UNITCHECK", "ROUND").contains(name) && args.size() != 2)) {
            throw new IllegalArgumentException("Invalid argument count for " + name);
        }
        Quantity first = evaluate(args.getFirst(), inputs, checkOnly, deadline);
        if ("FX".equals(name)) {
            Quantity rate = evaluate(args.get(1), inputs, checkOnly, deadline);
            String target = literal(args.get(2));
            if (!target.matches("[A-Z]{3}") || first.dimension().size() != 1
                    || first.dimension().entrySet().stream().noneMatch(e -> e.getKey().startsWith("CURRENCY_") && e.getValue() == 1)) {
                throw new IllegalArgumentException("FX requires a currency amount and a three-letter target currency");
            }
            requireDimension(rate, Map.of(), "exchange rate");
            if (!checkOnly && rate.value().signum() <= 0) {
                throw new Failure("invalid_rate", "Exchange rate must be positive");
            }
            return result(checkOnly ? BigDecimal.ONE : first.value().multiply(rate.value(), PRECISION),
                    Map.of("CURRENCY_" + target, 1));
        }
        if ("UNITCHECK".equals(name)) {
            String expected = literal(args.get(1));
            requireDimension(first, MeasurementUnit.dimensions(expected), "builder connection");
            return first;
        }
        if ("IF".equals(name)) {
            requireDimension(first, Map.of(), "IF condition");
            if (!checkOnly) return evaluate(args.get(first.value().signum() != 0 ? 1 : 2), inputs, false, deadline);
            Quantity yes = evaluate(args.get(1), inputs, true, deadline);
            Quantity no = evaluate(args.get(2), inputs, true, deadline);
            return result(BigDecimal.ONE, same(yes, no));
        }
        BigDecimal value = first.value();
        if (APPROXIMATE.contains(name)) {
            requireDimension(first, Map.of(), name);
            return checkOnly ? scalar(BigDecimal.ONE) : scalar(approximate(name, value.doubleValue()));
        }
        switch (name) {
            case "FACT":
                requireDimension(first, Map.of(), "FACT");
                if (checkOnly) return scalar(BigDecimal.ONE);
                int n = boundedInteger(value, 166, "FACT argument");
                if (n < 0) throw new Failure("non_finite", "Factorial requires a nonnegative integer");
                BigDecimal factorial = BigDecimal.ONE;
                for (int i = 2; i <= n; i++) factorial = factorial.multiply(BigDecimal.valueOf(i));
                return scalar(factorial.round(PRECISION));
            case "CEILING", "FLOOR":
                requireDimension(first, Map.of(), name);
                return scalar(value.setScale(0, "CEILING".equals(name)
                        ? java.math.RoundingMode.CEILING : java.math.RoundingMode.FLOOR));
            case "ABS":
                return result(value.abs(), first.dimension());
            case "SQRT":
                Map<String, Integer> dimensions = new HashMap<>();
                first.dimension().forEach((key, exponent) -> {
                    if (exponent % 2 != 0) throw new IllegalArgumentException("SQRT requires even unit exponents");
                    dimensions.put(key, exponent / 2);
                });
                if (!checkOnly && value.signum() < 0) {
                    throw new Failure("non_finite", "Square root of a negative value is undefined");
                }
                return result(checkOnly ? BigDecimal.ONE : value.sqrt(PRECISION), dimensions);
            case "ROUND":
                Quantity places = evaluate(args.get(1), inputs, checkOnly, deadline);
                requireDimension(places, Map.of(), "ROUND precision");
                if (!first.dimension().isEmpty()) {
                    throw new IllegalArgumentException("ROUND requires a unitless value; measured results are rounded for display");
                }
                return checkOnly ? scalar(BigDecimal.ONE)
                        : scalar(value.setScale(boundedInteger(places.value(), 100, "ROUND precision"), PRECISION.getRoundingMode()));
            case "SUM", "AVERAGE", "MIN", "MAX":
                for (int i = 1; i < args.size(); i++) {
                    Quantity next = evaluate(args.get(i), inputs, checkOnly, deadline);
                    same(first, next);
                    value = switch (name) {
                        case "MIN" -> value.min(next.value());
                        case "MAX" -> value.max(next.value());
                        default -> value.add(next.value(), PRECISION);
                    };
                    if (!checkOnly) decimal(value);
                }
                if ("AVERAGE".equals(name)) value = divide(value, BigDecimal.valueOf(args.size()));
                return result(checkOnly ? BigDecimal.ONE : value, first.dimension());
            default:
                throw unsupported(name);
        }
    }

    private static BigDecimal approximate(String name, double value) {
        double result = switch (name) {
            case "SIN" -> Math.sin(Math.toRadians(value));
            case "COS" -> Math.cos(Math.toRadians(value));
            case "TAN" -> Math.tan(Math.toRadians(value));
            case "SINR" -> Math.sin(value);
            case "COSR" -> Math.cos(value);
            case "TANR" -> Math.tan(value);
            case "ASIN" -> Math.toDegrees(Math.asin(value));
            case "ACOS" -> Math.toDegrees(Math.acos(value));
            case "ATAN" -> Math.toDegrees(Math.atan(value));
            case "ASINR" -> Math.asin(value);
            case "ACOSR" -> Math.acos(value);
            case "ATANR" -> Math.atan(value);
            case "LOG" -> Math.log(value);
            case "LOG10" -> Math.log10(value);
            case "DEG" -> Math.toDegrees(value);
            case "RAD" -> Math.toRadians(value);
            default -> throw unsupported(name);
        };
        if (!Double.isFinite(result)) throw new Failure("non_finite", name + " returned an undefined or infinite result");
        return BigDecimal.valueOf(result);
    }

    private static String literal(ASTNode node) {
        if (node.getToken().getType() != TokenType.STRING_LITERAL) {
            throw new IllegalArgumentException("Expected a quoted unit or currency code");
        }
        return node.getToken().getValue();
    }

    private static Map<String, Integer> same(Quantity first, Quantity second) {
        requireDimension(second, first.dimension(), "operation");
        return first.dimension();
    }

    public static void requireDimension(Quantity value, Map<String, Integer> expected, String field) {
        if (!value.dimension().equals(expected)) {
            throw new IllegalArgumentException("Incompatible units for " + field + ": expected " + expected
                    + ", received " + value.dimension());
        }
    }

    private static Map<String, Integer> combine(Quantity first, Quantity second, int sign) {
        Map<String, Integer> result = new HashMap<>(first.dimension());
        second.dimension().forEach((key, exponent) -> result.merge(key, sign * exponent, Integer::sum));
        result.values().removeIf(exponent -> exponent == 0);
        result.values().forEach(DecimalCalculation::dimensionExponent);
        return Map.copyOf(result);
    }

    private static int dimensionExponent(int exponent) {
        if (Math.abs(exponent) > 100) throw new IllegalArgumentException("Unit exponent exceeds the supported range");
        return exponent;
    }

    private static int boundedInteger(BigDecimal value, int limit, String field) {
        try {
            int number = value.intValueExact();
            if (Math.abs((long) number) <= limit) return number;
        } catch (ArithmeticException ignored) {
            // Fractional and out-of-range values use the same error.
        }
        throw new Failure("numeric_range", field + " must be an integer between -" + limit + " and " + limit);
    }

    public static BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right.signum() == 0) throw new Failure("division_by_zero", "Division by zero");
        return left.divide(right, PRECISION);
    }

    public static BigDecimal decimal(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("A finite decimal value is required");
        if (value.precision() > 256 || Math.abs((long) value.scale()) > 400
                || Math.abs((long) value.precision() - value.scale() - 1) > 300) {
            throw new Failure("numeric_range", "Number is outside the supported decimal range");
        }
        return value.round(PRECISION);
    }

    private static Quantity scalar(BigDecimal value) {
        return result(value, Map.of());
    }

    private static Quantity result(BigDecimal value, Map<String, Integer> dimension) {
        return new Quantity(decimal(value), dimension);
    }

    private static Failure unsupported(String symbol) {
        return new Failure("unsupported_operation", "Unsupported calculation operation: " + symbol);
    }

    public record Quantity(BigDecimal value, Map<String, Integer> dimension) {}

    public static class Failure extends RuntimeException {
        private final String code;

        public Failure(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    @FunctionParameter(name = "value")
    @FunctionParameter(name = "dimension")
    private static final class CheckSignature extends AbsFunction {}
}
