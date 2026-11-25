# Performance, Scalability and Reliability

No throughput, concurrency, data volume, p95, SLO, RPO or RTO values are approved. Mark them `DA`. Measurements must precede numeric requirements.

Required characteristics: offline local usefulness; bounded startup/query/render; predictable import row limits; background work deadlines/backoff; no duplicate side effects; back-pressure/queue bounds; cancellation/fencing; cross-zone calendar correctness; graceful provider/OS/network degradation; recover stale-running jobs; validate backup restore and device reboot; capacity and cost alarms; tested runbooks.

Failure matrix: provider unavailable/quota/invalid credential/timeout; network; Firebase/Play; DB locked/full/corrupt/migration; alarm/permission/reboot; stale update/duplicate click; process death; account switch; cloud deletion/backup race; public site/config. Each maps to retry/no-retry, user action, retention, operation ID and recovery evidence.

Detailed architecture: [performance section](../../definitive/03-technical-architecture.md).
