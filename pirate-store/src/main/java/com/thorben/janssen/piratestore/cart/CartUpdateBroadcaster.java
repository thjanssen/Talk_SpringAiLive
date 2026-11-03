package com.thorben.janssen.piratestore.cart;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Small in-memory publish/subscribe registry that delivers {@link CartChangedEvent}s
 * only to listeners registered for the same {@code ownerKey}. This is the "small
 * UI-internal event solution" referenced by the technical specification: it is
 * simpler and safer than relying on Spring's {@code @EventListener} together with
 * scoped proxies for per-session Vaadin components, while still guaranteeing that
 * a session only ever observes its own cart changes.
 *
 * <p>UI components register a listener in {@code onAttach} and remove it again in
 * {@code onDetach} so that no session or UI reference is kept beyond the
 * component's lifecycle.
 */
@Component
public class CartUpdateBroadcaster {

    private final Map<UUID, List<Consumer<CartChangedEvent>>> listenersByOwner = new ConcurrentHashMap<>();

    public void register(UUID ownerKey, Consumer<CartChangedEvent> listener) {
        listenersByOwner.computeIfAbsent(ownerKey, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void remove(UUID ownerKey, Consumer<CartChangedEvent> listener) {
        List<Consumer<CartChangedEvent>> listeners = listenersByOwner.get(ownerKey);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public void publish(CartChangedEvent event) {
        List<Consumer<CartChangedEvent>> listeners = listenersByOwner.get(event.ownerKey());
        if (listeners == null) {
            return;
        }
        for (Consumer<CartChangedEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
