package com.laundrygo.ui;

import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Owns the single Stage and swaps scenes: login → staff counter / owner dashboard. */
public final class AppNavigator {

    private final Stage stage;
    private final AppServices services;

    public AppNavigator(Stage stage, AppServices services) {
        this.stage = stage;
        this.services = services;
    }

    public Stage stage() {
        return stage;
    }

    public AppServices services() {
        return services;
    }

    public void showLogin() {
        show(new LoginView(this).getRoot(), 1120, 740, "LaundryGo · Sign in");
    }

    public void showStaffDashboard(User user) {
        show(new StaffDashboardView(this, user).getRoot(), 1380, 880, "LaundryGo · Counter Desk");
    }

    public void showAdminDashboard(User user) {
        show(new AdminDashboardView(this, user).getRoot(), 1380, 880, "LaundryGo · Shop Overview");
    }

    private void show(Parent root, double width, double height, String title) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(Ui.stylesheet());
        stage.setScene(scene);
        stage.setTitle(title);
        stage.sizeToScene();
        stage.centerOnScreen();
        if (!stage.isShowing()) {
            stage.show();
        }
    }
}
