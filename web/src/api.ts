const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080';

let operatorToken: string | null = localStorage.getItem('havenbot-token');

export function getApiBase() {
  return API_BASE;
}

export function getOperatorToken() {
  return operatorToken;
}

export function setOperatorToken(token: string | null) {
  operatorToken = token;
  if (token) {
    localStorage.setItem('havenbot-token', token);
  } else {
    localStorage.removeItem('havenbot-token');
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers ?? {});
  headers.set('Content-Type', 'application/json');
  if (operatorToken) {
    headers.set('X-Operator-Token', operatorToken);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { error?: string };
    throw new Error(body.error ?? `Request failed with ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  if (!text.trim()) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<{ token: string }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
  health: () => request<{ status: string }>('/api/health'),
  accounts: {
    list: <T>() => request<T>('/api/accounts'),
    create: <T>(payload: unknown) =>
      request<T>('/api/accounts', { method: 'POST', body: JSON.stringify(payload) }),
    update: <T>(id: string, payload: unknown) =>
      request<T>(`/api/accounts/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    remove: (id: string) => request<void>(`/api/accounts/${id}`, { method: 'DELETE' }),
  },
  bots: {
    list: <T>() => request<T>('/api/bots'),
    get: <T>(id: string) => request<T>(`/api/bots/${id}`),
    create: <T>(payload: unknown) =>
      request<T>('/api/bots', { method: 'POST', body: JSON.stringify(payload) }),
    update: <T>(id: string, payload: unknown) =>
      request<T>(`/api/bots/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    remove: (id: string) => request<void>(`/api/bots/${id}`, { method: 'DELETE' }),
    launch: (id: string) => request<{ registrationToken: string }>(`/api/bots/${id}/launch`, { method: 'POST' }),
    stop: (id: string) => request<void>(`/api/bots/${id}/stop`, { method: 'POST' }),
    pause: (id: string) => request<void>(`/api/bots/${id}/pause`, { method: 'POST' }),
    resume: (id: string) => request<void>(`/api/bots/${id}/resume`, { method: 'POST' }),
    abort: (id: string) => request<void>(`/api/bots/${id}/abort`, { method: 'POST' }),
    clearQueue: (id: string) => request<{ cleared: number }>(`/api/bots/${id}/queue/clear`, { method: 'POST' }),
    beginTakeover: (id: string) => request<void>(`/api/bots/${id}/takeover/begin`, { method: 'POST' }),
    endTakeover: (id: string) => request<void>(`/api/bots/${id}/takeover/end`, { method: 'POST' }),
    focus: (id: string) => request<void>(`/api/bots/${id}/focus`, { method: 'POST' }),
    saveReplay: <T>(id: string, payload: unknown) =>
      request<T>(`/api/bots/${id}/replay/save`, { method: 'POST', body: JSON.stringify(payload) }),
    requestScreenshot: (id: string) => request<void>(`/api/bots/${id}/screenshot`, { method: 'POST' }),
    startLiveFeed: (id: string, intervalMillis: number) =>
      request<void>(`/api/bots/${id}/live-feed/start`, {
        method: 'POST',
        body: JSON.stringify({ intervalMillis }),
      }),
    stopLiveFeed: (id: string) => request<void>(`/api/bots/${id}/live-feed/stop`, { method: 'POST' }),
    saveLiveFrame: (id: string) => request<void>(`/api/bots/${id}/live-frame/save`, { method: 'POST' }),
    webrtcOffer: <T>(id: string, payload: unknown) =>
      request<T>(`/api/bots/${id}/webrtc/offer`, {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    startScreenshotStream: (id: string, intervalSeconds: number) =>
      request<void>(`/api/bots/${id}/screenshot-stream/start`, {
        method: 'POST',
        body: JSON.stringify({ intervalSeconds }),
      }),
    stopScreenshotStream: (id: string) => request<void>(`/api/bots/${id}/screenshot-stream/stop`, { method: 'POST' }),
    activity: <T>(id: string, limit = 120) => request<T>(`/api/bots/${id}/activity?limit=${limit}`),
    remoteInput: (id: string, payload: unknown) =>
      request<void>(`/api/bots/${id}/remote-input`, { method: 'POST', body: JSON.stringify(payload) }),
    action: <T>(id: string, payload: unknown) =>
      request<T>(`/api/bots/${id}/actions`, { method: 'POST', body: JSON.stringify(payload) }),
    runTaskPreset: <T>(id: string, presetId: string) =>
      request<T>(`/api/bots/${id}/task-presets/${presetId}`, { method: 'POST' }),
    runRoutePreset: <T>(id: string, presetId: string) =>
      request<T>(`/api/bots/${id}/route-presets/${presetId}`, { method: 'POST' }),
    liveFeedUrl: (id: string) => {
      const token = getOperatorToken();
      const url = new URL(`${API_BASE}/api/bots/${id}/live-feed`);
      if (token) {
        url.searchParams.set('token', token);
      }
      return url.toString();
    },
  },
  tasks: {
    list: <T>(botId?: string) => request<T>(botId ? `/api/tasks?botId=${botId}` : '/api/tasks'),
    create: <T>(payload: unknown) =>
      request<T>('/api/tasks', { method: 'POST', body: JSON.stringify(payload) }),
    cancel: (id: string) => request<void>(`/api/tasks/${id}/cancel`, { method: 'POST' }),
  },
  routes: {
    list: <T>() => request<T>('/api/routes'),
    create: <T>(payload: unknown) =>
      request<T>('/api/routes', { method: 'POST', body: JSON.stringify(payload) }),
    update: <T>(id: string, payload: unknown) =>
      request<T>(`/api/routes/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    remove: (id: string) => request<void>(`/api/routes/${id}`, { method: 'DELETE' }),
  },
  taskPresets: {
    list: <T>() => request<T>('/api/task-presets'),
    create: <T>(payload: unknown) =>
      request<T>('/api/task-presets', { method: 'POST', body: JSON.stringify(payload) }),
    update: <T>(id: string, payload: unknown) =>
      request<T>(`/api/task-presets/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    remove: (id: string) => request<void>(`/api/task-presets/${id}`, { method: 'DELETE' }),
  },
  screenshots: {
    list: <T>(botId?: string) => request<T>(botId ? `/api/screenshots?botId=${botId}` : '/api/screenshots'),
    contentUrl: (id: string) => {
      const token = getOperatorToken();
      const url = new URL(`${API_BASE}/api/screenshots/${id}/content`);
      if (token) {
        url.searchParams.set('token', token);
      }
      return url.toString();
    },
  },
  clips: {
    list: <T>(botId?: string) => request<T>(botId ? `/api/clips?botId=${botId}` : '/api/clips'),
    contentUrl: (id: string) => {
      const token = getOperatorToken();
      const url = new URL(`${API_BASE}/api/clips/${id}/content`);
      if (token) {
        url.searchParams.set('token', token);
      }
      return url.toString();
    },
  },
  audit: {
    list: <T>() => request<T>('/api/audit'),
  },
  wiki: {
    detail: <T>(params: Record<string, string>) => {
      const query = new URLSearchParams(params);
      return request<T>(`/api/wiki/detail?${query.toString()}`);
    },
    iconUrl: (params: Record<string, string>) => {
      const token = getOperatorToken();
      const url = new URL(`${API_BASE}/api/wiki/icon`);
      if (token) {
        url.searchParams.set('token', token);
      }
      for (const [key, value] of Object.entries(params)) {
        url.searchParams.set(key, value);
      }
      return url.toString();
    },
  },
};
