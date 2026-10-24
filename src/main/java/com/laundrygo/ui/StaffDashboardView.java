package com.laundrygo.ui;

import com.laundrygo.model.Order;
import com.laundrygo.model.OrderStatus;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.component.AppSidebar;
import com.laundrygo.ui.component.StatusBadge;
import com.laundrygo.ui.component.Toast;
import com.laundrygo.ui.dialog.CustomerFormDialog;
import com.laundrygo.ui.dialog.NewOrderDialog;
import com.laundrygo.ui.dialog.OrderProfileDialog;
import com.laundrygo.util.Money;
import com.laundrygo.util.Times;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Frame 2 — the frontline counter hub. */
public final class StaffDashboardView {

    private final AppNavigator nav;
    private final AppServices services;
    private final User user;

    private final StackPane root = new StackPane();
    private final ObservableList<Order> master = FXCollections.observableArrayList();
    private final FilteredList<Order> filtered = new FilteredList<>(master, order -> true);
    private final SortedList<Order> sorted = new SortedList<>(filtered);

    private final Label queueBadge = Ui.label("0", "count-badge");
    private final Label footerCount = Ui.label("", "footer-text");
    private final Label readyValue = Ui.label("0", "metric-value");
    private final Label progressValue = Ui.label("0", "metric-value");
    private final Label overdueValue = Ui.label("0", "metric-value");

    private TableView<Order> table;
    private TextField searchField;
    private Predicate<Order> queueFilter = order -> true;

    public StaffDashboardView(AppNavigator nav, User user) {
        this.nav = nav;
        this.services = nav.services();
        this.user = user;

        build();
        refresh();
    }

    public Parent getRoot() {
        return root;
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setLeft(buildSidebar());

        BorderPane content = new BorderPane();
        content.getStyleClass().add("content-area");
        content.setTop(buildHeader());
        content.setCenter(buildQueueCard());
        content.setRight(buildQuickPanel());
        content.setBottom(buildFooter());

        shell.setCenter(content);
        root.getChildren().add(shell);
    }

    private AppSidebar buildSidebar() {
        AppSidebar sidebar = new AppSidebar("Counter desk", user, this::logout);

        Button desk = sidebar.addNavItem("Counter desk", () -> applyQueueFilter(order -> true));
        sidebar.addNavItem("Ready for pickup", () -> applyQueueFilter(order -> order.getStatus() == OrderStatus.READY));
        sidebar.addNavItem("In progress", () -> applyQueueFilter(order -> order.getStatus() == OrderStatus.IN_PROGRESS));
        sidebar.addNavItem("Overdue", () -> applyQueueFilter(order -> order.getStatus() == OrderStatus.OVERDUE));
        sidebar.activate(desk);

        return sidebar;
    }

    private VBox buildHeader() {
        Label title = Ui.label("Counter desk", "page-title");
        Label subtitle = Ui.label(Times.HEADER_DATE.format(LocalDate.now()) + "  ·  " + shiftLabel() + " shift",
                "page-subtitle");

        searchField = new TextField();
        searchField.setPromptText("Search order ID, client name or number");
        searchField.getStyleClass().add("search-input");
        searchField.textProperty().addListener((obs, old, value) -> applyFilters());

        HBox searchShell = new HBox(searchField);
        searchShell.getStyleClass().add("search-shell");
        searchShell.setAlignment(Pos.CENTER_LEFT);
        searchShell.setPrefWidth(360);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button register = Ui.button("Register walk-in client", "secondary-button");
        register.setOnAction(event -> registerWalkIn());

        HBox row = new HBox(16, new VBox(3, title, subtitle), Ui.hSpacer(), searchShell, register);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(row);
        header.getStyleClass().add("page-header");
        return header;
    }

