package com.laundrygo.ui.dialog;

import com.laundrygo.model.Order;
import com.laundrygo.model.OrderStatus;
import com.laundrygo.model.PaymentMethod;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.Ui;
import com.laundrygo.util.Money;
import com.laundrygo.util.Times;
import java.time.LocalDateTime;
import java.util.Locale;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Frame 4 — order profile: read-only details on the left, status tracker and payment panel on the
 * right. Status transitions drive SMS alerts and the penalty controller.
 */
public final class OrderProfileDialog extends Dialog<Boolean> {

    private final AppServices services;
    private final User actor;
    private final Order order;

    private final Label headerBadge = new Label();
    private final ComboBox<OrderStatus> statusBox = new ComboBox<>();
    private final Label statusHint = Ui.label("", "hint");
    private final ProgressIndicator smsSpinner = new ProgressIndicator();
    private final Label smsLabel = new Label();
    private final HBox smsRow = new HBox(8, smsSpinner, smsLabel);

    private final Label baseValue = Ui.label("₱0.00", "money-value");
    private final Label lateValue = Ui.label("₱0.00", "money-value");
    private final Label totalValue = Ui.label("₱0.00", "money-total");
    private final Label paidValue = Ui.label("₱0.00", "money-value");
    private final Label remainingValue = Ui.label("₱0.00", "money-total");

    private final TabPane tabs = new TabPane();
    private final TextField cashAmount = new TextField();
    private final Label changeLabel = Ui.label("", "hint");
    private final TextField gcashRef = new TextField();
    private final TextField gcashAmount = new TextField();
    private final Label paymentError = Ui.label("", "error-text");

    private boolean suppressStatusEvents;
    private boolean paymentRecorded;

    public OrderProfileDialog(Window owner, AppServices services, User actor, Order order) {
        this.services = services;
        this.actor = actor;
        this.order = order;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Order " + order.getCode());
        setResizable(true);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefSize(1040, 720);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setContent(buildContent());

        refreshFinancials();
        updateChangeLabel();
        setResultConverter(button -> paymentRecorded);
    }

    // ------------------------------------------------------------------ layout

    private VBox buildContent() {
        VBox details = buildDetailsCard();
        details.setPrefWidth(330);
        details.setMinWidth(330);
        details.setMaxWidth(330);

        VBox right = new VBox(16, buildStatusCard(), buildFinancialCard(), buildPaymentCard());
        HBox.setHgrow(right, Priority.ALWAYS);
        right.setMaxWidth(Double.MAX_VALUE);

        HBox split = new HBox(18, details, right);
        split.setAlignment(Pos.TOP_LEFT);
        split.setFillHeight(false);
        HBox.setHgrow(right, Priority.ALWAYS);

        VBox body = new VBox(18, buildHeader(), split);
        body.getStyleClass().add("dialog-body");
        return body;
    }

    private HBox buildHeader() {
        Label title = Ui.label("Order " + order.getCode(), "dialog-title");
        Label subtitle = Ui.label(order.getCustomerName() + "  ·  " + order.getServiceName()
                + "  ·  " + order.getWeightLabel(), "dialog-subtitle");

        updateHeaderBadge();

        Label dueChip = Ui.label("Due " + Times.SHORT.format(order.getDueAt()), "due-chip");
        if (order.isOverdue()) {
            dueChip.getStyleClass().add("due-chip-overdue");
        }

        HBox header = new HBox(12, new VBox(3, title, subtitle), Ui.hSpacer(), dueChip, headerBadge);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header");
        return header;
    }

    private VBox buildDetailsCard() {
        String unit = order.getPricingMode() == com.laundrygo.model.PricingMode.PER_KG ? "kg" : "pc";
        VBox card = new VBox(13,
                Ui.label("Order details", "card-title"),
                detail("Client", order.getCustomerName()),
                detail("Contact", order.getCustomerPhone()),
                detail("Service", order.getServiceName()),
                detail("Billable units", order.getWeightLabel()),
                detail("Unit price", Money.format(order.getUnitPrice()) + " / " + unit),
                detail("Logged", Times.display(order.getCreatedAt())),
                detail("Due", Times.display(order.getDueAt())),
                detail("Notes", order.getNotes() == null || order.getNotes().isBlank() ? "—" : order.getNotes()),
                Ui.label("Tip: double-click any queue row to reopen this profile.", "hint"));
        card.getStyleClass().add("card");
        return card;
    }

    private VBox detail(String label, String value) {
        Label name = Ui.label(label, "detail-label");
        Label text = Ui.label(value == null || value.isBlank() ? "—" : value, "detail-value");
        text.setWrapText(true);
        return new VBox(1, name, text);
    }

