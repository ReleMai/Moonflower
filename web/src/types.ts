export type BotStatus =
  | 'OFFLINE'
  | 'LAUNCHING'
  | 'CONNECTING'
  | 'STOPPING'
  | 'IDLE'
  | 'RUNNING'
  | 'TAKEOVER'
  | 'ERROR';

export type TimestampValue = string | number;

export type TaskStatus =
  | 'QUEUED'
  | 'DISPATCHED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELED'
  | 'INTERRUPTED';

export interface AccountRecord {
  id: string;
  name: string;
  username: string;
  characterName: string | null;
  createdAt: TimestampValue;
  updatedAt: TimestampValue;
}

export interface BotRecord {
  id: string;
  name: string;
  accountId: string | null;
  clientInstallPath: string;
  preferredCharacter: string | null;
  preferredWorld: string | null;
  profileName: string | null;
  launchCommand: string | null;
  status: BotStatus;
  takeoverActive: boolean;
  lastState: Record<string, unknown> | null;
  createdAt: TimestampValue;
  updatedAt: TimestampValue;
}

export interface TaskRecord {
  id: string;
  botId: string;
  actionType: string;
  params: Record<string, unknown>;
  status: TaskStatus;
  queuedAt: TimestampValue;
  startedAt: TimestampValue | null;
  completedAt: TimestampValue | null;
  errorMessage: string | null;
}

export interface RoutePresetRecord {
  id: string;
  name: string;
  route: Record<string, unknown>;
  createdAt: TimestampValue;
}

export interface TaskPresetRecord {
  id: string;
  name: string;
  actionType: string;
  params: Record<string, unknown>;
  createdAt: TimestampValue;
}

export interface ScreenshotRecord {
  id: string;
  botId: string;
  fileName: string;
  mediaType: string;
  saved: boolean;
  metadata: {
    width?: number;
    height?: number;
    fileName?: string;
    mediaType?: string;
  };
  createdAt: TimestampValue;
}

export interface AuditRecord {
  id: number;
  botId: string | null;
  actor: string;
  eventType: string;
  details: Record<string, unknown>;
  createdAt: TimestampValue;
}

export interface ActivityRecord {
  id: number;
  botId: string;
  source: string;
  category: string;
  message: string;
  details: Record<string, unknown>;
  createdAt: TimestampValue;
}

export interface MediaClipRecord {
  id: string;
  botId: string;
  fileName: string;
  mediaType: string;
  triggerType: string;
  reason: string | null;
  durationSeconds: number;
  metadata: Record<string, unknown>;
  createdAt: TimestampValue;
}

export interface OperatorEvent {
  type: string;
  payload: unknown;
}
