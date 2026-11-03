# Pirate Store

A small, fully local pirate-themed online shop built with Spring Boot and
Vaadin. It is the starting point for a later live-coding session that adds a
Spring AI-powered support assistant ("the Quartermaster").

This first version has **no AI integration**. The support chat window is
fully built, including confirm/cancel controls for structured cart-change
proposals, but the assistant itself is a stub that always replies with the
same fixed sentence.

## Quick start

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8080>. No API keys, accounts, or external
services are required. Product data is created automatically on first start
and stored in a local H2 database under `./data`, so it survives restarts.

Run the tests with:

```bash
./mvnw test
```

## Versions

| Component | Version |
| --- | --- |
| Java | 21 |
| Maven (via wrapper) | 3.9.9 |
| Spring Boot | 4.2.0-RC1 |
| Vaadin | 25.3.5 (`vaadin-core`, no Pro components) |
| H2 | provided by Spring Boot's dependency management |

## Architecture overview

```text
com.thorben.janssen.piratestore
├── PirateStoreApplication      -- entry point, @Theme("pirate-store")
├── product                     -- catalog domain: Product, ProductService, ProductRepository
├── cart                        -- Cart, CartItem, CartService (all cart mutations go through here),
│                                   CartSummary/CartItemView DTOs, CartUpdateBroadcaster
├── session                     -- ShoppingSession (@VaadinSessionScope): ownerKey, conversationId,
│                                   chat history, order numbers -- the single source of session context
├── order                       -- CustomerOrder, OrderItem (immutable snapshot), OrderService,
│                                   CheckoutCommand DTO, OrderNumberGenerator
├── support                     -- SupportAssistantService interface + StubSupportAssistantService,
│                                   CartAssistantTools + CartChangePreviewService (confirmed cart changes),
│                                   SupportChatPanel (the chat UI component)
├── ui                          -- MainLayout, ProductView, CartView, CheckoutView, OrderConfirmationView
└── config                      -- DemoDataInitializer (idempotent, by SKU)
```

**Business logic lives in services, not views.** Every view is a thin
Vaadin component that calls `ProductService` / `CartService` / `OrderService`
and renders their results; none of them sum
prices or apply business rules themselves.

**Session isolation.** `ShoppingSession` is a `@VaadinSessionScope` bean that
generates a random `ownerKey` (and `conversationId`) once per browser
session. Every `CartService` / `OrderService`
method takes `ownerKey` as an explicit parameter -- there is no method that
looks up "the" active cart without it, and the UI, the chat panel, and (later)
AI tools all read `ownerKey` from `ShoppingSession`, never from a URL, a
prompt, or a model response.

**Cart change notifications.** `CartService` publishes a `CartChangedEvent`
(`ownerKey` + `CartSummary`) through `CartUpdateBroadcaster`, a small
in-memory pub/sub registry keyed by `ownerKey`. `MainLayout` and `CartView`
register a listener in `onAttach` and remove it in `onDetach`, so a session
only ever observes its own cart -- and badge/cart-view updates work
immediately after any mutation, including one applied through a confirmed
assistant action. We used a dedicated broadcaster instead of Spring's
`@EventListener` because reliably scoping `@EventListener` delivery to a
single `@VaadinSessionScope` bean instance requires scoped proxies and is a
common source of subtle bugs in Vaadin+Spring apps; a small owner-keyed
registry is simpler to get right and equally forward-compatible with an
async AI implementation (listener callbacks already wrap updates in
`UI.access`).

**Confirmation gate for assistant actions.** The cart tools in
`CartAssistantTools` are marked `@Tool(requiresConfirmation = true)`, so Spring
AI pauses the tool-calling loop before they run and returns the reply to
`SupportAssisstantService`. `ShoppingSession` keeps that reply on the server, and
`SupportChatPanel` shows a card with a preview from `CartChangePreviewService`.
Only "Confirm" runs the tool, and `CartService` checks stock and availability
again at that point. A call that cannot succeed is declined before the card
appears, so the assistant explains the problem instead. The flow is covered by
`CartConfirmationFlowTest` with a scripted model.

## Deliberate simplifications (not specified in detail by the documents)

- **Order numbers** (`PIR-yyyyMMdd-0001`) are generated from the count of
  orders placed on the same day. Good enough for a single-user demo; a
  high-concurrency deployment would need a proper sequence.
- **Chat input** is a single-line `TextField` with Enter-to-send. The
  optional Shift+Enter-for-newline behavior from the tech spec was left out
  to avoid fragile client-side key-event hacking for a feature explicitly
  marked optional.
- **Category filter** on the product page is a simple `ComboBox` re-querying
  `ProductService` on change -- no client-side caching.
