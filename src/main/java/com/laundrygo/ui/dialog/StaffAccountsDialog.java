package com.laundrygo.ui.dialog;

import com.laundrygo.model.Role;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import com.laundrygo.ui.Ui;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Owner configuration: staff accounts and access. */
public final class StaffAccountsDialog extends Dialog<Boolean> {

    private final AppServices services;
    private final User actor;

    private final ObservableList<User> data = FXCollections.observableArrayList();
    private final TableView<User> table = new TableView<>(data);

    private final TextField fullNameField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final ComboBox<Role> roleBox = new ComboBox<>();
    private final Label error = Ui.label("", "error-text");

    private User selected;
    private boolean changed;

    public StaffAccountsDialog(Window owner, AppServices services, User actor) {
        this.services = services;
        this.actor = actor;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Staff accounts");
        setResizable(true);

        DialogPane pane = getDialogPane();
        Ui.styleDialog(pane);
        pane.setPrefSize(900, 620);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setContent(buildContent());

        reload();
        setResultConverter(button -> changed);
    }

    private VBox buildContent() {
        Label title = Ui.label("Staff accounts", "dialog-title");
        Label subtitle = Ui.label("Create counter accounts and control who can sign in.", "dialog-subtitle");

        buildTable();

        fullNameField.setPromptText("e.g. Mika Santos");
        usernameField.setPromptText("e.g. mika");
        passwordField.setPromptText("Minimum 6 characters");

        roleBox.getItems().setAll(Role.values());
        roleBox.setValue(Role.STAFF);
        roleBox.setMaxWidth(Double.MAX_VALUE);
        roleBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Role role) {
                return role == null ? "" : role.displayName();
            }

            @Override
            public Role fromString(String text) {
                return Role.fromDb(text);
            }
        });

        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);

        Button create = Ui.button("Create account", "primary-button");
        create.setOnAction(event -> createAccount());

        Button reset = Ui.button("Reset password", "ghost-button");
        reset.setOnAction(event -> resetPassword());

        Button toggle = Ui.button("Activate / deactivate", "ghost-button");
        toggle.setOnAction(event -> toggleActive());

        VBox form = new VBox(10,
                Ui.label("Account editor", "section-title"),
                new VBox(6, Ui.fieldLabel("Full name"), fullNameField),
                new VBox(6, Ui.fieldLabel("Username"), usernameField),
                new VBox(6, Ui.fieldLabel("Password"), passwordField),
                new VBox(6, Ui.fieldLabel("Role"), roleBox),
                error,
                create,
                new HBox(8, reset, toggle));
        form.getStyleClass().add("card");
        form.setPrefWidth(310);
        form.setMinWidth(310);
        ((HBox) form.getChildren().get(form.getChildren().size() - 1)).setAlignment(Pos.CENTER_LEFT);

        VBox tableCard = new VBox(10, Ui.label("Accounts", "card-title"), table);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox.setHgrow(tableCard, Priority.ALWAYS);

        HBox split = new HBox(16, tableCard, form);
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox body = new VBox(16, new VBox(4, title, subtitle), split);
        body.getStyleClass().add("dialog-body");
        return body;
    }

    private void buildTable() {
        table.getStyleClass().add("lg-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.label("No accounts yet.", "table-empty"));

        TableColumn<User, String> name = new TableColumn<>("Full name");
        name.setPrefWidth(200);
        name.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getFullName()));

        TableColumn<User, String> username = new TableColumn<>("Username");
        username.setPrefWidth(150);
        username.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getUsername()));

        TableColumn<User, String> role = new TableColumn<>("Role");
        role.setPrefWidth(150);
        role.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().getRole().displayName()));

        TableColumn<User, String> status = new TableColumn<>("Status");
        status.setPrefWidth(110);
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().isActive() ? "Active" : "Disabled"));

        table.getColumns().addAll(name, username, role, status);

        table.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    selected = row.getItem();
                    fullNameField.setText(selected.getFullName());
                    usernameField.setText(selected.getUsername());
                    roleBox.setValue(selected.getRole());
                    passwordField.clear();
                    hideError();
                }
            });
            return row;
        });
    }

    private void reload() {
        data.setAll(services.users().findAll());
    }

    private void createAccount() {
        String fullName = fullNameField.getText() == null ? "" : fullNameField.getText().trim();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (fullName.length() < 2) {
            showError("Enter the staff member's full name.");
            return;
        }
        if (!username.matches("[A-Za-z0-9._-]{3,24}")) {
            showError("Username must be 3–24 characters (letters, numbers, . _ -).");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }
        if (data.stream().anyMatch(user -> user.getUsername().equalsIgnoreCase(username))) {
            showError("That username is already taken.");
            return;
        }

        try {
            services.users().create(fullName, username, password, roleBox.getValue());
            services.audit().log(actor.getUsername(), "ACCOUNT_CREATED",
                    fullName + " (" + username + ") as " + roleBox.getValue().displayName());
            changed = true;
            reload();
            fullNameField.clear();
            usernameField.clear();
            passwordField.clear();
            hideError();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void resetPassword() {
        if (selected == null) {
            showError("Select an account in the table first.");
            return;
        }
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (password.length() < 6) {
            showError("Enter a new password (6+ characters) in the password field first.");
            return;
        }
        try {
            services.users().resetPassword(selected.getId(), password);
            services.audit().log(actor.getUsername(), "PASSWORD_RESET",
                    "Password reset for " + selected.getUsername());
            changed = true;
            passwordField.clear();
            hideError();
            Ui.info(getOwner(), "Password updated",
                    "The new password for " + selected.getUsername() + " is active immediately.");
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void toggleActive() {
        if (selected == null) {
            showError("Select an account in the table first.");
            return;
        }
        if (selected.getId() == actor.getId()) {
            showError("You cannot disable the account you are signed in with.");
            return;
        }
        boolean target = !selected.isActive();
        try {
            services.users().setActive(selected.getId(), target);
            services.audit().log(actor.getUsername(), "ACCOUNT_STATUS",
                    selected.getUsername() + (target ? " enabled" : " disabled"));
            changed = true;
            reload();
            selected = null;
            table.getSelectionModel().clearSelection();
            hideError();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        error.setText(message);
        error.setManaged(true);
        error.setVisible(true);
    }

    private void hideError() {
        error.setManaged(false);
        error.setVisible(false);
    }
}
