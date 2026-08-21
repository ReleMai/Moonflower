import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Dispatch, FormEvent, ReactNode, SetStateAction } from 'react';
import { api, getOperatorToken, setOperatorToken } from './api';
import { resolvePackIcon } from './iconPack';
import './index.css';
import type {
  AccountRecord,
  ActivityRecord,
  AuditRecord,
  BotRecord,
  MediaClipRecord,
  OperatorEvent,
  RoutePresetRecord,
  TaskPresetRecord,
  TaskRecord,
  TimestampValue,
} from './types';
import { useOperatorSocket } from './useOperatorSocket';

type View = 'fleet' | 'accounts' | 'tasks' | 'routes' | 'presets' | 'audit';
type DashboardMode = 'single' | 'multi';
type NoticeTone = 'info' | 'error';

const DEFAULT_CLIENT_INSTALL_PATH = 'D:\\Codex Project\\Haven and Hearth Custom Client\\client\\bin';

const QUICK_ACTIONS = [
  ['cleanup.start', 'Start Cleanup'],
  ['cleanup.stop', 'Stop Cleanup'],
  ['fishing.start', 'Start Fishing'],
  ['fishing.stop', 'Stop Fishing'],
  ['inventory.sort', 'Sort Inventory'],
  ['grubgrub.start', 'Start Grub-Grub'],
  ['grubgrub.stop', 'Stop Grub-Grub'],
  ['tar-kiln.start', 'Start Tar Kiln'],
  ['tar-kiln.stop', 'Stop Tar Kiln'],
  ['roasting.start', 'Start Roasting'],
  ['roasting.stop', 'Stop Roasting'],
  ['cellar.start', 'Start Cellar Digging'],
  ['cellar.stop', 'Stop Cellar Digging'],
  ['ocean-scout.start', 'Start Ocean Scout'],
  ['ocean-scout.stop', 'Stop Ocean Scout'],
  ['safe-logout', 'Safe Logout'],
] as const;

const ADVANCED_ACTIONS = [
  'cleanup.start',
  'cleanup.stop',
  'fishing.start',
  'fishing.stop',
  'route.start',
  'route.stop',
  'auto-repeat-flower',
  'auto-repeat-flower.clear',
  'inventory.sort',
  'grubgrub.start',
  'grubgrub.stop',
  'tar-kiln.start',
  'tar-kiln.stop',
  'roasting.start',
  'roasting.stop',
  'cellar.start',
  'cellar.stop',
  'ocean-scout.start',
  'ocean-scout.stop',
  'safe-logout',
];

const SERVER_VIEWS: Array<{ id: View; label: string }> = [
  { id: 'fleet', label: 'Bots' },
  { id: 'accounts', label: 'Accounts' },
  { id: 'tasks', label: 'Tasks' },
  { id: 'routes', label: 'Routes' },
  { id: 'presets', label: 'Task Presets' },
  { id: 'audit', label: 'Audit' },
];

const ATTRIBUTE_KEYS = new Set(['str', 'agi', 'int', 'con', 'prc', 'csm', 'dex', 'wil', 'psy']);
const ABILITY_KEYS = new Set([
  'unarmed',
  'melee',
  'ranged',
  'explore',
  'stealth',
  'sewing',
  'smithing',
  'masonry',
  'carpentry',
  'cooking',
  'farming',
  'survive',
  'lore',
  'swim',
  'mining',
]);
const STAT_KEY_ALIASES: Record<string, string> = {
  strength: 'str',
  agility: 'agi',
  intelligence: 'int',
  constitution: 'con',
  perception: 'prc',
  charisma: 'csm',
  dexterity: 'dex',
  will: 'wil',
  psyche: 'psy',
  'unarmed combat': 'unarmed',
  unarmed: 'unarmed',
  'melee combat': 'melee',
  melee: 'melee',
  marksmanship: 'ranged',
  ranged: 'ranged',
  exploration: 'explore',
  explore: 'explore',
  stealth: 'stealth',
  sewing: 'sewing',
  smithing: 'smithing',
  masonry: 'masonry',
  carpentry: 'carpentry',
  cooking: 'cooking',
  farming: 'farming',
  survival: 'survive',
  survive: 'survive',
  lore: 'lore',
  swimming: 'swim',
  swim: 'swim',
  mining: 'mining',
};
const STAT_LABELS: Record<string, string> = {
  str: 'Strength',
  agi: 'Agility',
  int: 'Intelligence',
  con: 'Constitution',
  prc: 'Perception',
  csm: 'Charisma',
  dex: 'Dexterity',
  wil: 'Will',
  psy: 'Psyche',
  unarmed: 'Unarmed Combat',
  melee: 'Melee Combat',
  ranged: 'Marksmanship',
  explore: 'Exploration',
  stealth: 'Stealth',
  sewing: 'Sewing',
  smithing: 'Smithing',
  masonry: 'Masonry',
  carpentry: 'Carpentry',
  cooking: 'Cooking',
  farming: 'Farming',
  survive: 'Survival',
  lore: 'Lore',
  swim: 'Swimming',
  mining: 'Mining',
};
const LEGACY_SKILL_LABELS: Record<string, string> = {
  ahusb: 'Animal Husbandry',
  archery: 'Archery',
  baking: 'Baking',
  mechanics: 'Basic Mechanics',
  beekeeping: 'Beekeeping',
  boating: 'Boat Building',
  crp: 'Carpentry',
  coaling: 'Charcoal Burning',
  cooking: 'Cooking',
  farming: 'Farming',
  firecraft: 'Firecraft',
  fishing: 'Fishing',
  forage: 'Foraging',
  glass: 'Glassblowing',
  hearthmagic: 'Hearth Magic',
  hunting: 'Hunting',
  landscaping: 'Landscaping',
  locks: 'Locksmithing',
  lumber: 'Lumberjacking',
  metalworking: 'Metal Working',
  mining: 'Mining',
  oral: 'Oral Tradition',
  pottery: 'Pottery',
  tools: 'Toolmaking',
  ropetwin: 'Ropemaking',
  sewing: 'Sewing',
  stonework: 'Stoneworking',
  swim: 'Swimming',
  tanning: 'Tanning',
  wzm: 'Woodsmanship',
  srv: 'Wilderness Survival',
  wheelwrighting: 'Wheelwrighting',
  yeomanry: 'Yeomanry',
};

function formatJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}

interface DashboardSnapshot {
  bots: BotRecord[];
  accounts: AccountRecord[];
  tasks: TaskRecord[];
  routes: RoutePresetRecord[];
  taskPresets: TaskPresetRecord[];
  audit: AuditRecord[];
}

async function loadDashboardSnapshot(): Promise<DashboardSnapshot> {
  const [bots, accounts, tasks, routes, taskPresets, audit] = await Promise.all([
    api.bots.list<BotRecord[]>(),
    api.accounts.list<AccountRecord[]>(),
    api.tasks.list<TaskRecord[]>(),
    api.routes.list<RoutePresetRecord[]>(),
    api.taskPresets.list<TaskPresetRecord[]>(),
    api.audit.list<AuditRecord[]>(),
  ]);
  return { bots, accounts, tasks, routes, taskPresets, audit };
}

