import { createClient } from "npm:@supabase/supabase-js@2";

type FirebaseServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
};

type NotificationRow = {
  id: string;
  recipient_user_id: string;
  notification_type: string;
  service_plan_id: string | null;
  rehearsal_id: string | null;
  title: string;
  message: string;
};

type TokenRow = { token: string };

const response = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

function base64Url(value: string | Uint8Array): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function decodePrivateKey(pem: string): Uint8Array {
  const normalized = pem
    .replace(/\\n/g, "\n")
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binary = atob(normalized);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

async function createGoogleAccessToken(account: FirebaseServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsignedJwt = `${header}.${claims}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    decodePrivateKey(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsignedJwt),
  );
  const assertion = `${unsignedJwt}.${base64Url(new Uint8Array(signature))}`;
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const tokenBody = await tokenResponse.json();
  if (!tokenResponse.ok || typeof tokenBody.access_token !== "string") {
    throw new Error("Google OAuth token exchange failed.");
  }
  return tokenBody.access_token;
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return response(405, { error: "Method not allowed." });

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const secretKeysValue = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (!supabaseUrl || !secretKeysValue) {
    return response(500, { error: "Supabase server credentials are unavailable." });
  }

  let serviceKey = "";
  try {
    serviceKey = (JSON.parse(secretKeysValue) as Record<string, string>).default ?? "";
  } catch {
    return response(500, { error: "Supabase server credentials are invalid." });
  }
  if (!serviceKey) return response(500, { error: "Supabase server key is unavailable." });

  let payload: { notification_id?: string };
  try {
    payload = await request.json();
  } catch {
    return response(400, { error: "Invalid JSON payload." });
  }

  const notificationId = payload.notification_id ?? "";
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(notificationId)) {
    return response(400, { error: "Invalid notification identifier." });
  }

  const signature = request.headers.get("x-unityhub-signature") ?? "";
  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: verified, error: verificationError } = await admin.rpc(
    "verify_notification_push_webhook",
    { p_notification_id: notificationId, p_signature: signature },
  );
  if (verificationError || verified !== true) {
    return response(401, { error: "Webhook signature verification failed." });
  }

  const { data: notificationData, error: notificationError } = await admin
    .from("notifications")
    .select("id, recipient_user_id, notification_type, service_plan_id, rehearsal_id, title, message")
    .eq("id", notificationId)
    .maybeSingle();
  const notification = notificationData as NotificationRow | null;
  if (notificationError || !notification) {
    return response(404, { error: "Notification not found." });
  }

  const { data: tokenData, error: tokenError } = await admin
    .from("device_push_tokens")
    .select("token")
    .eq("user_id", notification.recipient_user_id);
  const tokens = tokenData as TokenRow[] | null;
  if (tokenError) return response(500, { error: "Could not load recipient devices." });
  if (!tokens?.length) return response(200, { sent: 0, reason: "No registered Android devices." });

  const serviceAccountValue = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!serviceAccountValue) {
    return response(503, { error: "Firebase server credentials have not been configured." });
  }

  let serviceAccount: FirebaseServiceAccount;
  try {
    serviceAccount = JSON.parse(serviceAccountValue) as FirebaseServiceAccount;
  } catch {
    return response(500, { error: "Firebase service-account credentials are invalid." });
  }
  if (!serviceAccount.project_id || !serviceAccount.client_email || !serviceAccount.private_key) {
    return response(500, { error: "Firebase service-account fields are incomplete." });
  }

  try {
    const accessToken = await createGoogleAccessToken(serviceAccount);
    let sent = 0;
    let failed = 0;
    const fcmStatuses: Record<string, number> = {};
    const fcmErrorCodes: Record<string, number> = {};

    for (const device of tokens) {
      const fcmResponse = await fetch(
        `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token: device.token,
              notification: { title: notification.title, body: notification.message },
              data: {
                notification_id: notification.id,
                notification_type: notification.notification_type,
                service_plan_id: notification.service_plan_id ?? "",
                rehearsal_id: notification.rehearsal_id ?? "",
                title: notification.title,
                message: notification.message,
              },
              android: {
                priority: "HIGH",
                notification: { channel_id: "team_updates", sound: "default" },
              },
            },
          }),
        },
      );

      if (fcmResponse.ok) {
        sent += 1;
        continue;
      }

      failed += 1;
      const failure = await fcmResponse.json().catch(() => ({}));
      const statusLabel =
        typeof failure?.error?.status === "string"
          ? failure.error.status
          : `HTTP_${fcmResponse.status}`;
      fcmStatuses[statusLabel] = (fcmStatuses[statusLabel] ?? 0) + 1;

      const details = Array.isArray(failure?.error?.details) ? failure.error.details : [];
      for (const detail of details) {
        if (typeof detail?.errorCode === "string") {
          fcmErrorCodes[detail.errorCode] = (fcmErrorCodes[detail.errorCode] ?? 0) + 1;
        }
      }
      const isUnregistered = details.some(
        (detail: Record<string, unknown>) => detail.errorCode === "UNREGISTERED",
      );
      if (isUnregistered) {
        await admin.from("device_push_tokens").delete().eq("token", device.token);
      }
    }
    // Return only aggregate, non-sensitive FCM failure categories. Never expose
    // device tokens, OAuth access tokens, private keys, or raw Google error bodies.
    return response(200, { sent, failed, fcm_statuses: fcmStatuses, fcm_error_codes: fcmErrorCodes });
  } catch {
    return response(502, { error: "Firebase message delivery failed." });
  }
});
