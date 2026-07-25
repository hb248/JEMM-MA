package com.lariflix.jemm.tagteam;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * A single interpretation of a date token found in a filename. Carries the year and,
 * when a full date could be resolved, the month/day too. {@link #getDisplay()} is what
 * the user picks from; {@link #toDate()} yields a {@link Date} for {@code PremiereDate}.
 */
public class DateCandidate {

    private final int year;
    private final Integer month; // 1-12, null when year-only
    private final Integer day;   // 1-31, null when year-only
    private final String display;

    public DateCandidate(int year) {
        this(year, null, null);
    }

    public DateCandidate(int year, Integer month, Integer day) {
        this.year = year;
        this.month = month;
        this.day = day;
        if (month != null && day != null) {
            this.display = String.format("%04d-%02d-%02d", year, month, day);
        } else {
            this.display = String.format("%04d", year);
        }
    }

    public int getYear() {
        return year;
    }

    public Integer getMonth() {
        return month;
    }

    public Integer getDay() {
        return day;
    }

    public String getDisplay() {
        return display;
    }

    public boolean isFullDate() {
        return month != null && day != null;
    }

    /**
     * @return a {@link Date} for this candidate; a full date when known, otherwise Jan 1 of the year
     */
    public Date toDate() {
        Calendar cal = new GregorianCalendar();
        cal.clear();
        if (isFullDate()) {
            cal.set(year, month - 1, day, 0, 0, 0);
        } else {
            cal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
        }
        return cal.getTime();
    }

    @Override
    public String toString() {
        return display;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DateCandidate)) {
            return false;
        }
        DateCandidate other = (DateCandidate) o;
        return display.equals(other.display);
    }

    @Override
    public int hashCode() {
        return display.hashCode();
    }
}
