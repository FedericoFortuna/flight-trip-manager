package com.flighttripmanager.shared.infrastructure.logging;

import java.util.regex.Pattern;

/** Defense in depth for labelled text, not a substitute for avoiding sensitive payload logging. */
public final class SensitiveDataMasker {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\\b(?:proxy-)?authorization\\b[\"']?\\s*[:=]\\s*)[^\\r\\n]+");
    private static final Pattern AUTH_TOKEN = Pattern.compile("(?i)\\b(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern FIELD = Pattern.compile(
            "(?i)(\\b(?:pnr|booking[_-]?reference|electronic[_-]?ticket[_-]?number|ticket[_-]?number"
                    + "|api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|secret)\\b[\"']?\\s*[:=]\\s*)"
                    + "(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|[^\\s,;}]+)");

    private SensitiveDataMasker() {
    }

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = AUTHORIZATION.matcher(text).replaceAll("$1" + REDACTED);
        masked = AUTH_TOKEN.matcher(masked).replaceAll(REDACTED);
        return FIELD.matcher(masked).replaceAll("$1" + REDACTED);
    }
}
