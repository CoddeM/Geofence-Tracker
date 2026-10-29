import { createRemoteJWKSet, jwtVerify } from "jose";
import { GoogleApi, hasField, stringArrayField, stringField } from "./google";

/**
 * Exit-alert service (Cloudflare Worker).
 *
 * POST /notify-exit  { groupId, eventId }   Authorization: Bearer <Firebase ID token>
 *
 * Called by the app after it saved an EXIT event to Firestore. Verifies the caller, checks the
 * event really is theirs, marks it as notified exactly once, and pushes a notification to every
 * other group member through FCM.
 */

export interface Env {
  FIREBASE_PROJECT_ID: string;
  /** Full service-account JSON, stored with `wrangler secret put` (never in the repo). */
  FIREBASE_SERVICE_ACCOUNT: string;
}

// Must match NotificationHelper.CHANNEL_ALERTS in the app.
const ALERT_CHANNEL_ID = "geofence_alerts";
const ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;

// Google's public keys for Firebase ID tokens (fetched and cached by jose).
const firebaseIdTokenKeys = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);

class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/notify-exit") {
      return json({ error: "not-found" }, 404);
    }
    try {
      const callerId = await verifyCaller(request, env.FIREBASE_PROJECT_ID);
      const body = (await request.json().catch(() => null)) as { groupId?: unknown; eventId?: unknown } | null;
      const groupId = body?.groupId;
      const eventId = body?.eventId;
      if (typeof groupId !== "string" || !ID_PATTERN.test(groupId) ||
          typeof eventId !== "string" || !ID_PATTERN.test(eventId)) {
        throw new HttpError(400, "groupId and eventId are required");
      }
      const api = new GoogleApi(env.FIREBASE_PROJECT_ID, env.FIREBASE_SERVICE_ACCOUNT);
      return json(await notifyExit(api, callerId, groupId, eventId));
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status);
      console.error("notify-exit failed", error);
      return json({ error: "internal" }, 500);
    }
  },
};

async function verifyCaller(request: Request, projectId: string): Promise<string> {
  const header = request.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) throw new HttpError(401, "missing token");
  try {
    const { payload } = await jwtVerify(header.slice("Bearer ".length), firebaseIdTokenKeys, {
      issuer: `https://securetoken.google.com/${projectId}`,
      audience: projectId,
    });
    if (!payload.sub) throw new Error("no subject");
    return payload.sub;
  } catch {
    throw new HttpError(401, "invalid token");
  }
}

async function notifyExit(api: GoogleApi, callerId: string, groupId: string, eventId: string) {
  const eventPath = `groups/${groupId}/events/${eventId}`;
  const event = await api.getDocument(eventPath);
  if (!event) throw new HttpError(404, "event not found");
  if (stringField(event, "userId") !== callerId || stringField(event, "type") !== "EXIT") {
    throw new HttpError(403, "not your exit event");
  }
  if (hasField(event, "notifiedAt")) return { status: "already-notified" };

  const group = await api.getDocument(`groups/${groupId}`);
  if (!group) throw new HttpError(404, "group not found");
  const memberIds = stringArrayField(group, "memberIds");
  if (!memberIds.includes(callerId)) throw new HttpError(403, "not a member");

  // App retries can call us twice for one event. Only the request that manages to set
  // notifiedAt (on the version of the event we just read) sends the push.
  const claimed = await api.setTimestampIfUnchanged(eventPath, event.updateTime, "notifiedAt");
  if (!claimed) return { status: "already-notified" };

  const recipientIds = memberIds.filter((id) => id !== callerId);
  const users = await api.getDocuments(recipientIds.map((id) => `users/${id}`));
  const recipients = users.flatMap((user) => {
    const token = stringField(user, "fcmToken");
    return token ? [{ path: user.name.split("/documents/")[1], token }] : [];
  });

  const userName = stringField(event, "userName") || "A member";
  const groupName = stringField(group, "name") || "your group";
  const results = await Promise.all(
    recipients.map((recipient) =>
      api.sendPush({
        token: recipient.token,
        title: `${userName} left the zone`,
        body: `${userName} moved outside the geofence of ${groupName}.`,
        // groupId lets the app open the right group when the notification is tapped.
        data: { groupId, eventId, type: "EXIT" },
        channelId: ALERT_CHANNEL_ID,
      }),
    ),
  );

  const staleTokens = recipients.filter((_, i) => results[i] === "stale-token");
  await Promise.all(staleTokens.map((r) => api.deleteField(r.path, "fcmToken").catch(() => undefined)));

  const summary = {
    status: "sent",
    recipients: recipients.length,
    sent: results.filter((r) => r === "sent").length,
    staleTokensRemoved: staleTokens.length,
  };
  console.log("exit alert", { groupId, eventId, ...summary });
  return summary;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
