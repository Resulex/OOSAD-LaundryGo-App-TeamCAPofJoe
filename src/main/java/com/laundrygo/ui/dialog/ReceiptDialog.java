package com.laundrygo.ui.dialog;

import com.laundrygo.model.Receipt;
import com.laundrygo.ui.PrintSupport;
import com.laundrygo.ui.Ui;
import com.laundrygo.util.Money;
import com.laundrygo.util.Times;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Frame — printable customer receipt for a settled order. */
public final class ReceiptDialog extends Dialog<Boolean> {

    private final Receipt receipt;

    public ReceiptDialog(Window owner, Receipt receipt) {
        this.receipt = receipt;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Receipt " + receipt.orderCode());
        setResizable(false);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefWidth(430);

        ButtonType printType = new ButtonType("Print receipt", ButtonBar.ButtonData.LEFT);
        pane.getButtonTypes().addAll(printType, ButtonType.CLOSE);
        pane.setContent(buildPaper());

        Button printButton = (Button) pane.lookupButton(printType);
        printButton.getStyleClass().add("primary-button");
        printButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); // keep the dialog open so the cashier can print again
            PrintSupport.print(getOwner(), "LaundryGo receipt " + receipt.orderCode(), buildPaper());
        });

        setResultConverter(button -> false);
    }

    /** A fresh paper node each call so printing never scales the on-screen copy. */
    private Node buildPaper() {
        Label brand = Ui.label("LaundryGo", "receipt-brand");
        Label tagline = Ui.label("Wash · Dry · Fold · Press", "receipt-note");
        VBox masthead = new VBox(2, brand, tagline);
        masthead.setAlignment(Pos.CENTER);

        Label docTitle = Ui.label("OFFICIAL RECEIPT", "receipt-doc-type");
        Label reference = Ui.label("Receipt for order " + receipt.orderCode(), "receipt-note");
        VBox titleBox = new VBox(3, docTitle, reference);
        titleBox.setAlignment(Pos.CENTER);

        VBox meta = new VBox(6,
                row("Settled", Times.display(receipt.settledAt())),
                row("Client", receipt.clientName()),
                row("Contact", blankToDash(receipt.clientPhone())),
                row("Cashier", blankToDash(receipt.cashier())));

        VBox service = new VBox(6,
                row("Service", receipt.serviceName()),
                row("Billable units", blankToDash(receipt.units())),
                row("Unit price", Money.format(receipt.unitPrice())));

        VBox totals = new VBox(6,
                row("Base cost", Money.format(receipt.baseCost())),
                row("Late fee", Money.format(receipt.lateFee())),
                totalRow("TOTAL", Money.format(receipt.total())),
                row("Amount paid", Money.format(receipt.paid())),
                row("Change", Money.format(receipt.change())));

        VBox payment = new VBox(6,
                row("Payment method", receipt.method() == null ? "—" : receipt.method().displayName()),
                row("Reference", blankToDash(receipt.reference())));

        Label thanks = Ui.label("Thank you for trusting us with your laundry.", "receipt-thanks");
        thanks.setWrapText(true);
        Label footer = Ui.label("Keep this receipt for pickup verification. Unclaimed items past the "
                + "storage window may incur the published penalty.", "receipt-note");
        footer.setWrapText(true);

        VBox paper = new VBox(12,
                masthead,
                Ui.divider(),
                titleBox,
                Ui.divider(),
                meta,
                Ui.divider(),
                service,
                Ui.divider(),
                totals,
                Ui.divider(),
                payment,
                Ui.divider(),
                thanks,
                footer);
        paper.getStyleClass().add("receipt-paper");
        paper.setPrefWidth(350);
        paper.setMaxWidth(350);
        return paper;
    }

    private HBox row(String label, String value) {
        Label name = Ui.label(label, "receipt-line");
        Label text = Ui.label(value, "receipt-strong");
        text.setWrapText(true);
        HBox row = new HBox(12, name, Ui.hSpacer(), text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox totalRow(String label, String value) {
        Label name = Ui.label(label, "receipt-total");
        Label text = Ui.label(value, "receipt-total");
        HBox row = new HBox(12, name, Ui.hSpacer(), text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
