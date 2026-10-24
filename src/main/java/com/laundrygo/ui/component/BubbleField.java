package com.laundrygo.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * Decorative field of translucent soap bubbles, used behind the login hero, the form panel and the
 * navigation rail. Purely presentational: mouse-transparent, non-interactive and clipped to its
 * bounds.
 *
 * <p>Positions are normalised, so the field re-flows with the pane. A seeded {@link Random} keeps the
 * composition identical on every launch.</p>
 */
public final class BubbleField extends Pane {

    private record Bubble(Circle body, Circle shine, double x, double y, double radius) {
    }

    private final List<Bubble> bubbles = new ArrayList<>();

    public BubbleField(long seed,
                       int count,
                       double minRadius,
                       double maxRadius,
                       double minOpacity,
                       double maxOpacity,
                       Color base) {
        getStyleClass().add("bubble-field");
        setMouseTransparent(true);
        setPickOnBounds(false);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            double radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
            double opacity = minOpacity + random.nextDouble() * (maxOpacity - minOpacity);

            Circle body = new Circle(1);
            body.setFill(withOpacity(base, opacity));
            body.setStroke(withOpacity(base, Math.min(0.32, opacity + 0.06)));
            body.setStrokeWidth(1);
            body.setManaged(false);

            Circle shine = new Circle(1);
            shine.setFill(withOpacity(base, Math.min(0.38, opacity + 0.12)));
            shine.setManaged(false);

            bubbles.add(new Bubble(body, shine, random.nextDouble(), random.nextDouble(), radius));
            getChildren().addAll(body, shine);
        }

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
    }

    /** Large, soft bubbles for the blue login hero. */
    public static BubbleField hero() {
        return new BubbleField(7L, 16, 0.035, 0.115, 0.03, 0.07, Color.WHITE);
    }

    /** Small, restrained bubbles for the dark navigation rail. */
    public static BubbleField rail() {
        return new BubbleField(19L, 10, 0.07, 0.16, 0.018, 0.045, Color.WHITE);
    }

    /** Faint blue bubbles for the light form side of the login screen. */
    public static BubbleField tint() {
        return new BubbleField(31L, 9, 0.045, 0.125, 0.02, 0.045, Color.web("#2563EB"));
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        double base = Math.min(width, height);
        for (Bubble bubble : bubbles) {
            double radius = Math.max(3, bubble.radius() * base);
            double centerX = bubble.x() * width;
            double centerY = bubble.y() * height;

            bubble.body().setRadius(radius);
            bubble.body().setCenterX(centerX);
            bubble.body().setCenterY(centerY);

            bubble.shine().setRadius(Math.max(1.2, radius * 0.2));
            bubble.shine().setCenterX(centerX - radius * 0.33);
            bubble.shine().setCenterY(centerY - radius * 0.35);
        }
    }

    private static Color withOpacity(Color color, double opacity) {
        return Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(1, opacity)));
    }
}
