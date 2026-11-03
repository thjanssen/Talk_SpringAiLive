package com.thorben.janssen.piratestore.support.tools;

import java.math.BigDecimal;

/** What a cart tool call would change, shown to the user before they confirm it. */
public record CartChangePreview(String description, BigDecimal priceImpact) {
}
