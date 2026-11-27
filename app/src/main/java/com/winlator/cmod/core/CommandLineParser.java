package com.winlator.cmod.core;

import java.util.ArrayList;

/** Pure-Java command line parser, isolated so its edge cases can be JVM-tested. */
final class CommandLineParser {
    private CommandLineParser() {}

    public static String[] split(String command) {
        if (command == null || command.trim().isEmpty()) return new String[0];

        /*
         * cmd.exe /c is different from an ordinary executable invocation: everything
         * after /c is one command-line string. Treating its quoted tail as one normal
         * argv token makes Wine cmd interpret "wget.exe -q ..." as the executable name.
         * Preserve the command tail, and if it was wrapped as one outer quoted string,
         * move those quotes onto the executable token (the common Winlator Extras form).
         */
        int scan = skipWhitespace(command, 0);
        while (scan < command.length()) {
            int tokenEnd = findTokenEnd(command, scan);
            if (tokenEnd <= scan) break;
            String token = unquoteToken(command.substring(scan, tokenEnd));
            int switchStart = skipWhitespace(command, tokenEnd);
            int switchEnd = findTokenEnd(command, switchStart);
            boolean isCmd = token.toLowerCase(java.util.Locale.ROOT).endsWith("cmd.exe") ||
                    token.equalsIgnoreCase("cmd");
            if (isCmd && switchEnd > switchStart &&
                    (command.substring(switchStart, switchEnd).equalsIgnoreCase("/c") ||
                     command.substring(switchStart, switchEnd).equalsIgnoreCase("/k"))) {
                String tail = command.substring(skipWhitespace(command, switchEnd)).trim();
                if (tail.length() >= 2 && tail.charAt(0) == '"' &&
                        tail.charAt(tail.length() - 1) == '"' &&
                        tail.substring(1, tail.length() - 1).indexOf('"') < 0) {
                    tail = tail.substring(1, tail.length() - 1).trim();
                    int space = firstWhitespace(tail);
                    String commandName = space < 0 ? tail : tail.substring(0, space);
                    if (!commandName.isEmpty() && !commandName.startsWith("\"") &&
                            (commandName.toLowerCase(java.util.Locale.ROOT).endsWith(".exe") ||
                             commandName.toLowerCase(java.util.Locale.ROOT).endsWith(".com") ||
                             commandName.toLowerCase(java.util.Locale.ROOT).endsWith(".bat") ||
                             commandName.toLowerCase(java.util.Locale.ROOT).endsWith(".cmd"))) {
                        tail = "\"" + commandName + "\"" +
                                (space < 0 ? "" : tail.substring(space));
                    }
                }
                if (tail.isEmpty()) throw new IllegalArgumentException("Missing command after cmd.exe /c");
                String prefix = command.substring(0, scan);
                String[] prefixArgs = prefix.trim().isEmpty() ? new String[0] : split(prefix);
                String[] result = new String[prefixArgs.length + 3];
                System.arraycopy(prefixArgs, 0, result, 0, prefixArgs.length);
                result[prefixArgs.length] = token;
                result[prefixArgs.length + 1] = command.substring(switchStart, switchEnd);
                result[prefixArgs.length + 2] = tail;
                return result;
            }
            scan = skipWhitespace(command, tokenEnd);
        }

        ArrayList<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean inQuotes = false;
        boolean tokenStarted = false;

        for (int i = 0; i < command.length(); i++) {
            char current = command.charAt(i);
            if (current == '"') {
                inQuotes = !inQuotes;
                tokenStarted = true;
                continue;
            }
            if (current == '\\' && i + 1 < command.length()
                    && Character.isWhitespace(command.charAt(i + 1))) {
                value.append(command.charAt(++i));
                tokenStarted = true;
                continue;
            }
            if (Character.isWhitespace(current) && !inQuotes) {
                if (tokenStarted) {
                    result.add(value.toString());
                    value.setLength(0);
                    tokenStarted = false;
                }
                continue;
            }
            value.append(current);
            tokenStarted = true;
        }
        if (inQuotes) throw new IllegalArgumentException("Unterminated double quote in command line");
        if (tokenStarted) result.add(value.toString());
        return result.toArray(new String[0]);
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return index;
    }

    private static int findTokenEnd(String value, int start) {
        boolean quoted = false;
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (Character.isWhitespace(c) && !quoted) return i;
        }
        return value.length();
    }

    private static String unquoteToken(String token) {
        return token.length() >= 2 && token.charAt(0) == '"' &&
                token.charAt(token.length() - 1) == '"'
                ? token.substring(1, token.length() - 1) : token;
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
    }

}
