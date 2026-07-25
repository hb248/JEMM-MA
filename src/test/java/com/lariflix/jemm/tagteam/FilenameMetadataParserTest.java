package com.lariflix.jemm.tagteam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class FilenameMetadataParserTest {

    private final FilenameMetadataParser parser = new FilenameMetadataParser();

    @Test
    public void parsesStudioActorTitleAndDate() {
        FilenameSuggestions s = parser.parse("[BigStudio]Jane Doe - A Great Title (2024).mp4");
        assertEquals(1, s.getStudios().size());
        assertEquals("BigStudio", s.getStudios().get(0));
        assertEquals(1, s.getActors().size());
        assertEquals("Jane Doe", s.getActors().get(0));
        assertEquals("A Great Title", s.getTitle());
        assertFalse(s.getDates().isEmpty());
        assertEquals(2024, s.getDates().get(0).getYear());
    }

    @Test
    public void parsesMultipleStudiosAndActors() {
        FilenameSuggestions s = parser.parse("[Studio A | Studio B]Anna & Bob - The Title 2019.mkv");
        assertEquals(2, s.getStudios().size());
        assertTrue(s.getStudios().contains("Studio A"));
        assertTrue(s.getStudios().contains("Studio B"));
        assertEquals(2, s.getActors().size());
        assertTrue(s.getActors().contains("Anna"));
        assertTrue(s.getActors().contains("Bob"));
        assertEquals("The Title", s.getTitle());
    }

    @Test
    public void ambiguousSixDigitDatePrunesInvalidMonth() {
        // 30 can only be a day, so 12/30/22 (MMDDYY) is the sole valid reading.
        List<DateCandidate> dates = parser.parseDateCandidates("123022");
        assertEquals(1, dates.size());
        assertEquals("2022-12-30", dates.get(0).getDisplay());
    }

    @Test
    public void sixDigitDateOffersSeveralReadings() {
        List<DateCandidate> dates = parser.parseDateCandidates("110524");
        assertTrue(dates.size() >= 2);
        assertTrue(dates.stream().anyMatch(d -> d.getDisplay().equals("2024-05-11")));
        assertTrue(dates.stream().anyMatch(d -> d.getDisplay().equals("2024-11-05")));
    }

    @Test
    public void dottedDateParsesDayMonthYear() {
        List<DateCandidate> dates = parser.parseDateCandidates("11.05.24");
        assertTrue(dates.stream().anyMatch(d -> d.getDisplay().equals("2024-05-11")));
    }

    @Test
    public void yearOnlyToken() {
        List<DateCandidate> dates = parser.parseDateCandidates("2001");
        assertEquals(1, dates.size());
        assertEquals("2001", dates.get(0).getDisplay());
        assertFalse(dates.get(0).isFullDate());
    }

    @Test
    public void titleWithoutActorsSeparator() {
        FilenameSuggestions s = parser.parse("Just A Title (1999).jpg");
        assertTrue(s.getActors().isEmpty());
        assertEquals("Just A Title", s.getTitle());
        assertEquals(1999, s.getDates().get(0).getYear());
    }

    @Test
    public void bracketStudioWithoutDashTreatsRemainderAsActor() {
        FilenameSuggestions s = parser.parse("[StudioX]darsteller");
        assertEquals(List.of("StudioX"), s.getStudios());
        assertEquals(List.of("darsteller"), s.getActors());
        assertEquals("", s.getTitle());
    }

    @Test
    public void emptyInputYieldsEmptySuggestions() {
        FilenameSuggestions s = parser.parse("");
        assertTrue(s.getStudios().isEmpty());
        assertTrue(s.getActors().isEmpty());
        assertTrue(s.getDates().isEmpty());
        assertEquals("", s.getTitle());
    }
}
