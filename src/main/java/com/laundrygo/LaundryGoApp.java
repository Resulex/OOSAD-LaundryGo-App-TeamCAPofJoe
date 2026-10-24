package com.laundrygo;

import javafx.application.Application;

/**
 * Plain entry point.
 *
 * <p>This class intentionally does <em>not</em> extend {@link Application}. The JDK launcher only
 * accepts a classpath launch of JavaFX when the main class is not an {@code Application} subclass;
 * otherwise it aborts with "JavaFX runtime components are missing" and exit code 1. Delegating to
 * {@link DesktopApp} means the client starts from:</p>
 *
 * <ul>
 *   <li>{@code mvn javafx:run} (module path),</li>
 *   <li>{@code java -cp "target/classes:..." com.laundrygo.LaundryGoApp}, and</li>
 *   <li>{@code mvn exec:exec -Dexec.executable=java -Dexec.args="-classpath %classpath com.laundrygo.LaundryGoApp"}.</li>
 * </ul>
 */
public final class LaundryGoApp {

    private LaundryGoApp() {
    }

    public static void main(String[] args) {
        // Slightly softer text rendering on macOS where LCD text is on by default.
        System.setProperty("prism.lcdtext", System.getProperty("prism.lcdtext", "false"));
        Application.launch(DesktopApp.class, args);
    }
}