    private VBox buildStatusCard() {
        VBox card = new VBox(12, Ui.label("Status tracker", "card-title"));

        statusBox.getItems().setAll(OrderStatus.PENDING, OrderStatus.IN_PROGRESS,
                OrderStatus.READY, OrderStatus.OVERDUE);
        statusBox.setValue(order.getStatus() == OrderStatus.COMPLETED ? OrderStatus.READY : order.getStatus());
        statusBox.setMaxWidth(Double.MAX_VALUE);
        statusBox.setDisable(order.getStatus() == OrderStatus.COMPLETED);

        statusBox.valueProperty().addListener((obs, old, value) -> {
            if (suppressStatusEvents || value == null || value == old) {
                return;
            }
            handleStatusChange(value, old);
        });

        statusHint.setWrapText(true);

        smsSpinner.setPrefSize(14, 14);
        smsSpinner.setVisible(false);
        smsSpinner.setManaged(false);
        smsRow.setAlignment(Pos.CENTER_LEFT);
        smsRow.setVisible(false);
        smsRow.setManaged(false);
        smsLabel.setWrapText(true);

        card.getChildren().addAll(statusBox, statusHint, smsRow);
        card.getStyleClass().add("card");

        statusHint.setText(initialStatusHint());
        if (order.getStatus() == OrderStatus.OVERDUE) {
            statusHint.getStyleClass().setAll("hint", "hint-danger");
        }
        return card;
    }

