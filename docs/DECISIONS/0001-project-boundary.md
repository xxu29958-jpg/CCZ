# 0001 Project Boundary

Date: 2026-06-20

## Context

The project targets a modern Android tactics engine. Cao Cao Zhuan MOD formats are legacy content sources.

## Decision

The runtime only loads native content packs. Legacy MOD parsing, opcode mapping, binary layout handling, and asset extraction are offline converter concerns.

## Consequences

- Runtime stays smaller and testable.
- Converter can be rebuilt as samples and tools become available.
- Unknown legacy structures block conversion, not runtime startup.

## Revisit When

- A legally reusable full MOD runtime exists and is worth adopting.
- The goal changes to arbitrary MOD plug-and-play compatibility.

