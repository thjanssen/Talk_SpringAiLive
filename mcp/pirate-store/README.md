# Pirate Store

A small, fully local pirate-themed online shop built with Spring Boot and
Vaadin.

The AI-powered support assistant ("the Quartermaster") does **not** live in
this application. This app exposes its product/cart operations as an **MCP
server** (`/mcp`) for a separate application, [`pirate-store-ai`](../pirate-store-ai),
which runs the actual `ChatClient` (RAG + MCP tools) and is called by
`SupportChatPanel` over a plain REST call. See "AI architecture" below for
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
| Spring Boot | 4.1.1 |
| Vaadin | 25.2.6 (`vaadin-core`, no Pro components) |
| Spring AI (MCP server only) | 2.0.1 |
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
├── support                     -- SupportAssisstantService (HTTP client to pirate-store-ai's /chat),
│                                   SupportChatPanel (the chat UI component)
│   ├── cartaction               -- CartAssistantActionService + PendingCartAction(Store) -- the
│   │                                confirmation gate; process-wide store, see its Javadoc
│   └── tools                    -- ProductTools, CartAssisstantTools + McpToolsConfig: the MCP server
│                                    exposed to pirate-store-ai, see "AI architecture" below
├── ui                          -- MainLayout, ProductView, CartView, CheckoutView, OrderConfirmationView,
│                                   PriceLabel (shared coin-icon price display)
└── config                      -- DemoDataInitializer (idempotent, by SKU)
```

**Business logic lives in services, not views.** Every view is a thin
Vaadin component that calls `ProductService` / `CartService` / `OrderService`
/ `CartAssistantActionService` and renders their results; none of them sum
prices or apply business rules themselves.

**Session isolation.** `ShoppingSession` is a `@VaadinSessionScope` bean that
generates a random `ownerKey` (and `conversationId`) once per browser
session. Every `CartService` / `OrderService` / `CartAssistantActionService`
method takes `ownerKey` as an explicit parameter -- there is no method that
looks up "the" active cart without it. The UI and `SupportAssisstantService`
read it from `ShoppingSession`; MCP tool calls (arriving from the separate
`pirate-store-ai` process, with no Vaadin session at all) instead read it
from an `X-Owner-Key` HTTP header via `McpOwnerKeyContext` -- see "AI
architecture" below. Either way, it is never a value the model itself
supplies: it is not a declared parameter on any tool, and the model never
sees the header.

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

**Confirmation gate for assistant actions.** `CartAssistantActionService` is
the only way an AI tool can change a cart. `proposeAdd` / `proposeQuantityChange`
/ `proposeRemoval` (exposed as MCP tools by `CartAssisstantTools`) only *read*
the cart and create a `PendingCartAction`; `confirm` -- triggered only by the
user clicking "Confirm" in `SupportChatPanel` -- re-validates product
availability, stock, and the current cart quantity against live data (not the
snapshot from proposal time) before calling the regular `CartService`, and
rejects actions that are missing, already resolved, or owned by a different
session. `PendingCartActionStore` is a plain, process-wide singleton (not
session-scoped): MCP tool calls arrive with no Vaadin session bound to the
thread at all, so isolation between owners is enforced entirely by the
explicit `ownerKey` check in `CartAssistantActionService`, not by container
scoping -- see the store's Javadoc.

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
Spring Boot 4.1 / Vaadin 25.2 pairing that original license issue no longer
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

The project was upgraded from Spring Boot 3.5 / Vaadin 24.10 to the current
stable Spring Boot 4.1 / Vaadin 25.2 line (Vaadin 25 targets Spring Boot 4;
mixing Vaadin 25 with Spring Boot 3.5, or Vaadin 24 with Spring Boot 4,
causes classpath/bean-definition conflicts -- keep the major lines paired).
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

- `./mvnw test` -- all 28 tests green, including a full `@SpringBootTest`
  context load (confirms the MCP server wiring in `McpToolsConfig` boots
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
- MCP server, live: started this app alone and `curl`'d `/mcp` directly --
  `tools/list` returns all tools with `ownerKey` absent from every schema;
  `tools/call addProduct` without an `X-Owner-Key` header is rejected with a
  clean error; with the header, it creates a real `PendingCartAction`.
- Full split, live, with a real OpenAI key: started this app and
  `pirate-store-ai` together and called `pirate-store-ai`'s `POST /chat`
  directly -- a product question is answered correctly via the `search` MCP
  tool and the RAG docs; "add 2 eye patches to my cart" returns a
  `pendingAction` whose JSON shape matches `SupportResponse`/
  `PendingCartActionView` field-for-field.

## AI architecture: why a second application, and how `ownerKey` still stays safe

The AI part (the `ChatClient`, RAG over `docs/*.md`, the system prompt) lives
entirely in the separate [`pirate-store-ai`](../pirate-store-ai) application,
not here. This app only exposes `ProductTools` / `CartAssisstantTools` as an
**MCP server**, via `spring-ai-starter-mcp-server-webmvc` in `STATELESS`
protocol mode (`support.tools.McpToolsConfig`, endpoint `/mcp`). `SupportChatPanel`
talks to `pirate-store-ai`'s `POST /chat` through the plain HTTP client
`SupportAssisstantService` -- no Spring AI dependency in this application at
all, on either side of that call.

Splitting the AI part out this way means an MCP tool call arrives here as a
**plain HTTP request with no Vaadin session bound to the thread** -- unlike
the earlier in-process design, `CartAssisstantTools` cannot simply read
`ShoppingSession.getOwnerKey()`. The guardrail ("`ownerKey` never a value the
model supplies or sees") still has to hold, so it is re-established across
the process boundary like this:

1. `SupportChatPanel` sends `ownerKey` (from `ShoppingSession`) as part of the
   request **body** it POSTs to `pirate-store-ai`'s `/chat` -- a trusted,
   server-to-server call, not something the browser or the model touches.
2. `pirate-store-ai` stores that `ownerKey` in a `ThreadLocal`
   (`mcp.OwnerKeyContext`) for the duration of handling that one request. Its
   `ChatClient.call()` is synchronous and runs its whole tool-calling loop --
   including every MCP tool call -- on that same thread.
3. An `McpSyncHttpClientRequestCustomizer`
   (`mcp.OwnerKeyHttpRequestCustomizer`) reads that `ThreadLocal` and attaches
   it as an `X-Owner-Key` HTTP header to every outgoing MCP call, entirely in
   Java code the model never runs or influences.
4. Here, `McpToolsConfig` configures the MCP server's `WebMvcStatelessServerTransport`
   with a `contextExtractor` that reads that header into an `McpTransportContext`.
   `ProductTools` and `CartAssisstantTools` use Spring AI's `@McpTool`
   annotation (rather than the generic, in-process `@Tool`), which is
   discovered automatically by the MCP annotation scanner and, on a
   `STATELESS` server, injects that `McpTransportContext` directly as a
   method parameter -- excluded from the tool's JSON schema, so the model can
   neither see nor supply it. `McpOwnerKeyContext` reads it back out.

`STATELESS` MCP mode (rather than a persistent SSE/streamable session) is a
deliberate choice here: every tool call is a standalone HTTP request, so the
header above is read fresh on every single call -- there is no persistent
connection whose `ownerKey` could go stale or leak across conversations.

Verified end-to-end (both apps running locally, a real OpenAI key): a tool
call without the header is rejected cleanly by `McpOwnerKeyContext`; a tool
call with it correctly creates a `PendingCartAction` scoped to that
`ownerKey`, with `ownerKey` never appearing in the tool's JSON schema at any
point (`curl`'d directly against `/mcp`).
