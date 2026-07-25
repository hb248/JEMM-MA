package com.lariflix.jemm.tagteam;

import com.lariflix.jemm.dtos.JellyfinPeopleItem;
import com.lariflix.jemm.dtos.JellyfinStudioItem;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the canonical Tag-Team media title:
 * {@code [Studio1 | Studio2] Actor1, Actor2 - Videotitel (YYYY-MM-DD)}.
 *
 * <p>Empty parts are omitted. Only people with Type=Actor go into the cast segment.
 * Date uses {@code (YYYY-MM-DD)} when a full date is present, {@code (YYYY)} for year-only.</p>
 */
public final class TitleComposer {

    private static final Pattern FULL_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern YEAR_ONLY = Pattern.compile("^(\\d{4})$");
    private static final Pattern LEADING_BRACKETS = Pattern.compile("^\\[([^\\]]*)\\]\\s*");

    private TitleComposer() {
    }

    /**
     * Composes a canonical title from the current panel state.
     *
     * @param studios    studio panel entries (order preserved)
     * @param people     people panel entries (only Type=Actor used for cast)
     * @param coreTitle  the Videotitel middle part
     * @param dateField  raw date field text (yyyy-MM-dd or yyyy)
     * @return composed title, or empty string when nothing usable
     */
    public static String compose(List<JellyfinStudioItem> studios,
            List<JellyfinPeopleItem> people,
            String coreTitle,
            String dateField) {
        List<String> studioNames = new ArrayList<>();
        if (studios != null) {
            for (JellyfinStudioItem s : studios) {
                if (s != null && notBlank(s.getName())) {
                    studioNames.add(s.getName().trim());
                }
            }
        }

        List<String> actors = new ArrayList<>();
        if (people != null) {
            for (JellyfinPeopleItem p : people) {
                if (p == null || blank(p.getName())) {
                    continue;
                }
                String type = p.getType() == null ? "Actor" : p.getType().trim();
                if ("Actor".equalsIgnoreCase(type)) {
                    actors.add(p.getName().trim());
                }
            }
        }

        String core = sanitizeCoreTitle(coreTitle, studioNames, actors);

        StringBuilder sb = new StringBuilder();
        if (!studioNames.isEmpty()) {
            sb.append('[');
            for (int i = 0; i < studioNames.size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                sb.append(studioNames.get(i));
            }
            sb.append(']');
        }

        if (!actors.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            for (int i = 0; i < actors.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(actors.get(i));
            }
        }

        if (!core.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(core);
        }

        String dateSuffix = formatDateSuffix(dateField);
        if (!dateSuffix.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(dateSuffix);
        }

        return sb.toString().trim();
    }

    /**
     * Removes studio brackets / actor names already represented in the panels from a core title,
     * so folder names like {@code [Studio] Actor} do not become
     * {@code [Studio] - [Studio] Actor} when composed.
     */
    public static String sanitizeCoreTitle(String coreTitle, List<String> studioNames, List<String> actorNames) {
        if (blank(coreTitle)) {
            return "";
        }
        String core = coreTitle.trim();

        Matcher bm = LEADING_BRACKETS.matcher(core);
        while (bm.find()) {
            String inside = bm.group(1);
            if (bracketMatchesStudios(inside, studioNames)) {
                core = core.substring(bm.end()).trim();
                bm = LEADING_BRACKETS.matcher(core);
            } else {
                break;
            }
        }

        if (core.startsWith("-")) {
            core = core.replaceFirst("^-+\\s*", "").trim();
        }

        core = stripLeadingActors(core, actorNames);

        if (core.startsWith("-")) {
            core = core.replaceFirst("^-+\\s*", "").trim();
        }

        if (containsIgnoreCase(studioNames, core) || containsIgnoreCase(actorNames, core)) {
            return "";
        }

        if (isFullyCoveredByPanels(core, studioNames, actorNames)) {
            return "";
        }

        return core;
    }

    private static boolean bracketMatchesStudios(String inside, List<String> studioNames) {
        if (blank(inside) || studioNames == null || studioNames.isEmpty()) {
            return false;
        }
        String[] pieces = inside.split("\\|");
        boolean any = false;
        for (String piece : pieces) {
            String p = piece.trim();
            if (p.isEmpty()) {
                continue;
            }
            any = true;
            if (!containsIgnoreCase(studioNames, p)) {
                return false;
            }
        }
        return any;
    }

