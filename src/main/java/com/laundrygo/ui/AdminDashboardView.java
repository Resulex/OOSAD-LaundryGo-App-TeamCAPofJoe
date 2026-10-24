package com.laundrygo.ui;

import com.laundrygo.model.AuditEntry;
import com.laundrygo.model.Payment;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.component.AppSidebar;
import com.laundrygo.ui.component.StatCard;
import com.laundrygo.ui.component.Toast;
import com.laundrygo.ui.dialog.PenaltySettingsDialog;
import com.laundrygo.ui.dialog.RevenueReportDialog;
import com.laundrygo.ui.dialog.ServiceTypesDialog;
import com.laundrygo.ui.dialog.StaffAccountsDialog;
import com.laundrygo.util.Money;
import com.laundrygo.util.Times;
import java.time.LocalDate;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Frame 5 — the shop owner's analytics and configuration console. */
public final class AdminDashboardView {

    private final AppNavigator nav;
    private final AppServices services;
    private final User user;

    private final StackPane root = new StackPane();

    private final StatCard revenueCard =
            new StatCard("Daily revenue", "Settled transactions today", "stat-card-primary");
    private final StatCard activeCard =
            new StatCard("Active orders", "Orders still on the counter queue", "stat-card-neutral");
    private final StatCard overdueCard =
            new StatCard("Flagged overdue", "Past due, penalties applied", "stat-card-danger");

    private final TableView<AuditEntry> auditTable = new TableView<>();
    private final TableView<Payment> paymentTable = new TableView<>();

    private AppSidebar sidebar;
    private Button overviewButton;

    public AdminDashboardView(AppNavigator nav, User user) {
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
        content.setCenter(buildBody());
        content.setBottom(buildFooter());

        shell.setCenter(content);
        root.getChildren().add(shell);
    }

    private AppSidebar buildSidebar() {
        sidebar = new AppSidebar("Owner console", user, this::logout);

        overviewButton = sidebar.addNavItem("Overview", this::refresh);
        sidebar.activate(overviewButton);

        sidebar.addSection("Reports");
        sidebar.addNavItem("Generate revenue report", this::openRevenueReport);

        sidebar.addSection("Configuration");
        sidebar.addNavItem("Configure penalty rates", this::openPenaltySettings);
        sidebar.addNavItem("Manage staff accounts", this::openStaffAccounts);
        sidebar.addNavItem("Define service types", this::openServiceTypes);

        return sidebar;
    }

    private VBox buildHeader() {
        Label title = Ui.label("Shop overview", "page-title");
        Label subtitle = Ui.label(Times.HEADER_DATE.format(LocalDate.now())
                + "  ·  Live totals from the counter database", "page-subtitle");

        Button refreshButton = Ui.button("Refresh metrics", "secondary-button");
        refreshButton.setOnAction(event -> {
            refresh();
            Toast.show(root, "Metrics recalculated.", Toast.Kind.INFO);
        });

        HBox row = new HBox(16, new VBox(3, title, subtitle), Ui.hSpacer(), refreshButton);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(row);
        header.getStyleClass().add("page-header");
        return header;
    }

