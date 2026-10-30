package com.laundrygo.ui.dialog;

import com.laundrygo.model.Payment;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.PrintSupport;
import com.laundrygo.ui.Ui;
import com.laundrygo.util.Money;
import com.laundrygo.util.Times;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Owner report: revenue for any date range, with CSV export. */
public final class RevenueReportDialog extends Dialog<Boolean> {

    private final AppServices services;
    private final ObservableList<Payment> data = FXCollections.observableArrayList();
    private final TableView<Payment> table = new TableView<>(data);
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final Label totalLabel = Ui.label("₱0.00", "stat-value-small");
    private final Label countLabel = Ui.label("", "hint");

    public RevenueReportDialog(Window owner, AppServices services) {
        this.services = services;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Revenue report");
        setResizable(true);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefSize(920, 640);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setContent(buildContent());

        load();
    }

    private VBox buildContent() {
        Label title = Ui.label("Revenue report", "dialog-title");
        Label subtitle = Ui.label("Every settled transaction in the selected date range.", "dialog-subtitle");

        from.setValue(LocalDate.now().minusDays(7));
        to.setValue(LocalDate.now());
        from.setOnAction(event -> load());
        to.setOnAction(event -> load());

        Button run = Ui.button("Run report", "primary-button");
        run.setOnAction(event -> load());

        Button export = Ui.button("Export CSV", "secondary-button");
        export.setOnAction(event -> exportCsv());

        Button print = Ui.button("Print report", "ghost-button");
        print.setOnAction(event -> printReport());

        HBox filters = new HBox(12,
                new VBox(6, Ui.fieldLabel("From"), from),
                new VBox(6, Ui.fieldLabel("To"), to),
                new VBox(6, Ui.fieldLabel(""), run),
                Ui.hSpacer(),
                new VBox(6, Ui.fieldLabel("Total collected"), totalLabel));
        filters.setAlignment(Pos.BOTTOM_LEFT);

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox card = new VBox(12, table, countLabel);
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);

        HBox actions = new HBox(10, Ui.hSpacer(), print, export);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(16, new VBox(4, title, subtitle), filters, card, actions);
        VBox.setVgrow(card, Priority.ALWAYS);
        body.getStyleClass().add("dialog-body");
        return body;
    }

    private void buildTable() {
        table.getStyleClass().add("lg-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.label("No transactions in this range.", "table-empty"));

        TableColumn<Payment, String> time = new TableColumn<>("Date & time");
        time.setPrefWidth(180);
        time.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(Times.display(cell.getValue().at())));

        TableColumn<Payment, String> order = new TableColumn<>("Order");
        order.setPrefWidth(110);
        order.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().orderCode()));

        TableColumn<Payment, String> client = new TableColumn<>("Client");
        client.setPrefWidth(190);
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

        table.getColumns().addAll(time, order, client, method, reference, amount);
    }

    private void load() {
        LocalDate fromDate = from.getValue() == null ? LocalDate.now().minusDays(7) : from.getValue();
        LocalDate toDate = to.getValue() == null ? LocalDate.now() : to.getValue();
        if (toDate.isBefore(fromDate)) {
            LocalDate swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }

        List<Payment> rows = services.reports().paymentsBetween(fromDate, toDate);
        data.setAll(rows);
        double total = rows.stream().mapToDouble(Payment::amount).sum();
        totalLabel.setText(Money.format(total));
        countLabel.setText(rows.size() + " transaction(s) between " + fromDate + " and " + toDate);
    }

    // ------------------------------------------------------------------ printing

    private void printReport() {
        List<Payment> rows = new ArrayList<>(data);
        if (rows.isEmpty()) {
            Ui.info(getOwner(), "Nothing to print", "Run a report with at least one transaction first.");
            return;
        }
        LocalDate fromDate = from.getValue() == null ? LocalDate.now().minusDays(7) : from.getValue();
        LocalDate toDate = to.getValue() == null ? LocalDate.now() : to.getValue();
        String range = fromDate + "  to  " + toDate;
        double total = rows.stream().mapToDouble(Payment::amount).sum();

        int perPage = 24;
        int pageCount = (rows.size() + perPage - 1) / perPage;
        List<Node> pages = new ArrayList<>(pageCount);
        for (int index = 0; index < pageCount; index++) {
            int start = index * perPage;
            int end = Math.min(rows.size(), start + perPage);
            pages.add(buildPrintPage(rows.subList(start, end), range, total, rows.size(),
                    index + 1, pageCount));
        }
        PrintSupport.print(getOwner(), "LaundryGo revenue report", pages);
    }

    /** One printed page: repeated header, at most {@code perPage} rows and a page footer. */
    private Node buildPrintPage(List<Payment> slice, String range, double total, int count,
                                int page, int pages) {
        VBox table = new VBox(4, printLine(true,
                "Date & time", "Order", "Client", "Method", "Reference", "Amount"));
        for (Payment payment : slice) {
            table.getChildren().add(printLine(false,
                    Times.display(payment.at()),
                    payment.orderCode(),
                    payment.customerName(),
                    payment.method().displayName(),
                    payment.reference() == null || payment.reference().isBlank() ? "—" : payment.reference(),
                    Money.format(payment.amount())));
        }

        Label footer = Ui.label("Generated " + Times.display(LocalDateTime.now())
                + "   ·   page " + page + " of " + pages, "print-meta");

        VBox sheet = new VBox(12,
                new VBox(3,
                        Ui.label("LaundryGo — revenue report", "print-title"),
                        Ui.label(range + "   ·   " + count + " transaction(s)   ·   total "
                                + Money.format(total), "print-meta")),
                Ui.divider(),
                table,
                Ui.divider(),
                footer);
        sheet.getStyleClass().add("print-page");
        sheet.setPadding(new Insets(30, 34, 30, 34));
        sheet.setPrefWidth(700);
        return sheet;
    }

    private HBox printLine(boolean header, String... cells) {
        double[] widths = {150, 78, 168, 70, 120, 96};
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < cells.length; i++) {
            Label label = Ui.label(cells[i], header ? "print-head" : "print-cell");
            label.setPrefWidth(widths[i]);
            label.setMinWidth(widths[i]);
            label.setMaxWidth(widths[i]);
            if (i == cells.length - 1) {
                label.setAlignment(Pos.CENTER_RIGHT);
            }
            row.getChildren().add(label);
        }
        return row;
    }

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export revenue report");
        chooser.setInitialFileName("laundrygo-revenue-" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showSaveDialog(getOwner());
        if (file == null) {
            return;
        }

        StringBuilder csv = new StringBuilder("Timestamp,Order,Client,Method,Reference,Amount\n");
        for (Payment payment : data) {
            csv.append(quote(Times.display(payment.at()))).append(',')
                    .append(quote(payment.orderCode())).append(',')
                    .append(quote(payment.customerName())).append(',')
                    .append(quote(payment.method().displayName())).append(',')
                    .append(quote(payment.reference() == null ? "" : payment.reference())).append(',')
                    .append(Money.plain(payment.amount())).append('\n');
        }

        try {
            Files.writeString(file.toPath(), csv.toString(), StandardCharsets.UTF_8);
            Ui.info(getOwner(), "Report exported", "Saved to " + file.getAbsolutePath());
        } catch (IOException ex) {
            Ui.error(getOwner(), "Export failed", ex.getMessage());
        }
    }

    private String quote(String value) {
        return '"' + (value == null ? "" : value.replace("\"", "\"\"")) + '"';
    }
}
