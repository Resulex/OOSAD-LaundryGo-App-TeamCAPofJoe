package com.laundrygo.ui.component;

import com.laundrygo.model.OrderStatus;
import javafx.scene.control.Label;

/** Colour-coded status pill: grey pending, blue in-progress, green ready, red overdue. */
public final class StatusBadge {

    private StatusBadge() {
    }

    public static Label of(OrderStatus status) {
        Label label = new Label(status.displayName());
        label.getStyleClass().addAll("status-pill", "status-" + status.cssClass());
        return label;
    }

    public static Label pill(String text, String variant) {
        Label label = new Label(text);
        label.getStyleClass().addAll("status-pill", "status-" + variant);
        return label;
    }
}