export default function App() {
  const [token, setToken] = useState<string | null>(getOperatorToken());
  const [loginError, setLoginError] = useState<string | null>(null);
  const [view, setView] = useState<View>('fleet');
  const [dashboardMode, setDashboardMode] = useState<DashboardMode>('single');
  const [notice, setNotice] = useState<{ tone: NoticeTone; message: string } | null>(null);

  const [bots, setBots] = useState<BotRecord[]>([]);
  const [accounts, setAccounts] = useState<AccountRecord[]>([]);
  const [tasks, setTasks] = useState<TaskRecord[]>([]);
  const [routes, setRoutes] = useState<RoutePresetRecord[]>([]);
  const [taskPresets, setTaskPresets] = useState<TaskPresetRecord[]>([]);
  const [audit, setAudit] = useState<AuditRecord[]>([]);
  const [activityByBot, setActivityByBot] = useState<Record<string, ActivityRecord[]>>({});
  const [clipsByBot, setClipsByBot] = useState<Record<string, MediaClipRecord[]>>({});

  const [selectedBotId, setSelectedBotId] = useState<string | null>(null);
  const [sheetBotId, setSheetBotId] = useState<string | null>(null);
  const [editingAccountId, setEditingAccountId] = useState<string | null>(null);
  const [editingRouteId, setEditingRouteId] = useState<string | null>(null);
  const [editingTaskPresetId, setEditingTaskPresetId] = useState<string | null>(null);

  const [advancedActionType, setAdvancedActionType] = useState('cleanup.start');
  const [advancedActionParams, setAdvancedActionParams] = useState('{}');
  const [flowerOption, setFlowerOption] = useState('');
  const [selectedRoutePresetId, setSelectedRoutePresetId] = useState('');
  const [selectedTaskPresetId, setSelectedTaskPresetId] = useState('');
  const [manualX, setManualX] = useState(320);
  const [manualY, setManualY] = useState(240);
  const [manualKeyCode, setManualKeyCode] = useState(32);
  const [clipSaveSeconds, setClipSaveSeconds] = useState(300);

  const [accountForm, setAccountForm] = useState({
    name: '',
    username: '',
    secret: '',
    characterName: '',
  });
  const [botForm, setBotForm] = useState({
    name: '',
    accountId: '',
    clientInstallPath: DEFAULT_CLIENT_INSTALL_PATH,
    preferredCharacter: '',
    preferredWorld: '',
    profileName: '',
    launchCommand: '',
  });
  const [routeForm, setRouteForm] = useState({
    name: '',
    routeJson: '{"checkpoints":[]}',
  });
  const [taskPresetForm, setTaskPresetForm] = useState({
    name: '',
    actionType: 'cleanup.start',
    paramsJson: '{}',
  });

  const selectedBot = useMemo(
    () => bots.find((bot) => bot.id === selectedBotId) ?? null,
    [bots, selectedBotId],
  );

  const sheetBot = useMemo(
    () => bots.find((bot) => bot.id === sheetBotId) ?? null,
    [bots, sheetBotId],
  );

  const selectedBotState = useMemo(
    () => asRecord(selectedBot?.lastState) ?? {},
    [selectedBot],
  );

  const selectedBotActivity = selectedBotId ? activityByBot[selectedBotId] ?? [] : [];
  const selectedBotClips = selectedBotId ? clipsByBot[selectedBotId] ?? [] : [];

  const fleetSummary = useMemo(() => {
    const counts = {
      total: bots.length,
      online: 0,
      running: 0,
      takeover: 0,
      error: 0,
    };
    for (const bot of bots) {
      if (isBotOnline(bot.status)) counts.online += 1;
      if (bot.status === 'RUNNING') counts.running += 1;
      if (bot.status === 'TAKEOVER') counts.takeover += 1;
      if (bot.status === 'ERROR') counts.error += 1;
    }
    return counts;
  }, [bots]);

  async function run<T>(action: () => Promise<T>, successMessage?: string) {
    try {
      const result = await action();
      if (successMessage) {
        setNotice({ tone: 'info', message: successMessage });
      }
      return result;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Request failed';
      setNotice({ tone: 'error', message });
      throw error;
    }
  }

  const applyDashboardSnapshot = useCallback((snapshot: DashboardSnapshot) => {
    setBots(snapshot.bots);
    setAccounts(snapshot.accounts);
    setTasks(snapshot.tasks);
    setRoutes(snapshot.routes);
    setTaskPresets(snapshot.taskPresets);
    setAudit(snapshot.audit);
    setSelectedBotId((current) => {
      if (current && snapshot.bots.some((bot) => bot.id === current)) {
        return current;
      }
      return snapshot.bots[0]?.id ?? null;
    });
  }, []);

  const refreshBotClips = useCallback(async (botId: string | null) => {
    if (!token || !botId) {
      return;
    }
    const clips = await api.clips.list<MediaClipRecord[]>(botId);
    setClipsByBot((current) => ({ ...current, [botId]: clips }));
  }, [token]);

  const refreshAll = useCallback(async () => {
    if (!token) return;
    const [snapshot, feed, clips] = await Promise.all([
      loadDashboardSnapshot(),
      selectedBotId ? api.bots.activity<ActivityRecord[]>(selectedBotId, 120) : null,
      selectedBotId ? api.clips.list<MediaClipRecord[]>(selectedBotId) : null,
    ]);
    applyDashboardSnapshot(snapshot);
    if (selectedBotId && feed) {
      setActivityByBot((current) => ({ ...current, [selectedBotId]: feed }));
    }
    if (selectedBotId && clips) {
      setClipsByBot((current) => ({ ...current, [selectedBotId]: clips }));
    }
  }, [applyDashboardSnapshot, selectedBotId, token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    void loadDashboardSnapshot()
      .then((snapshot) => {
        if (!cancelled) {
          applyDashboardSnapshot(snapshot);
        }
      })
      .catch((error: Error) => {
        if (!cancelled && error.message.includes('401')) {
          setToken(null);
          setOperatorToken(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [applyDashboardSnapshot, token]);

  useEffect(() => {
    if (!token || !selectedBotId) return;
    let cancelled = false;
    void Promise.all([
      api.bots.activity<ActivityRecord[]>(selectedBotId, 120),
      api.clips.list<MediaClipRecord[]>(selectedBotId),
    ]).then(([feed, clips]) => {
      if (!cancelled) {
        setActivityByBot((current) => ({ ...current, [selectedBotId]: feed }));
        setClipsByBot((current) => ({ ...current, [selectedBotId]: clips }));
      }
    }).catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [selectedBotId, token]);

  const handleOperatorEvent = useCallback((operatorEvent: OperatorEvent) => {
    if (operatorEvent.type === 'activity-event') {
      const record = asActivityRecord(operatorEvent.payload);
      if (record) {
        setActivityByBot((current) => {
          const existing = current[record.botId] ?? [];
          return {
            ...current,
            [record.botId]: [record, ...existing].slice(0, 120),
          };
        });
        if (record.category === 'clip') {
          refreshBotClips(record.botId).catch(() => undefined);
        }
      }
      return;
    }

    if (operatorEvent.type === 'fast-state-update' || operatorEvent.type === 'state-snapshot') {
      const patch = operatorStatePatch(operatorEvent.payload);
      if (patch) {
        applyBotStatePatch(setBots, patch);
      }
      return;
    }

    const refreshTypes = new Set([
      'bot-status',
      'bot-registered',
      'task-started',
      'task-completed',
      'task-failed',
      'takeover-changed',
      'client-error',
      'session-changed',
      'command-dispatched',
      'clip-saved',
    ]);

    if (refreshTypes.has(operatorEvent.type)) {
      refreshAll().catch(() => undefined);
    }
  }, [refreshAll, refreshBotClips]);

  const { event, connected } = useOperatorSocket(Boolean(token), handleOperatorEvent);

  async function handleLogin(eventValue: FormEvent<HTMLFormElement>) {
    eventValue.preventDefault();
    const formData = new FormData(eventValue.currentTarget);
    try {
      const response = await api.login(
        String(formData.get('username') ?? ''),
        String(formData.get('password') ?? ''),
      );
      setOperatorToken(response.token);
      setToken(response.token);
      setLoginError(null);
      setNotice(null);
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : 'Login failed');
    }
  }

  function resetBotForm() {
    setBotForm({
      name: '',
      accountId: '',
      clientInstallPath: DEFAULT_CLIENT_INSTALL_PATH,
      preferredCharacter: '',
      preferredWorld: '',
      profileName: '',
      launchCommand: '',
    });
  }

  function resetAccountForm() {
    setAccountForm({ name: '', username: '', secret: '', characterName: '' });
    setEditingAccountId(null);
  }

  function resetRouteForm() {
    setRouteForm({ name: '', routeJson: '{"checkpoints":[]}' });
    setEditingRouteId(null);
  }

  function resetTaskPresetForm() {
    setTaskPresetForm({ name: '', actionType: 'cleanup.start', paramsJson: '{}' });
    setEditingTaskPresetId(null);
  }

  async function queueAction(actionType: string, params: Record<string, unknown> = {}) {
    if (!selectedBot) return;
    await run(
      () => api.bots.action<TaskRecord>(selectedBot.id, { actionType, params }),
      `${humanActionLabel(actionType)} queued.`,
    );
    await refreshAll();
  }

  async function submitAdvancedAction() {
    await queueAction(advancedActionType, JSON.parse(advancedActionParams) as Record<string, unknown>);
  }

  async function sendRemoteInput(inputType: string, extra: Record<string, unknown> = {}) {
    if (!selectedBot) return;
    await run(
      () => api.bots.remoteInput(selectedBot.id, { inputType, ...extra }),
      `Remote input sent: ${inputType}`,
    );
  }

  const currentTask = asRecord(selectedBotState.currentTask);
  const currentActionType = typeof currentTask?.actionType === 'string' ? currentTask.actionType : '';
  const learningPoints = typeof selectedBotState.learningPoints === 'number' ? selectedBotState.learningPoints : null;
  const experience = typeof selectedBotState.experience === 'number' ? selectedBotState.experience : null;

  if (!token) {
    return (
      <div className="login-shell">
        <form className="login-card" onSubmit={handleLogin}>
          <h1>Haven Bot Control</h1>
          <p>Single-operator login for the local multi-bot console.</p>
          <label>
            Username
            <input name="username" defaultValue="admin" />
          </label>
          <label>
            Password
            <input name="password" type="password" defaultValue="changeme" />
          </label>
          {loginError ? <div className="notice error">{loginError}</div> : null}
          <button type="submit">Sign In</button>
        </form>
      </div>
    );
  }

  return (
    <div className="operator-shell">
      <main className="operator-main">
        <header className="topbar">
          <div>
            <span className="eyebrow">Control Room</span>
            <h1>Haven Bot Command Deck</h1>
            <p className="muted">
              {event ? `Last event: ${(event as OperatorEvent).type}` : 'No live events yet'}
            </p>
          </div>
          <div className="topbar-actions">
            <button onClick={() => refreshAll().catch(() => undefined)}>Refresh</button>
            <div className={connected ? 'socket-pill online' : 'socket-pill'}>
              {connected ? 'Operator socket live' : 'Socket offline'}
            </div>
          </div>
        </header>

        {notice ? <div className={`notice ${notice.tone}`}>{notice.message}</div> : null}

        {view === 'fleet' ? (
          <section className="fleet-scene">
            <div className="bot-strip">
              <div className="section-heading">
                <div>
                  <h2>Active Bots</h2>
                  <p className="muted">
                    Swap between one focused live view or a full grid of every connected bot.
                  </p>
                </div>
                <div className="mode-toggle" role="tablist" aria-label="View mode">
                  <button
                    className={dashboardMode === 'single' ? 'active' : ''}
                    onClick={() => setDashboardMode('single')}
                  >
                    Single View
                  </button>
                  <button
                    className={dashboardMode === 'multi' ? 'active' : ''}
                    onClick={() => setDashboardMode('multi')}
                  >
                    Multi View
                  </button>
                </div>
              </div>
              {selectedBot ? (
                <div className="panel bot-summary">
                  <div className="bot-summary-copy">
                    <div>
                      <span className="eyebrow">Selected Bot</span>
                      <h3>{selectedBot.name}</h3>
                      <p className="muted">
                        {selectedBot.preferredCharacter || 'No preferred character'} •{' '}
                        {selectedBot.preferredWorld || 'Unknown world'}
                      </p>
                    </div>
                    <button type="button" className="sheet-launch" onClick={() => setSheetBotId(selectedBot.id)}>
                      <CharacterSheetIcon />
                      <span>Character Sheet</span>
                    </button>
                  </div>
                  <div className="bot-summary-meters">
                    <MetricCard label="Health" value={healthLabel(selectedBotState.health)} />
                    <MetricCard label="Stamina" value={meterLabel(selectedBotState.stamina)} />
                    <MetricCard label="Energy" value={meterLabel(selectedBotState.energy)} />
                    <MetricCard
                      label="Learning Points"
                      value={learningPoints !== null ? learningPoints.toLocaleString() : 'Unknown'}
                    />
                    <MetricCard
                      label="Experience"
                      value={experience !== null ? experience.toLocaleString() : 'Unknown'}
                    />
                  </div>
                </div>
              ) : null}
              <div className="bot-tab-row">
                {bots.map((bot) => (
                  <BotTabCard
                    key={bot.id}
                    bot={bot}
                    active={selectedBotId === bot.id}
                    onSelect={() => setSelectedBotId(bot.id)}
                    onOpenSheet={() => setSheetBotId(bot.id)}
                  />
                ))}
              </div>
            </div>

            <div className="command-grid">
              <section className="panel live-stage">
                {dashboardMode === 'single' ? (
                  <>
                    <div className="stage-header">
                      <div>
                        <h3>Live View</h3>
                        <p className="muted">
                          {selectedBot
                            ? `${selectedBot.name} • ${selectedBot.status} • ${
                                currentActionType ? humanActionLabel(currentActionType) : 'Idle'
                              }`
                            : 'Choose a bot from the strip above'}
                        </p>
                      </div>
                      {selectedBot ? (
                        <div className="button-row">
                          <button
                            onClick={() => run(() => api.bots.saveLiveFrame(selectedBot.id), 'Live frame saved.')}
                          >
                            Save Frame
                          </button>
                        </div>
                      ) : null}
                    </div>

                    {selectedBot && canRenderLiveVideo(selectedBot) ? (
                      <div className="hero-stage">
                        <BotLiveVideo bot={selectedBot} />
                        <div className="stage-overlay">
                          <span>{selectedBot?.status ?? 'Unknown'}</span>
                          <span>WebRTC live video</span>
                        </div>
                      </div>
                    ) : (
                      <div className="stage-placeholder">
                        <strong>No live feed yet</strong>
                        <p>Launch a bot and the live video session will connect automatically.</p>
                      </div>
                    )}
                  </>
                ) : (
                  <>
                    <div className="stage-header">
                      <div>
                        <h3>Multi View</h3>
                        <p className="muted">All online bots are kept on a 1-second renderer-backed live grid.</p>
                      </div>
                    </div>
                    <div className="live-grid">
                      {bots.map((bot) => (
                        <button
                          key={bot.id}
                          className={selectedBotId === bot.id ? 'live-card active' : 'live-card'}
                          onClick={() => setSelectedBotId(bot.id)}
                          onDoubleClick={() => {
                            setSelectedBotId(bot.id);
                            setDashboardMode('single');
                          }}
                        >
                          <div className="live-card-meta">
                            <strong>{bot.name}</strong>
                            <span>{bot.status}</span>
                          </div>
                          {canRenderLiveVideo(bot) ? (
                            <BotLiveVideo bot={bot} compact />
                          ) : (
                            <div className="live-card-empty">Waiting for live feed...</div>
                          )}
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </section>

              <section className="panel activity-panel">
                <div className="section-heading">
                  <div>
                    <h3>Bot Activity Log</h3>
                    <p className="muted">
                      Inventory shifts, route progress, meter changes, server lifecycle events, and task flow.
                    </p>
                  </div>
                </div>
                <div className="activity-feed">
                  {selectedBotActivity.length > 0 ? (
                    selectedBotActivity.map((entry) => (
                      <article key={entry.id} className={`activity-entry ${entry.category}`}>
                        <div className="activity-meta">
                          <span>{entry.category}</span>
                          <small>{formatDateTime(entry.createdAt)}</small>
                        </div>
                        <strong>{entry.message}</strong>
                        <div className="activity-source">{entry.source}</div>
                      </article>
                    ))
                  ) : (
                    <p className="muted">No activity recorded for the selected bot yet.</p>
                  )}
                </div>
              </section>

              <aside className="right-rail panel">
                {selectedBot ? (
                  <>
                    <ActionGroup title="Session Controls">
                      <div className="quick-grid compact">
                        <button onClick={() => run(() => api.bots.launch(selectedBot.id), 'Bot launch started.').then(async () => refreshAll())}>
                          Launch
                        </button>
                        <button onClick={() => run(() => api.bots.stop(selectedBot.id), 'Graceful stop started.').then(async () => refreshAll())}>
                          Stop
                        </button>
                        <button onClick={() => run(() => api.bots.pause(selectedBot.id), 'Bot paused.').then(async () => refreshAll())}>
                          Pause
                        </button>
                        <button onClick={() => run(() => api.bots.resume(selectedBot.id), 'Bot resumed.').then(async () => refreshAll())}>
                          Resume
                        </button>
                        <button onClick={() => run(() => api.bots.abort(selectedBot.id), 'Abort sent.').then(async () => refreshAll())}>
                          Abort
                        </button>
                        <button
                          onClick={() =>
                            run(() => api.bots.clearQueue(selectedBot.id), 'Queued tasks cleared.').then(
                              async () => refreshAll(),
                            )
                          }
                        >
                          Clear Queue
                        </button>
                        <button onClick={() => run(() => api.bots.focus(selectedBot.id), 'Client focused.')}>
                          Focus Client
                        </button>
                        <button
                          onClick={() =>
                            run(() => api.bots.beginTakeover(selectedBot.id), 'Takeover enabled.').then(
                              async () => refreshAll(),
                            )
                          }
                        >
                          Begin Takeover
                        </button>
                        <button
                          onClick={() =>
                            run(() => api.bots.endTakeover(selectedBot.id), 'Takeover ended.').then(
                              async () => refreshAll(),
                            )
                          }
                        >
                          End Takeover
                        </button>
                      </div>
                    </ActionGroup>

                    <ActionGroup title="Live Tools">
                      <div className="info-card subtle">
                        <span>Stream Mode</span>
                        <strong>
                          {dashboardMode === 'single'
                            ? 'Live feed every 250ms'
                            : 'Live grid every 1s'}
                        </strong>
                      </div>
                      <button
                        onClick={() => run(() => api.bots.saveLiveFrame(selectedBot.id), 'Live frame saved.')}
                      >
                        Save Current Frame
                      </button>
                    </ActionGroup>

                    <ActionGroup title="Replay Buffer">
                      <label>
                        Save Last Seconds
                        <input
                          type="number"
                          min={30}
                          max={300}
                          step={30}
                          value={clipSaveSeconds}
                          onChange={(input) => setClipSaveSeconds(Number(input.target.value) || 300)}
                        />
                      </label>
                      <button
                        onClick={async () => {
                          await run(
                            () => api.bots.saveReplay<MediaClipRecord>(selectedBot.id, { requestedSeconds: clipSaveSeconds }),
                            'Replay clip saved.',
                          );
                          await refreshBotClips(selectedBot.id);
                        }}
                      >
                        Save Rolling Replay
                      </button>
                      <div className="activity-feed">
                        {selectedBotClips.slice(0, 4).map((clip) => (
                          <article key={clip.id} className="activity-entry clip">
                            <div className="activity-meta">
                              <span>{clip.triggerType}</span>
                              <small>{formatDateTime(clip.createdAt)}</small>
                            </div>
                            <strong>{clip.reason || 'Replay clip saved.'}</strong>
                            <div className="activity-source">
                              {clip.durationSeconds}s •{' '}
                              <a href={api.clips.contentUrl(clip.id)} target="_blank" rel="noreferrer">
                                Open Clip
                              </a>
                            </div>
                          </article>
                        ))}
                        {selectedBotClips.length === 0 ? <p className="muted">No replay clips saved yet.</p> : null}
                      </div>
                    </ActionGroup>

                    <ActionGroup title="Quick Actions">
                      <div className="quick-grid compact">
                        {QUICK_ACTIONS.map(([actionType, label]) => (
                          <button key={actionType} onClick={() => queueAction(actionType)}>
                            {label}
                          </button>
                        ))}
                      </div>
                    </ActionGroup>

                    <ActionGroup title="Flower Menu">
                      <input
                        placeholder="Option name"
                        value={flowerOption}
                        onChange={(input) => setFlowerOption(input.target.value)}
                      />
                      <div className="button-row">
                        <button onClick={() => queueAction('auto-repeat-flower', { option: flowerOption })}>
                          Arm Auto-Repeat
                        </button>
                        <button onClick={() => queueAction('auto-repeat-flower.clear')}>Clear</button>
                      </div>
                    </ActionGroup>

                    <ActionGroup title="Presets">
                      <label>
                        Task Preset
                        <select
                          value={selectedTaskPresetId}
                          onChange={(input) => setSelectedTaskPresetId(input.target.value)}
                        >
                          <option value="">Select preset</option>
                          {taskPresets.map((preset) => (
                            <option key={preset.id} value={preset.id}>
                              {preset.name}
                            </option>
                          ))}
                        </select>
                      </label>
                      <button
                        onClick={async () => {
                          if (!selectedTaskPresetId) return;
                          await run(
                            () => api.bots.runTaskPreset<TaskRecord>(selectedBot.id, selectedTaskPresetId),
                            'Task preset queued.',
                          );
                          await refreshAll();
                        }}
                      >
                        Run Task Preset
                      </button>
                      <label>
                        Route Preset
                        <select
                          value={selectedRoutePresetId}
                          onChange={(input) => setSelectedRoutePresetId(input.target.value)}
                        >
                          <option value="">Select route</option>
                          {routes.map((route) => (
                            <option key={route.id} value={route.id}>
                              {route.name}
                            </option>
                          ))}
                        </select>
                      </label>
                      <button
                        onClick={async () => {
                          if (!selectedRoutePresetId) return;
                          await run(
                            () => api.bots.runRoutePreset<TaskRecord>(selectedBot.id, selectedRoutePresetId),
                            'Route queued.',
                          );
                          await refreshAll();
                        }}
                      >
                        Run Route Preset
                      </button>
                    </ActionGroup>

                    <ActionGroup title="Manual Control">
                      <div className="direction-grid">
                        <button onClick={() => sendRemoteInput('KEY_TAP', { keyCode: 87 })}>W</button>
                        <button onClick={() => sendRemoteInput('KEY_TAP', { keyCode: 65 })}>A</button>
                        <button onClick={() => sendRemoteInput('KEY_TAP', { keyCode: 83 })}>S</button>
                        <button onClick={() => sendRemoteInput('KEY_TAP', { keyCode: 68 })}>D</button>
                      </div>
                      <div className="inline-grid">
                        <input
                          type="number"
                          value={manualX}
                          onChange={(input) => setManualX(Number(input.target.value) || 0)}
                        />
                        <input
                          type="number"
                          value={manualY}
                          onChange={(input) => setManualY(Number(input.target.value) || 0)}
                        />
                        <input
                          type="number"
                          value={manualKeyCode}
                          onChange={(input) => setManualKeyCode(Number(input.target.value) || 0)}
                        />
                      </div>
                      <div className="quick-grid compact">
                        <button onClick={() => sendRemoteInput('MOUSE_MOVE', { x: manualX, y: manualY })}>
                          Move Mouse
                        </button>
                        <button onClick={() => sendRemoteInput('LEFT_CLICK', { x: manualX, y: manualY })}>
                          Left Click
                        </button>
                        <button onClick={() => sendRemoteInput('RIGHT_CLICK', { x: manualX, y: manualY })}>
                          Right Click
                        </button>
                        <button onClick={() => sendRemoteInput('MIDDLE_CLICK', { x: manualX, y: manualY })}>
                          Middle Click
                        </button>
                        <button onClick={() => sendRemoteInput('KEY_TAP', { keyCode: manualKeyCode })}>
                          Tap Key
                        </button>
                        <button onClick={() => sendRemoteInput('KEY_PRESS', { keyCode: manualKeyCode })}>
                          Press Key
                        </button>
                      </div>
                    </ActionGroup>

                    <ActionGroup title="Advanced Action">
                      <select value={advancedActionType} onChange={(input) => setAdvancedActionType(input.target.value)}>
                        {ADVANCED_ACTIONS.map((option) => (
                          <option key={option} value={option}>
                            {option}
                          </option>
                        ))}
                      </select>
                      <textarea
                        value={advancedActionParams}
                        onChange={(input) => setAdvancedActionParams(input.target.value)}
                      />
                      <button onClick={() => submitAdvancedAction().catch(() => undefined)}>Queue Action</button>
                    </ActionGroup>
                  </>
                ) : (
                  <p>Select a bot to unlock controls.</p>
                )}
              </aside>
            </div>
            {sheetBot ? <CharacterSheetModal bot={sheetBot} onClose={() => setSheetBotId(null)} /> : null}

            <section className="panel admin-panel">
              <div className="section-heading">
                <div>
                  <h3>Bot Configuration</h3>
                  <p className="muted">
                    Create, edit, or remove bot profiles without leaving the live command deck.
                  </p>
                </div>
                <button onClick={resetBotForm}>New Bot</button>
              </div>
              <div className="admin-grid">
                <div className="bot-admin-list">
                  {bots.map((bot) => (
                    <button
                      key={bot.id}
                      className={selectedBotId === bot.id ? 'bot-admin-card active' : 'bot-admin-card'}
                      onClick={() => setSelectedBotId(bot.id)}
                    >
                      <strong>{bot.name}</strong>
                      <span>{bot.status}</span>
                      <small>{bot.preferredCharacter || 'No preferred character'}</small>
                    </button>
                  ))}
                </div>
                <div className="form-grid">
                  <div className="button-row">
                    <h4>{selectedBot ? 'Create Or Update Bot' : 'Create Bot'}</h4>
                    {selectedBot ? (
                      <button
                        onClick={() =>
                          setBotForm({
                            name: selectedBot.name,
                            accountId: selectedBot.accountId ?? '',
                            clientInstallPath: selectedBot.clientInstallPath,
                            preferredCharacter: selectedBot.preferredCharacter ?? '',
                            preferredWorld: selectedBot.preferredWorld ?? '',
                            profileName: selectedBot.profileName ?? '',
                            launchCommand: selectedBot.launchCommand ?? '',
                          })
                        }
                      >
                        Load Selected
                      </button>
                    ) : null}
                  </div>
                  <input
                    placeholder="Bot name"
                    value={botForm.name}
                    onChange={(input) => setBotForm({ ...botForm, name: input.target.value })}
                  />
                  <select
                    value={botForm.accountId}
                    onChange={(input) => setBotForm({ ...botForm, accountId: input.target.value })}
                  >
                    <option value="">No account</option>
                    {accounts.map((account) => (
                      <option key={account.id} value={account.id}>
                        {account.name}
                      </option>
                    ))}
                  </select>
                  <input
                    placeholder="Client install path"
                    value={botForm.clientInstallPath}
                    onChange={(input) =>
                      setBotForm({ ...botForm, clientInstallPath: input.target.value })
                    }
                  />
                  <input
                    placeholder="Preferred character"
                    value={botForm.preferredCharacter}
                    onChange={(input) =>
                      setBotForm({ ...botForm, preferredCharacter: input.target.value })
                    }
                  />
                  <input
                    placeholder="Preferred world"
                    value={botForm.preferredWorld}
                    onChange={(input) => setBotForm({ ...botForm, preferredWorld: input.target.value })}
                  />
                  <input
                    placeholder="Profile name"
                    value={botForm.profileName}
                    onChange={(input) => setBotForm({ ...botForm, profileName: input.target.value })}
                  />
                  <input
                    placeholder="Optional launch command"
                    value={botForm.launchCommand}
                    onChange={(input) => setBotForm({ ...botForm, launchCommand: input.target.value })}
                  />
                  <div className="button-row">
                    <button
                      onClick={async () => {
                        if (selectedBot && botForm.name === selectedBot.name) {
                          await run(() => api.bots.update(selectedBot.id, botForm), 'Bot updated.');
                        } else {
                          await run(() => api.bots.create(botForm), 'Bot created.');
                        }
                        resetBotForm();
                        await refreshAll();
                      }}
                    >
                      Save Bot
                    </button>
                    {selectedBot ? (
                      <button
                        onClick={async () => {
                          await run(() => api.bots.remove(selectedBot.id), 'Bot removed.');
                          setSelectedBotId(null);
                          await refreshAll();
                        }}
                      >
                        Delete Selected
                      </button>
                    ) : null}
                  </div>
                </div>
              </div>
            </section>
          </section>
        ) : null}

        {view === 'accounts' ? (
          <section className="management-grid">
            <div className="panel">
              <div className="section-heading">
                <h3>Account Vault</h3>
                <button onClick={resetAccountForm}>New Account</button>
              </div>
              <div className="list-grid compact">
                {accounts.map((account) => (
                  <article key={account.id} className="event-card">
                    <strong>{account.name}</strong>
                    <span>{account.username}</span>
                    <small>{account.characterName || 'No default character'}</small>
                    <div className="button-row">
                      <button
                        onClick={() => {
                          setEditingAccountId(account.id);
                          setAccountForm({
                            name: account.name,
                            username: account.username,
                            secret: '',
                            characterName: account.characterName ?? '',
                          });
                        }}
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => run(() => api.accounts.remove(account.id), 'Account removed.').then(async () => refreshAll())}
                      >
                        Delete
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>

            <div className="panel">
              <h3>{editingAccountId ? 'Update Account' : 'Create Account'}</h3>
              <div className="form-grid">
                <input
                  placeholder="Display name"
                  value={accountForm.name}
                  onChange={(input) => setAccountForm({ ...accountForm, name: input.target.value })}
                />
                <input
                  placeholder="Username"
                  value={accountForm.username}
                  onChange={(input) => setAccountForm({ ...accountForm, username: input.target.value })}
                />
                <input
                  placeholder="Secret / password / token"
                  value={accountForm.secret}
                  onChange={(input) => setAccountForm({ ...accountForm, secret: input.target.value })}
                />
                <input
                  placeholder="Default character"
                  value={accountForm.characterName}
                  onChange={(input) =>
                    setAccountForm({ ...accountForm, characterName: input.target.value })
                  }
                />
                <div className="button-row">
                  <button
                    onClick={async () => {
                      if (editingAccountId) {
                        await run(() => api.accounts.update(editingAccountId, accountForm), 'Account updated.');
                      } else {
                        await run(() => api.accounts.create(accountForm), 'Account stored.');
                      }
                      resetAccountForm();
                      await refreshAll();
                    }}
                  >
                    Save Account
                  </button>
                  <button onClick={resetAccountForm}>Reset</button>
                </div>
              </div>
            </div>
          </section>
        ) : null}

        {view === 'routes' ? (
          <section className="management-grid">
            <div className="panel">
              <div className="section-heading">
                <h3>Route Presets</h3>
                <button onClick={resetRouteForm}>New Route</button>
              </div>
              <div className="list-grid compact">
                {routes.map((route) => (
                  <article key={route.id} className="event-card">
                    <strong>{route.name}</strong>
                    <small>{summarizeRoute(route.route)}</small>
                    <div className="button-row">
                      <button
                        onClick={() => {
                          setEditingRouteId(route.id);
                          setRouteForm({ name: route.name, routeJson: formatJson(route.route) });
                        }}
                      >
                        Edit
                      </button>
                      {selectedBot ? (
                        <button
                          onClick={() =>
                            run(
                              () => api.bots.runRoutePreset<TaskRecord>(selectedBot.id, route.id),
                              'Route queued.',
                            ).then(async () => refreshAll())
                          }
                        >
                          Queue To Selected Bot
                        </button>
                      ) : null}
                      <button onClick={() => run(() => api.routes.remove(route.id), 'Route removed.').then(async () => refreshAll())}>
                        Delete
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>

            <div className="panel">
              <h3>{editingRouteId ? 'Update Route Preset' : 'Create Route Preset'}</h3>
              <div className="form-grid">
                <input
                  placeholder="Route name"
                  value={routeForm.name}
                  onChange={(input) => setRouteForm({ ...routeForm, name: input.target.value })}
                />
                <textarea
                  value={routeForm.routeJson}
                  onChange={(input) => setRouteForm({ ...routeForm, routeJson: input.target.value })}
                />
                <div className="button-row">
                  <button
                    onClick={async () => {
                      const payload = { name: routeForm.name, route: JSON.parse(routeForm.routeJson) };
                      if (editingRouteId) {
                        await run(() => api.routes.update(editingRouteId, payload), 'Route updated.');
                      } else {
                        await run(() => api.routes.create(payload), 'Route created.');
                      }
                      resetRouteForm();
                      await refreshAll();
                    }}
                  >
                    Save Route
                  </button>
                  <button onClick={resetRouteForm}>Reset</button>
                </div>
              </div>
            </div>
          </section>
        ) : null}

        {view === 'presets' ? (
          <section className="management-grid">
            <div className="panel">
              <div className="section-heading">
                <h3>Task Presets</h3>
                <button onClick={resetTaskPresetForm}>New Preset</button>
              </div>
              <div className="list-grid compact">
                {taskPresets.map((preset) => (
                  <article key={preset.id} className="event-card">
                    <strong>{preset.name}</strong>
                    <span>{preset.actionType}</span>
                    <small>{formatJson(preset.params)}</small>
                    <div className="button-row">
                      <button
                        onClick={() => {
                          setEditingTaskPresetId(preset.id);
                          setTaskPresetForm({
                            name: preset.name,
                            actionType: preset.actionType,
                            paramsJson: formatJson(preset.params),
                          });
                        }}
                      >
                        Edit
                      </button>
                      {selectedBot ? (
                        <button
                          onClick={() =>
                            run(
                              () => api.bots.runTaskPreset<TaskRecord>(selectedBot.id, preset.id),
                              'Task preset queued.',
                            ).then(async () => refreshAll())
                          }
                        >
                          Queue To Selected Bot
                        </button>
                      ) : null}
                      <button
                        onClick={() =>
                          run(() => api.taskPresets.remove(preset.id), 'Task preset removed.').then(async () => refreshAll())
                        }
                      >
                        Delete
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>

            <div className="panel">
              <h3>{editingTaskPresetId ? 'Update Task Preset' : 'Create Task Preset'}</h3>
              <div className="form-grid">
                <input
                  placeholder="Preset name"
                  value={taskPresetForm.name}
                  onChange={(input) => setTaskPresetForm({ ...taskPresetForm, name: input.target.value })}
                />
                <select
                  value={taskPresetForm.actionType}
                  onChange={(input) => setTaskPresetForm({ ...taskPresetForm, actionType: input.target.value })}
                >
                  {ADVANCED_ACTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
                <textarea
                  value={taskPresetForm.paramsJson}
                  onChange={(input) => setTaskPresetForm({ ...taskPresetForm, paramsJson: input.target.value })}
                />
                <div className="button-row">
                  <button
                    onClick={async () => {
                      const payload = {
                        name: taskPresetForm.name,
                        actionType: taskPresetForm.actionType,
                        params: JSON.parse(taskPresetForm.paramsJson),
                      };
                      if (editingTaskPresetId) {
                        await run(() => api.taskPresets.update(editingTaskPresetId, payload), 'Task preset updated.');
                      } else {
                        await run(() => api.taskPresets.create(payload), 'Task preset created.');
                      }
                      resetTaskPresetForm();
                      await refreshAll();
                    }}
                  >
                    Save Preset
                  </button>
                  <button onClick={resetTaskPresetForm}>Reset</button>
                </div>
              </div>
            </div>
          </section>
        ) : null}

        {view === 'tasks' ? (
          <section className="panel">
            <h3>Task Queue</h3>
            <div className="list-grid compact">
              {tasks.map((task) => (
                <article key={task.id} className="event-card">
                  <strong>{humanActionLabel(task.actionType)}</strong>
                  <span>{task.status}</span>
                  <small>{task.botId}</small>
                  <pre>{formatJson(task.params)}</pre>
                  {['QUEUED', 'DISPATCHED', 'RUNNING'].includes(task.status) ? (
                    <button onClick={() => run(() => api.tasks.cancel(task.id), 'Task canceled.').then(async () => refreshAll())}>
                      Cancel Task
                    </button>
                  ) : null}
                </article>
              ))}
            </div>
          </section>
        ) : null}

        {view === 'audit' ? (
          <section className="panel">
            <h3>Audit Log</h3>
            <div className="list-grid compact">
              {audit.map((entry) => {
                const details = asRecord(entry.details) ?? {};
                const summary = auditSummary(entry);
                const logTail = typeof details.logTail === 'string' ? details.logTail : '';
                const launchTarget = typeof details.launchTarget === 'string' ? details.launchTarget : '';
                const logPath = typeof details.logPath === 'string' ? details.logPath : '';
                const severity = auditSeverity(entry);
                const botName = entry.botId ? bots.find((bot) => bot.id === entry.botId)?.name ?? entry.botId : 'System';

                return (
                  <article key={entry.id} className={`event-card audit-card ${severity}`}>
                    <div className="audit-header">
                      <strong>{entry.eventType}</strong>
                      <span className={`audit-badge ${severity}`}>{botName}</span>
                    </div>
                    <span>{entry.actor}</span>
                    <small>{formatDateTime(entry.createdAt)}</small>
                    {summary ? <p className="audit-summary">{summary}</p> : null}
                    {launchTarget ? <div className="audit-meta">Launch target: {launchTarget}</div> : null}
                    {logPath ? <div className="audit-meta">Log file: {logPath}</div> : null}
                    {logTail ? <pre>{logTail}</pre> : null}
                    <details className="audit-raw">
                      <summary>Raw details</summary>
                      <pre>{formatJson(entry.details)}</pre>
                    </details>
                  </article>
                );
              })}
            </div>
          </section>
        ) : null}
      </main>

      <aside className="server-rail">
        <div className="rail-top">
          <span className="eyebrow">Server</span>
          <div className="summary-stack">
            <div className="summary-chip">
              <strong>{fleetSummary.total}</strong>
              <span>Bots</span>
            </div>
            <div className="summary-chip">
              <strong>{fleetSummary.online}</strong>
              <span>Online</span>
            </div>
            <div className="summary-chip">
              <strong>{fleetSummary.running}</strong>
              <span>Running</span>
            </div>
          </div>
        </div>

        <nav className="server-nav">
          {SERVER_VIEWS.map((item) => (
            <button
              key={item.id}
              className={view === item.id ? 'server-nav-button active' : 'server-nav-button'}
              onClick={() => setView(item.id)}
            >
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <button
          className="server-nav-button logout"
          onClick={() => {
            void api.logout().catch(() => undefined);
            setOperatorToken(null);
            setToken(null);
          }}
        >
          Sign Out
        </button>
      </aside>
    </div>
  );
}

function BotTabCard({
  bot,
  active,
  onSelect,
  onOpenSheet,
}: {
  bot: BotRecord;
  active: boolean;
  onSelect: () => void;
  onOpenSheet: () => void;
}) {
  const state = asRecord(bot.lastState);
  const currentTask = asRecord(state?.currentTask);
  const actionType = typeof currentTask?.actionType === 'string' ? currentTask.actionType : '';
  const sessionStatus = typeof state?.sessionStatus === 'string' ? state.sessionStatus : 'Unknown';

  return (
    <button className={active ? 'bot-tab active' : 'bot-tab'} onClick={onSelect}>
      <div className="bot-tab-copy">
        <div className="bot-tab-title">
          <strong>{bot.name}</strong>
          <span className={`status-dot ${statusTone(bot.status)}`} />
        </div>
        <small>{bot.status}</small>
        <small>{actionType ? humanActionLabel(actionType) : sessionStatus}</small>
        <div className="bot-tab-actions">
          <button
            type="button"
            className="sheet-icon-button"
            aria-label={`Open ${bot.name} character sheet`}
            onClick={(event) => {
              event.stopPropagation();
              onOpenSheet();
            }}
          >
            <CharacterSheetIcon />
          </button>
        </div>
      </div>
    </button>
  );
}

function CharacterSheetModal({
  bot,
  onClose,
}: {
  bot: BotRecord;
  onClose: () => void;
}) {
  const [detailEntry, setDetailEntry] = useState<SheetEntry | null>(null);
  const [detailData, setDetailData] = useState<WikiDetailResponse | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const state = asRecord(bot.lastState) ?? {};
  const inventory = asRecord(state.inventory);
  const currentTask = asRecord(state.currentTask);
  const position = asRecord(state.position);
  const routeInfo = asRecord(state.routeInfo);
  const visibleStats = asRecord(state.visibleStats) ?? {};
  const attributeDetails = toStatEntries(
    state.attributeDetails,
    asRecord(state.attributes),
    fallbackVisibleStatEntries(visibleStats, 'attribute'),
    'attribute',
  );
  const skillDetails = toStatEntries(
    state.skillDetails,
    asRecord(state.skills),
    fallbackVisibleStatEntries(visibleStats, 'ability'),
    'ability',
  );
  const knownSkillDetails = toNamedEntries(state.knownSkillDetails, state.knownSkills, 'skill');
  const credoDetails = toNamedEntries(state.credoDetails, state.credos, 'credo');
  const equipmentDetails = toNamedEntries(state.equipmentDetails, state.equipment, 'item');
  const inventoryDetails = toNamedEntries(inventory?.itemDetails, inventory?.items, 'item');
  const handItemDetail = toSingleNamedEntry(inventory?.handItemDetail, inventory?.handItem, 'item');
  const currentQuests = toQuestEntries(state.currentQuests);
  const meterEntries = [
    meterEntry('health', 'Health', healthLabel(state.health), 'Represents your current well-being, including soft and hard health values.'),
    meterEntry('stamina', 'Stamina', meterLabel(state.stamina), "Represents the character's current physical exertion reserve."),
    meterEntry('energy', 'Energy', meterLabel(state.energy), "Represents the character's nourishment reserve and long-term efficiency."),
  ];
  const learningPoints = typeof state.learningPoints === 'number' ? state.learningPoints : null;
  const experience = typeof state.experience === 'number' ? state.experience : null;
  const currentActionType = typeof currentTask?.actionType === 'string' ? currentTask.actionType : '';
  const overviewStats = [
    ['Session', String(state.sessionStatus ?? 'Unknown')],
    ['Current Activity', currentActionType ? humanActionLabel(currentActionType) : 'Standing by'],
    ['Position', position ? `${shortNum(position.x)}, ${shortNum(position.y)}` : 'Unknown'],
    ['Route', routeInfo?.active ? `${String(routeInfo.checkpointCount ?? 0)} checkpoints` : 'Inactive'],
    ['Inventory', `${String(inventory?.itemCount ?? 0)} items`],
    ['Hand Item', handItemDetail?.label ?? String(inventory?.handItem ?? 'Empty')],
  ];
  const progressStats = [
    ['Learning Points', learningPoints !== null ? learningPoints.toLocaleString() : 'Unknown'],
    ['Experience', experience !== null ? experience.toLocaleString() : 'Unknown'],
    ['Known Skills', String(knownSkillDetails.length || asArray(state.knownSkills).length || 0)],
    ['Active Quests', String(currentQuests.length)],
  ];
  function selectDetailEntry(entry: SheetEntry) {
    setDetailData(null);
    setDetailError(null);
    setDetailLoading(true);
    setDetailEntry(entry);
  }

  function closeDetailEntry() {
    setDetailEntry(null);
    setDetailData(null);
    setDetailError(null);
    setDetailLoading(false);
  }

  useEffect(() => {
    if (!detailEntry) return;
    let cancelled = false;
    void api.wiki
      .detail<WikiDetailResponse>({
        label: detailEntry.label,
        kind: detailEntry.kind,
        wikiTitle: detailEntry.wikiTitle,
        wikiSection: detailEntry.wikiSection,
        wikiUrl: detailEntry.wikiUrl,
      })
      .then((response) => {
        if (!cancelled) {
          setDetailData(response);
        }
      })
      .catch((error: Error) => {
        if (!cancelled) {
          setDetailError(error.message);
          setDetailData(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setDetailLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [detailEntry]);

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet-modal panel" onClick={(event) => event.stopPropagation()}>
        <div className="sheet-header">
          <div>
            <span className="eyebrow">Character Sheet</span>
            <h3>{bot.name}</h3>
            <p className="muted">
              {bot.preferredCharacter || 'No preferred character'} • {bot.preferredWorld || 'Unknown world'}
            </p>
          </div>
          <button type="button" className="sheet-close" onClick={onClose}>
            Close
          </button>
        </div>

        <div className="sheet-vitals">
          {meterEntries.map((entry) => (
            <MetricCard
              key={entry.key}
              label={entry.label}
              value={entry.value}
              onClick={() => selectDetailEntry(entry)}
              title={hoverText(entry)}
            />
          ))}
        </div>

        <div className="sheet-grid">
          <section className="sheet-section">
            <h4>Overview</h4>
            <div className="sheet-stat-grid">
              {overviewStats.map(([label, value]) => (
                <div key={label} className="info-card subtle">
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </div>
          </section>

          <section className="sheet-section">
            <h4>Progress</h4>
            <div className="sheet-stat-grid">
              {progressStats.map(([label, value]) => (
                <div key={label} className="info-card subtle">
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </div>
          </section>

          <section className="sheet-section">
            <h4>Attributes</h4>
            <div className="sheet-icon-stat-grid">
              {attributeDetails.map((entry) => (
                <StatDetailCard key={entry.key || entry.label} entry={entry} onSelect={selectDetailEntry} />
              ))}
              {attributeDetails.length === 0 ? <div className="info-card subtle">No attribute data loaded yet.</div> : null}
            </div>
          </section>

          <section className="sheet-section">
            <h4>Skills</h4>
            <div className="sheet-icon-stat-grid">
              {skillDetails.map((entry) => (
                <StatDetailCard key={entry.key || entry.label} entry={entry} onSelect={selectDetailEntry} />
              ))}
              {skillDetails.length === 0 ? <div className="info-card subtle">No skill data loaded yet.</div> : null}
            </div>
          </section>

          <section className="sheet-section">
            <h4>Known Skills</h4>
            <div className="sheet-icon-grid">
              {knownSkillDetails.map((entry, index) => (
                <NamedEntryTile key={`${entry.label}-${index}`} entry={entry} onSelect={selectDetailEntry} />
              ))}
              {knownSkillDetails.length === 0 ? <div className="info-card subtle">No known skills loaded yet.</div> : null}
            </div>
          </section>

          <section className="sheet-section">
            <h4>Credos</h4>
            <div className="sheet-icon-grid">
              {credoDetails.map((entry, index) => (
                <NamedEntryTile key={`${entry.label}-${index}`} entry={entry} onSelect={selectDetailEntry} />
              ))}
              {credoDetails.length === 0 ? <div className="info-card subtle">No credo data loaded yet.</div> : null}
            </div>
          </section>

          <section className="sheet-section">
            <h4>Equipment</h4>
            <div className="sheet-icon-grid">
              {equipmentDetails.map((entry, index) => (
                <NamedEntryTile key={`${entry.label}-${index}`} entry={entry} onSelect={selectDetailEntry} />
              ))}
              {equipmentDetails.length === 0 ? <div className="info-card subtle">No equipment data loaded yet.</div> : null}
            </div>
          </section>

          <section className="sheet-section">
            <h4>Inventory Preview</h4>
            <div className="sheet-icon-grid">
              {inventoryDetails.map((entry, index) => (
                <NamedEntryTile key={`${entry.label}-${index}`} entry={entry} onSelect={selectDetailEntry} />
              ))}
              {inventoryDetails.length === 0 ? <div className="info-card subtle">No inventory preview loaded yet.</div> : null}
            </div>
          </section>

          <section className="sheet-section sheet-section-wide">
            <h4>Current Quests</h4>
            <div className="sheet-quest-list">
              {currentQuests.map((quest, index) => (
                <QuestCard key={`${quest.id ?? quest.label}-${index}`} quest={quest} onSelect={selectDetailEntry} />
              ))}
              {currentQuests.length === 0 ? <div className="info-card subtle">No active quests loaded yet.</div> : null}
            </div>
          </section>
        </div>

        {detailEntry ? (
          <SheetDetailWindow
            entry={detailEntry}
            detail={detailData}
            loading={detailLoading}
            error={detailError}
            onClose={closeDetailEntry}
          />
        ) : null}
      </div>
    </div>
  );
}

function CharacterSheetIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M7 3h8a3 3 0 0 1 3 3v12.5a1.5 1.5 0 0 0-1.5-1.5H7a2 2 0 0 0-2 2V5a2 2 0 0 1 2-2Zm1 4a3 3 0 1 0 6 0a3 3 0 0 0-6 0Zm-1 9h9.5a2.5 2.5 0 0 1 1.5.5V6a2 2 0 0 0-2-2H7a1 1 0 0 0-1 1v11.8A3 3 0 0 1 7 16Zm4-5c-2.2 0-4 1.12-4 2.5V14h8v-.5c0-1.38-1.8-2.5-4-2.5Z"
        fill="currentColor"
      />
    </svg>
  );
}

type SheetEntry = {
  key: string;
  label: string;
  icon: string;
  wikiUrl: string;
  resourceName: string;
  value: string;
  pursuing: boolean;
  slotIndex: number | null;
  summary: string;
  kind: string;
  wikiTitle: string;
  wikiSection: string;
};

type QuestEntry = SheetEntry & {
  id: number | null;
  done: number | null;
  mtime: number | null;
  conditions: Array<{ description: string; status: string; done: number | null }>;
};

type WikiDetailResponse = {
  title: string;
  summary: string;
  sourceUrl: string;
  facts: Array<{ label: string; value: string }>;
  sections: Array<{ title: string; lines: string[] }>;
};

function StatDetailCard({ entry, onSelect }: { entry: SheetEntry; onSelect: (entry: SheetEntry) => void }) {
  return (
    <SheetEntryButton entry={entry} className="sheet-entry-card sprite-stat-tile" onSelect={onSelect}>
      <SheetEntryIcon entry={entry} className="sheet-entry-icon large" />
      <strong className="sheet-entry-value-badge">{entry.value}</strong>
    </SheetEntryButton>
  );
}

function NamedEntryTile({ entry, onSelect }: { entry: SheetEntry; onSelect: (entry: SheetEntry) => void }) {
  return (
    <SheetEntryButton
      entry={entry}
      className={entry.pursuing ? 'sheet-icon-tile sprite-only-tile pursuing' : 'sheet-icon-tile sprite-only-tile'}
      onSelect={onSelect}
    >
      <SheetEntryIcon entry={entry} className="sheet-entry-icon" />
      {entry.pursuing ? <span className="sheet-entry-flag">P</span> : null}
    </SheetEntryButton>
  );
}

function QuestCard({ quest, onSelect }: { quest: QuestEntry; onSelect: (entry: SheetEntry) => void }) {
  return (
    <SheetEntryButton entry={quest} className="info-card subtle sheet-quest-card" onSelect={onSelect}>
      <div className="sheet-quest-head">
        <div className="sheet-quest-title">
          <SheetEntryIcon entry={quest} className="sheet-entry-icon" />
          <div className="sheet-entry-copy">
            <strong>{quest.label}</strong>
            <small>{quest.conditions.length} objectives</small>
          </div>
        </div>
        <small>{questStatusLabel(quest.done)}</small>
      </div>
      {quest.conditions.length > 0 ? (
        <div className="sheet-condition-list">
          {quest.conditions.map((condition, index) => (
            <div key={`${quest.id ?? quest.label}-${index}`} className="sheet-condition">
              <span>{condition.description}</span>
              <small>{condition.status || objectiveStatusLabel(condition.done)}</small>
            </div>
          ))}
        </div>
      ) : null}
    </SheetEntryButton>
  );
}

function SheetEntryButton({
  entry,
  className,
  onSelect,
  children,
}: {
  entry: SheetEntry;
  className: string;
  onSelect: (entry: SheetEntry) => void;
  children: ReactNode;
}) {
  return (
    <button type="button" className={className} title={hoverText(entry)} onClick={() => onSelect(entry)}>
      {children}
    </button>
  );
}

function SheetEntryIcon({
  entry,
  className,
}: {
  entry: SheetEntry;
  className: string;
}) {
  const candidates = useMemo(() => iconCandidates(entry), [entry]);
  const candidateKey = candidates.join('\u0000');

  return <SheetEntryIconImage key={candidateKey} candidates={candidates} className={className} />;
}

function SheetEntryIconImage({
  candidates,
  className,
}: {
  candidates: string[];
  className: string;
}) {
  const [index, setIndex] = useState(0);
  const src = candidates[index] ?? '';

  return src ? (
    <img
      src={src}
      alt=""
      className={className}
      loading="lazy"
      onError={() => setIndex((current) => (current + 1 < candidates.length ? current + 1 : current))}
    />
  ) : (
    <span className={`${className} placeholder`} aria-hidden="true">
      ?
    </span>
  );
}

function SheetDetailWindow({
  entry,
  detail,
  loading,
  error,
  onClose,
}: {
  entry: SheetEntry;
  detail: WikiDetailResponse | null;
  loading: boolean;
  error: string | null;
  onClose: () => void;
}) {
  return (
    <div className="sheet-detail-backdrop" onClick={onClose}>
      <div className="sheet-detail-window panel" onClick={(event) => event.stopPropagation()}>
        <div className="sheet-detail-header">
          <div className="sheet-detail-title">
            <SheetEntryIcon entry={entry} className="sheet-entry-icon large" />
            <div>
              <span className="eyebrow">{entry.kind || 'Entry'}</span>
              <h4>{detail?.title ?? entry.label}</h4>
              <p className="muted">{detail?.summary ?? entry.summary ?? 'Loading details...'}</p>
            </div>
          </div>
          <div className="sheet-detail-actions">
            {detail?.sourceUrl || entry.wikiUrl ? (
              <a href={detail?.sourceUrl || entry.wikiUrl} target="_blank" rel="noreferrer" className="sheet-detail-link">
                Open Wiki
              </a>
            ) : null}
            <button type="button" className="sheet-close" onClick={onClose}>
              Close
            </button>
          </div>
        </div>

        {loading ? <div className="info-card subtle">Loading wiki details…</div> : null}
        {error ? <div className="info-card subtle">{error}</div> : null}

        {!loading && !error ? (
          <div className="sheet-detail-grid">
            {detail?.facts && detail.facts.length > 0 ? (
              <section className="sheet-section">
                <h5>Quick Facts</h5>
                <div className="sheet-fact-grid">
                  {detail.facts.map((fact, index) => (
                    <div key={`${fact.label}-${index}`} className="info-card subtle">
                      <span>{fact.label}</span>
                      <strong>{fact.value}</strong>
                    </div>
                  ))}
                </div>
              </section>
            ) : null}

            {detail?.sections?.map((section, index) => (
              <section key={`${section.title}-${index}`} className="sheet-section">
                <h5>{section.title}</h5>
                <div className="sheet-detail-lines">
                  {section.lines.map((line, lineIndex) => (
                    <div key={`${section.title}-${lineIndex}`} className="info-card subtle">
                      <span>{line}</span>
                    </div>
                  ))}
                </div>
              </section>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function BotLiveVideo({
  bot,
  compact = false,
}: {
  bot: BotRecord;
  compact?: boolean;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [connectionState, setConnectionState] = useState('connecting');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let disposed = false;
    let peerConnection: RTCPeerConnection | null = null;
    const videoElement = videoRef.current;

    async function connect() {
      setConnectionState('connecting');
      setError(null);
      const pc = new RTCPeerConnection({ iceServers: [] });
      peerConnection = pc;
      pc.onconnectionstatechange = () => {
        if (!disposed) {
          setConnectionState(pc.connectionState || 'connecting');
        }
      };
      pc.ontrack = (event) => {
        if (disposed || !videoElement) {
          return;
        }
        const [stream] = event.streams;
        const mediaStream = stream ?? new MediaStream([event.track]);
        videoElement.srcObject = mediaStream;
        void videoElement.play().catch(() => undefined);
        setConnectionState('live');
      };
      pc.addTransceiver('video', { direction: 'recvonly' });
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      await waitForIceGatheringComplete(pc);
      if (!pc.localDescription?.sdp || !pc.localDescription.type) {
        throw new Error('Failed to build WebRTC offer.');
      }
      const answer = await api.bots.webrtcOffer<{ sdp: string; type: RTCSdpType }>(bot.id, {
        sdp: pc.localDescription.sdp,
        type: pc.localDescription.type,
      });
      if (disposed) {
        return;
      }
      await pc.setRemoteDescription(answer);
    }

    void connect().catch((reason: unknown) => {
      if (disposed) {
        return;
      }
      const message = reason instanceof Error ? reason.message : 'Failed to start WebRTC video.';
      setError(message);
      setConnectionState('error');
    });

    return () => {
      disposed = true;
      if (videoElement) {
        videoElement.pause();
        videoElement.srcObject = null;
      }
      if (peerConnection) {
        peerConnection.close();
      }
    };
  }, [bot.id]);

  return (
    <div className={compact ? 'video-surface compact' : 'video-surface'}>
      <video ref={videoRef} autoPlay muted playsInline />
      <div className="video-status">
        {error ? error : connectionState === 'live' ? 'Live' : 'Connecting video…'}
      </div>
    </div>
  );
}

function ActionGroup({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="action-group">
      <h4>{title}</h4>
      {children}
    </section>
  );
}

async function waitForIceGatheringComplete(pc: RTCPeerConnection) {
  if (pc.iceGatheringState === 'complete') {
    return;
  }
  await new Promise<void>((resolve) => {
    function checkState() {
      if (pc.iceGatheringState === 'complete') {
        pc.removeEventListener('icegatheringstatechange', checkState);
        resolve();
      }
    }
    pc.addEventListener('icegatheringstatechange', checkState);
    setTimeout(() => {
      pc.removeEventListener('icegatheringstatechange', checkState);
      resolve();
    }, 4000);
  });
}

function MetricCard({
  label,
  value,
  onClick,
  title,
}: {
  label: string;
  value: string;
  onClick?: () => void;
  title?: string;
}) {
  const className = onClick ? 'metric-card metric-card-button' : 'metric-card';
  const content = (
    <>
      <span>{label}</span>
      <strong>{value}</strong>
    </>
  );
  return onClick ? (
    <button type="button" className={className} onClick={onClick} title={title}>
      {content}
    </button>
  ) : (
    <article className={className} title={title}>
      {content}
    </article>
  );
}

function toStatEntries(
  detailsValue: unknown,
  recordValue: Record<string, unknown> | null,
  fallbackEntries: SheetEntry[],
  kind: 'attribute' | 'ability',
): SheetEntry[] {
  const detailEntries = asArray(detailsValue)
    .map((entry) => normalizeSheetEntry(entry))
    .filter((entry): entry is SheetEntry => entry !== null)
    .sort((left, right) => left.label.localeCompare(right.label));
  if (detailEntries.length > 0) {
    return dedupeEntries(detailEntries);
  }

  const recordEntries = sortedEntries(recordValue ?? {})
    .map(([label, value]) => {
      if (typeof value !== 'number') {
        return null;
      }
      return fallbackStatEntry(label, value, kind);
    })
    .filter((entry): entry is SheetEntry => entry !== null);
  if (recordEntries.length > 0) {
    return dedupeEntries(recordEntries);
  }

  return dedupeEntries(fallbackEntries);
}

function toNamedEntries(detailsValue: unknown, fallbackValue: unknown, kind = ''): SheetEntry[] {
  const detailEntries = asArray(detailsValue)
    .map((entry) => normalizeSheetEntry(entry))
    .filter((entry): entry is SheetEntry => entry !== null);
  if (detailEntries.length > 0) {
    return dedupeEntries(detailEntries.sort((left, right) => left.label.localeCompare(right.label)));
  }

  return dedupeEntries(
    asArray(fallbackValue)
    .map((value, index) => {
      if (typeof value !== 'string' || !value.trim()) {
        return null;
      }
      return fallbackNamedEntry(`${value}-${index}`, value, '', kind);
    })
    .filter((entry): entry is SheetEntry => entry !== null),
  );
}

function toSingleNamedEntry(detailValue: unknown, fallbackValue: unknown, kind = ''): SheetEntry | null {
  const detail = normalizeSheetEntry(detailValue);
  if (detail) {
    return detail;
  }
  if (typeof fallbackValue !== 'string' || !fallbackValue.trim()) {
    return null;
  }
  return fallbackNamedEntry(fallbackValue, fallbackValue, '', kind);
}

function toQuestEntries(value: unknown): QuestEntry[] {
  return asArray(value)
    .map((entry) => {
      const record = asRecord(entry);
      if (!record) {
        return null;
      }
      const base = normalizeSheetEntry({
        ...record,
        label: record.title,
      });
      if (!base) {
        return null;
      }
      return {
        ...base,
        id: typeof record.id === 'number' ? record.id : Number.isFinite(Number(record.id)) ? Number(record.id) : null,
        done: typeof record.done === 'number' ? record.done : Number.isFinite(Number(record.done)) ? Number(record.done) : null,
        mtime: typeof record.mtime === 'number' ? record.mtime : Number.isFinite(Number(record.mtime)) ? Number(record.mtime) : null,
        conditions: asArray(record.conditions).map((condition) => {
          const conditionRecord = asRecord(condition);
          return {
            description: String(conditionRecord?.description ?? 'Objective'),
            status: String(conditionRecord?.status ?? ''),
            done:
              typeof conditionRecord?.done === 'number'
                ? conditionRecord.done
                : Number.isFinite(Number(conditionRecord?.done))
                  ? Number(conditionRecord?.done)
                  : null,
          };
        }),
      } satisfies QuestEntry;
    })
    .filter((entry): entry is QuestEntry => entry !== null);
}

function normalizeSheetEntry(value: unknown): SheetEntry | null {
  const record = asRecord(value);
  if (!record) {
    return null;
  }
  const labelSource = [record.label, record.name, record.title, record.key]
    .find((candidate) => typeof candidate === 'string' && candidate.trim()) as string | undefined;
  if (!labelSource) {
    return null;
  }

  const rawValue = record.value;
  const valueLabel =
    typeof rawValue === 'number'
      ? rawValue.toLocaleString()
      : typeof rawValue === 'string' && rawValue.trim()
        ? rawValue
        : '';

  return {
    key:
      typeof record.key === 'string' && record.key.trim()
        ? record.key
        : typeof record.resourceName === 'string' && record.resourceName.trim()
          ? record.resourceName
          : labelSource,
    label: labelSource,
    icon: typeof record.icon === 'string' ? record.icon : '',
    wikiUrl: typeof record.wikiUrl === 'string' ? record.wikiUrl : '',
    resourceName: typeof record.resourceName === 'string' ? record.resourceName : '',
    value: valueLabel,
    pursuing: Boolean(record.pursuing),
    slotIndex: typeof record.slotIndex === 'number' ? record.slotIndex : null,
    summary: typeof record.summary === 'string' ? record.summary : '',
    kind: typeof record.kind === 'string' ? record.kind : '',
    wikiTitle: typeof record.wikiTitle === 'string' ? record.wikiTitle : '',
    wikiSection: typeof record.wikiSection === 'string' ? record.wikiSection : '',
  };
}

function fallbackNamedEntry(key: string, label: string, value: string, kind = ''): SheetEntry {
  const normalized = normalizeNamedFallbackLabel(label, kind);
  return {
    key,
    label: normalized,
    icon: '',
    wikiUrl: `https://ringofbrodgar.com/wiki/${normalized.trim().replaceAll(' ', '_')}`,
    resourceName: '',
    value,
    pursuing: false,
    slotIndex: null,
    summary: '',
    kind,
    wikiTitle: '',
    wikiSection: '',
  };
}

function fallbackStatEntry(rawKey: string, value: number, kind: 'attribute' | 'ability'): SheetEntry {
  const key = canonicalStatKey(rawKey);
  const label = displayStatLabel(key || rawKey);
  return {
    key: key || rawKey,
    label,
    icon: '',
    wikiUrl: `https://ringofbrodgar.com/wiki/${label.trim().replaceAll(' ', '_')}`,
    resourceName: '',
    value: value.toLocaleString(),
    pursuing: false,
    slotIndex: null,
    summary: '',
    kind,
    wikiTitle: kind === 'attribute' ? 'Attributes' : 'Abilities',
    wikiSection: label,
  };
}

function meterEntry(key: string, label: string, value: string, summary: string): SheetEntry {
  return {
    key,
    label,
    icon: '',
    wikiUrl: '',
    resourceName: '',
    value,
    pursuing: false,
    slotIndex: null,
    summary,
    kind: 'meter',
    wikiTitle: '',
    wikiSection: '',
  };
}

function hoverText(entry: SheetEntry) {
  const parts = [entry.label];
  if (entry.value) {
    parts.push(`Value: ${entry.value}`);
  }
  if (entry.summary) {
    parts.push(entry.summary);
  }
  parts.push('Click for full details.');
  return parts.join('\n');
}

function fallbackVisibleStatEntries(
  stats: Record<string, unknown>,
  kind: 'attribute' | 'ability',
): SheetEntry[] {
  return sortedEntries(stats)
    .map(([key, value]) => {
      if (typeof value !== 'number') {
        return null;
      }
      const canonical = canonicalStatKey(key);
      if (!canonical || statKind(canonical) !== kind) {
        return null;
      }
      return fallbackStatEntry(canonical, value, kind);
    })
    .filter((entry): entry is SheetEntry => entry !== null);
}

function dedupeEntries(entries: SheetEntry[]) {
  const seen = new Set<string>();
  return entries.filter((entry) => {
    const key = `${entry.kind}|${entry.key}|${entry.label}|${entry.slotIndex ?? ''}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function canonicalStatKey(value: string) {
  const normalized = value.trim().toLowerCase();
  return STAT_KEY_ALIASES[normalized] ?? normalized;
}

function statKind(key: string): 'attribute' | 'ability' | 'other' {
  if (ATTRIBUTE_KEYS.has(key)) {
    return 'attribute';
  }
  if (ABILITY_KEYS.has(key)) {
    return 'ability';
  }
  return 'other';
}

function displayStatLabel(key: string) {
  return STAT_LABELS[key] ?? key;
}

function normalizeNamedFallbackLabel(label: string, kind: string) {
  const raw = label.trim();
  if (kind === 'skill') {
    return LEGACY_SKILL_LABELS[raw.toLowerCase()] ?? raw;
  }
  if (kind === 'credo' && raw === raw.toLowerCase()) {
    return raw.charAt(0).toUpperCase() + raw.slice(1);
  }
  return raw;
}

function iconCandidates(entry: SheetEntry) {
  const candidates = [
    resolvePackIcon(entry),
    entry.icon,
    entry.kind === 'meter'
      ? ''
      : api.wiki.iconUrl({
          label: entry.label,
          kind: entry.kind,
          wikiTitle: entry.wikiTitle,
          wikiSection: entry.wikiSection,
          wikiUrl: entry.wikiUrl,
        }),
  ];
  return Array.from(new Set(candidates.filter((candidate) => Boolean(candidate && candidate.trim()))));
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function sortedEntries(record: Record<string, unknown>) {
  return Object.entries(record).sort(([left], [right]) => left.localeCompare(right));
}

function questStatusLabel(value: number | null) {
  switch (value) {
    case 1:
      return 'Completed';
    case 2:
      return 'Failed';
    case 3:
      return 'Disabled';
    default:
      return 'Active';
  }
}

function objectiveStatusLabel(value: number | null) {
  switch (value) {
    case 1:
      return 'Done';
    case 2:
      return 'Failed';
    default:
      return 'Pending';
  }
}

function healthLabel(value: unknown) {
  const meter = asRecord(value);
  if (!meter) return 'Unknown';
  if (typeof meter.displayText === 'string' && meter.displayText.trim()) {
    return meter.displayText;
  }
  return meterLabel(value);
}

function meterLabel(value: unknown) {
  const meter = asRecord(value);
  if (!meter) return 'Unknown';
  const percentage =
    typeof meter.percentage === 'number'
      ? meter.percentage
      : typeof meter.current === 'number' && meter.current <= 1
        ? meter.current
        : null;
  if (typeof percentage === 'number') {
    return `${Math.round(percentage * 100)}%`;
  }
  return 'Unknown';
}

function shortNum(value: unknown) {
  return typeof value === 'number' ? value.toFixed(0) : '?';
}

function operatorStatePatch(value: unknown): Record<string, unknown> | null {
  const envelope = asRecord(value);
  const payload = asRecord(envelope?.payload);
  return payload;
}

function applyBotStatePatch(
  setBots: Dispatch<SetStateAction<BotRecord[]>>,
  patch: Record<string, unknown>,
) {
  const botId = typeof patch.botId === 'string' ? patch.botId : null;
  if (!botId) {
    return;
  }
  setBots((current) =>
    current.map((bot) =>
      bot.id === botId
        ? {
            ...bot,
            lastState: {
              ...(asRecord(bot.lastState) ?? {}),
              ...patch,
            },
          }
        : bot,
    ),
  );
}

function summarizeRoute(route: Record<string, unknown>) {
  const checkpoints = asArray(route.checkpoints);
  return `${checkpoints.length} checkpoints`;
}

function formatDateTime(value: TimestampValue | null | undefined) {
  const parsed = parseTimestamp(value);
  return parsed ? parsed.toLocaleString() : 'Unknown';
}

function parseTimestamp(value: unknown) {
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return value;
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    const millis = value < 1_000_000_000_000 ? value * 1000 : value;
    const parsed = new Date(millis);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  return null;
}

function auditSeverity(entry: AuditRecord) {
  if (
    entry.eventType.includes('failed') ||
    entry.eventType.includes('error') ||
    entry.eventType.includes('exited') ||
    entry.eventType.includes('force-killed')
  ) {
    return 'error';
  }
  return 'info';
}

function auditSummary(entry: AuditRecord) {
  const details = asRecord(entry.details) ?? {};
  const summary = typeof details.summary === 'string' ? details.summary : '';
  if (summary) {
    return summary;
  }
  const message = typeof details.message === 'string' ? details.message : '';
  if (message) {
    return message;
  }
  const cause = typeof details.cause === 'string' ? details.cause : '';
  if (cause) {
    return cause;
  }
  if (typeof details.exitCode === 'number') {
    return `Process exited with code ${details.exitCode}.`;
  }
  const status = typeof details.status === 'string' ? details.status : '';
  if (status) {
    return `Status: ${status}.`;
  }
  return '';
}

function statusTone(status: BotRecord['status']) {
  switch (status) {
    case 'RUNNING':
      return 'success';
    case 'STOPPING':
      return 'warning';
    case 'ERROR':
      return 'danger';
    case 'TAKEOVER':
      return 'accent';
    default:
      return 'neutral';
  }
}

function isBotOnline(status: BotRecord['status']) {
  return ['CONNECTING', 'STOPPING', 'IDLE', 'RUNNING', 'TAKEOVER'].includes(status);
}

function canRenderLiveVideo(bot: BotRecord) {
  if (isBotOnline(bot.status)) {
    return true;
  }
  const state = asRecord(bot.lastState);
  return typeof state?.sessionStatus === 'string' && state.sessionStatus === 'CONNECTED';
}

function humanActionLabel(actionType: string) {
  return (
    {
      'cleanup.start': 'Cleanup',
      'cleanup.stop': 'Stop Cleanup',
      'fishing.start': 'Fishing',
      'fishing.stop': 'Stop Fishing',
      'route.start': 'Route',
      'route.stop': 'Stop Route',
      'inventory.sort': 'Inventory Sort',
      'auto-repeat-flower': 'Flower Auto-Repeat',
      'auto-repeat-flower.clear': 'Clear Flower Auto-Repeat',
      'grubgrub.start': 'Grub-Grub',
      'grubgrub.stop': 'Stop Grub-Grub',
      'tar-kiln.start': 'Tar Kiln',
      'tar-kiln.stop': 'Stop Tar Kiln',
      'roasting.start': 'Roasting',
      'roasting.stop': 'Stop Roasting',
      'cellar.start': 'Cellar Digging',
      'cellar.stop': 'Stop Cellar Digging',
      'ocean-scout.start': 'Ocean Scout',
      'ocean-scout.stop': 'Stop Ocean Scout',
      'safe-logout': 'Safe Logout',
    }[actionType] ?? actionType
  );
}

function asActivityRecord(value: unknown): ActivityRecord | null {
  const record = asRecord(value);
  if (!record || typeof record.botId !== 'string' || typeof record.message !== 'string') {
    return null;
  }
  return {
    id: typeof record.id === 'number' ? record.id : Number(record.id ?? 0),
    botId: record.botId,
    source: typeof record.source === 'string' ? record.source : 'unknown',
    category: typeof record.category === 'string' ? record.category : 'general',
    message: record.message,
    details: asRecord(record.details) ?? {},
    createdAt:
      typeof record.createdAt === 'string' || typeof record.createdAt === 'number'
        ? record.createdAt
        : new Date().toISOString(),
  };
}
