package com.laundrygo;

import com.laundrygo.db.Database;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.AppNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

/** The JavaFX application class: boots the database, services and the login scene. */
public final class DesktopApp extends Application {

    @Override
    public void start(Stage stage) {
        Database.initialize();

        AppServices services = new AppServices();
        AppNavigator navigator = new AppNavigator(stage, services);

        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        navigator.showLogin();
    }
}
