package com.laundrygo.ui.component;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Lightweight in-app notification that slides into the top of a dashboard. */
public final class Toast {

    public enum Kind {
        SUCCESS, INFO, ERROR
    }

    private Toast() {
    }

    public static void show(StackPane host, String message, Kind kind) {
        Label toast = new Label(message);
        toast.getStyleClass().addAll("toast", "toast-" + kind.name().toLowerCase());
        toast.setWrapText(true);
        toast.setMaxWidth(620);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(20, 20, 0, 20));
        toast.setOpacity(0);
        toast.setTranslateY(-12);

        host.getChildren().add(toast);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), toast);
        fadeIn.setToValue(1);
        javafx.animation.TranslateTransition slideIn =
                new javafx.animation.TranslateTransition(Duration.millis(180), toast);
        slideIn.setToY(0);
        fadeIn.play();
        slideIn.play();

        PauseTransition hold = new PauseTransition(Duration.seconds(3.6));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(320), toast);
        fadeOut.setToValue(0);

        SequentialTransition sequence = new SequentialTransition(hold, fadeOut);
        sequence.setOnFinished(event -> host.getChildren().remove(toast));
        sequence.play();
    }
}
