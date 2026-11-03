package com.thorben.janssen.piratestore.order;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Data entered in the checkout form. Kept as a plain DTO, bound by the
 * {@code CheckoutView}, so the checkout form never binds directly to a JPA
 * entity (see {@code CLAUDE.md} guardrails).
 */
public record CheckoutCommand(
        @NotBlank(message = "Recipient name is required") String recipientName,
        @NotBlank(message = "Email is required") @Email(message = "Please enter a valid email address") String email,
        @NotBlank(message = "Street and house number are required") String street,
        @NotBlank(message = "Postal code is required") String postalCode,
        @NotBlank(message = "City is required") String city,
        @NotBlank(message = "Country is required") String country
) {

    public static CheckoutCommand empty() {
        return new CheckoutCommand("", "", "", "", "", "Germany");
    }
}
