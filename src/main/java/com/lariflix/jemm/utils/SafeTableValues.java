package com.lariflix.jemm.utils;

import javax.swing.table.TableModel;

/**
 * Null-safe helpers for reading values from Swing table models.
 */
public final class SafeTableValues {

    private SafeTableValues() {
    }

    public static String asString(TableModel model, int row, int column) {
        if (model == null || row < 0 || column < 0 || row >= model.getRowCount() || column >= model.getColumnCount()) {
            return "";
        }
        Object value = model.getValueAt(row, column);
        return value == null ? "" : value.toString().trim();
    }

    public static int asInt(TableModel model, int row, int column, int defaultValue) {
        String raw = asString(model, row, column);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            if (raw.contains(".")) {
                return (int) Math.round(Double.parseDouble(raw));
            }
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
