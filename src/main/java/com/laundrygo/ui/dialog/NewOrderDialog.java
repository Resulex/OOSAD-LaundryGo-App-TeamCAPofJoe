package com.laundrygo.ui.dialog;

import com.laundrygo.model.Customer;
import com.laundrygo.model.Order;
import com.laundrygo.model.PricingMode;
import com.laundrygo.model.ServiceType;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.Ui;
import com.laundrygo.util.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

/** Frame 3 — log a new order with live base-cost calculation. */
public final class NewOrderDialog extends Dialog<Order> {

    private static final ButtonType SAVE = new ButtonType("Save & initialize order", ButtonBar.ButtonData.OK_DONE);

    private final AppServices services;
    private final User actor;
    private final Customer preselected;

    private final ComboBox<Customer> customerBox = new ComboBox<>();
    private final ObservableList<Customer> customerItems = FXCollections.observableArrayList();
    private final ComboBox<ServiceType> serviceBox = new ComboBox<>();
    private final TextField weightField = new TextField();
    private final TextArea notesArea = new TextArea();
    private final Label unitLabel = Ui.label("kg", "unit-label");
    private final Label baseCostLabel = Ui.label("₱0.00", "cost-value");
    private final Label costHint = Ui.label("Select a service type to price the order.", "cost-hint");
    private final Label errorLabel = Ui.label("", "error-text");

    private List<Customer> directory = new ArrayList<>();
    private List<ServiceType> catalogue = new ArrayList<>();
    private Order created;

    public NewOrderDialog(Window owner, AppServices services, User actor) {
        this(owner, services, actor, null);
    }

    public NewOrderDialog(Window owner, AppServices services, User actor, Customer preselected) {
        this.services = services;
        this.actor = actor;
        this.preselected = preselected;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Log new order");
        setResizable(false);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefWidth(620);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, SAVE);
        pane.setContent(buildContent());

