# Pirate Store

A small, fully local pirate-themed online shop built with Spring Boot and
Vaadin.

The AI-powered support assistant ("the Quartermaster") does **not** live in
this application. This app exposes its product/cart operations as an **MCP
server** (`/mcp`) for a separate application, [`pirate-store-ai`](../pirate-store-ai),
which runs the actual `ChatClient` (RAG + MCP tools) and is called by
`SupportChatPanel` over REST, with the answer streamed back. See "AI architecture" below for
why, and how `ownerKey` still never becomes something the model can see or
supply despite crossing a process boundary.

## Quick start

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8080>. No API keys, accounts, or external
services are required to run this app **on its own** -- product browsing,
cart, and checkout work with no further setup, and its 28 tests need no AI
credentials either. Product data is created automatically on first start and
stored in a local H2 database under `./data`, so it survives restarts.

The support chat window only becomes conversational once
[`pirate-store-ai`](../pirate-store-ai) is also running (see its README) --
without it, `SupportChatPanel` shows a connection error but the rest of the
app is unaffected.

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
| Spring AI (MCP server only) | 2.1.0-RC1 |
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
├── support                     -- SupportAssisstantService (streaming client for pirate-store-ai),
│                                   SupportChatPanel (the chat UI component), SupportEvent
│   └── tools                    -- ProductTools, CartAssistantTools: the MCP server exposed to
│                                    pirate-store-ai; CartChangePreviewService for the confirmation
│                                    card, see "AI architecture" below
├── ui                          -- MainLayout, ProductView, CartView, CheckoutView, OrderConfirmationView,
│                                   PriceLabel (shared coin-icon price display)
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
looks up "the" active cart without it. The UI and `SupportAssisstantService`
read it from `ShoppingSession`; MCP tool calls (arriving from the separate
`pirate-store-ai` process, with no Vaadin session at all) instead read it
from the `_meta` of the tool call via `McpOwnerKeyContext` -- see "AI
architecture" below. Either way, it is never a value the model itself
supplies: it is not a declared parameter on any tool, and the model never
sees it.

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

**Confirmation gate for assistant actions.** The cart tools only run after the
user confirmed them. `pirate-store-ai` pauses before calling them and sends the
waiting calls with its stream. `SupportChatPanel` shows a card with a preview
from `CartChangePreviewService`, and "Confirm" or "Cancel" sends the decision
back. `CartService` checks stock and availability again when the tool runs. A
call that cannot succeed is declined before the card appears, so the assistant
explains the problem instead.

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
java -jar target/pirate-store-mcp-shop-0.1.0.jar
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

- `./mvnw test` -- all 27 tests green, including a full `@SpringBootTest`
  context load (confirms the MCP server wiring boots
  correctly without any AI credentials).
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
  the chat drawer.
- Full split, live, with a real OpenAI key, driven through the real UI with
  Playwright: answers stream into the chat, RAG and memory work, and `addToCart`,
  `changeQuantity` and `removeFromCart` show a card and only run after "Confirm".
  "Cancel", an ignored card and a quantity above the stock leave the cart
  unchanged, and a pending card survives a page reload.

## AI architecture: why a second application, and how `ownerKey` still stays safe

The AI part (the `ChatClient`, RAG over `docs/*.md`, the system prompt, the
conversation memory) lives entirely in the separate
[`pirate-store-ai`](../pirate-store-ai) application. This app provides the
frontend and exposes `ProductTools` / `CartAssistantTools` as an **MCP server**,
via `spring-ai-starter-mcp-server-webmvc` in `STATELESS` protocol mode (endpoint
`/mcp`). `SupportAssisstantService` talks to `pirate-store-ai` through a
`WebClient` and reads the answer as a stream of `SupportEvent`s.

An MCP tool call arrives here as a **plain HTTP request with no Vaadin session
bound to the thread**, so the tools cannot read `ShoppingSession`. The guardrail
("`ownerKey` is never a value the model supplies or sees") is re-established
across the process boundary like this:

1. `SupportAssisstantService` sends `ownerKey` (from `ShoppingSession`) in the
   request **body** to `pirate-store-ai` -- a trusted, server-to-server call, not
   something the browser or the model touches.
2. `pirate-store-ai` attaches it to the `ChatClient` call's tool context. Spring
   AI's MCP client copies the tool context into the `_meta` of each outgoing MCP
   tool call.
3. Here, `@McpTool` methods receive the `McpSchema.CallToolRequest`, which is
   excluded from the tool's JSON schema. `McpOwnerKeyContext` reads `ownerKey`
   from its `_meta`.

## Confirming cart changes across the two applications

The paused reply stays in `pirate-store-ai`, because whoever can change it could
run any tool with any arguments. The cart data stays here, so the preview does
too:

1. `pirate-store-ai` ends the stream with an event that lists the waiting tool
   calls, with their names and arguments.
2. `CartChangePreviewService` turns them into the text and price on the card. A
   call that cannot succeed is declined right away with the reason.
3. "Confirm" or "Cancel" sends `POST /chat/decision`, which contains only the
   decision. `pirate-store-ai` runs the tool calls it kept and streams the answer.
