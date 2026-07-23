package com.lariflix.jemm.utils;

import org.junit.jupiter.api.Test;
import javax.swing.table.DefaultTableModel;
import static org.junit.jupiter.api.Assertions.*;

public class SafeTableValuesTest {

    @Test
    public void readsNullSafeValues() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"A", "B"}, 0);
        model.addRow(new Object[]{null, "7.5"});
        assertEquals("", SafeTableValues.asString(model, 0, 0));
        assertEquals(8, SafeTableValues.asInt(model, 0, 1, 0));
        assertEquals(0, SafeTableValues.asInt(model, 0, 0, 0));
    }
}
