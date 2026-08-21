const PACK_BASE = '/game-icons/character';

const ICON_PATHS: Record<string, string> = {
  str: `${PACK_BASE}/str.png`,
  agi: `${PACK_BASE}/agi.png`,
  int: `${PACK_BASE}/int.png`,
  con: `${PACK_BASE}/constitution.png`,
  prc: `${PACK_BASE}/prc.png`,
  csm: `${PACK_BASE}/csm.png`,
  dex: `${PACK_BASE}/dex.png`,
  wil: `${PACK_BASE}/wil.png`,
  psy: `${PACK_BASE}/psy.png`,
  unarmed: `${PACK_BASE}/unarmed.png`,
  melee: `${PACK_BASE}/melee.png`,
  ranged: `${PACK_BASE}/ranged.png`,
  explore: `${PACK_BASE}/explore.png`,
  stealth: `${PACK_BASE}/stealth.png`,
  sewing: `${PACK_BASE}/sewing.png`,
  smithing: `${PACK_BASE}/smithing.png`,
  masonry: `${PACK_BASE}/masonry.png`,
  carpentry: `${PACK_BASE}/carpentry.png`,
  cooking: `${PACK_BASE}/cooking.png`,
  farming: `${PACK_BASE}/farming.png`,
  survive: `${PACK_BASE}/survive.png`,
  lore: `${PACK_BASE}/lore.png`,
  swim: `${PACK_BASE}/swim.png`,
  mining: `${PACK_BASE}/mining.png`,
  hp: `${PACK_BASE}/hp.png`,
  stam: `${PACK_BASE}/stam.png`,
  nrj: `${PACK_BASE}/nrj.png`,
  'character-sheet': `${PACK_BASE}/character-sheet.png`,
  'gfx/hud/chr/str': `${PACK_BASE}/str.png`,
  'gfx/hud/chr/agi': `${PACK_BASE}/agi.png`,
  'gfx/hud/chr/int': `${PACK_BASE}/int.png`,
  'gfx/hud/chr/con': `${PACK_BASE}/constitution.png`,
  'gfx/hud/chr/prc': `${PACK_BASE}/prc.png`,
  'gfx/hud/chr/csm': `${PACK_BASE}/csm.png`,
  'gfx/hud/chr/dex': `${PACK_BASE}/dex.png`,
  'gfx/hud/chr/wil': `${PACK_BASE}/wil.png`,
  'gfx/hud/chr/psy': `${PACK_BASE}/psy.png`,
  'gfx/hud/chr/unarmed': `${PACK_BASE}/unarmed.png`,
  'gfx/hud/chr/melee': `${PACK_BASE}/melee.png`,
  'gfx/hud/chr/ranged': `${PACK_BASE}/ranged.png`,
  'gfx/hud/chr/explore': `${PACK_BASE}/explore.png`,
  'gfx/hud/chr/stealth': `${PACK_BASE}/stealth.png`,
  'gfx/hud/chr/sewing': `${PACK_BASE}/sewing.png`,
  'gfx/hud/chr/smithing': `${PACK_BASE}/smithing.png`,
  'gfx/hud/chr/masonry': `${PACK_BASE}/masonry.png`,
  'gfx/hud/chr/carpentry': `${PACK_BASE}/carpentry.png`,
  'gfx/hud/chr/cooking': `${PACK_BASE}/cooking.png`,
  'gfx/hud/chr/farming': `${PACK_BASE}/farming.png`,
  'gfx/hud/chr/survive': `${PACK_BASE}/survive.png`,
  'gfx/hud/chr/lore': `${PACK_BASE}/lore.png`,
  'gfx/hud/chr/swim': `${PACK_BASE}/swim.png`,
  'gfx/hud/chr/mining': `${PACK_BASE}/mining.png`,
  'gfx/hud/meter/hp': `${PACK_BASE}/hp.png`,
  'gfx/hud/meter/stam': `${PACK_BASE}/stam.png`,
  'gfx/hud/meter/nrj': `${PACK_BASE}/nrj.png`,
};

export type IconEntry = {
  key?: string;
  label?: string;
  resourceName?: string;
  kind?: string;
};

function normalize(value: string | undefined) {
  return value?.trim().toLowerCase().replace(/\s+/g, '-') ?? '';
}

export function resolvePackIcon(entry: IconEntry) {
  const directKeys = [entry.resourceName ?? '', entry.key ?? '', normalize(entry.key), normalize(entry.label)];
  for (const key of directKeys) {
    if (key && ICON_PATHS[key]) {
      return ICON_PATHS[key];
    }
  }
  if (entry.kind === 'meter') {
    const key = normalize(entry.key);
    if (key === 'health') return ICON_PATHS.hp;
    if (key === 'stamina') return ICON_PATHS.stam;
    if (key === 'energy') return ICON_PATHS.nrj;
  }
  return '';
}
