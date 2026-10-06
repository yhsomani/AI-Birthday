export interface GooglePlaySubscriptionLineItem {
  productId: string;
  expiryTime: string | null;
}

export interface GooglePlaySubscriptionSnapshot {
  subscriptionState: string;
  acknowledgementState: string | null;
  obfuscatedExternalAccountId: string | null;
  lineItems: GooglePlaySubscriptionLineItem[];
}

export class GooglePlayApiError extends Error {
  constructor(
    message: string,
    readonly statusCode: number,
  ) {
    super(message);
  }
}

/// Minimal Google Play Developer API client using the Cloud Functions runtime
/// service account through the GCP metadata server.
///
/// The runtime service account must be granted the required app access in
/// Google Play Console. No Play service-account private key is stored in this
/// repository.
export class GooglePlaySubscriptionClient {
  constructor(
    private readonly httpFetch: typeof fetch = fetch,
    private readonly metadataTokenUrl =
      'http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token',
  ) {}

  async getSubscription(
    packageName: string,
    purchaseToken: string,
  ): Promise<GooglePlaySubscriptionSnapshot> {
    const accessToken = await this.getAccessToken();
    const url =
      'https://androidpublisher.googleapis.com/androidpublisher/v3/' +
      'applications/' +
      encodeURIComponent(packageName) +
      '/purchases/subscriptionsv2/tokens/' +
      encodeURIComponent(purchaseToken);

    const response = await this.httpFetch(url, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: 'application/json',
      },
    });

    if (!response.ok) {
      throw new GooglePlayApiError(
        'GOOGLE_PLAY_SUBSCRIPTION_LOOKUP_FAILED',
        response.status,
      );
    }

    const body = (await response.json()) as Record<string, unknown>;
    const subscriptionState =
      typeof body['subscriptionState'] === 'string'
        ? body['subscriptionState']
        : 'SUBSCRIPTION_STATE_UNSPECIFIED';

    const lineItemsRaw = body['lineItems'];
    const lineItems: GooglePlaySubscriptionLineItem[] = [];

    if (Array.isArray(lineItemsRaw)) {
      for (const rawItem of lineItemsRaw) {
        if (typeof rawItem !== 'object' || rawItem === null) continue;
        const item = rawItem as Record<string, unknown>;
        const productId = item['productId'];
        if (typeof productId !== 'string' || productId.length === 0) continue;
        const expiryTime = item['expiryTime'];
        lineItems.push({
          productId,
          expiryTime: typeof expiryTime === 'string' ? expiryTime : null,
        });
      }
    }

    let obfuscatedExternalAccountId: string | null = null;
    const externalIdentifiers = body['externalAccountIdentifiers'];
    if (
      typeof externalIdentifiers === 'object' &&
      externalIdentifiers !== null
    ) {
      const value =
        (externalIdentifiers as Record<string, unknown>)[
          'obfuscatedExternalAccountId'
        ];
      if (typeof value === 'string' && value.length > 0) {
        obfuscatedExternalAccountId = value;
      }
    }

    const acknowledgementState =
      typeof body['acknowledgementState'] === 'string'
        ? body['acknowledgementState']
        : null;

    return {
      subscriptionState,
      acknowledgementState,
      obfuscatedExternalAccountId,
      lineItems,
    };
  }

  private async getAccessToken(): Promise<string> {
    const response = await this.httpFetch(this.metadataTokenUrl, {
      method: 'GET',
      headers: {
        'Metadata-Flavor': 'Google',
      },
    });

    if (!response.ok) {
      throw new GooglePlayApiError(
        'GOOGLE_RUNTIME_CREDENTIAL_UNAVAILABLE',
        response.status,
      );
    }

    const body = (await response.json()) as Record<string, unknown>;
    const accessToken = body['access_token'];
    if (typeof accessToken !== 'string' || accessToken.length === 0) {
      throw new GooglePlayApiError(
        'GOOGLE_RUNTIME_CREDENTIAL_UNAVAILABLE',
        500,
      );
    }

    return accessToken;
  }
}
