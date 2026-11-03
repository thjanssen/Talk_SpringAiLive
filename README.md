# Pirate Store Demo

Dieses Verzeichnis enthält die Anforderungen für eine kleine Spring-Boot-Anwendung, die als Ausgangspunkt für eine Spring-AI-Live-Coding-Session dient.

## Dokumente

1. [01-product-requirements.md](claude/01-product-requirements.md) beschreibt Ziel, Umfang, Benutzerführung und funktionale Anforderungen.
2. [02-technical-specification.md](claude/02-technical-specification.md) legt Architektur, Domänenmodell und technische Leitplanken fest.
3. [03-acceptance-criteria.md](claude/03-acceptance-criteria.md) enthält überprüfbare Akzeptanzkriterien und Testfälle.
4. [CLAUDE.md](claude/CLAUDE.md) gibt Claude Code konkrete Anweisungen für die Implementierung.

## Ziel der ersten Version

Die erste Version ist ein funktionsfähiger, lokal ausführbarer Onlineshop für Piratenbedarf. Der Shop enthält Produktkatalog, einen getrennten Warenkorb je Vaadin-Session und Checkout. Ein global verfügbares Chatfenster für einen später anzubindenden Spring-AI-Support-Assistenten ist bereits vorbereitet, enthält aber noch keine AI-Integration. Die Services und UI-Ereignisse sind so gestaltet, dass der Assistent den Warenkorb der aktuellen Session lesen und nach Bestätigung verändern kann.
# Talk_SpringAiLive
