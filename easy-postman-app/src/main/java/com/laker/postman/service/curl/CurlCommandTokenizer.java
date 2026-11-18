package com.laker.postman.service.curl;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
class CurlCommandTokenizer {

    static List<String> tokenize(String command) {
        return parseCommand(command).argv();
    }

    static ShellCommand parseCommand(String command) {
        List<String> tokens = new ArrayList<>();
        List<CurlParseWarning> warnings = new ArrayList<>();
        if (command == null || command.trim().isEmpty()) {
            return new ShellCommand(tokens, warnings);
        }

        String cmd = preprocessCommand(command);
        StringBuilder currentToken = new StringBuilder();
        boolean tokenStarted = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inDollarQuote = false;

        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);

            if (c == '\\' && !inSingleQuote && !inDoubleQuote && !inDollarQuote) {
                int continuationEnd = lineContinuationEnd(cmd, i);
                if (continuationEnd > i) {
                    i = continuationEnd - 1;
                    continue;
                }
                if (i + 1 < cmd.length()) {
                    currentToken.append(cmd.charAt(++i));
                    tokenStarted = true;
                    continue;
                }
                currentToken.append(c);
                tokenStarted = true;
                continue;
            }

            if (c == '\\' && inDoubleQuote) {
                i = appendDoubleQuotedEscape(cmd, i, currentToken);
                tokenStarted = true;
                continue;
            }

            if (c == '\\' && inDollarQuote) {
                EscapeResult escape = decodeAnsiCBackslash(cmd, i);
                currentToken.append(escape.value());
                i = escape.endIndex();
                tokenStarted = true;
                continue;
            }

            if (c == '\'' && !inDoubleQuote && !inDollarQuote) {
                inSingleQuote = !inSingleQuote;
                tokenStarted = true;
                continue;
            }

            if (c == '"' && !inSingleQuote && !inDollarQuote) {
                inDoubleQuote = !inDoubleQuote;
                tokenStarted = true;
                continue;
            }

            if (c == '$' && i + 1 < cmd.length() && cmd.charAt(i + 1) == '\'' && !inSingleQuote && !inDoubleQuote) {
                inDollarQuote = true;
                tokenStarted = true;
                i++;
                continue;
            }

