export type ProductType = "ARCHIVE" | "TASKING" | "MONITORING";
export type OrderStatus =
  "CREATED" | "PAYMENT_PENDING" | "PAID" | "PAYMENT_FAILED" | "REJECTED";

export interface Balance {
  user_id: string;
  balance: number;
  currency: string;
}

export interface Order {
  order_id: string;
  user_id: string;
  product_type: ProductType;
  payload: Record<string, unknown>;
  price: number;
  status: OrderStatus;
  failure_reason?: string | null;
  created_at: string;
}

export interface OrderDraft {
  product_type: ProductType;
  price: number;
  payload: Record<string, unknown>;
}

export interface Profile {
  user_id: string;
  email: string;
  display_name: string;
  job_title?: string | null;
  company?: string | null;
  phone?: string | null;
  bio?: string | null;
  created_at: string;
  updated_at: string;
}

export interface AuthSession {
  access_token: string;
  token_type: "Bearer";
  expires_in: number;
  profile: Profile;
}

export interface ProfileDraft {
  display_name: string;
  job_title?: string;
  company?: string;
  phone?: string;
  bio?: string;
}

export interface GeoAnalysis {
  valid: boolean;
  area_sq_km: number;
  resolution_m: number;
  price_geocredits: number;
  bbox: [number, number, number, number];
  normalized_geojson: { type: "Polygon"; coordinates: number[][][] };
  quote_id: string;
}

export interface NotificationItem {
  id: string;
  order_id: string;
  type: "PAYMENT_COMPLETED" | "PAYMENT_FAILED";
  title: string;
  message: string;
  is_read: boolean;
  created_at: string;
}

interface ApiErrorBody {
  error_code?: string;
  message?: string;
}

let currentAccessToken: string | null = null;

const request = async <T>(
  path: string,
  userId: string,
  init?: RequestInit,
): Promise<T> => {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-User-Id": userId,
      ...(currentAccessToken
        ? { Authorization: `Bearer ${currentAccessToken}` }
        : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorBody;
    throw new Error(
      error.message || error.error_code || `HTTP ${response.status}`,
    );
  }
  return response.json() as Promise<T>;
};

const authRequest = async <T>(path: string, init: RequestInit): Promise<T> => {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init.headers },
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorBody;
    throw new Error(
      error.message || error.error_code || `HTTP ${response.status}`,
    );
  }
  return response.json() as Promise<T>;
};

export const api = {
  authorize: (token: string | null) => {
    currentAccessToken = token;
  },
  createAccount: (userId: string) =>
    request<Balance>("/payments/api/v1/payments/accounts", userId, {
      method: "POST",
    }),
  balance: (userId: string) =>
    request<Balance>("/payments/api/v1/payments/accounts/balance", userId),
  topUp: (userId: string, amount: number) =>
    request<Balance>("/payments/api/v1/payments/accounts/top-up", userId, {
      method: "POST",
      body: JSON.stringify({ amount }),
    }),
  orders: (userId: string) =>
    request<Order[]>("/orders/api/v1/orders/orders", userId),
  createOrder: (userId: string, draft: OrderDraft) =>
    request<Order>("/orders/api/v1/orders/orders", userId, {
      method: "POST",
      body: JSON.stringify(draft),
    }),
  register: (email: string, password: string, displayName: string) =>
    authRequest<AuthSession>("/auth/api/v1/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password, display_name: displayName }),
    }),
  login: (email: string, password: string) =>
    authRequest<AuthSession>("/auth/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  profile: (token: string) =>
    authRequest<Profile>("/auth/api/v1/profile", {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
    }),
  updateProfile: (token: string, draft: ProfileDraft) =>
    authRequest<Profile>("/auth/api/v1/profile", {
      method: "PUT",
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify(draft),
    }),
  analyzeGeoJson: (
    userId: string,
    geojson: object,
    productType: ProductType,
    resolutionM: number,
  ) =>
    request<GeoAnalysis>("/geo/api/v1/geo/analyze", userId, {
      method: "POST",
      body: JSON.stringify({
        geojson,
        product_type: productType,
        resolution_m: resolutionM,
      }),
    }),
  createSnapshot: async (
    userId: string,
    bbox: [number, number, number, number],
  ) => {
    const response = await fetch("/geo/api/v1/geo/snapshot", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-User-Id": userId,
        ...(currentAccessToken
          ? { Authorization: `Bearer ${currentAccessToken}` }
          : {}),
      },
      body: JSON.stringify({ bbox, width: 1600 }),
    });
    if (!response.ok) throw new Error("Не удалось подготовить снимок");
    return response.blob();
  },
  notifications: (userId: string) =>
    request<NotificationItem[]>("/notifications/api/v1/notifications", userId),
  readNotification: (userId: string, id: string) =>
    request<NotificationItem>(
      `/notifications/api/v1/notifications/${id}/read`,
      userId,
      { method: "PATCH" },
    ),
  readAllNotifications: (userId: string) =>
    request<{ status: string }>(
      "/notifications/api/v1/notifications/read-all",
      userId,
      { method: "PATCH" },
    ),
};

export const notificationStream = (
  userId: string,
  token: string,
  signal: AbortSignal,
) =>
  fetch("/notifications/api/v1/notifications/stream", {
    headers: { "X-User-Id": userId, Authorization: `Bearer ${token}` },
    signal,
  });
