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
        StringBuilder sb = new StringBuilder();

        List<String> studioNames = new ArrayList<>();
        if (studios != null) {
            for (JellyfinStudioItem s : studios) {
                if (s != null && notBlank(s.getName())) {
                    studioNames.add(s.getName().trim());
                }
            }
        }
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

        String core = coreTitle == null ? "" : coreTitle.trim();
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
        // Long alphanumeric blob with no spaces.
        if (!t.contains(" ") && t.length() >= 12 && t.matches("[A-Za-z0-9._\\-]+")) {
            // Allow if it has several letter words separated somehow; otherwise treat as junk.
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

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean notBlank(String s) {
        return !blank(s);
    }
}
