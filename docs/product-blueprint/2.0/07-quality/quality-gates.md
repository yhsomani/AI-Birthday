# Quality and Release Gates

| Gate | Minimum evidence |
|---|---|
| PR | requirements/rule/AC/TC links; no source-doc contradiction; analysis/format; scoped tests; privacy/security review where affected |
| Build | reproducible restore; typecheck/lint; secrets/provenance; capability checks; migration compatibility |
| Test environment | contract/emulator/db tests; fixture fidelity; no production credentials; named commit |
| Security/privacy | threat/abuse/diff review; secret and dependency scan; rules/App Check; data lifecycle; provider disclosure |
| Accessibility | automated plus manual assistive/large-text/narrow journey evidence |
| Performance/reliability | approved workload; response/job/query measures; failure/recovery/drill evidence |
| Release candidate | signed artifact; schema/flags; store/region/legal/Hosting matrix; approved decisions; rollback |
| Production deploy | environment/IAM/App Check/secrets/rules/index/TTL and smoke evidence; separate approval |
| Post-deploy | smoke, telemetry, incident/support readiness, backup/restore and rollback readiness |

A green CI from a different commit or emulator is not release evidence. A test report is not deployment or device proof.
