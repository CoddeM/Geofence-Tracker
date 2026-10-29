import { importPKCS8, SignJWT } from "jose";

/**
 * Minimal Google REST client for a Cloudflare Worker: OAuth via a service account,
 * Firestore (read / conditional write) and FCM HTTP v1. The Admin SDK doesn't run on Workers,
 * so these are plain fetch calls.
 */

interface ServiceAccount {
  client_email: string;
  private_key: string;
}

export interface FirestoreValue {
  stringValue?: string;
  integerValue?: string;
  timestampValue?: string;
  arrayValue?: { values?: FirestoreValue[] };
  mapValue?: { fields?: Record<string, FirestoreValue> };
}

export interface FirestoreDocument {
  name: string;
  fields?: Record<string, FirestoreValue>;
  updateTime: string;
}

export interface PushMessage {
  token: string;
  title: string;
  body: string;
  data: Record<string, string>;
  channelId: string;
}

export type PushResult = "sent" | "stale-token" | "failed";

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPES = [
  "https://www.googleapis.com/auth/datastore",
  "https://www.googleapis.com/auth/firebase.messaging",
];

// Reused across requests while the Worker instance stays warm (tokens live 1 hour).
let cachedToken: { value: string; expiresAt: number } | null = null;

export class GoogleApi {
  private readonly documentsUrl: string;

  constructor(
    private readonly projectId: string,
    private readonly serviceAccountJson: string,
  ) {
    this.documentsUrl =
      `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents`;
  }

  /** Returns the document, or null if it doesn't exist. */
  async getDocument(path: string): Promise<FirestoreDocument | null> {
    const response = await this.request(`${this.documentsUrl}/${path}`);
    if (response.status === 404) return null;
    await ensureOk(response, `read ${path}`);
    return (await response.json()) as FirestoreDocument;
  }

  async getDocuments(paths: string[]): Promise<FirestoreDocument[]> {
    if (paths.length === 0) return [];
    const prefix = `projects/${this.projectId}/databases/(default)/documents`;
    const response = await this.request(`${this.documentsUrl}:batchGet`, {
      method: "POST",
      body: JSON.stringify({ documents: paths.map((p) => `${prefix}/${p}`) }),
    });
    await ensureOk(response, "batchGet");
    const results = (await response.json()) as Array<{ found?: FirestoreDocument }>;
    return results.flatMap((r) => (r.found ? [r.found] : []));
  }

  /**
   * Sets a timestamp field only if the document hasn't changed since [updateTime].
   * Returns false if someone else changed it first (optimistic locking): this is how two
   * concurrent requests for the same event agree on who sends the notification.
   */
  async setTimestampIfUnchanged(path: string, updateTime: string, field: string): Promise<boolean> {
    const params = new URLSearchParams({
      "updateMask.fieldPaths": field,
      "currentDocument.updateTime": updateTime,
    });
    const response = await this.request(`${this.documentsUrl}/${path}?${params}`, {
      method: "PATCH",
      body: JSON.stringify({ fields: { [field]: { timestampValue: new Date().toISOString() } } }),
    });
    if (response.status === 400 || response.status === 409) {
      const error = (await response.json()) as { error?: { status?: string } };
      if (error.error?.status === "FAILED_PRECONDITION" || error.error?.status === "ABORTED") return false;
      throw new Error(`conditional update of ${path} failed: ${JSON.stringify(error)}`);
    }
    await ensureOk(response, `update ${path}`);
    return true;
  }

  /** Removes one field (an update mask with the field but no value deletes it). */
  async deleteField(path: string, field: string): Promise<void> {
    const params = new URLSearchParams({
      "updateMask.fieldPaths": field,
      "currentDocument.exists": "true",
    });
    const response = await this.request(`${this.documentsUrl}/${path}?${params}`, {
      method: "PATCH",
      body: JSON.stringify({ fields: {} }),
    });
    await ensureOk(response, `delete ${field} on ${path}`);
  }

  async sendPush(message: PushMessage): Promise<PushResult> {
    const response = await this.request(
      `https://fcm.googleapis.com/v1/projects/${this.projectId}/messages:send`,
      {
        method: "POST",
        body: JSON.stringify({
          message: {
            token: message.token,
            notification: { title: message.title, body: message.body },
            data: message.data,
            android: { priority: "HIGH", notification: { channel_id: message.channelId } },
          },
        }),
      },
    );
    if (response.ok) return "sent";
    const text = await response.text();
    // UNREGISTERED: the app was uninstalled or the token rotated; stop using this token.
    if (response.status === 404 || text.includes("UNREGISTERED")) return "stale-token";
    console.error("FCM send failed", response.status, text);
    return "failed";
  }

  private async request(url: string, init: RequestInit = {}): Promise<Response> {
    return fetch(url, {
      ...init,
      headers: {
        Authorization: `Bearer ${await this.accessToken()}`,
        "Content-Type": "application/json",
      },
    });
  }

  /** OAuth 2.0 "JWT bearer" flow: sign a JWT with the service account key, swap it for a token. */
  private async accessToken(): Promise<string> {
    if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value;

    const account = JSON.parse(this.serviceAccountJson) as ServiceAccount;
    const key = await importPKCS8(account.private_key, "RS256");
    const assertion = await new SignJWT({ scope: SCOPES.join(" ") })
      .setProtectedHeader({ alg: "RS256", typ: "JWT" })
      .setIssuer(account.client_email)
      .setSubject(account.client_email)
      .setAudience(TOKEN_URL)
      .setIssuedAt()
      .setExpirationTime("1h")
      .sign(key);

    const response = await fetch(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
    });
    await ensureOk(response, "OAuth token exchange");
    const token = (await response.json()) as { access_token: string; expires_in: number };
    cachedToken = { value: token.access_token, expiresAt: Date.now() + token.expires_in * 1000 };
    return token.access_token;
  }
}

export function stringField(doc: FirestoreDocument, name: string): string | undefined {
  return doc.fields?.[name]?.stringValue;
}

export function stringArrayField(doc: FirestoreDocument, name: string): string[] {
  const values = doc.fields?.[name]?.arrayValue?.values ?? [];
  return values.flatMap((v) => (v.stringValue !== undefined ? [v.stringValue] : []));
}

export function hasField(doc: FirestoreDocument, name: string): boolean {
  return doc.fields?.[name] !== undefined;
}

async function ensureOk(response: Response, action: string): Promise<void> {
  if (!response.ok) {
    throw new Error(`${action} failed: HTTP ${response.status} ${await response.text()}`);
  }
}
