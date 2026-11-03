package com.thorben.janssen.piratestore.support.cartaction;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide, in-memory store for {@link PendingCartAction}s. A demo
 * application does not need these proposals to survive a restart, so a plain
 * map is sufficient (see technical specification, section 4,
 * {@code PendingCartActionStore}).
 *
 * <p>A single map shared across all sessions -- not one scoped to the Vaadin
 * session -- is required because proposals are now created from MCP tool
 * calls (see {@code support.tools.CartAssisstantTools}), which arrive as
 * plain HTTP requests from the pirate-store-ai application with no Vaadin
 * session bound to the thread at all. This is safe: isolation between
 * owners is enforced by {@link CartAssistantActionService} explicitly
 * checking {@code action.getOwnerKey()} against the caller's {@code ownerKey}
 * on every lookup (see {@code requireOwnedAction}), not by container-level
 * session scoping. Action ids are random UUIDs, so entries never collide
 * across owners either.
 */
@Component
public class PendingCartActionStore {

    private final Map<UUID, PendingCartAction> actions = new ConcurrentHashMap<>();

    public void save(PendingCartAction action) {
        actions.put(action.getActionId(), action);
    }

    public Optional<PendingCartAction> find(UUID actionId) {
        return Optional.ofNullable(actions.get(actionId));
    }
}
