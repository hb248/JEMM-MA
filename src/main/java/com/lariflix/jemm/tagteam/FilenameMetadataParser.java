package com.lariflix.jemm.tagteam;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives Studio / Actor / Title / Release-date suggestions from a media filename.
 *
 * <p>Handles the common layouts:
 * <pre>
 *   [Studio]actor - title (date)
 *   [Studio | Studio]actor &amp; actor - title date
 * </pre>
 * Studios come from {@code [...]} (split on {@code |}); the segment before {@code " - "}
 * yields actors (split on {@code &} / {@code ,} / {@code and}); the remainder is the title;
 * trailing digit groups or a {@code (...)} group produce ranked {@link DateCandidate}s.
 * Actors are always meant to be added as {@code Type=Actor} by the caller.</p>
 */
public class FilenameMetadataParser {

    private static final Pattern BRACKETS = Pattern.compile("\\[([^\\]]*)\\]");
    private static final Pattern PARENS = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern TRAILING_DATE = Pattern.compile("([0-9][0-9\\-./ ]*[0-9]|[0-9]{2,})\\s*$");

    /**
     * Parses suggestions from a full path or bare filename.
     */
    public FilenameSuggestions parse(String pathOrName) {
        FilenameSuggestions out = new FilenameSuggestions();
        if (pathOrName == null || pathOrName.trim().isEmpty()) {
            return out;
        }

        String base = stripExtension(baseName(pathOrName));

        // 1) Studios from [...] groups.
        Matcher bm = BRACKETS.matcher(base);
        StringBuilder withoutBrackets = new StringBuilder();
        int last = 0;
        while (bm.find()) {
            withoutBrackets.append(base, last, bm.start());
            String inside = bm.group(1);
            if (inside != null) {
                for (String piece : inside.split("\\|")) {
                    String s = piece.trim();
                    if (!s.isEmpty() && !containsIgnoreCase(out.getStudios(), s)) {
                        out.getStudios().add(s);
                    }
                }
            }
            last = bm.end();
        }
        withoutBrackets.append(base.substring(last));
        String work = withoutBrackets.toString().trim();

        // 2) Date: prefer a (...) group, else a trailing digit group.
        String dateToken = null;
        Matcher pm = PARENS.matcher(work);
        int lastParenStart = -1;
        int lastParenEnd = -1;
        String lastParenContent = null;
        while (pm.find()) {
            lastParenStart = pm.start();
            lastParenEnd = pm.end();
            lastParenContent = pm.group(1);
        }
        if (lastParenContent != null && !parseDateCandidates(lastParenContent).isEmpty()) {
            dateToken = lastParenContent;
            work = (work.substring(0, lastParenStart) + work.substring(lastParenEnd)).trim();
        } else {
            // Remove any leftover parenthetical (non-date) content from the title tail.
            if (lastParenStart >= 0) {
                work = (work.substring(0, lastParenStart) + work.substring(lastParenEnd)).trim();
            }
            Matcher tm = TRAILING_DATE.matcher(work);
            if (tm.find()) {
                String candidate = tm.group(1).trim();
                if (!parseDateCandidates(candidate).isEmpty()) {
                    dateToken = candidate;
                    work = work.substring(0, tm.start()).trim();
                }
            }
        }
        if (dateToken != null) {
            out.getDates().addAll(parseDateCandidates(dateToken));
        }

        // 3) Actors and title from remainder, split on the first " - ".
        String actorsSegment = null;
        String titleSegment;
        int dash = work.indexOf(" - ");
        if (dash >= 0) {
            actorsSegment = work.substring(0, dash).trim();
            titleSegment = work.substring(dash + 3).trim();
        } else {
            titleSegment = work;
        }

        if (actorsSegment != null && !actorsSegment.isEmpty()) {
            for (String piece : actorsSegment.split("\\s*(?:&|,|\\band\\b)\\s*")) {
                String a = piece.trim();
                if (!a.isEmpty() && !containsIgnoreCase(out.getActors(), a)) {
                    out.getActors().add(a);
                }
            }
        }

        out.setTitle(cleanupTitle(titleSegment));
        return out;
    }

