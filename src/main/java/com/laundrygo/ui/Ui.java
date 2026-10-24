package com.laundrygo.ui;

import java.net.URL;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Window;

/** Small factory helpers that keep styling consistent across the whole app. */
public final class Ui {

    private static String stylesheet;

    private Ui() {
    }

    public static String stylesheet() {
        if (stylesheet == null) {
            URL url = Ui.class.getResource("/com/laundrygo/app.css");
            if (url == null) {
                throw new IllegalStateException("app.css is missing from the classpath");
            }
            stylesheet = url.toExternalForm();
        }
        return stylesheet;
    }

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        if (styleClasses.length > 0) {
            label.getStyleClass().addAll(styleClasses);
        }
        return label;
    }

    public static Button button(String text, String... styleClasses) {
        Button button = new Button(text);
        if (styleClasses.length > 0) {
            button.getStyleClass().addAll(styleClasses);
        }
        return button;
    }

    public static Label fieldLabel(String text) {
        return label(text, "field-label");
    }

    public static TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        return field;
    }

    public static Region hSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMinWidth(0);
        return spacer;
    }

    public static Region vSpacer() {
        Region spacer = new Region();
        javafx.scene.layout.VBox.setVgrow(spacer, Priority.ALWAYS);
        spacer.setMinHeight(0);
        return spacer;
    }

    public static HBox divider() {
        Region line = new Region();
        line.getStyleClass().add("divider-line");
        line.setPrefHeight(1);
        HBox.setHgrow(line, Priority.ALWAYS);
        HBox box = new HBox(line);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("divider");
        return box;
    }

    public static void styleDialog(DialogPane pane) {
        pane.getStylesheets().add(stylesheet());
        pane.getStyleClass().add("lg-dialog");
    }

    public static void info(Window owner, String title, String message) {
        showAlert(owner, title, message, Alert.AlertType.INFORMATION);
    }

    public static void error(Window owner, String title, String message) {
        showAlert(owner, title, message, Alert.AlertType.ERROR);
    }

    public static boolean confirm(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initOwner(owner);
        styleDialog(alert.getDialogPane());
        Label body = label(message, "hint");
        body.setWrapText(true);
        body.setMaxWidth(420);
        alert.getDialogPane().setContent(body);
        return alert.showAndWait().filter(type -> type == ButtonType.OK).isPresent();
    }

    private static void showAlert(Window owner, String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initOwner(owner);
        styleDialog(alert.getDialogPane());
        Label body = label(message, "hint");
        body.setWrapText(true);
        body.setMaxWidth(420);
        alert.getDialogPane().setContent(body);
        alert.showAndWait();
    }

    public static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
