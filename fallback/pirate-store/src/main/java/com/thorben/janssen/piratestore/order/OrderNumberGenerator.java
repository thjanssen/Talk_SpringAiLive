package com.thorben.janssen.piratestore.order;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates readable, unique order numbers of the form {@code PIR-20260905-0001}.
 *
 * <p>The sequence is derived from the number of orders already placed today,
 * which is simple and sufficient for a single-user demo (see README for the
 * documented limitation under concurrent checkouts).
 */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;

    public OrderNumberGenerator(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public String nextOrderNumber() {
        String datePart = LocalDate.now(ZoneId.systemDefault()).format(DATE_PATTERN);
        String prefix = "PIR-" + datePart + "-";
        long countToday = orderRepository.countByOrderNumberStartingWith(prefix);
        String candidate = prefix + String.format("%04d", countToday + 1);
        // Extremely unlikely in a demo, but guard against a duplicate anyway.
        while (orderRepository.findByOrderNumber(candidate).isPresent()) {
            countToday++;
            candidate = prefix + String.format("%04d", countToday + 1);
        }
        return candidate;
    }
}