    private VBox buildBody() {
        HBox metrics = new HBox(16, revenueCard, activeCard, overdueCard);
        metrics.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(revenueCard, Priority.ALWAYS);
        HBox.setHgrow(activeCard, Priority.ALWAYS);
        HBox.setHgrow(overdueCard, Priority.ALWAYS);

        buildAuditTable();
        buildPaymentTable();

        Tab auditTab = new Tab("Audit log", auditTable);
        auditTab.setClosable(false);
        Tab paymentsTab = new Tab("Today's transactions", paymentTable);
        paymentsTab.setClosable(false);

        TabPane tabs = new TabPane(auditTab, paymentsTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("lg-tabs");
        tabs.setMinHeight(0);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox activityCard = new VBox(12,
                new HBox(10, Ui.label("Recent activity", "card-title"), Ui.hSpacer(),
                        Ui.label("Newest first", "hint")),
                tabs);
        activityCard.getStyleClass().add("card");
        VBox.setVgrow(activityCard, Priority.ALWAYS);

        VBox body = new VBox(18, metrics, activityCard);
        VBox.setVgrow(activityCard, Priority.ALWAYS);
        BorderPane.setMargin(body, new Insets(0, 0, 0, 0));
        return body;
    }

    private void buildAuditTable() {
        auditTable.getStyleClass().add("lg-table");
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auditTable.setPlaceholder(Ui.label("No activity recorded yet.", "table-empty"));

        TableColumn<AuditEntry, String> time = new TableColumn<>("Timestamp");
        time.setPrefWidth(140);
        time.setMinWidth(120);
        time.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(Times.shortDisplay(cell.getValue().at())));

        TableColumn<AuditEntry, String> actor = new TableColumn<>("Actor");
        actor.setPrefWidth(110);
        actor.setMinWidth(80);
        actor.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().actor()));

        TableColumn<AuditEntry, String> action = new TableColumn<>("Action");
        action.setPrefWidth(150);
        action.setMinWidth(120);
        action.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                friendlyAction(cell.getValue().action())));

        TableColumn<AuditEntry, String> detail = new TableColumn<>("Detail");
        detail.setPrefWidth(420);
        detail.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().detail()));

        auditTable.getColumns().addAll(time, actor, action, detail);
        auditTable.setMinHeight(0);
    }

    private void buildPaymentTable() {
        paymentTable.getStyleClass().add("lg-table");
        paymentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        paymentTable.setPlaceholder(Ui.label("No payments recorded today.", "table-empty"));

        TableColumn<Payment, String> time = new TableColumn<>("Time");
        time.setPrefWidth(110);
        time.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().at() == null ? "—" : cell.getValue().at().format(Times.CLOCK)));

        TableColumn<Payment, String> code = new TableColumn<>("Order");
        code.setPrefWidth(110);
        code.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().orderCode()));

        TableColumn<Payment, String> client = new TableColumn<>("Client");
        client.setPrefWidth(180);
        client.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().customerName()));

        TableColumn<Payment, String> method = new TableColumn<>("Method");
        method.setPrefWidth(90);
        method.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().method().displayName()));

        TableColumn<Payment, String> reference = new TableColumn<>("Reference");
        reference.setPrefWidth(140);
        reference.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().reference() == null || cell.getValue().reference().isBlank()
                        ? "—"
                        : cell.getValue().reference()));

        TableColumn<Payment, String> amount = new TableColumn<>("Amount");
        amount.setPrefWidth(120);
        amount.getStyleClass().add("num-column");
        amount.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                Money.format(cell.getValue().amount())));

        paymentTable.getColumns().addAll(time, code, client, method, reference, amount);
        paymentTable.setMinHeight(0);
    }

    /** STATUS_CHANGED -> "Status changed" for readability in the audit table. */
    private String friendlyAction(String action) {
        if (action == null || action.isBlank()) {
            return "—";
        }
        String text = action.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private HBox buildFooter() {
        HBox footer = new HBox(12,
                Ui.label("Signed in as " + user.getFullName() + " · " + user.getRole().displayName(),
                        "footer-text"),
                Ui.hSpacer(),
                Ui.label("LaundryGo · owner console", "footer-text"));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("footer-bar");
        return footer;
    }

    // ------------------------------------------------------------------ data

    public void refresh() {
        try {
            revenueCard.setValue(Money.format(services.reports().revenueToday()));
            activeCard.setValue(String.valueOf(services.reports().activeOrders()));
            overdueCard.setValue(String.valueOf(services.reports().overdueOrders()));
            auditTable.getItems().setAll(services.reports().recentActivity(150));
            paymentTable.getItems().setAll(services.reports().todayPayments());
        } catch (RuntimeException ex) {
            Toast.show(root, "Could not load metrics: " + ex.getMessage(), Toast.Kind.ERROR);
        }
    }

    // ------------------------------------------------------------------ actions

    private void openRevenueReport() {
        new RevenueReportDialog(nav.stage(), services).showAndWait();
        refresh();
        sidebar.activate(overviewButton);
    }

    private void openPenaltySettings() {
        PenaltySettingsDialog dialog = new PenaltySettingsDialog(nav.stage(), services, user);
        dialog.showAndWait().filter(Boolean::booleanValue).ifPresent(saved -> {
            refresh();
            Toast.show(root, "Penalty configuration updated.", Toast.Kind.SUCCESS);
        });
        sidebar.activate(overviewButton);
    }

    private void openStaffAccounts() {
        new StaffAccountsDialog(nav.stage(), services, user).showAndWait();
        refresh();
        sidebar.activate(overviewButton);
    }

    private void openServiceTypes() {
        new ServiceTypesDialog(nav.stage(), services, user).showAndWait();
        refresh();
        sidebar.activate(overviewButton);
    }

    private void logout() {
        services.audit().log(user.getUsername(), "LOGOUT", user.getFullName() + " signed out");
        nav.showLogin();
    }
}
