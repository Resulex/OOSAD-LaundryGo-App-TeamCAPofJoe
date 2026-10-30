package com.laundrygo.ui;

import java.util.Collections;
import java.util.List;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

/**
 * Sends printable JavaFX nodes to the system print dialog.
 *
 * <p>Each page is laid out in an unattached scene first so the design-system stylesheet applies,
 * then scaled down when it is wider or taller than the printable area. When the machine has no
 * printer configured (common on demo laptops) the call reports it instead of throwing.</p>
 */
public final class PrintSupport {

    private PrintSupport() {
    }

    public static boolean print(Window owner, String jobName, Node page) {
        return print(owner, jobName, Collections.singletonList(page));
    }

    public static boolean print(Window owner, String jobName, List<Node> pages) {
        if (pages == null || pages.isEmpty()) {
            return false;
        }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Ui.info(owner, "No printer available",
                    "No printer is configured on this machine. Connect a printer (or add a virtual "
                            + "PDF printer) and try again.");
            return false;
        }
        if (!job.showPrintDialog(owner)) {
            return false;
        }
        job.getJobSettings().setJobName(jobName);
        PageLayout layout = job.getJobSettings().getPageLayout();

        boolean printed = true;
        for (Node page : pages) {
            printed &= job.printPage(prepare(page, layout));
        }
        job.endJob();

        if (!printed) {
            Ui.error(owner, "Printing failed",
                    "The document was not accepted by the printer. Check the printer queue and try again.");
        }
        return printed;
    }

    private static Group prepare(Node content, PageLayout layout) {
        Group wrapper = new Group(content);
        Scene scene = new Scene(wrapper);
        scene.getStylesheets().add(Ui.stylesheet());
        wrapper.applyCss();
        wrapper.layout();

        double width = content.getLayoutBounds().getWidth();
        double height = content.getLayoutBounds().getHeight();
        double scale = Math.min(1.0, Math.min(
                layout.getPrintableWidth() / Math.max(width, 1),
                layout.getPrintableHeight() / Math.max(height, 1)));
        if (scale < 1.0) {
            wrapper.getTransforms().add(new Scale(scale, scale));
        }
        return wrapper;
    }
}