    private VBox buildQueueCard() {
        table = buildTable();

        Button refreshButton = Ui.button("Refresh", "ghost-button");
        refreshButton.setOnAction(event -> {
            refresh();
            Toast.show(root, "Queue synchronised with the counter database.", Toast.Kind.INFO);
        });

        Button logOrder = Ui.button("Log new order", "primary-button");
        logOrder.setOnAction(event -> logNewOrder());

        HBox cardHeader = new HBox(10,
                Ui.label("Active queue", "card-title"),
                queueBadge,
                Ui.hSpacer(),
                refreshButton,
                logOrder);
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, cardHeader, table);
        card.getStyleClass().addAll("card", "queue-card");
        VBox.setVgrow(table, Priority.ALWAYS);
        BorderPane.setMargin(card, new Insets(0, 18, 0, 0));
        return card;
    }

    private TableView<Order> buildTable() {
        TableView<Order> view = new TableView<>(sorted);
        view.getStyleClass().add("lg-table");
        view.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        view.setPlaceholder(Ui.label("No orders match this view yet.", "table-empty"));
        sorted.comparatorProperty().bind(view.comparatorProperty());

        TableColumn<Order, String> code = textColumn("Order ID", 100, Order::getCode, "cell-strong");
        code.setMinWidth(88);
        TableColumn<Order, String> client = textColumn("Client", 132, Order::getCustomerName, null);
        client.setMinWidth(96);
        TableColumn<Order, String> service = textColumn("Service", 124, Order::getServiceName, null);
        service.setMinWidth(104);
        TableColumn<Order, String> weight = textColumn("Weight", 86, Order::getWeightLabel, "cell-number");
        weight.setMinWidth(74);
        TableColumn<Order, Order> due = dueColumn();
        TableColumn<Order, OrderStatus> status = statusColumn();
        TableColumn<Order, String> balance = textColumn("Balance", 90,
                order -> Money.format(order.getBalance()), "cell-number");
        balance.setMinWidth(82);

        view.getColumns().addAll(code, client, service, weight, due, status, balance);
        view.setMinHeight(0);

        view.setRowFactory(tv -> {
            TableRow<Order> row = new TableRow<>() {
                @Override
                protected void updateItem(Order item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("row-overdue");
                    if (!empty && item != null && item.isOverdue()) {
                        getStyleClass().add("row-overdue");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openOrder(row.getItem());
                }
            });
            return row;
        });

        return view;
    }

    private TableColumn<Order, String> textColumn(String title,
                                                  double width,
                                                  Function<Order, String> getter,
                                                  String cellStyleClass) {
        TableColumn<Order, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(getter.apply(cell.getValue())));
        if ("cell-number".equals(cellStyleClass)) {
            column.getStyleClass().add("num-column");
        }
        if (cellStyleClass != null) {
            column.setCellFactory(c -> new TableCell<>() {
                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : value);
                    getStyleClass().removeAll("cell-strong", "cell-muted", "cell-number");
                    if (!empty) {
                        getStyleClass().add(cellStyleClass);
                    }
                    setAlignment("cell-number".equals(cellStyleClass) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                }
            });
        }
        return column;
    }

    private TableColumn<Order, Order> dueColumn() {
        TableColumn<Order, Order> column = new TableColumn<>("Due");
        column.setPrefWidth(126);
        column.setMinWidth(122);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Order order, boolean empty) {
                super.updateItem(order, empty);
                if (empty || order == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(Times.SHORT.format(order.getDueAt()));
                setStyle(order.isOverdue()
                        ? "-fx-text-fill: #B91C1C; -fx-font-weight: bold;"
                        : "-fx-text-fill: #475569;");
            }
        });
        return column;
    }

    private TableColumn<Order, OrderStatus> statusColumn() {
        TableColumn<Order, OrderStatus> column = new TableColumn<>("Status");
        column.setPrefWidth(116);
        column.setMinWidth(104);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getStatus()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(OrderStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(StatusBadge.of(status));
            }
        });
        return column;
    }

    private VBox buildQuickPanel() {
        Button logNew = Ui.button("+  Log new order", "primary-button", "quick-primary");
        logNew.setMaxWidth(Double.MAX_VALUE);
        logNew.setOnAction(event -> logNewOrder());

        Button register = Ui.button("Register walk-in client", "secondary-button");
        register.setMaxWidth(Double.MAX_VALUE);
        register.setOnAction(event -> registerWalkIn());

        Button openSelected = Ui.button("Open selected order", "ghost-button");
        openSelected.setMaxWidth(Double.MAX_VALUE);
        openSelected.setOnAction(event -> {
            Order selected = table == null ? null : table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Toast.show(root, "Select an order in the queue first.", Toast.Kind.INFO);
            } else {
                openOrder(selected);
            }
        });

        VBox actions = new VBox(10,
                Ui.label("Quick actions", "card-title"),
                logNew,
                register,
                openSelected);
        actions.getStyleClass().add("card");

        VBox snapshot = new VBox(12,
                Ui.label("Shift snapshot", "card-title"),
                metricRow("Ready for pickup", readyValue, "ok"),
                metricRow("In progress", progressValue, "info"),
                metricRow("Overdue", overdueValue, "danger"));
        snapshot.getStyleClass().add("card");

        VBox panel = new VBox(16, actions, snapshot);
        panel.setPrefWidth(264);
        panel.setMinWidth(264);
        panel.setMaxWidth(264);
        BorderPane.setMargin(panel, new Insets(0, 0, 0, 0));
        return panel;
    }

    private HBox metricRow(String text, Label value, String variant) {
        Label dot = new Label();
        dot.getStyleClass().addAll("metric-dot", "metric-dot-" + variant);
        value.getStyleClass().add("metric-value-" + variant);
        HBox row = new HBox(9, dot, Ui.label(text, "metric-label"), Ui.hSpacer(), value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildFooter() {
        HBox footer = new HBox(12,
                Ui.label("Signed in as " + user.getFullName() + " · " + user.getRole().displayName(), "footer-text"),
                Ui.hSpacer(),
                footerCount);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("footer-bar");
        return footer;
    }

    // ------------------------------------------------------------------ data

    public void refresh() {
        try {
            int flagged = services.orderService().flagOverdueOrders(user.getUsername());
            master.setAll(services.orderService().activeOrders());
            updateSnapshot();
            applyFilters();
            if (flagged > 0) {
                Toast.show(root, flagged + " order(s) crossed the due date — penalties applied.", Toast.Kind.INFO);
            }
        } catch (RuntimeException ex) {
            Toast.show(root, "Could not refresh the queue: " + ex.getMessage(), Toast.Kind.ERROR);
        }
    }

    private void applyQueueFilter(Predicate<Order> predicate) {
        this.queueFilter = predicate;
        applyFilters();
    }

    private void applyFilters() {
        String query = searchField == null || searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);

        filtered.setPredicate(order -> queueFilter.test(order) && matches(order, query));

        queueBadge.setText(String.valueOf(filtered.size()));
        footerCount.setText("Showing " + filtered.size() + " of " + master.size() + " active orders");
        if (table != null) {
            table.refresh();
        }
    }

    private boolean matches(Order order, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return contains(order.getCode(), query)
                || contains(order.getCustomerName(), query)
                || contains(order.getCustomerPhone(), query)
                || contains(order.getServiceName(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void updateSnapshot() {
        readyValue.setText(String.valueOf(count(OrderStatus.READY)));
        progressValue.setText(String.valueOf(count(OrderStatus.IN_PROGRESS)));
        overdueValue.setText(String.valueOf(count(OrderStatus.OVERDUE)));
    }

    private long count(OrderStatus status) {
        return master.stream().filter(order -> order.getStatus() == status).count();
    }

    // ------------------------------------------------------------------ actions

    private void logNewOrder() {
        NewOrderDialog dialog = new NewOrderDialog(nav.stage(), services, user);
        dialog.showAndWait().ifPresent(order -> {
            refresh();
            Toast.show(root, "Order " + order.getCode() + " initialised · due "
                    + Times.SHORT.format(order.getDueAt()), Toast.Kind.SUCCESS);
        });
    }

    private void registerWalkIn() {
        CustomerFormDialog dialog = new CustomerFormDialog(nav.stage(), services);
        dialog.showAndWait().ifPresent(customer ->
                Toast.show(root, "Client " + customer.name() + " added to the directory.", Toast.Kind.SUCCESS));
    }

    private void openOrder(Order order) {
        if (order == null) {
            return;
        }
        OrderProfileDialog dialog = new OrderProfileDialog(nav.stage(), services, user, order);
        dialog.showAndWait();
        refresh();
    }

    private void logout() {
        services.audit().log(user.getUsername(), "LOGOUT", user.getFullName() + " signed out");
        nav.showLogin();
    }

    private String shiftLabel() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) {
            return "morning";
        }
        return hour < 18 ? "afternoon" : "evening";
    }
}
