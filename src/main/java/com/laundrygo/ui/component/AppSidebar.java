package com.laundrygo.ui.component;

import com.laundrygo.model.User;
import com.laundrygo.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Dark navigation rail shared by the staff and owner shells. */
public final class AppSidebar extends StackPane {

    private final VBox content = new VBox();
    private final VBox navBox = new VBox(6);
    private final List<Button> navButtons = new ArrayList<>();

    public AppSidebar(String eyebrow, User user, Runnable onLogout) {
        getStyleClass().add("sidebar");
        setPrefWidth(236);
        setMinWidth(236);
        setMaxWidth(236);

        content.getStyleClass().add("sidebar-content");
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        content.setFillWidth(true);

        StackPane mark = new StackPane(Ui.label("LG", "logo-mark-text"));
        mark.getStyleClass().add("logo-mark");
        VBox brandText = new VBox(0,
                Ui.label("LaundryGo", "brand-name"),
                Ui.label(eyebrow, "brand-tag"));
        HBox brand = new HBox(10, mark, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);

        navBox.getStyleClass().add("sidebar-nav");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label avatar = Ui.label(Ui.initials(user.getFullName()), "avatar");
        VBox who = new VBox(1,
                Ui.label(user.getFullName(), "sidebar-user"),
                Ui.label(user.getRole().displayName(), "sidebar-role"));
        HBox chip = new HBox(10, avatar, who);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("user-chip");

        Button logout = Ui.button("Log out", "logout-button");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(event -> onLogout.run());

        VBox footer = new VBox(10, chip, logout);

        content.getChildren().addAll(brand, navBox, spacer, footer);
        getChildren().addAll(BubbleField.rail(), content);
    }

    public void addSection(String title) {
        Label label = Ui.label(title.toUpperCase(), "nav-section");
        navBox.getChildren().add(label);
    }

    /** Adds a nav entry; clicking it highlights the entry and runs the handler. */
    public Button addNavItem(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-item");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> {
            activate(button);
            if (action != null) {
                action.run();
            }
        });
        navBox.getChildren().add(button);
        navButtons.add(button);
        return button;
    }

    public void activate(Button button) {
        for (Button other : navButtons) {
            other.getStyleClass().remove("nav-item-active");
        }
        if (!button.getStyleClass().contains("nav-item-active")) {
            button.getStyleClass().add("nav-item-active");
        }
    }
}
