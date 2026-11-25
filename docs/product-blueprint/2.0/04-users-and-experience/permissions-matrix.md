# Permissions Matrix

| Capability | Unauthenticated local owner | Signed-in owner | Recipient | Support | Backend/operator |
|---|---|---|---|---|---|
| People/occurrence/draft/history local CRUD | Allow own device | Same local data until ownership policy | None | None | None unless explicit support transfer |
| Device contacts import | runtime permission + explicit review | same | N/A | None | N/A |
| AI without entitlement | Deny call; manual allowed | Deny call; manual allowed | N/A | N/A | cannot grant from client data |
| AI with entitlement | Provider-capability route | same + server-verified entitlement | N/A | N/A | verify, not create arbitrary grant |
| Cloud backup | None | Owner-scoped only; opt-in | N/A | none | server/admin least privilege only |
| Entitlement read | local cached state if valid policy | owner read; server write only | N/A | read status only if authorized | write through verification |
| Deletion/account control | local-only scope pending | recent auth for server surfaces | N/A | status workflow pending | audited, fenced operations |
| Autopilot control plane | N/A; blocked core | only if separately approved | N/A | restricted operations | server-only; not direct client data |

Runtime permissions (contacts, notifications, exact alarm, SMS if separately approved) are requested at point of use, explain purpose, honor denial/revocation and retain a manual fallback.
