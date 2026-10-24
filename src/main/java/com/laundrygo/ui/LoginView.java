package com.laundrygo.ui;

import com.laundrygo.model.Role;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.component.BubbleField;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** Frame 1 — the global login portal. */
public final class LoginView {

    private final AppNavigator nav;
    private final AppServices services;

    private final StackPane root = new StackPane();
    private final VBox card = new VBox(14);
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = Ui.button("Sign in", "primary-button", "login-button");
    private final ProgressIndicator busy = new ProgressIndicator();
    private final Label errorLabel = Ui.label("", "error-text");

    public LoginView(AppNavigator nav) {
        this.nav = nav;
        this.services = nav.services();

        root.getStyleClass().add("login-root");

        HBox split = new HBox();
        split.getStyleClass().add("login-split");
        split.getChildren().addAll(buildBrandPanel(), buildFormSide());
        root.getChildren().add(split);

        usernameField.textProperty().addListener((obs, old, value) -> hideError());
        passwordField.textProperty().addListener((obs, old, value) -> hideError());
        passwordField.setOnAction(event -> attemptLogin());
    }

    public Parent getRoot() {
        return root;
    }

    // ------------------------------------------------------------------ layout

    private StackPane buildBrandPanel() {
        Label watermark = new Label("LG");
        watermark.getStyleClass().add("brand-watermark");
        StackPane.setAlignment(watermark, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(watermark, new Insets(0, -8, -46, 0));

        Circle dot = new Circle(120);
        dot.getStyleClass().add("brand-glow");
        StackPane.setAlignment(dot, Pos.TOP_RIGHT);
        StackPane.setMargin(dot, new Insets(-80, -60, 0, 0));

        StackPane mark = new StackPane(Ui.label("LG", "logo-mark-text"));
        mark.getStyleClass().add("logo-mark");
        HBox logoRow = new HBox(12, mark,
                new VBox(2, Ui.label("LaundryGo", "brand-name-light"),
                        Ui.label("SHOP OPERATING SYSTEM", "brand-tag-light")));
        logoRow.setAlignment(Pos.CENTER_LEFT);

        Label headline = Ui.label("Run the counter.\nOwn the numbers.", "brand-headline");
        headline.setWrapText(true);

        Label sub = Ui.label(
                "One desktop workspace for order intake, the active queue, pickup alerts and daily revenue.",
                "brand-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(390);

        VBox content = new VBox(26,
                logoRow,
                new VBox(14, headline, sub),
                Ui.vSpacer(),
                feature("Fast intake", "Log a walk-in order in under thirty seconds with live pricing."),
                feature("Queue at a glance", "Status-coded rows keep pending, in-progress, ready and overdue obvious."),
                feature("Owner analytics", "Daily revenue, overdue flags and a complete audit trail."));
        content.setPadding(new Insets(48, 44, 44, 48));
        content.setMaxWidth(520);

        StackPane panel = new StackPane(BubbleField.hero(), dot, content, watermark);
        panel.getStyleClass().add("brand-panel");
        panel.setPrefWidth(520);
        panel.setMinWidth(440);
        panel.setMaxWidth(560);
        return panel;
    }

    private HBox feature(String title, String caption) {
        Region dot = new Region();
        dot.getStyleClass().add("feature-dot");
        Label titleLabel = Ui.label(title, "feature-title");
        Label captionLabel = Ui.label(caption, "feature-sub");
        captionLabel.setWrapText(true);
        VBox text = new VBox(2, titleLabel, captionLabel);
        HBox row = new HBox(12, dot, text);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        return row;
    }

    private StackPane buildFormSide() {
        card.getStyleClass().add("login-card");
        card.setMaxWidth(430);

        Label kicker = Ui.label("SIGN IN", "kicker");
        Label title = Ui.label("Welcome back", "login-title");
        Label sub = Ui.label("Use your counter or owner account to continue.", "login-sub");
        sub.setWrapText(true);

        usernameField.setPromptText("e.g. staff");
        usernameField.getStyleClass().add("login-field");
        passwordField.setPromptText("Your password");
        passwordField.getStyleClass().add("login-field");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(event -> attemptLogin());

        busy.setPrefSize(16, 16);
        busy.setVisible(false);
        busy.setManaged(false);
        busy.getStyleClass().add("login-spinner");

        errorLabel.setWrapText(true);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        HBox statusRow = new HBox(8, busy, errorLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        HBox leftDivider = Ui.divider();
        HBox rightDivider = Ui.divider();
        HBox.setHgrow(leftDivider, Priority.ALWAYS);
        HBox.setHgrow(rightDivider, Priority.ALWAYS);
        HBox demoLabel = new HBox(10, leftDivider, Ui.label("demo accounts", "divider-label"), rightDivider);
        demoLabel.setAlignment(Pos.CENTER);

        Button staffChip = Ui.button("Staff  ·  staff / staff123", "credential-chip");
        staffChip.setOnAction(event -> fill("staff", "staff123"));
        Button ownerChip = Ui.button("Owner  ·  admin / admin123", "credential-chip");
        ownerChip.setOnAction(event -> fill("admin", "admin123"));
        VBox chips = new VBox(8, staffChip, ownerChip);
        staffChip.setMaxWidth(Double.MAX_VALUE);
        ownerChip.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().addAll(
                kicker,
                new VBox(6, title, sub),
                new VBox(6, Ui.fieldLabel("Username or staff ID"), usernameField),
                new VBox(6, Ui.fieldLabel("Password"), passwordField),
                statusRow,
                loginButton,
                demoLabel,
                chips);

        StackPane side = new StackPane(BubbleField.tint(), card);
        side.getStyleClass().add("login-form-side");
        side.setPadding(new Insets(48));
        HBox.setHgrow(side, Priority.ALWAYS);
        return side;
    }

    // ------------------------------------------------------------------ logic

    private void fill(String username, String password) {
        usernameField.setText(username);
        passwordField.setText(password);
        hideError();
        loginButton.requestFocus();
    }

    private void attemptLogin() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Enter both your username and password.");
            return;
        }

        hideError();
        setBusy(true);

        // Credential check happens off the FX thread so the window never freezes.
        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                Thread.sleep(600); // simulated round-trip to the credential service
                return services.auth().authenticate(username, password).orElse(null);
            }
        };

