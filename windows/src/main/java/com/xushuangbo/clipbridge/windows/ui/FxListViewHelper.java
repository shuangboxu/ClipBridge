package com.xushuangbo.clipbridge.windows.ui;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;

public final class FxListViewHelper {
    private FxListViewHelper() {
    }

    public static <T> void bindAdaptiveHeight(
        ListView<T> listView,
        ObservableList<T> sourceItems,
        double rowHeight,
        int minRows,
        int maxRows
    ) {
        if (listView == null || sourceItems == null) {
            return;
        }
        int normalizedMinRows = Math.max(1, minRows);
        int normalizedMaxRows = Math.max(normalizedMinRows, maxRows);

        Runnable resize = () -> {
            int rows = Math.max(normalizedMinRows, Math.min(normalizedMaxRows, sourceItems.size()));
            double calculatedHeight = rows * rowHeight + 18;
            listView.setMinHeight(calculatedHeight);
            listView.setPrefHeight(calculatedHeight);
            listView.setMaxHeight(calculatedHeight);
        };

        // 中文注释：列表数据变化时同步更新高度，让页面更紧凑且可滚动。
        sourceItems.addListener((ListChangeListener<T>) change -> resize.run());
        resize.run();
    }
}

