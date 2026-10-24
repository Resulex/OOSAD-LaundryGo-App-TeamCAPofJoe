package com.laundrygo.service;

import com.laundrygo.model.Order;
import com.laundrygo.util.Times;
import java.time.LocalDateTime;
import javafx.concurrent.Task;

/**
 * Stubbed SMS gateway.
 *
 * <p>Wire {@link #readyForPickup(Order)} to a real provider (Semaphore, Twilio, Movider …) by
 * replacing the body of {@link ReadySmsTask#call()}. The task already runs off the FX thread, so a
 * blocking HTTP client is safe here. The simulated latency makes the "sending" state visible.</p>
 */
public final class SmsService {

    private final boolean simulateLatency;

    public SmsService() {
        this(true);
    }

    public SmsService(boolean simulateLatency) {
        this.simulateLatency = simulateLatency;
    }

    public Task<String> readyForPickup(Order order) {
        return new ReadySmsTask(order, simulateLatency);
    }

    private static final class ReadySmsTask extends Task<String> {

        private final Order order;
        private final boolean simulateLatency;

        private ReadySmsTask(Order order, boolean simulateLatency) {
            this.order = order;
            this.simulateLatency = simulateLatency;
        }

        @Override
        protected String call() throws Exception {
            if (order.getCustomerPhone() == null || order.getCustomerPhone().isBlank()) {
                throw new IllegalStateException("No contact number on file");
            }
            if (simulateLatency) {
                Thread.sleep(900);
            }
            String sentAt = LocalDateTime.now().format(Times.CLOCK);
            return "SMS alert sent to " + mask(order.getCustomerPhone()) + " at " + sentAt;
        }

        private static String mask(String phone) {
            String digits = phone.replaceAll("[^0-9]", "");
            if (digits.length() <= 4) {
                return phone;
            }
            return digits.substring(0, 4) + " ••• " + digits.substring(digits.length() - 4);
        }
    }
}
