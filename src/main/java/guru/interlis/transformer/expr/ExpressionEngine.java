package guru.interlis.transformer.expr;

import ch.interlis.iom.IomObject;
import java.util.Map;

public final class ExpressionEngine {
    public Object evaluate(String expression, Map<String, IomObject> sources) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String expr = expression.trim();
        if (expr.startsWith("if(")) {
            return evaluateIf(expr, sources);
        }
        if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
            return expr.substring(1, expr.length() - 1);
        }
        if (expr.startsWith("${") && expr.endsWith("}")) {
            return resolvePath(expr.substring(2, expr.length() - 1), sources);
        }
        return expr;
    }

    private Object evaluateIf(String expr, Map<String, IomObject> sources) {
        String args = expr.substring(3, expr.length() - 1);
        String[] parts = args.split(",", 3);
        if (parts.length < 3) {
            return null;
        }
        boolean condition = evaluateCondition(parts[0].trim(), sources);
        return evaluate(condition ? parts[1].trim() : parts[2].trim(), sources);
    }

    private boolean evaluateCondition(String cond, Map<String, IomObject> sources) {
        if (cond.contains("!= null")) {
            String path = cond.replace("!= null", "").trim();
            Object value = evaluate(path, sources);
            return value != null;
        }
        return Boolean.parseBoolean(cond);
    }

    private Object resolvePath(String path, Map<String, IomObject> sources) {
        String[] parts = path.split("\\.", 2);
        if (parts.length < 2) {
            return null;
        }
        IomObject source = sources.get(parts[0]);
        if (source == null) {
            return null;
        }
        return source.getattrvalue(parts[1]);
    }
}
