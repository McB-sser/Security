package de.mcbesser.security.moderation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class SmartWordFilter {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final Map<Character, Character> LEET_MAP = Map.ofEntries(
            Map.entry('0', 'o'), Map.entry('1', 'i'), Map.entry('3', 'e'), Map.entry('4', 'a'),
            Map.entry('5', 's'), Map.entry('6', 'g'), Map.entry('7', 't'), Map.entry('8', 'b'),
            Map.entry('9', 'g'), Map.entry('@', 'a'), Map.entry('$', 's'), Map.entry('!', 'i')
    );

    private final List<String> patterns;

    public SmartWordFilter(List<String> blockedPatterns) {
        List<String> tmp = new ArrayList<>();
        for (String p : blockedPatterns) {
            String normalized = normalizeForComparison(p);
            if (!normalized.isBlank()) {
                tmp.add(normalized);
            }
        }
        this.patterns = List.copyOf(tmp);
    }

    public boolean matches(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String compact = normalizeForComparison(message);
        if (compact.isBlank()) {
            return false;
        }
        String words = normalizeWords(message);
        String[] tokens = words.isBlank() ? new String[0] : words.split(" ");

        for (String pattern : patterns) {
            if (compact.contains(pattern)) {
                return true;
            }
            for (String token : tokens) {
                if (token.contains(pattern) || fuzzyMatch(token, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String normalizeForComparison(String input) {
        if (input == null) {
            return "";
        }
        String s = input.toLowerCase(Locale.ROOT)
                .replace("\u00e4", "ae")
                .replace("\u00f6", "oe")
                .replace("\u00fc", "ue")
                .replace("\u00df", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");

        StringBuilder mapped = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            mapped.append(LEET_MAP.getOrDefault(c, c));
        }

        return collapseRepeats(NON_ALNUM.matcher(mapped.toString()).replaceAll(""));
    }

    private String normalizeWords(String input) {
        if (input == null) {
            return "";
        }
        String s = input.toLowerCase(Locale.ROOT)
                .replace("\u00e4", "ae")
                .replace("\u00f6", "oe")
                .replace("\u00fc", "ue")
                .replace("\u00df", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");

        StringBuilder mapped = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            char out = LEET_MAP.getOrDefault(c, c);
            mapped.append(Character.isLetterOrDigit(out) ? out : ' ');
        }
        String normalizedSpaces = SPACES.matcher(mapped.toString().trim()).replaceAll(" ");
        if (normalizedSpaces.isBlank()) {
            return "";
        }
        String[] raw = normalizedSpaces.split(" ");
        List<String> tokens = new ArrayList<>();
        for (String token : raw) {
            String collapsed = collapseRepeats(token);
            if (!collapsed.isBlank()) {
                tokens.add(collapsed);
            }
        }
        return String.join(" ", tokens);
    }

    private String collapseRepeats(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        char last = 0;
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == last) {
                count++;
            } else {
                last = c;
                count = 1;
            }
            if (count <= 2) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private boolean fuzzyMatch(String token, String pattern) {
        if (token.length() < 6 || pattern.length() < 6) {
            return false;
        }
        if (Math.abs(token.length() - pattern.length()) > 1) {
            return false;
        }
        // Reduce false positives like "falsch" vs short stems by requiring stable edges for fuzzy matches.
        if (token.charAt(0) != pattern.charAt(0) || token.charAt(token.length() - 1) != pattern.charAt(pattern.length() - 1)) {
            return false;
        }
        int maxDistance = pattern.length() >= 7 ? 2 : 1;
        return levenshtein(token, pattern, maxDistance) <= maxDistance;
    }

    private int levenshtein(String a, String b, int cutoff) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, curr[j]);
            }
            if (rowMin > cutoff) {
                return cutoff + 1;
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