        Button saveButton = (Button) pane.lookupButton(SAVE);
        saveButton.getStyleClass().add("primary-button");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (!save()) {
                event.consume();
            }
        });

        setResultConverter(button -> button == SAVE ? created : null);
    }

    // ------------------------------------------------------------------ layout

    private VBox buildContent() {
        loadData();

        Label title = Ui.label("New laundry order", "dialog-title");
        Label subtitle = Ui.label(
                "Pick a client, choose a service, and the base cost recalculates as you type.",
                "dialog-subtitle");
        subtitle.setWrapText(true);

        // --- client lookup (editable combo with auto-complete) --------------
        customerBox.setEditable(true);
        customerBox.setMaxWidth(Double.MAX_VALUE);
        customerBox.setItems(customerItems);
        customerBox.setVisibleRowCount(7);
        customerBox.setPromptText("Search the client directory");
        customerBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                return customer == null ? "" : customer.displayLabel();
            }

            @Override
            public Customer fromString(String text) {
                return directory.stream()
                        .filter(customer -> customer.displayLabel().equalsIgnoreCase(text))
                        .findFirst()
                        .orElse(null);
            }
        });
        customerBox.getEditor().addEventHandler(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN
                    || event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                return;
            }
            filterCustomers(customerBox.getEditor().getText());
        });

        Button newClient = Ui.button("+ New client", "ghost-button");
        newClient.setOnAction(event -> {
            CustomerFormDialog dialog = new CustomerFormDialog(getOwner(), services, actor.getUsername());
            dialog.showAndWait().ifPresent(customer -> {
                loadData();
                customerBox.setValue(customer);
                customerBox.getEditor().setText(customer.displayLabel());
                recompute();
            });
        });
        HBox.setHgrow(customerBox, Priority.ALWAYS);

        if (preselected != null) {
            customerBox.setValue(preselected);
            customerBox.getEditor().setText(preselected.displayLabel());
        }

        // --- order details ---------------------------------------------------
        serviceBox.setMaxWidth(Double.MAX_VALUE);
        serviceBox.setPromptText("Choose a service");
        serviceBox.getItems().setAll(catalogue);
        serviceBox.valueProperty().addListener((obs, old, value) -> {
            updateUnit(value);
            recompute();
        });

        weightField.setPromptText("e.g. 3.5");
        weightField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d{0,3}(\\.\\d{0,2})?") ? change : null));
        weightField.textProperty().addListener((obs, old, value) -> recompute());

        HBox weightRow = new HBox(8, weightField, unitLabel);
        weightRow.setAlignment(Pos.CENTER_LEFT);
        unitLabel.setMinWidth(28);
        HBox.setHgrow(weightField, Priority.ALWAYS);

        notesArea.setPromptText("Special instructions (optional)");
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        Label costTitle = Ui.label("Base cost", "cost-label");
        costHint.setWrapText(true);
        VBox costText = new VBox(3, costTitle, costHint);
        HBox.setHgrow(costText, Priority.ALWAYS);
        HBox costCard = new HBox(16, costText, baseCostLabel);
        costCard.setAlignment(Pos.CENTER_LEFT);
        costCard.getStyleClass().add("cost-preview");

        errorLabel.setWrapText(true);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        VBox body = new VBox(18,
                new VBox(5, title, subtitle),
                section("Client", new HBox(8, customerBox, newClient)),
                section("Order details",
                        new VBox(6, Ui.fieldLabel("Service type"), serviceBox),
                        new VBox(6, Ui.fieldLabel("Weight / piece count"), weightRow),
                        new VBox(6, Ui.fieldLabel("Notes"), notesArea)),
                costCard,
                errorLabel);
        body.getStyleClass().add("dialog-body");
        return body;
    }

    private VBox section(String title, javafx.scene.Node... content) {
        VBox box = new VBox(10);
        box.getChildren().add(Ui.label(title, "section-title"));
        box.getChildren().addAll(content);
        return box;
    }

    // ------------------------------------------------------------------ behaviour

    private void loadData() {
        directory = services.customers().findAll();
        catalogue = services.serviceTypes().findAll(true);
        customerItems.setAll(directory);
    }

    private void filterCustomers(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        List<Customer> matches = query.isEmpty()
                ? directory
                : directory.stream()
                        .filter(customer -> customer.name().toLowerCase(Locale.ROOT).contains(query)
                                || customer.phone().replace(" ", "").contains(query.replace(" ", "")))
                        .toList();

        String text = customerBox.getEditor().getText();
        customerItems.setAll(matches);
        customerBox.getEditor().setText(text);
        customerBox.getEditor().end();
        if (!matches.isEmpty()) {
            customerBox.show();
        }
    }

    private void updateUnit(ServiceType service) {
        if (service == null) {
            unitLabel.setText("kg");
            return;
        }
        unitLabel.setText(service.unitLabel());
        weightField.setPromptText(service.mode() == PricingMode.PER_KG ? "e.g. 3.5" : "e.g. 2");
    }

    private void recompute() {
        ServiceType service = serviceBox.getValue();
        double units = parseUnits();

        if (service == null) {
            baseCostLabel.setText(Money.format(0));
            costHint.setText("Select a service type to price the order.");
            return;
        }
        double base = service.price() * Math.max(units, 0);
        baseCostLabel.setText(Money.format(Money.round2(base)));
        String unitsLabel = units == Math.rint(units)
                ? String.valueOf((long) units)
                : String.format("%.2f", units);
        costHint.setText(Money.format(service.price()) + " per " + service.unitLabel()
                + "  ×  " + unitsLabel + " " + service.unitLabel());
    }

    private double parseUnits() {
        String text = weightField.getText() == null ? "" : weightField.getText().trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Customer resolveCustomer() {
        String text = customerBox.getEditor().getText() == null ? "" : customerBox.getEditor().getText().trim();
        Customer selected = customerBox.getValue();
        if (selected != null
                && (selected.displayLabel().equalsIgnoreCase(text) || selected.name().equalsIgnoreCase(text))) {
            return selected;
        }
        if (text.isEmpty()) {
            return null;
        }
        return directory.stream()
                .filter(customer -> customer.displayLabel().equalsIgnoreCase(text)
                        || customer.name().equalsIgnoreCase(text)
                        || customer.phone().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }

    private boolean save() {
        Customer customer = resolveCustomer();
        ServiceType service = serviceBox.getValue();
        double units = parseUnits();

        if (customer == null) {
            showError("Choose a client from the directory, or register a walk-in first.");
            return false;
        }
        if (service == null) {
            showError("Choose a service type.");
            return false;
        }
        if (units <= 0) {
            showError("Enter a weight / piece count greater than zero.");
            return false;
        }
        if (service.mode() == PricingMode.FLAT && units != Math.rint(units)) {
            showError("Flat-rate services are billed per piece — use a whole number.");
            return false;
        }

        try {
            created = services.orderService().createOrder(customer, service, units, notesArea.getText(), actor);
            return true;
        } catch (RuntimeException ex) {
            showError("Could not save the order: " + ex.getMessage());
            return false;
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }
}