    /**
     * Generates ranked date interpretations for a raw token (visible for testing).
     * Full valid dates first, then year-only fallbacks; duplicates removed.
     */
    List<DateCandidate> parseDateCandidates(String raw) {
        List<DateCandidate> full = new ArrayList<>();
        List<DateCandidate> yearOnly = new ArrayList<>();
        if (raw == null) {
            return full;
        }
        String token = raw.trim();
        if (token.isEmpty()) {
            return full;
        }

        boolean hasSeparators = token.matches(".*[.\\-/ ].*");
        if (hasSeparators) {
            String[] parts = token.split("[.\\-/ ]+");
            List<Integer> nums = new ArrayList<>();
            boolean allNumeric = true;
            for (String p : parts) {
                if (p.isEmpty()) {
                    continue;
                }
                if (p.matches("\\d+")) {
                    nums.add(Integer.parseInt(p));
                } else {
                    allNumeric = false;
                }
            }
            if (allNumeric && nums.size() == 3) {
                int a = nums.get(0);
                int b = nums.get(1);
                int c = nums.get(2);
                boolean lastIsYear = parts[2].length() == 4;
                boolean firstIsYear = parts[0].length() == 4;
                if (firstIsYear) {
                    addFull(full, a, b, c); // YYYY MM DD
                } else if (lastIsYear) {
                    addFull(full, c, b, a); // DD MM YYYY
                    addFull(full, c, a, b); // MM DD YYYY
                } else {
                    int year = expandYear(c);
                    addFull(full, year, b, a); // DD MM YY
                    addFull(full, year, a, b); // MM DD YY (only added if distinct+valid)
                }
            } else {
                // Fall back to any 4-digit year found among the parts.
                for (String p : parts) {
                    if (p.matches("(19|20)\\d{2}")) {
                        addYear(yearOnly, Integer.parseInt(p));
                    }
                }
            }
        } else if (token.matches("\\d+")) {
            switch (token.length()) {
                case 8: {
                    int d1 = num(token, 0, 2), d2 = num(token, 2, 4), d3 = num(token, 4, 6), d4 = num(token, 6, 8);
                    addFull(full, num(token, 0, 4), d3, d4); // YYYYMMDD
                    addFull(full, num(token, 4, 8), d1, d2); // DDMMYYYY
                    addFull(full, num(token, 4, 8), d2, d1); // MMDDYYYY
                    break;
                }
                case 6: {
                    int p1 = num(token, 0, 2), p2 = num(token, 2, 4), p3 = num(token, 4, 6);
                    addFull(full, expandYear(p3), p2, p1); // DDMMYY
                    addFull(full, expandYear(p1), p2, p3); // YYMMDD
                    addFull(full, expandYear(p3), p1, p2); // MMDDYY
                    break;
                }
                case 4: {
                    if (token.matches("(19|20)\\d{2}")) {
                        addYear(yearOnly, Integer.parseInt(token));
                    }
                    break;
                }
                case 2: {
                    addYear(yearOnly, expandYear(Integer.parseInt(token)));
                    break;
                }
                default:
                    // 1, 3, 5, 7 digits: try to spot an embedded year.
                    Matcher ym = Pattern.compile("(19|20)\\d{2}").matcher(token);
                    if (ym.find()) {
                        addYear(yearOnly, Integer.parseInt(ym.group()));
                    }
                    break;
            }
        }

        List<DateCandidate> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DateCandidate c : full) {
            if (seen.add(c.getDisplay())) {
                result.add(c);
            }
        }
        for (DateCandidate c : yearOnly) {
            if (seen.add(c.getDisplay())) {
                result.add(c);
            }
        }
        return result;
    }

    private void addFull(List<DateCandidate> list, int year, int month, int day) {
        if (isValid(year, month, day)) {
            list.add(new DateCandidate(year, month, day));
        }
    }

    private void addYear(List<DateCandidate> list, int year) {
        if (year >= 1900 && year <= 2099) {
            list.add(new DateCandidate(year));
        }
    }

    private int num(String s, int from, int to) {
        return Integer.parseInt(s.substring(from, to));
    }

    private int expandYear(int yy) {
        if (yy >= 100) {
            return yy;
        }
        return yy < 70 ? 2000 + yy : 1900 + yy;
    }

    private boolean isValid(int year, int month, int day) {
        if (year < 1900 || year > 2099) {
            return false;
        }
        if (month < 1 || month > 12) {
            return false;
        }
        if (day < 1 || day > daysInMonth(month, year)) {
            return false;
        }
        return true;
    }

    private int daysInMonth(int month, int year) {
        switch (month) {
            case 2:
                boolean leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
                return leap ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    private String cleanupTitle(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim();
        // Strip stray leading/trailing separators and empty parens.
        t = t.replaceAll("\\(\\s*\\)", "").trim();
        t = t.replaceAll("^[\\-_.\\s]+", "").replaceAll("[\\-_.\\s]+$", "").trim();
        return t;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static String baseName(String path) {
        if (path == null) {
            return "";
        }
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        // Only treat a short trailing dot-group as an extension.
        if (dot > 0 && name.length() - dot <= 5) {
            return name.substring(0, dot);
        }
        return name;
    }

    /**
     * @return true when a title looks like a hash / digit dump (delegates to {@link TitleComposer})
     */
    public static boolean isLowQualityTitle(String title) {
        return TitleComposer.isLowQualityTitle(title);
    }
}
