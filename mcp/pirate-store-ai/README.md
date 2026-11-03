# Pirate Store AI

The AI part of the [Pirate Store](../pirate-store) demo, split out into its
own application: a Spring AI `ChatClient` (RAG over `docs/*.md`, plus
product/cart tools fetched from `pirate-store`'s MCP server) exposed as two
streaming REST endpoints, `POST /chat` and `POST /chat/decision`, for
`pirate-store`'s chat UI to call.

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

It starts on <http://localhost:8081>. `pirate-store`'s `SupportAssisstantService`
calls `POST http://localhost:8081/chat` (configurable via
`pirate-store.ai-service.base-url` in `pirate-store`'s `application.properties`).

Try it directly without the UI:

```bash
curl -sN -X POST http://localhost:8081/chat \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
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
| Spring Boot | 4.2.0-RC1 |
| Spring AI | 2.1.0-RC1 |

## Architecture overview

```text
com.thorben.janssen.piratestoreai
├── PirateStoreAiApplication    -- entry point
├── chat
│   ├── SupportChatController    -- POST /chat, POST /chat/decision (streamed as NDJSON)
│   ├── SupportChatService       -- builds the ChatClient (system prompt, RAG advisor,
│   │                                session memory), keeps paused replies, runs decisions
│   ├── SessionMemoryConfig      -- in-memory SessionService; this app has no database
│   └── ChatRequest / DecisionRequest / ChatEvent -- wire DTOs; ChatEvent's field names
│                                    must keep matching pirate-store's SupportEvent
├── mcp
│   └── OwnerKeyContext           -- tool context key for the ownerKey
└── rag
    └── IngestionPipeline         -- embeds docs/*.md into the SimpleVectorStore bean
```

## Why ownerKey still never reaches the model

This is the same guardrail `pirate-store` enforces (`ownerKey` is never a
tool parameter the model can see or supply) -- re-established across the
process boundary between these two applications. Full write-up in
[`pirate-store`'s README, "AI architecture"](../pirate-store/README.md#ai-architecture-why-a-second-application-and-how-ownerkey-still-stays-safe).
Short version:

1. `pirate-store` sends `ownerKey` in the request **body** (server to server,
   never touched by the browser or the model).
2. `SupportChatService` attaches it via `toolContext(...)`, on the first request
   and when it runs a decision.
3. Spring AI's MCP client copies the tool context into the `_meta` of every
   outgoing MCP tool call.
4. `pirate-store`'s MCP tools read it from there -- it is never a declared,
   model-fillable parameter.

## Confirming cart changes

`spring.ai.tools.confirmation.tools` names `addToCart`, `changeQuantity` and
`removeFromCart`, so Spring AI pauses the tool-calling loop before they run.

- `SupportChatService` keeps the paused reply per conversation. It never leaves
  this application, so a caller cannot change the tool calls.
- The stream ends with a `ChatEvent` that lists the waiting calls. `pirate-store`
  builds the preview, because the cart data lives there.
- `POST /chat/decision` runs or declines the calls and streams the answer. The
  reply is removed first, so a second decision has no effect.
- A new `POST /chat` declines a pause the user ignored.

The paused replies and the session memory are held in memory, so they are lost on
restart. This is enough for the demo, but not for several instances.

## Manual verification performed

- `./mvnw test` -- `SupportChatServiceTest` runs the confirmation flow with a
  scripted model and a recording tool.
- Started together with `pirate-store`, with a real OpenAI API key, and driven
  through its real UI with Playwright: see `pirate-store`'s README.
- Starting this app before `pirate-store` is up fails, because the MCP client
  connects at startup -- start `pirate-store` first.

## Known limitation

No authentication between the two applications (matches the "no
authentication" guardrail of the whole demo) -- `POST /chat` and `/mcp`
should not be exposed beyond a trusted local network in a real deployment.
