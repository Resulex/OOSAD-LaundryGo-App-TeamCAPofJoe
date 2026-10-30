package com.laundrygo.ui.dialog;

import com.laundrygo.model.Customer;
import com.laundrygo.model.Order;
import com.laundrygo.model.OrderStatus;
import com.laundrygo.model.Payment;
import com.laundrygo.model.Receipt;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.Ui;
import com.laundrygo.ui.component.StatusBadge;
import com.laundrygo.util.Money;
import com.laundrygo.util.Times;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Frame — client directory: search the address book, inspect a client profile with their full
 * order history, then edit, delete or start a new order for them.
 */
public final class CustomerDirectoryDialog extends Dialog<Boolean> {

    private final AppServices services;
    private final User actor;

    private final ObservableList<Customer> data = FXCollections.observableArrayList();
    private final FilteredList<Customer> filtered = new FilteredList<>(data, customer -> true);
    private final TableView<Customer> table = new TableView<>(filtered);

    private final ObservableList<Order> history = FXCollections.observableArrayList();
    private final TableView<Order> historyTable = new TableView<>(history);

    private final TextField searchField = new TextField();
    private final Label footerCount = Ui.label("", "hint");

    private final Label nameLabel = Ui.label("Select a client", "profile-name");
    private final Label phoneLabel = Ui.label("Pick a row to load the profile and order history.",
            "profile-sub");
    private final Label sinceValue = Ui.label("—", "profile-value");
    private final Label ordersValue = Ui.label("—", "profile-value");
    private final Label openValue = Ui.label("—", "profile-value");
    private final Label spendValue = Ui.label("—", "profile-value");

    private final Button editButton = Ui.button("Edit client", "secondary-button");
    private final Button deleteButton = Ui.button("Delete client", "ghost-button");
    private final Button orderButton = Ui.button("Log new order", "primary-button");

    private Customer selected;
    private boolean changed;

    public CustomerDirectoryDialog(Window owner, AppServices services, User actor) {
        this.services = services;
        this.actor = actor;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Client directory");
        setResizable(true);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefSize(1100, 760);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setContent(buildContent());

        reload();
        setResultConverter(button -> changed);
    }

    // ------------------------------------------------------------------ layout

    private VBox buildContent() {
        Label title = Ui.label("Client directory", "dialog-title");
        Label subtitle = Ui.label(
                "Search by name or mobile number, review past transactions, then edit or remove the record.",
                "dialog-subtitle");
        subtitle.setWrapText(true);

        searchField.setPromptText("Search client name or mobile number");
        searchField.getStyleClass().add("search-input");
        searchField.textProperty().addListener((obs, old, value) -> {
            applyFilter();
            syncSelection();
        });

        HBox searchShell = new HBox(searchField);
        searchShell.getStyleClass().add("search-shell");
        searchShell.setAlignment(Pos.CENTER_LEFT);
        searchShell.setMaxWidth(420);

        HBox split = new HBox(16, buildDirectoryCard(), buildProfileCard());
        VBox.setVgrow(split, Priority.ALWAYS);
        HBox.setHgrow(split.getChildren().get(0), Priority.ALWAYS);

        VBox body = new VBox(16, new VBox(4, title, subtitle), searchShell, split, buildHistoryCard());
        VBox.setVgrow(split, Priority.ALWAYS);
        body.getStyleClass().add("dialog-body");
        return body;
    }

    private VBox buildDirectoryCard() {
        buildTable();

        Label heading = Ui.label("Clients", "card-title");
        HBox header = new HBox(10, heading, Ui.hSpacer(), footerCount);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, header, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        card.setMinWidth(420);
        return card;
    }

    private void buildTable() {
        table.getStyleClass().add("lg-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.label("No clients match this search.", "table-empty"));
        table.setMinHeight(0);

        TableColumn<Customer, String> name = new TableColumn<>("Client");
        name.setPrefWidth(230);
        name.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().name()));