            if (c == '\'' && inDollarQuote) {
                inDollarQuote = false;
                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote && !inDollarQuote) {
                if (tokenStarted) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                    tokenStarted = false;
                }
                continue;
            }

            currentToken.append(c);
            tokenStarted = true;
        }

        if (tokenStarted) {
            tokens.add(currentToken.toString());
        }
        if (inSingleQuote) {
            warnings.add(new CurlParseWarning("shell.unclosed_single_quote", "Unclosed single quote in cURL command."));
        }
        if (inDoubleQuote) {
            warnings.add(new CurlParseWarning("shell.unclosed_double_quote", "Unclosed double quote in cURL command."));
        }
        if (inDollarQuote) {
            warnings.add(new CurlParseWarning("shell.unclosed_ansi_c_quote", "Unclosed ANSI-C quote in cURL command."));
        }
        return new ShellCommand(tokens, warnings);
    }

    private static String preprocessCommand(String curl) {
        return transformWindowsCmdEscapes(curl.trim());
    }

    private static String transformWindowsCmdEscapes(String command) {
        if (command.indexOf('^') < 0 || hasShellSingleQuote(command)) {
            return command;
        }

        StringBuilder transformed = new StringBuilder(command.length());
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c != '^' || i + 1 >= command.length()) {
                transformed.append(c);
                continue;
            }

            char next = command.charAt(i + 1);
            if (next == '%' && i + 2 < command.length() && command.charAt(i + 2) == '^') {
                transformed.append('%');
                i += 2;
                continue;
            }
            if (next == '\n') {
                i++;
                continue;
            }
            if (next == '\r') {
                if (i + 2 < command.length() && command.charAt(i + 2) == '\n') {
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            if (next == '"') {
                transformed.append('\\').append('"');
                i++;
                continue;
            }
            if (isCmdEscapedChar(next)) {
                transformed.append(next);
                i++;
                continue;
            }

            transformed.append(c);
        }
        return transformed.toString();
    }

    /**
     * cmd.exe has no single-quote quoting, so a '...' segment outside double quotes means a
     * shell command, where ^ is literal text. A ' inside double quotes, or one with no closing
     * ' (like the apostrophe in o'connor), is just a character.
     */
    private static boolean hasShellSingleQuote(String command) {
        boolean inDoubleQuotes = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\\' || (c == '^' && !inDoubleQuotes)) {
                i++;
            } else if (c == '"') {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                return command.indexOf('\'', i + 1) >= 0;
            }
        }
        return false;
    }

    private static boolean isCmdEscapedChar(char c) {
        return c == '^'
                || c == '{'
                || c == '}'
                || c == '['
                || c == ']'
                || c == '<'
                || c == '>'
                || c == '\\'
                || c == '|'
                || c == '&';
    }

    private static int lineContinuationEnd(String value, int slashIndex) {
        int nextIndex = slashIndex + 1;
        if (nextIndex >= value.length()) {
            return -1;
        }
        char next = value.charAt(nextIndex);
        if (next == '\n') {
            return nextIndex + 1;
        }
        if (next == '\r') {
            int afterCarriageReturn = nextIndex + 1;
            if (afterCarriageReturn < value.length() && value.charAt(afterCarriageReturn) == '\n') {
                return afterCarriageReturn + 1;
            }
            return afterCarriageReturn;
        }
        return -1;
    }

    private static int appendDoubleQuotedEscape(String command, int slashIndex, StringBuilder currentToken) {
        int nextIndex = slashIndex + 1;
        if (nextIndex >= command.length()) {
            currentToken.append('\\');
            return slashIndex;
        }

        int continuationEnd = lineContinuationEnd(command, slashIndex);
        if (continuationEnd > slashIndex) {
            return continuationEnd - 1;
        }

        char next = command.charAt(nextIndex);
        if (next == '$' || next == '`' || next == '"' || next == '\\') {
            currentToken.append(next);
            return nextIndex;
        }

        currentToken.append('\\');
        return slashIndex;
    }

    private static EscapeResult decodeAnsiCBackslash(String command, int slashIndex) {
        int nextIndex = slashIndex + 1;
        if (nextIndex >= command.length()) {
            return new EscapeResult("\\", slashIndex);
        }

        int continuationEnd = lineContinuationEnd(command, slashIndex);
        if (continuationEnd > slashIndex) {
            return new EscapeResult("", continuationEnd - 1);
        }

        char next = command.charAt(nextIndex);
        return switch (next) {
            case 'a' -> new EscapeResult("\u0007", nextIndex);
            case 'b' -> new EscapeResult("\b", nextIndex);
            case 'e', 'E' -> new EscapeResult("\u001B", nextIndex);
            case 'f' -> new EscapeResult("\f", nextIndex);
            case 'n' -> new EscapeResult("\n", nextIndex);
            case 'r' -> new EscapeResult("\r", nextIndex);
            case 't' -> new EscapeResult("\t", nextIndex);
            case 'v' -> new EscapeResult("\u000B", nextIndex);
            case '\\' -> new EscapeResult("\\", nextIndex);
            case '\'' -> new EscapeResult("'", nextIndex);
            case '"' -> new EscapeResult("\"", nextIndex);
            case '?' -> new EscapeResult("?", nextIndex);
            case 'x' -> decodeHexEscape(command, nextIndex + 1, 2, slashIndex);
            case 'u' -> decodeHexEscape(command, nextIndex + 1, 4, slashIndex);
            case 'U' -> decodeHexEscape(command, nextIndex + 1, 8, slashIndex);
            default -> {
                if (next >= '0' && next <= '7') {
                    yield decodeOctalEscape(command, nextIndex);
                }
                yield new EscapeResult(String.valueOf(next), nextIndex);
            }
        };
    }

    private static EscapeResult decodeHexEscape(String command, int startIndex, int maxDigits, int slashIndex) {
        int endIndex = startIndex;
        int value = 0;
        while (endIndex < command.length() && endIndex - startIndex < maxDigits) {
            int digit = Character.digit(command.charAt(endIndex), 16);
            if (digit < 0) {
                break;
            }
            value = (value << 4) + digit;
            endIndex++;
        }
        if (endIndex == startIndex) {
            return new EscapeResult(String.valueOf(command.charAt(slashIndex + 1)), slashIndex + 1);
        }
        return new EscapeResult(toCodePointString(value), endIndex - 1);
    }

    private static EscapeResult decodeOctalEscape(String command, int firstDigitIndex) {
        int endIndex = firstDigitIndex;
        int value = 0;
        while (endIndex < command.length() && endIndex - firstDigitIndex < 3) {
            char c = command.charAt(endIndex);
            if (c < '0' || c > '7') {
                break;
            }
            value = (value << 3) + (c - '0');
            endIndex++;
        }
        return new EscapeResult(String.valueOf((char) value), endIndex - 1);
    }

    private static String toCodePointString(int value) {
        if (!Character.isValidCodePoint(value)) {
            return "";
        }
        return new String(Character.toChars(value));
    }

    private record EscapeResult(String value, int endIndex) {
    }
}
