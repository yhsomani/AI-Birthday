## 2026-07-20 - Technical clarification on useDeferredValue
**Learning:** While useDeferredValue prevents main thread blocking during typing by deprioritizing the render, it relies on React's concurrent rendering to schedule state transitions with lower priority on the main JS thread, rather than actually delegating to a background thread or Web Worker.
**Action:** When adding comments or PR descriptions explaining useDeferredValue, correctly describe it as leveraging React's concurrent rendering for lower-priority scheduling rather than background task delegation.
