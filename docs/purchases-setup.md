# Pro purchases: setup and verification

Pro is unlocked only after the `verifyPurchase` Cloud Function confirms a
Google Play subscription. Until that function is deployed to the project the
app uses, purchases are refused before Google Play bills anyone.

## Current state

- App project: `relateai-birthday-ysoman-a2372` (see `lib/core/config/firebase_config.dart`).
- `verifyPurchase` at `https://asia-south1-relateai-birthday-ysoman-a2372.cloudfunctions.net/verifyPurchase`
  returned **404** when last probed, so it is not deployed.
- The app probes that endpoint before billing. If it is missing or unreachable,
  the purchase is not started and Settings says you have not been charged.

## 1. Deploy the backend function

The backend lives in `backend/`. Its deploy alias already points at
`relateai-birthday-ysoman-a2372` (`backend/.firebaserc`).

```bash
npm install -g firebase-tools        # once
firebase login                       # interactive: run it yourself
cd backend
firebase functions:secrets:set COORDINATION_HMAC_KEYRING   # JSON keyring
# Set CONTROL_PLANE_SERVICE_ACCOUNT to the runtime service account email (see step 2).
firebase deploy --only functions:verifyPurchase
```

Check the deployment, then probe it. A deployed function answers with 400 or
401 for an empty request, not 404:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{"data":{}}' https://asia-south1-relateai-birthday-ysoman-a2372.cloudfunctions.net/verifyPurchase
```

## 2. Let the runtime service account read Play purchases

`GooglePlaySubscriptionClient` calls the Google Play Developer API with the
function's runtime service account. No Play key is stored in the repository.

1. Pick the runtime service account (the value of `CONTROL_PLANE_SERVICE_ACCOUNT`).
2. In Google Play Console → Users and permissions, invite that service account
   email and grant it access to `com.yashsomani.ai_birthday` with permission to
   view subscriptions and orders.

## 3. Create the Play product

In Play Console, create the auto-renewing subscription with product ID
`ai_birthday_pro_monthly` for `com.yashsomani.ai_birthday`. The app and the
backend both expect exactly this ID.

## 4. Test a purchase

- A debug build installed with `adb` cannot buy: Play Billing does not serve
  products to sideloaded builds, and Settings says so.
- Upload a build to an internal testing track, add your account as a license
  tester, install from the Play link, then tap **Upgrade to Pro**.
- Expected: Google Play sheet opens; after purchase, Settings shows Pro active
  once `verifyPurchase` returns `active`.

## Failure messages shown in Settings

| Cause | Message |
| --- | --- |
| Not signed in | Sign in with Google to subscribe to Pro. |
| Function missing or unreachable | Pro purchases are temporarily unavailable. You have not been charged. |
| Billing not available | Google Play Billing is not available on this device. |
| Product not served (sideload) | Pro is not available on this install. Install from Google Play. |
