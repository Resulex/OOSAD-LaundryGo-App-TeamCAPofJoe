package com.laundrygo;

import com.laundrygo.db.Database;
import com.laundrygo.model.Order;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.AdminDashboardView;
import com.laundrygo.ui.AppNavigator;
import com.laundrygo.ui.LoginView;
import com.laundrygo.ui.StaffDashboardView;
import com.laundrygo.ui.Ui;
import com.laundrygo.ui.dialog.NewOrderDialog;
import com.laundrygo.ui.dialog.OrderProfileDialog;
import com.laundrygo.ui.dialog.PenaltySettingsDialog;
import com.laundrygo.ui.dialog.RevenueReportDialog;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Development helper: renders every frame to /tmp so the layout can be reviewed without driving
 * the UI by hand. Not part of the shipped application.
 */
public final class UiSnapshot {

    private static AppServices services;
    private static Stage owner;

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                capture();
            } catch (Throwable ex) {
                ex.printStackTrace();
            } finally {
                Platform.exit();
                done.countDown();
            }
        });
        done.await();
    }

    private static void capture() throws Exception {
        Database.initialize();
        services = new AppServices();
        owner = new Stage();

        AppNavigator navigator = new AppNavigator(owner, services);
        User staff = services.auth().authenticate("staff", "staff123").orElseThrow();
        User admin = services.auth().authenticate("admin", "admin123").orElseThrow();

        renderScene(new LoginView(navigator).getRoot(), 1120, 740, "/tmp/lg-1-login.png");
        renderScene(new StaffDashboardView(navigator, staff).getRoot(), 1380, 880, "/tmp/lg-2-staff.png");

        // The owner window keeps a real scene (dialogs require one) and doubles as the admin shot.
        Parent adminRoot = new AdminDashboardView(navigator, admin).getRoot();
        Scene adminScene = new Scene(adminRoot, 1380, 880);
        adminScene.getStylesheets().add(Ui.stylesheet());
        owner.setScene(adminScene);
        owner.show();
        adminRoot.applyCss();
        adminRoot.layout();
        write(adminScene.snapshot(null), "/tmp/lg-5-admin.png");

        renderDialog(new NewOrderDialog(owner, services, staff), "/tmp/lg-3-new-order.png");

        Order order = services.orderService().activeOrders().get(0);
        renderDialog(new OrderProfileDialog(owner, services, staff, order), "/tmp/lg-4-profile.png");

        renderDialog(new PenaltySettingsDialog(owner, services, admin), "/tmp/lg-6-penalty.png");
        renderDialog(new RevenueReportDialog(owner, services), "/tmp/lg-7-report.png");

        owner.close();
        System.out.println("SNAPSHOTS WRITTEN");
    }

    private static void renderScene(Parent root, double width, double height, String path)
            throws IOException {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(Ui.stylesheet());
        root.applyCss();
        root.layout();
        write(scene.snapshot(null), path);
    }

    private static void renderDialog(Dialog<?> dialog, String path) throws IOException {
        dialog.show();
        DialogPane pane = dialog.getDialogPane();
        pane.applyCss();
        pane.layout();
        Scene scene = pane.getScene();
        write(scene.snapshot(null), path);
        dialog.close();
    }

    private static void write(WritableImage image, String path) throws IOException {
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(path));
        System.out.println("wrote " + path);
    }
}