- **`OrderStatus`** only has a `PLACED` value, since cancellation and
  post-purchase edits are explicitly out of scope for this version.

## Development vs. production mode

The app runs in Vaadin's normal **development mode** by default (`mvn spring-boot:run`,
running `PirateStoreApplication` from an IDE, `./mvnw test`, ...). Development
mode builds and serves the frontend on the fly on first request and needs no
prior build step, so the app starts the same way no matter how it is
launched -- including directly from an IDE run configuration, which skips
Maven plugin executions entirely.

An earlier version of this project forced Vaadin's *production* mode on
unconditionally to sidestep a one-off license-check issue on an older
Vaadin/Spring Boot combination. That workaround backfired: production mode
requires a pre-built frontend bundle, and an IDE "Run" that compiles classes
without invoking the `vaadin-maven-plugin` never creates one, causing "Unable
to find index.html" / HTTP 500 on the very first request. With the current
Spring Boot 4.2 / Vaadin 25.3 pairing that original license issue no longer
occurs, so development mode is the default again, exactly like a standard
Vaadin + Spring Boot project.

To build a real production artifact (bundled and minified frontend, no
dev-mode tooling), use the `production` Maven profile:

```bash
./mvnw clean package -Pproduction
java -jar target/pirate-store-0.1.0.jar
```

`vaadin.productionMode` in `application.properties` is filled in via Maven
resource filtering (`@vaadin.productionMode@`, `false` by default, `true`
when the `production` profile is active), so no manual property needs to be
flipped by hand.

## Spring Boot 4 / Vaadin 25 migration notes

The project was upgraded from Spring Boot 3.5 / Vaadin 24.10 to the
Spring Boot 4.1 / Vaadin 25.2 line, and later to Spring Boot 4.2 / Vaadin 25.3
(Vaadin 25.2 manages JUnit versions that conflict with Spring Boot 4.2's).
Vaadin 25 targets Spring Boot 4; mixing Vaadin 25 with Spring Boot 3.5, or
Vaadin 24 with Spring Boot 4, causes classpath/bean-definition conflicts --
keep the major lines paired.
Application code needed no changes. Two dependency-level adjustments were
needed:

- Spring Boot 4 split `@DataJpaTest` and `TestEntityManager` out of
  `spring-boot-test-autoconfigure` into their own modules
  (`spring-boot-data-jpa-test` and `spring-boot-jpa-test`, in new packages
  `org.springframework.boot.data.jpa.test.autoconfigure` and
  `org.springframework.boot.jpa.test.autoconfigure`), so both are now
  declared as explicit test dependencies in `pom.xml`.
- Vaadin 25's `vaadin-core` no longer pulls in development-mode tooling by
  itself; `com.vaadin:vaadin-dev` is now an explicit dependency (needed for
  `vaadin.productionMode=false`, the default -- see "Development vs.
  production mode" above).

## Manual verification performed

- `./mvnw test` -- all 29 tests green.
- `./mvnw spring-boot:run` from a clean checkout (`target/`, `data/`,
  `src/main/frontend/generated/` removed) -- starts in development mode,
  catalog seeded, all routes return 200, no error-level log lines.
- IDE-run simulation: `mvn compile` only (no later lifecycle phases, no
  `vaadin-maven-plugin` execution), then launching `PirateStoreApplication`
  directly on the resolved classpath -- reproduces the reported "500 on
  first load" bug before the development-mode fix, and confirms it is gone
  afterwards (200, dev bundle built on first request).
- `./mvnw clean package -Pproduction` then `java -jar target/*.jar` --
  production mode, pre-built frontend, all routes return 200.
- Headless-browser walkthrough (Playwright): Products view renders all 8
  demo products; "Add to cart" updates the badge and cart; Cart view shows
  the correct line item, subtotal and total; "Ask the Quartermaster" opens
  the chat drawer; sending a message shows the stub reply; no browser
  console errors.

## Extension point for Spring AI

Spring AI 2.1.0-RC1 is built against Spring Boot 4.2.0-RC1 -- exactly the
Spring Boot version this project now uses, so adding `spring-ai-bom` 2.1.0-RC1
needs no further version juggling.

Replace `com.thorben.janssen.piratestore.support.StubSupportAssistantService`
(or add a new `SupportAssistantService` bean and remove the stub) with a
`ChatClient`-backed implementation. `SupportChatPanel` depends only on the
`SupportAssistantService` interface, so no UI code needs to change. Expose
`searchProducts`, `getCurrentCart`, `addToCart`,
`changeQuantity`, `removeFromCart`, and `getOrder` as tools on a
session-scoped tool object that reads `ownerKey` from `ShoppingSession`
(never as a tool parameter); mark every tool that writes with
`requiresConfirmation = true`, so the user has to confirm it first.