    private static String stripLeadingActors(String core, List<String> actorNames) {
        if (blank(core) || actorNames == null || actorNames.isEmpty()) {
            return core == null ? "" : core;
        }
        String work = core.trim();
        if (containsIgnoreCase(actorNames, work)) {
            return "";
        }

        int pos = 0;
        boolean consumedAny = false;
        while (pos < work.length()) {
            if (work.startsWith(" - ", pos)) {
                pos += 3;
                break;
            }
            int nextSep = indexOfSeparator(work, pos);
            int dash = work.indexOf(" - ", pos);
            int end = work.length();
            if (nextSep >= 0) {
                end = nextSep;
            }
            if (dash >= 0 && dash < end) {
                end = dash;
            }
            String token = work.substring(pos, end).trim();
            if (token.isEmpty() || !containsIgnoreCase(actorNames, token)) {
                break;
            }
            consumedAny = true;
            if (dash == end) {
                pos = end + 3;
                break;
            }
            if (nextSep < 0 || nextSep != end) {
                pos = end;
                break;
            }
            pos = end;
            while (pos < work.length() && (work.charAt(pos) == ',' || work.charAt(pos) == '&'
                    || Character.isWhitespace(work.charAt(pos)))) {
                pos++;
            }
            if (work.regionMatches(true, pos, "and", 0, 3)
                    && (pos + 3 >= work.length() || Character.isWhitespace(work.charAt(pos + 3)))) {
                pos += 3;
                while (pos < work.length() && Character.isWhitespace(work.charAt(pos))) {
                    pos++;
                }
            }
        }
        if (!consumedAny) {
            return work;
        }
        String rest = work.substring(pos).trim();
        if (rest.startsWith("-")) {
            rest = rest.replaceFirst("^-+\\s*", "").trim();
        }
        return rest;
    }

    private static int indexOfSeparator(String work, int from) {
        int comma = work.indexOf(',', from);
        int amp = work.indexOf('&', from);
        int best = -1;
        if (comma >= 0) {
            best = comma;
        }
        if (amp >= 0 && (best < 0 || amp < best)) {
            best = amp;
        }
        return best;
    }

    private static boolean isFullyCoveredByPanels(String core, List<String> studios, List<String> actors) {
        FilenameMetadataParser parser = new FilenameMetadataParser();
        FilenameSuggestions parsed = parser.parse(core);
        boolean hadStructure = !parsed.getStudios().isEmpty() || !parsed.getActors().isEmpty()
                || (parsed.getTitle() != null && !parsed.getTitle().isBlank());
        if (!hadStructure) {
            return false;
        }
        for (String s : parsed.getStudios()) {
            if (!containsIgnoreCase(studios, s)) {
                return false;
            }
        }
        for (String a : parsed.getActors()) {
            if (!containsIgnoreCase(actors, a) && !containsIgnoreCase(studios, a)) {
                return false;
            }
        }
        if (parsed.getTitle() != null && !parsed.getTitle().isBlank()) {
            String t = parsed.getTitle().trim();
            if (!containsIgnoreCase(actors, t) && !containsIgnoreCase(studios, t)
                    && !isLowQualityTitle(t)) {
                return false;
            }
        }
        return !parsed.getStudios().isEmpty() || !parsed.getActors().isEmpty()
                || (parsed.getTitle() != null && (containsIgnoreCase(actors, parsed.getTitle())
                || containsIgnoreCase(studios, parsed.getTitle())));
    }

    /**
     * @return {@code (YYYY-MM-DD)}, {@code (YYYY)}, or empty
     */
    public static String formatDateSuffix(String dateField) {
        if (blank(dateField)) {
            return "";
        }
        String text = dateField.trim();
        Matcher full = FULL_DATE.matcher(text);
        if (full.matches()) {
            return "(" + text + ")";
        }
        Matcher year = YEAR_ONLY.matcher(text);
        if (year.matches()) {
            return "(" + text + ")";
        }
        return "";
    }

    /**
     * True when a title looks like a hash / digit dump rather than a human title.
     */
    public static boolean isLowQualityTitle(String title) {
        if (blank(title)) {
            return true;
        }
        String t = title.trim();
        if (t.matches("\\d+")) {
            return true;
        }
        if (t.matches("(?i)[0-9a-f]{8,}")) {
            return true;
        }
        if (!t.contains(" ") && t.length() >= 12 && t.matches("[A-Za-z0-9._\\-]+")) {
            int letters = 0;
            int digits = 0;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (Character.isLetter(c)) {
                    letters++;
                } else if (Character.isDigit(c)) {
                    digits++;
                }
            }
            if (digits >= letters || digits >= 8) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks the Videotitel core: refined filename title, or folder name when low quality.
     * Prefer {@link SuggestionRefiner} which parses the folder instead of using it raw.
     */
    public static String resolveCoreTitle(String parsedTitle, String folderName) {
        String parsed = parsedTitle == null ? "" : parsedTitle.trim();
        String folder = folderName == null ? "" : folderName.trim();
        if (!isLowQualityTitle(parsed)) {
            return parsed;
        }
        if (!folder.isEmpty()) {
            return folder;
        }
        return parsed;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) {
            return false;
        }
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean notBlank(String s) {
        return !blank(s);
    }
}
