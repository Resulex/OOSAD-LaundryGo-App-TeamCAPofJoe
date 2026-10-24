package com.laundrygo.ui.dialog;

import com.laundrygo.model.Customer;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.Ui;
import java.util.regex.Pattern;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Small dialog used to register a new walk-in client. */
public final class CustomerFormDialog extends Dialog<Customer> {

    private static final Pattern PHONE = Pattern.compile("[0-9+()\\-\\s]{7,20}");

    public CustomerFormDialog(Window owner, AppServices services) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Register walk-in client");
        setResizable(false);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefWidth(460);

        ButtonType saveType = new ButtonType("Save client", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, saveType);

        TextField nameField = new TextField();
        nameField.setPromptText("Full name, e.g. Maria Dela Cruz");
        TextField phoneField = new TextField();
        phoneField.setPromptText("Mobile number, e.g. 0917 555 0134");

        Label error = Ui.label("", "error-text");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);

        VBox body = new VBox(14,
                Ui.label("New client", "dialog-title"),
                Ui.label("Only a name and contact number are needed to start an order.", "dialog-subtitle"),
                new VBox(6, Ui.fieldLabel("Client name"), nameField),
                new VBox(6, Ui.fieldLabel("Mobile number"), phoneField),
                error);
        body.getStyleClass().add("dialog-body");
        body.setPadding(new Insets(4, 0, 0, 0));
        pane.setContent(body);

        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.getStyleClass().add("primary-button");

        Customer[] created = new Customer[1];
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();

            if (name.length() < 2) {
                showError(error, "Enter the client's full name.");
                event.consume();
                return;
            }
            if (!PHONE.matcher(phone).matches()) {
                showError(error, "Enter a valid mobile number (7–20 digits).");
                event.consume();
                return;
            }
            try {
                created[0] = services.customers().insert(name, phone);
                services.audit().log("counter", "CLIENT_ADDED", name + " · " + phone);
            } catch (RuntimeException ex) {
                showError(error, "Could not save the client: " + ex.getMessage());
                event.consume();
            }
        });

        setResultConverter(button -> button == saveType ? created[0] : null);
    }

    private void showError(Label error, String message) {
        error.setText(message);
        error.setManaged(true);
        error.setVisible(true);
    }
}
