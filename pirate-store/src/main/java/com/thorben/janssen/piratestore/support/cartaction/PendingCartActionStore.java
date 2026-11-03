package com.thorben.janssen.piratestore.support.cartaction;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped, in-memory store for {@link PendingCartAction}s. A demo
 * application does not need these proposals to survive a restart, so a plain
 * map bound to the Vaadin session is sufficient (see technical specification,
 * section 4, {@code PendingCartActionStore}).
 *
 * <p>Uses a scoped proxy (rather than the plain {@code @VaadinSessionScope})
 * because it is injected into {@link CartAssistantActionService}, which is a
 * singleton created while the application starts up -- before any Vaadin
 * session exists. The proxy defers the actual lookup to when a method is
 * called, at which point a session is active.
 */
@Component
@Scope(scopeName = "vaadin-session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PendingCartActionStore {

    private final Map<UUID, PendingCartAction> actions = new ConcurrentHashMap<>();

    public void save(PendingCartAction action) {
        actions.put(action.getActionId(), action);
    }

    public Optional<PendingCartAction> find(UUID actionId) {
        return Optional.ofNullable(actions.get(actionId));
    }
}
