package com.laundrygo.ui.component;

import com.laundrygo.ui.Ui;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Metric tile used on the owner dashboard. */
public final class StatCard extends VBox {

    private final Label value = new Label("—");

    public StatCard(String title, String caption, String variantClass) {
        getStyleClass().addAll("stat-card", variantClass);
        setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(this, Priority.ALWAYS);

        Label titleLabel = Ui.label(title.toUpperCase(), "stat-title");
        value.getStyleClass().add("stat-value");
        Label captionLabel = Ui.label(caption, "stat-caption");
        captionLabel.setWrapText(true);

        getChildren().addAll(titleLabel, value, captionLabel);
    }

    public StatCard setValue(String text) {
        value.setText(text);
        return this;
    }
}
