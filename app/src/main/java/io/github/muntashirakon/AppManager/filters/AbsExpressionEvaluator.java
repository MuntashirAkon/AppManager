// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import androidx.annotation.NonNull;

public abstract class AbsExpressionEvaluator {
    protected abstract boolean evalId(@NonNull String id);

    public boolean evaluate(@NonNull String expr) {
        // Process parentheses first
        while (expr.contains("(")) {
            int start = expr.lastIndexOf('(');
            int end = expr.indexOf(')', start);
            // Get expression without parenthesis
            String subExpr = expr.substring(start + 1, end);
            boolean subResult = evalOrExpr(subExpr);
            expr = expr.substring(0, start) + subResult + expr.substring(end + 1);
        }
        // Evaluate the final expression without parentheses
        return evalOrExpr(expr);
    }

    private boolean evalOrExpr(@NonNull String expr) {
        String[] orParts = expr.split(" \\| ");
        for (String part : orParts) {
            if (evalAndExpr(part)) {
                // No need to evaluate any further
                return true;
            }
        }
        // None of the parts returned true
        return false;
    }

    private boolean evalAndExpr(@NonNull String expr) {
        String[] andParts = expr.split(" & ");
        for (String andPart : andParts) {
            andPart = andPart.trim();
            if (andPart.equals("true")) {
                continue;
            }
            if (andPart.equals("false") || !evalId(andPart)) {
                // No need to evaluate any further
                return false;
            }
        }
        return true;
    }
}