    private String initialStatusHint() {
        return switch (order.getStatus()) {
            case OVERDUE -> "Overdue by " + Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(
                    order.getDueAt().toLocalDate(), java.time.LocalDate.now())) + " day(s) — penalty applied.";
            case READY -> "Ready for pickup — the client is waiting to be notified.";
            case IN_PROGRESS -> "In the wash. Move it to Ready when it is folded and bagged.";
            case COMPLETED -> "Settled and closed.";
            default -> "Logged at the counter and waiting to start.";
        };
    }

    private VBox buildFinancialCard() {
        VBox rows = new VBox(8,
                moneyRow("Base cost", baseValue),
                moneyRow("Late fees", lateValue),
                Ui.divider(),
                moneyRow("Total balance", totalValue),
                moneyRow("Amount paid", paidValue),
                moneyRow("Remaining", remainingValue));
        VBox card = new VBox(10, Ui.label("Financial breakdown", "card-title"), rows);
        card.getStyleClass().add("card");
        return card;
    }

    private HBox moneyRow(String label, Label value) {
        HBox row = new HBox(10, Ui.label(label, "money-label"), Ui.hSpacer(), value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildPaymentCard() {
        tabs.getStyleClass().add("lg-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setMinHeight(168);

        cashAmount.setPromptText("e.g. 500.00");
        cashAmount.textProperty().addListener((obs, old, value) -> updateChangeLabel());
        Button exact = Ui.button("Use exact balance", "link-button");
        exact.setOnAction(event -> cashAmount.setText(Money.plain(order.getBalance())));
        HBox changeRow = new HBox(8, changeLabel, Ui.hSpacer(), exact);
        changeRow.setAlignment(Pos.CENTER_LEFT);

        Tab cashTab = new Tab("Cash", new VBox(8,
                Ui.fieldLabel("Amount received"), cashAmount, changeRow));
        cashTab.setClosable(false);

        gcashRef.setPromptText("e.g. GC-882134");
        gcashAmount.setPromptText("Amount sent");
        Tab gcashTab = new Tab("GCash", new VBox(8,
                Ui.fieldLabel("GCash reference number"), gcashRef,
                Ui.fieldLabel("Amount"), gcashAmount));
        gcashTab.setClosable(false);

        tabs.getTabs().addAll(cashTab, gcashTab);

        paymentError.setWrapText(true);
        paymentError.setManaged(false);
        paymentError.setVisible(false);

        Button pay = Ui.button("Process payment", "primary-button");
        pay.setMaxWidth(Double.MAX_VALUE);
        pay.setDisable(order.getStatus() == OrderStatus.COMPLETED);
        pay.setOnAction(event -> processPayment());

        VBox card = new VBox(10,
                Ui.label("Payment", "card-title"),
                tabs,
                paymentError,
                pay,
                Ui.label("Settling the balance completes the order and removes it from the active queue.",
                        "hint"));
        card.getStyleClass().add("card");
        return card;
    }

    // ------------------------------------------------------------------ behaviour

    private void updateHeaderBadge() {
        OrderStatus status = order.getStatus();
        headerBadge.setText(status.displayName());
        headerBadge.getStyleClass().setAll("status-pill", "status-" + status.cssClass());
    }

    private void refreshFinancials() {
        baseValue.setText(Money.format(order.getBaseCost()));
        lateValue.setText(Money.format(order.getLateFee()));
        totalValue.setText(Money.format(order.getTotal()));
        paidValue.setText(Money.format(order.getAmountPaid()));
        remainingValue.setText(Money.format(order.getBalance()));

        if (gcashAmount.getText() == null || gcashAmount.getText().isBlank() || !gcashAmount.isFocused()) {
            gcashAmount.setText(Money.plain(order.getBalance()));
        }
        updateChangeLabel();
    }

    private void updateChangeLabel() {
        double balance = order.getBalance();
        Double received = Money.parse(cashAmount.getText());
        if (received == null || received <= 0) {
            changeLabel.setText("Change due appears here.");
            changeLabel.getStyleClass().setAll("hint");
            return;
        }
        double change = received - balance;
        if (change < -0.005) {
            changeLabel.setText("Short by " + Money.format(-change));
            changeLabel.getStyleClass().setAll("hint", "hint-danger");
        } else {
            changeLabel.setText("Change due: " + Money.format(change));
            changeLabel.getStyleClass().setAll("hint", "hint-ok");
        }
    }

    private void handleStatusChange(OrderStatus target, OrderStatus previous) {
        try {
            services.orderService().changeStatus(order, target, actor);
        } catch (RuntimeException ex) {
            showPaymentError(ex.getMessage());
            suppressStatusEvents = true;
            statusBox.setValue(previous);
            suppressStatusEvents = false;
            return;
        }

        updateHeaderBadge();
        refreshFinancials();
        statusHint.getStyleClass().setAll("hint");

        switch (target) {
            case OVERDUE -> {
                long days = services.penalty().chargeableDays(order.getDueAt(), LocalDateTime.now());
                statusHint.setText(days + " billable day(s) past the grace period · "
                        + Money.format(services.penalty().perDay()) + "/day → penalty "
                        + Money.format(order.getLateFee()) + " applied.");
                statusHint.getStyleClass().setAll("hint", "hint-danger");
            }
            case READY -> {
                statusHint.setText("The client will be alerted that the order is ready for pickup.");
                sendReadySms();
            }
            default -> statusHint.setText("Status set to " + target.displayName() + ".");
        }
    }

    private void sendReadySms() {
        smsRow.setVisible(true);
        smsRow.setManaged(true);
        smsSpinner.setVisible(true);
        smsSpinner.setManaged(true);
        smsLabel.setText("Contacting the SMS gateway…");
        smsLabel.getStyleClass().setAll("hint");
        statusBox.setDisable(true);

        Task<String> task = services.sms().readyForPickup(order);

        task.setOnSucceeded(event -> {
            statusBox.setDisable(false);
            smsSpinner.setVisible(false);
            smsSpinner.setManaged(false);
            smsLabel.setText(task.getValue());
            smsLabel.getStyleClass().setAll("sms-ok");
            services.audit().log(actor.getUsername(), "SMS_SENT",
                    "Ready-for-pickup alert sent for " + order.getCode());
        });

        task.setOnFailed(event -> {
            statusBox.setDisable(false);
            smsSpinner.setVisible(false);
            smsSpinner.setManaged(false);
            smsLabel.setText("SMS gateway unavailable — the client was not notified.");
            smsLabel.getStyleClass().setAll("sms-fail");
        });

        Thread worker = new Thread(task, "sms-gateway");
        worker.setDaemon(true);
        worker.start();
    }

    private void processPayment() {
        paymentError.setVisible(false);
        paymentError.setManaged(false);

        if (order.getStatus() == OrderStatus.COMPLETED) {
            showPaymentError("This order is already settled.");
            return;
        }

        double balance = order.getBalance();
        PaymentMethod method;
        String reference = null;
        double amount;

        boolean cashSelected = tabs.getSelectionModel().getSelectedIndex() == 0;
        if (cashSelected) {
            method = PaymentMethod.CASH;
            Double received = Money.parse(cashAmount.getText());
            if (received == null || received <= 0) {
                showPaymentError("Enter the cash amount received.");
                return;
            }
            if (balance > 0 && received + 0.005 < balance) {
                showPaymentError("Received " + Money.format(received) + " — short of the "
                        + Money.format(balance) + " balance.");
                return;
            }
            amount = balance > 0 ? balance : received;
        } else {
            method = PaymentMethod.GCASH;
            reference = gcashRef.getText() == null ? "" : gcashRef.getText().trim().toUpperCase(Locale.ROOT);
            if (!reference.matches("[A-Z0-9\\-]{6,}")) {
                showPaymentError("Enter the GCash reference number (6+ letters or digits).");
                return;
            }
            Double entered = Money.parse(gcashAmount.getText());
            if (entered == null || entered <= 0) {
                showPaymentError("Enter the amount paid through GCash.");
                return;
            }
            if (balance > 0 && entered + 0.005 < balance) {
                showPaymentError("GCash amount must cover the " + Money.format(balance) + " balance.");
                return;
            }
            amount = balance > 0 ? balance : entered;
        }

        try {
            services.orderService().processPayment(order, method, amount, reference, actor);
            paymentRecorded = true;
            close();
        } catch (RuntimeException ex) {
            showPaymentError(ex.getMessage());
        }
    }

    private void showPaymentError(String message) {
        paymentError.setText(message);
        paymentError.setManaged(true);
        paymentError.setVisible(true);
    }
}