        task.setOnSucceeded(event -> {
            setBusy(false);
            User user = task.getValue();
            if (user == null) {
                showError("Incorrect credentials. Please try again.");
                passwordField.clear();
                passwordField.requestFocus();
                return;
            }
            if (user.getRole() == Role.ADMIN) {
                nav.showAdminDashboard(user);
            } else {
                nav.showStaffDashboard(user);
            }
        });

        task.setOnFailed(event -> {
            setBusy(false);
            Throwable cause = task.getException();
            showError("Sign-in failed: " + (cause == null || cause.getMessage() == null
                    ? "unexpected error"
                    : cause.getMessage()));
        });

        Thread worker = new Thread(task, "auth-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busyState) {
        loginButton.setDisable(busyState);
        loginButton.setText(busyState ? "Verifying…" : "Sign in");
        usernameField.setDisable(busyState);
        passwordField.setDisable(busyState);
        busy.setVisible(busyState);
        busy.setManaged(busyState);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);

        TranslateTransition shake = new TranslateTransition(Duration.millis(55), card);
        shake.setFromX(0);
        shake.setByX(7);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(event -> card.setTranslateX(0));
        shake.play();

        FadeTransition fade = new FadeTransition(Duration.millis(160), errorLabel);
        fade.setFromValue(0.3);
        fade.setToValue(1);
        fade.play();
    }

    private void hideError() {
        if (errorLabel.isVisible()) {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }
}
