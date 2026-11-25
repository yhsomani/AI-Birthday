# Product Roadmap Map

**Diagram type:** Mermaid · **Status:** target blueprint view · **Authority:** supporting diagram; the linked specification remains authoritative.

```mermaid
flowchart LR
  P0[Phase 0 discovery research] --> P1[Phase 1 product contracts]
  P1 --> P2[Phase 2 canonical domain data]
  P1 --> P3[Phase 3 UX design system]
  P2 --> P4[Phase 4 architecture security infrastructure]
  P3 --> P4
  P4 --> P5[Phase 5 trustworthy local core]
  P5 --> P6[Phase 6 truthful handoff history]
  P6 --> P7[Phase 7 subscription-gated AI]
  P6 --> P8[Phase 8 continuity privacy lifecycle]
  P7 --> P10[Phase 10 production readiness]
  P8 --> P10
  P10 --> P9[Phase 9 conditional automation]
  P10 --> P11[Phase 11 operate improve]
  P9 --> P11
  subgraph parallel_workstreams["Parallel workstreams"]
    X1[Customer and accessibility research]
    X2[Traceability and docs]
    X3[Migration fixtures and inventory]
    X4[Device provider store validation]
    X5[Support legal security operations]
  end
  X1 -. informs .-> P1
  X2 -. required at every phase .-> P10
  X3 -. gates destructive data work .-> P2
  X4 -. gates runtime claims .-> P7
  X5 -. gates release .-> P10
  click P0 "../08-roadmap.md" "Roadmap detail"
```
