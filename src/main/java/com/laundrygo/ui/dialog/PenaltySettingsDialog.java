package com.laundrygo.ui.dialog;

import com.laundrygo.model.PenaltyPolicy;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.Ui;
import com.laundrygo.util.Money;
import java.time.LocalDateTime;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Owner configuration: the daily penalty rate, grace period and cap. */
public final class PenaltySettingsDialog extends Dialog<Boolean> {

    public PenaltySettingsDialog(Window owner, AppServices services, User actor) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Penalty configuration");
        setResizable(false);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefWidth(540);

        ButtonType saveType = new ButtonType("Save configuration", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, saveType);

        Spinner<Integer> perDay = new Spinner<>(0, 1000,
                (int) Math.round(services.penalty().perDay()), 5);
        Spinner<Integer> graceDays = new Spinner<>(0, 14, services.penalty().graceDays());
        Spinner<Integer> capPercent = new Spinner<>(0, 200,
                (int) Math.round(services.penalty().capPercent()), 10);
        perDay.setEditable(false);
        graceDays.setEditable(false);
        capPercent.setEditable(false);
        perDay.setMaxWidth(Double.MAX_VALUE);
        graceDays.setMaxWidth(Double.MAX_VALUE);
        capPercent.setMaxWidth(Double.MAX_VALUE);

        Label preview = Ui.label("", "preview-value");
        Label previewHint = Ui.label(
                "Projection for a 3-day-late ₱500.00 order, using the values above.", "hint");
        previewHint.setWrapText(true);

        Runnable updatePreview = () -> {
            double fee = PenaltyPolicy.lateFee(
                    LocalDateTime.now().minusDays(3L),
                    LocalDateTime.now(),
                    500.0,
                    perDay.getValue(),
                    graceDays.getValue(),
                    capPercent.getValue());
            preview.setText(Money.format(fee) + " penalty");
        };
        perDay.valueProperty().addListener((obs, old, value) -> updatePreview.run());
        graceDays.valueProperty().addListener((obs, old, value) -> updatePreview.run());
        capPercent.valueProperty().addListener((obs, old, value) -> updatePreview.run());
        updatePreview.run();

        HBox previewCard = new HBox(14,
                new VBox(2, preview, previewHint),
                Ui.hSpacer(),
                Ui.label("live preview", "status-pill status-in-progress"));
        previewCard.setAlignment(Pos.CENTER_LEFT);
        previewCard.getStyleClass().add("cost-preview");

        VBox body = new VBox(16,
                new VBox(4,
                        Ui.label("Penalty rates", "dialog-title"),
                        Ui.label("Applied automatically when an order crosses its due date.",
                                "dialog-subtitle")),
                new VBox(6, Ui.fieldLabel("Penalty per late day (₱)"), perDay),
                new VBox(6, Ui.fieldLabel("Grace period (days)"), graceDays),
                new VBox(6, Ui.fieldLabel("Penalty cap (% of base cost)"), capPercent),
                previewCard,
                wrappedHint("Orders past their due date are flagged the next time a dashboard loads, "
                        + "and the accrued fee is stored on the order."));
        body.getStyleClass().add("dialog-body");
        pane.setContent(body);

        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.getStyleClass().add("primary-button");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                services.penalty().update(perDay.getValue(), graceDays.getValue(), capPercent.getValue());
                services.audit().log(actor.getUsername(), "PENALTY_CONFIG",
                        "Per day " + Money.format(perDay.getValue()) + " · grace " + graceDays.getValue()
                                + "d · cap " + capPercent.getValue() + "%");
            } catch (RuntimeException ex) {
                event.consume();
                Ui.error(getOwner(), "Could not save", ex.getMessage());
            }
        });

        setResultConverter(button -> button == saveType);
    }

    private Label wrappedHint(String text) {
        Label label = Ui.label(text, "hint");
        label.setWrapText(true);
        return label;
    }
}
