# Pirate Store AI

The AI part of the [Pirate Store](../pirate-store) demo, split out into its
own application: a Spring AI `ChatClient` (RAG over `docs/*.md`, plus
product/cart tools fetched from `pirate-store`'s MCP server) exposed as a
single REST endpoint, `POST /chat`, for `pirate-store`'s chat UI to call.

This application has no UI, no database, and no cart/order logic of its own
-- all of that stays in `pirate-store`. It only orchestrates the model and
calls back into `pirate-store` over MCP for anything that touches product or
cart data.

## Quick start

This app is only useful together with `pirate-store`, since it calls back
into `pirate-store`'s MCP server (`/mcp`) for its tools and connects to it
**at startup**:

```bash
# 1. In ../pirate-store
./mvnw spring-boot:run

# 2. In this directory, once pirate-store is up (http://localhost:8080)
export MyVerySecretChatGptKey=sk-...   # a real OpenAI API key
./mvnw spring-boot:run
```

It starts on <http://localhost:8081>. `pirate-store`'s `SupportChatPanel`
calls `POST http://localhost:8081/chat` (configurable via
`pirate-store.ai-service.base-url` in `pirate-store`'s `application.properties`).

Try it directly without the UI:

```bash
curl -s -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -d '{"ownerKey":"11111111-1111-1111-1111-111111111111",
       "conversationId":"22222222-2222-2222-2222-222222222222",
       "message":"Ahoy! Do you have any eye patches?"}'
```

On first start, RAG ingestion embeds `docs/*.md` into a local vector store
cached at `./rag/vectorstore.json` (see `IngestionPipeline`); delete that
file to force re-ingestion after editing the docs.

Unlike `pirate-store`, this application genuinely needs a real OpenAI API key
to do anything useful -- there is no stub or mock model here.

## Versions

| Component | Version |
| --- | --- |
| Java | 21 |
| Maven (via wrapper) | 3.9.9 |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |

## Architecture overview

```text
com.thorben.janssen.piratestoreai
├── PirateStoreAiApplication    -- entry point
├── chat
│   ├── SupportChatController    -- POST /chat
│   ├── SupportChatService       -- builds the ChatClient (system prompt, RAG advisor,
│   │                                ChatMemory) and runs it per request
│   ├── ChatRequest / ChatResponse -- wire DTOs; field names must keep matching
│   │                                pirate-store's SupportResponse/PendingCartActionView
│   └── CapturingToolCallback    -- captures a cart-mutating tool's raw JSON result so
│                                    SupportChatService can attach it as `pendingAction`
├── mcp
│   ├── OwnerKeyContext           -- ThreadLocal carrier for the current request's ownerKey
│   └── OwnerKeyHttpRequestCustomizer -- attaches it as an X-Owner-Key header to every
│                                        outgoing MCP call (see pirate-store's McpToolsConfig)
└── rag
    └── IngestionPipeline         -- embeds docs/*.md into the SimpleVectorStore bean
```

## Why ownerKey still never reaches the model

This is the same guardrail `pirate-store` enforces (`ownerKey` is never a
tool parameter the model can see or supply) -- re-established across the
process boundary between these two applications. Full write-up in
[`pirate-store`'s README, "AI architecture"](../pirate-store/README.md#ai-architecture-why-a-second-application-and-how-ownerkey-still-stays-safe).
Short version:

1. `pirate-store` sends `ownerKey` in the `/chat` request **body** (server to
   server, never touched by the browser or the model).
2. `SupportChatService` stores it in a `ThreadLocal` (`OwnerKeyContext`) for
   the duration of that one request; the synchronous `ChatClient.call()` runs
   its whole tool-calling loop on that same thread.
3. `OwnerKeyHttpRequestCustomizer` (a Spring AI MCP client extension point,
   `McpSyncHttpClientRequestCustomizer`) reads it and attaches it as an
   `X-Owner-Key` header to every outgoing MCP HTTP call -- deterministic Java
   code, never something the model runs.
4. `pirate-store`'s MCP server reads that header back into a `ToolContext`
   parameter on its tool methods -- never a declared, model-fillable
   parameter.

## Cart-mutating tools and `pendingAction`

`addProduct` / `changeQuantity` / `removeProduct` (from `pirate-store`'s
`CartAssisstantTools`) only ever create a `PendingCartAction` and return its
view as their tool result; they never apply anything themselves.
`SupportChatService` wraps those three tool callbacks with
`CapturingToolCallback` to capture that raw result and unwrap it (MCP's
`ToolCallback.call()` returns the result's `content` list, JSON-serialized,
not the tool's return value directly -- see `SupportChatService.extractPendingAction`)
into `ChatResponse.pendingAction`, so `pirate-store`'s chat UI can render the
Confirm/Cancel controls without the model needing to repeat or reformat
structured data as text.

## Manual verification performed

- `./mvnw compile` -- compiles cleanly.
- Started together with `pirate-store`, with a real OpenAI API key:
  - `POST /chat` with a product question -- answered correctly via the
    `search` MCP tool, in the pirate voice from the system prompt, citing the
    RAG-ingested shipping/cancellation docs where relevant.
  - `POST /chat` with "add 2 eye patches to my cart" -- returns a
    `pendingAction` whose JSON shape matches `pirate-store`'s
    `SupportResponse`/`PendingCartActionView` records field-for-field, and a
    real `PendingCartAction` is created on the `pirate-store` side (checked
    via a direct MCP `tools/call` too).
  - Starting this app before `pirate-store` is up fails fast and clearly
    (`Client failed to initialize by explicit API call`) rather than hanging
    or silently retrying -- start `pirate-store` first.

## Known limitation

No authentication between the two applications (matches the "no
authentication" guardrail of the whole demo) -- `POST /chat` and `/mcp`
should not be exposed beyond a trusted local network in a real deployment.
