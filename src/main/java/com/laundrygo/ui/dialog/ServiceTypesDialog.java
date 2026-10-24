package com.laundrygo.ui.dialog;

import com.laundrygo.model.PricingMode;
import com.laundrygo.model.ServiceType;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.Ui;
import com.laundrygo.util.Money;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Owner configuration: the service catalogue and its prices. */
public final class ServiceTypesDialog extends Dialog<Boolean> {

    private final AppServices services;
    private final User actor;

    private final ObservableList<ServiceType> data = FXCollections.observableArrayList();
    private final TableView<ServiceType> table = new TableView<>(data);

    private final TextField nameField = new TextField();
    private final ComboBox<PricingMode> modeBox = new ComboBox<>();
    private final TextField priceField = new TextField();
    private final Spinner<Integer> turnaround = new Spinner<>(0, 14, 2);
    private final Label error = Ui.label("", "error-text");

    private ServiceType selected;
    private boolean changed;

    public ServiceTypesDialog(Window owner, AppServices services, User actor) {
        this.services = services;
        this.actor = actor;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Service types");
        setResizable(true);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefSize(900, 640);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setContent(buildContent());

        reload();
        setResultConverter(button -> changed);
    }

    private VBox buildContent() {
        Label title = Ui.label("Service catalogue", "dialog-title");
        Label subtitle = Ui.label("Prices here drive the base cost of every new order.", "dialog-subtitle");

        buildTable();

        nameField.setPromptText("e.g. Wash-Dry-Fold");
        modeBox.getItems().setAll(PricingMode.values());
        modeBox.setValue(PricingMode.PER_KG);
        modeBox.setMaxWidth(Double.MAX_VALUE);
        priceField.setPromptText("e.g. 65.00");
        turnaround.setEditable(false);

        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);

        Button saveButton = Ui.button("Save service", "primary-button");
        saveButton.setOnAction(event -> save());

        Button newButton = Ui.button("New service", "ghost-button");
        newButton.setOnAction(event -> clearForm());

        Button retireButton = Ui.button("Retire selected", "ghost-button");
        retireButton.setOnAction(event -> retire());

        HBox actions = new HBox(10, saveButton, newButton, Ui.hSpacer(), retireButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(10,
                Ui.label("Service editor", "section-title"),
                new VBox(6, Ui.fieldLabel("Name"), nameField),
                new VBox(6, Ui.fieldLabel("Pricing mode"), modeBox),
                new VBox(6, Ui.fieldLabel("Price (₱)"), priceField),
                new VBox(6, Ui.fieldLabel("Turnaround (days)"), turnaround),
                error,
                actions);
        form.getStyleClass().add("card");

        VBox tableCard = new VBox(10, Ui.label("Catalogue", "card-title"), table);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox.setHgrow(tableCard, Priority.ALWAYS);

        HBox split = new HBox(16, tableCard, form);
        form.setPrefWidth(300);
        form.setMinWidth(300);

        VBox body = new VBox(16, new VBox(4, title, subtitle), split);
        VBox.setVgrow(split, Priority.ALWAYS);
        body.getStyleClass().add("dialog-body");
        return body;
    }

    private void buildTable() {
        table.getStyleClass().add("lg-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.label("No service types yet.", "table-empty"));

        TableColumn<ServiceType, String> name = new TableColumn<>("Service");
        name.setPrefWidth(190);
        name.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().name()));

        TableColumn<ServiceType, String> mode = new TableColumn<>("Billing");
        mode.setPrefWidth(140);
        mode.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().mode().displayName()));

        TableColumn<ServiceType, String> price = new TableColumn<>("Price");
        price.setPrefWidth(110);
        price.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                Money.format(cell.getValue().price()) + " / " + cell.getValue().unitLabel()));

        TableColumn<ServiceType, String> turnaroundDays = new TableColumn<>("Turnaround");
        turnaroundDays.setPrefWidth(100);
        turnaroundDays.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().turnaroundDays() + " day(s)"));

        TableColumn<ServiceType, String> active = new TableColumn<>("Status");
        active.setPrefWidth(90);
        active.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().active() ? "Active" : "Retired"));

        table.getColumns().addAll(name, mode, price, turnaroundDays, active);

        table.setRowFactory(tv -> {
            TableRow<ServiceType> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    select(row.getItem());
                }
            });
            return row;
        });
    }

    private void reload() {
        data.setAll(services.serviceTypes().findAll(false));
    }

    private void select(ServiceType service) {
        selected = service;
        nameField.setText(service.name());
        modeBox.setValue(service.mode());
        priceField.setText(Money.plain(service.price()));
        turnaround.getValueFactory().setValue(service.turnaroundDays());
        hideError();
    }

    private void clearForm() {
        selected = null;
        table.getSelectionModel().clearSelection();
        nameField.clear();
        priceField.clear();
        modeBox.setValue(PricingMode.PER_KG);
        turnaround.getValueFactory().setValue(2);
        hideError();
        nameField.requestFocus();
    }

    private void save() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        Double price = Money.parse(priceField.getText());
        PricingMode mode = modeBox.getValue();

        if (name.length() < 2) {
            showError("Enter a service name.");
            return;
        }
        if (price == null || price <= 0) {
            showError("Enter a price greater than zero.");
            return;
        }
        if (mode == null) {
            showError("Choose a pricing mode.");
            return;
        }

        try {
            if (selected == null) {
                services.serviceTypes().insert(name, mode, price, turnaround.getValue());
                services.audit().log(actor.getUsername(), "SERVICE_ADDED",
                        name + " · " + Money.format(price));
            } else {
                services.serviceTypes().update(selected.id(), name, mode, price,
                        turnaround.getValue(), selected.active());
                services.audit().log(actor.getUsername(), "SERVICE_UPDATED",
                        name + " · " + Money.format(price));
            }
            changed = true;
            reload();
            clearForm();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void retire() {
        if (selected == null) {
            showError("Select a service in the table first.");
            return;
        }
        if (!Ui.confirm(getOwner(), "Retire service",
                "Retire \"" + selected.name() + "\"? Existing orders are unaffected, but the service "
                        + "will no longer appear when logging new orders.")) {
            return;
        }
        try {
            services.serviceTypes().update(selected.id(), selected.name(), selected.mode(),
                    selected.price(), selected.turnaroundDays(), false);
            services.audit().log(actor.getUsername(), "SERVICE_RETIRED", selected.name());
            changed = true;
            reload();
            clearForm();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        error.setText(message);
        error.setManaged(true);
        error.setVisible(true);
    }

    private void hideError() {
        error.setManaged(false);
        error.setVisible(false);
    }
}