        TableColumn<Customer, String> phone = new TableColumn<>("Mobile");
        phone.setPrefWidth(170);
        phone.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().phone()));

        TableColumn<Customer, String> since = new TableColumn<>("Client since");
        since.setPrefWidth(150);
        since.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().createdAt() == null ? "—" : Times.shortDisplay(cell.getValue().createdAt())));

        table.getColumns().addAll(name, phone, since);

        table.setRowFactory(tv -> {
            TableRow<Customer> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    selected = row.getItem();
                    loadProfile(selected);
                }
            });
            return row;
        });

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value != null) {
                selected = value;
                loadProfile(value);
            }
        });
    }

    private VBox buildProfileCard() {
        VBox details = new VBox(8,
                statRow("Client since", sinceValue),
                statRow("Orders logged", ordersValue),
                statRow("Open orders", openValue),
                statRow("Lifetime paid", spendValue));

        editButton.setMaxWidth(Double.MAX_VALUE);
        editButton.setOnAction(event -> editSelected());
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setOnAction(event -> deleteSelected());
        orderButton.setMaxWidth(Double.MAX_VALUE);
        orderButton.setOnAction(event -> logOrderForSelected());

        VBox card = new VBox(12,
                Ui.label("Client profile", "card-title"),
                new VBox(3, nameLabel, phoneLabel),
                Ui.divider(),
                details,
                Ui.vSpacer(),
                orderButton,
                editButton,
                deleteButton);
        card.getStyleClass().add("card");
        card.setPrefWidth(316);
        card.setMinWidth(316);
        card.setMaxWidth(316);

        nameLabel.setWrapText(true);
        phoneLabel.setWrapText(true);
        setProfileEnabled(false);
        return card;
    }

    private HBox statRow(String label, Label value) {
        HBox row = new HBox(10, Ui.label(label, "detail-label"), Ui.hSpacer(), value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildHistoryCard() {
        historyTable.getStyleClass().add("lg-table");
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPlaceholder(Ui.label("No orders for this client yet.", "table-empty"));
        historyTable.setMinHeight(150);
        historyTable.setPrefHeight(230);

        TableColumn<Order, String> code = new TableColumn<>("Order");
        code.setPrefWidth(110);
        code.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getCode()));

        TableColumn<Order, String> logged = new TableColumn<>("Logged");
        logged.setPrefWidth(150);
        logged.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                Times.shortDisplay(cell.getValue().getCreatedAt())));

        TableColumn<Order, String> service = new TableColumn<>("Service");
        service.setPrefWidth(170);
        service.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getServiceName()));

        TableColumn<Order, String> units = new TableColumn<>("Units");
        units.setPrefWidth(90);
        units.getStyleClass().add("num-column");
        units.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getWeightLabel()));

        TableColumn<Order, String> total = new TableColumn<>("Total");
        total.setPrefWidth(110);
        total.getStyleClass().add("num-column");
        total.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                Money.format(cell.getValue().getTotal())));

        TableColumn<Order, OrderStatus> status = new TableColumn<>("Status");
        status.setPrefWidth(120);
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getStatus()));
        status.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(OrderStatus value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(StatusBadge.of(value));
            }
        });

        historyTable.getColumns().addAll(code, logged, service, units, total, status);

        Button receiptButton = Ui.button("Print receipt", "ghost-button");
        receiptButton.setDisable(true);
        receiptButton.setOnAction(event -> printReceiptForHistory());
        historyTable.getSelectionModel().selectedItemProperty().addListener((obs, old, order) ->
                receiptButton.setDisable(order == null || order.getStatus() != OrderStatus.COMPLETED));

        HBox header = new HBox(10, Ui.label("Order history", "card-title"), Ui.hSpacer(),
                receiptButton);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, header, historyTable);
        card.getStyleClass().add("card");
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        return card;
    }

    // ------------------------------------------------------------------ data

    private void reload() {
        Long keepId = selected == null ? null : selected.id();
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        data.setAll(query.isEmpty()
                ? services.customers().findAll()
                : services.customers().search(query));
        applyFilter();
        if (keepId != null) {
            selectById(keepId);
        } else {
            clearProfile();
        }
    }

    private void applyFilter() {
        String query = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);

        filtered.setPredicate(customer -> query.isEmpty()
                || customer.name().toLowerCase(Locale.ROOT).contains(query)
                || customer.phone().replace(" ", "").toLowerCase(Locale.ROOT).contains(query.replace(" ", "")));

        footerCount.setText(filtered.size() + " of " + data.size() + " clients");
    }

    private void syncSelection() {
        if (selected == null) {
            return;
        }
        if (!filtered.contains(selected)) {
            selected = null;
            table.getSelectionModel().clearSelection();
            clearProfile();
        }
    }

    private void selectById(long id) {
        Customer match = data.stream().filter(customer -> customer.id() == id).findFirst().orElse(null);
        if (match == null || !filtered.contains(match)) {
            clearProfile();
            return;
        }
        table.getSelectionModel().select(match);
        selected = match;
        loadProfile(match);
    }

    private void loadProfile(Customer customer) {
        if (customer == null) {
            clearProfile();
            return;
        }
        Long keepId = customer.id();
        Customer latest = services.customers().findById(keepId).orElse(customer);
        selected = latest;

        List<Order> orders = services.orders().findByCustomer(latest.id());
        history.setAll(orders);

        long open = orders.stream().filter(order -> order.getStatus() != OrderStatus.COMPLETED).count();
        double paid = orders.stream().mapToDouble(Order::getAmountPaid).sum();

        nameLabel.setText(latest.name());
        phoneLabel.setText(latest.phone() + "  ·  " + orders.size() + " order(s) on file");
        sinceValue.setText(latest.createdAt() == null ? "—" : Times.shortDisplay(latest.createdAt()));
        ordersValue.setText(String.valueOf(orders.size()));
        openValue.setText(String.valueOf(open));
        spendValue.setText(Money.format(paid));

        setProfileEnabled(true);
    }

    private void clearProfile() {
        selected = null;
        history.clear();
        nameLabel.setText("Select a client");
        phoneLabel.setText("Pick a row to load the profile and order history.");
        sinceValue.setText("—");
        ordersValue.setText("—");
        openValue.setText("—");
        spendValue.setText("—");
        setProfileEnabled(false);
    }

    private void setProfileEnabled(boolean enabled) {
        editButton.setDisable(!enabled);
        deleteButton.setDisable(!enabled);
        orderButton.setDisable(!enabled);
    }

    // ------------------------------------------------------------------ actions

    private void editSelected() {
        if (selected == null) {
            return;
        }
        new CustomerFormDialog(getOwner(), services, actor.getUsername(), selected)
                .showAndWait()
                .ifPresent(updated -> {
                    changed = true;
                    selected = updated;
                    reload();
                });
    }

    private void deleteSelected() {
        if (selected == null) {
            return;
        }
        long open = services.orders().countOpenByCustomer(selected.id());
        if (open > 0) {
            Ui.error(getOwner(), "Client still has open orders",
                    selected.name() + " has " + open + " order(s) in the queue. Settle or complete "
                            + "them before removing the profile.");
            return;
        }
        boolean confirmed = Ui.confirm(getOwner(), "Remove client",
                "Delete " + selected.name() + "'s profile?\n\n"
                        + "Their settled orders stay in the audit trail and reports, but the client "
                        + "disappears from the directory and the order picker.");
        if (!confirmed) {
            return;
        }
        try {
            String removedName = selected.name();
            services.customers().archive(selected.id());
            services.audit().log(actor.getUsername(), "CLIENT_DELETED",
                    selected.name() + " · " + selected.phone());
            changed = true;
            clearProfile();
            reload();
            Ui.info(getOwner(), "Client removed", removedName + " was removed from the directory.");
        } catch (RuntimeException ex) {
            Ui.error(getOwner(), "Could not remove the client", ex.getMessage());
        }
    }

    private void logOrderForSelected() {
        if (selected == null) {
            return;
        }
        new NewOrderDialog(getOwner(), services, actor, selected).showAndWait().ifPresent(order -> {
            changed = true;
            loadProfile(selected);
        });
    }

    /** Reprints the receipt of the settled order highlighted in the history table. */
    private void printReceiptForHistory() {
        Order order = historyTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            return;
        }
        Optional<Payment> latest = services.payments().findForOrder(order.getId());
        if (latest.isEmpty()) {
            Ui.info(getOwner(), "No payment on file",
                    order.getCode() + " has no recorded settlement, so there is no receipt to print.");
            return;
        }
        Payment payment = latest.get();
        Receipt receipt = new Receipt(order.getCode(), order.getCustomerName(), order.getCustomerPhone(),
                order.getServiceName(), order.getWeightLabel(), order.getUnitPrice(), order.getBaseCost(),
                order.getLateFee(), order.getTotal(), payment.amount(), 0, payment.method(),
                payment.reference(), payment.receivedBy(), payment.at());
        new ReceiptDialog(getOwner(), receipt).showAndWait();
    }
}
