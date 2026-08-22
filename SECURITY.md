# Security policy

WishWell handles private contact data and, on approved Android
channels, can submit pre-approved carrier SMS. Please do not disclose a suspected
vulnerability publicly before coordinated remediation.

## Reporting

Use the repository host's private security-advisory channel and include:

- the affected source revision and platform/version;
- a minimal reproduction using synthetic contacts and messages;
- observed and expected behavior;
- whether the issue could affect recipient choice, message content, sender/SIM,
  duplicate prevention, credentials, protected storage, account deletion, or
  privacy boundaries; and
- any safe diagnostic output after removing tokens, account identifiers, phone
  numbers, birthdays, messages, request IDs, installation IDs, and opaque
  coordination values.

Do not send real user data, provider credentials, signing material, HMAC peppers,
service-account keys, deletion receipt bearers, or production exploit traffic.
If a report requires private artifacts, agree on a protected transfer method with
the maintainer first.

## Response expectations

The maintainer should acknowledge a report within seven calendar days, assign a
severity and owner, and coordinate remediation and disclosure timing. A critical
issue affecting unintended SMS, duplicate prevention, credential exposure,
deletion fencing, or protected contact/message data requires immediate
fail-closed containment using [the operations runbook](docs/OPERATIONS_RUNBOOK.md).

## Supported releases

Only a release explicitly listed as supported in current signed distribution or
App Store evidence receives security fixes. Development, staging, lab, unsigned,
fixture, historical, and unapproved artifacts are not production releases. The
repository currently contains a fail-closed implementation candidate; it does
not itself prove that a production release is authorized.

## Safe-harbor boundary

Good-faith testing must use accounts, contacts, devices, phone numbers, Firebase
projects, SIMs, and carrier plans you own or are explicitly authorized to test.
Do not send unsolicited messages, access another person's data, bypass store or
carrier policy, degrade shared services, retain private data, or test production
deletion receipts without written authorization. This policy does not waive
applicable law, platform terms, telecom rules, or third-party rights.
