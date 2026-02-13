# Haven & Hearth — Comprehensive Game Systems Research

> **Research compiled from Ring of Brodgar wiki and Haven & Hearth forums**
> **Game Version: World 16.1 (December 5, 2025)**
> **Developers: loftar & jorb (Seatribe)**

---

## Table of Contents

1. [Game Overview](#1-game-overview)
2. [Attributes](#2-attributes)
3. [Skills](#3-skills)
4. [Abilities](#4-abilities)
5. [Combat System](#5-combat-system)
6. [Hit Points & Health](#6-hit-points--health)
7. [Wound System](#7-wound-system)
8. [Food & FEP System](#8-food--fep-system)
9. [Drink & Buff System](#9-drink--buff-system)
10. [Curiosity & LP System](#10-curiosity--lp-system)
11. [Credos](#11-credos)
12. [Claims & Property](#12-claims--property)
13. [Villages & Governance](#13-villages--governance)
14. [Realms & Kingdoms](#14-realms--kingdoms)
15. [Farming](#15-farming)
16. [Mining & Underground](#16-mining--underground)
17. [Fishing](#17-fishing)
18. [Animal Husbandry](#18-animal-husbandry)
19. [Hearth Magic](#19-hearth-magic)
20. [Gilding & Equipment](#20-gilding--equipment)
21. [The Symbel (Feasting)](#21-the-symbel-feasting)
22. [Criminal Acts & Scents](#22-criminal-acts--scents)
23. [Swimming & Travel](#23-swimming--travel)
24. [Terrain & World Structure](#24-terrain--world-structure)
25. [Generics (Crafting Material Groups)](#25-generics-crafting-material-groups)
26. [Energy, Stamina & Speed](#26-energy-stamina--speed)
27. [Day/Night & Seasons](#27-daynight--seasons)
28. [Death & Inheritance](#28-death--inheritance)
29. [Custom Client Development](#29-custom-client-development)
30. [Server APIs & Technical Data](#30-server-apis--technical-data)

---

## 1. Game Overview

Haven & Hearth is a Java-based sandbox MMO survival/crafting game set in a mythical Scandinavian wilderness. Players are "Hearthlings" who forage, farm, fight, build, and die permanently in a persistent world.

| Property | Value |
|----------|-------|
| **Developer** | Seatribe (loftar & jorb) |
| **Current World** | World 16.1 (started Dec 5, 2025) |
| **Platform** | Java (official client), Steam integration |
| **Game Server** | `game.havenandhearth.com` |
| **Resource Server** | `https://game.havenandhearth.com/hres/` |
| **Client Source** | `git://sh.seatribe.se/hafen-client` |
| **Launch Command** | `java -Xms512m -Xmx1024m -jar hafen.jar -U https://game.havenandhearth.com/hres/ game.havenandhearth.com` |
| **Build System** | Apache Ant (`Build.xml`) |
| **Permadeath** | Yes — HHP reaches 0 = permanent death |
| **Inheritance** | 15%–45% LP/FEP transfer to next character |

**Key Design Philosophy:** Quality-based crafting (everything has quality), soft/hard cap progression, permanent consequences (death, crime), deep interdependent systems.

---

## 2. Attributes

9 base attributes, increased by eating food (FEPs). Equipment can provide temporary modifiers.

| Attribute | Abbr | Primary Role |
|-----------|------|-------------|
| **Strength** | STR | Melee damage, carrying capacity, mining |
| **Agility** | AGI | Attack speed, dodge chance, movement |
| **Intelligence** | INT | LP from curiosities, mental weight capacity |
| **Constitution** | CON | Max HP (MHP), stamina, swimming distance |
| **Perception** | PER | Forage quality, detection range |
| **Charisma** | CHA | Party size, symbel bonuses, kin limit |
| **Dexterity** | DEX | Craft quality, ranged accuracy |
| **Will** | WIL | Combat openings, fishing list size |
| **Psyche** | PSY | Hearth magic effectiveness |

**FEP Threshold:** To gain +1 to any stat, you must accumulate FEPs equal to your *highest* attribute value. Which stat increases is probabilistic based on FEP distribution.

**Quality Modifier (QM):** `QM = sqrt(Q / 10)` — applies to equipment stat bonuses.

**Softcap:** Geometric mean of relevant stats. Going above softcap reduces gains.
**Hardcap:** Absolute maximum quality you can produce/harvest (skill-based).

---

## 3. Skills

61 skills organized in a dependency tree. Cost ranges from free to 250,000 LP.

### Skill Tiers (by LP cost)

| LP Cost | Skills |
|---------|--------|
| **Free** | Foraging |
| **100** | Carpentry, Hunting |
| **200** | Fishing, Fire Making, Pottery, Stone Working |
| **250** | Hearth Magic, The Will to Power |
| **400** | Farming, Tanning |
| **500** | Beekeeping, Swimming, Steelmaking |
| **750** | Animal Husbandry, Sewing, Winemaking |
| **1,000** | Mining, Baking, Metal Working, Deep Artifice |
| **1,500** | Boat Building, Tunneling |
| **2,000** | Alchemy, Forestry |
| **5,000** | Lawspeaking, Plant Lore, Druidic Rite |
| **10,000** | Rage, Ancestral Worship |
| **25,000** | Trespassing |
| **50,000** | Vandalism, Theft |
| **100,000** | Assault |
| **200,000** | Battery |
| **250,000** | Murder |

**Crime skills** are intentionally expensive. Each crime committed costs 1 SHP.

### Key Dependency Chains
- Foraging → Farming → Beekeeping → Silkfarming
- Foraging → Hunting → Animal Husbandry → Cheesemaking
- Fishing → Swimming → Boat Building
- Stone Working → Mining → Tunneling
- The Will to Power → Hearth Magic → Alchemy, Lawspeaking, First Aid, Music, etc.

---

## 4. Abilities

Incrementable stats that act as softcaps for various activities. 14 total.

### Combat Abilities (3)
- **Melee Combat** — melee damage/accuracy
- **Unarmed Combat** — unarmed damage/accuracy
- **Marksmanship** — ranged damage/accuracy

### Non-Combat Abilities (11)
Exploration, Survival, Cooking, Carpentry, Farming, Lore, Masonry, Smithing, Sewing, Stealth, Swimming

**Cost Formula:** `100 LP × (current_level + 1)` per point
**Softcap Formula:** Geometric mean of two base attributes (varies per ability)

| Ability | Softcap Attributes |
|---------|--------------------|
| Melee Combat | STR, AGI |
| Unarmed Combat | STR, AGI |
| Marksmanship | PER, DEX |
| Exploration | INT, PER |
| Survival | AGI, PER |
| Cooking | DEX, PER |
| Carpentry | STR, DEX |
| Farming | DEX, CON |
| Lore | INT, WIL |
| Masonry | STR, CON |
| Smithing | STR, INT |
| Sewing | DEX, PSY |
| Stealth | AGI, INT |
| Swimming | CON, (Swimming equipment bonuses count) |

---

## 5. Combat System

### Overview
- **4-color opening system:** Red, Green, Yellow, Blue
- **Initiative Points (IP):** Determines action order
- **Combat Decks:** Up to 10 cards, 5 stacks, 30 point budget
- **5 saved deck slots** — keys 1–5, SHIFT+1–5 to assign

### Melee Combat
- Click opponent to engage → combat UI opens
- Play attack cards from deck → cards execute based on IP
- Each card targets an opening (color) and may open a new opening on the opponent
- **Openings** must match the current opening on the target to land

### Ranged Combat
- Sling, Bow, Throwing weapons
- Accuracy based on Marksmanship ability
- Range and damage vary by weapon/ammo type

### Key mechanics
- **Yield:** Surrender in combat (drop to knees)
- **Knockout:** Battery reduces opponent to 0 SHP → unconscious
- **Murder:** Killing another player (requires Murder skill + Criminal Acts toggle)
- **Nidbane:** Summoned creature that hunts criminals (scales with crime severity)

---

## 6. Hit Points & Health

### Three HP Pools

| Pool | Full Name | Role | Recovery |
|------|-----------|------|----------|
| **SHP** | Soft Hit Points | Temporary HP, taken first | Regenerates when energy > 8000% |
| **HHP** | Hard Hit Points | Permanent HP, wounds reduce this | Heals only by treating wounds |
| **MHP** | Max Hit Points | Maximum possible HP | `MHP = 100 × (CON/10)^0.5` |

**Permadeath:** HHP reaches 0 → character dies permanently.

### Example
- CON 50 → MHP = 100 × sqrt(5) ≈ 223
- CON 100 → MHP = 100 × sqrt(10) ≈ 316

---

## 7. Wound System

30+ wound types with varying severity, stacking, and treatments.

### Wound Categories
- **Blunt trauma:** Bruises, Concussion, Black Eye, Swollen Bumps
- **Cuts/Lacerations:** Cuts, Deep Cuts, Gashing Wound, Overwhelming Gore
- **Burns:** Severe Burns
- **Special:** Infected Sore, Leech Burns, Frostbite, Unfaced (PvP)
- **Diseases:** Cough, Cold, Swamp Fever (grow over time)
- **Drowning:** Asphyxiation (10% MHP/sec when stamina runs out in deep water)
- **Cave-in:** Concussion + multiple wounds, damage increases per underground level

### Treatment System
- **Leeching:** Apply leeches to remove certain wounds
- **First Aid:** Bandages, poultices, herbal remedies
- **Quality matters:** Higher quality treatment items heal more effectively
- **Some wounds self-heal** over time at varying rates
- **Asphyxiation** heals fast (~1 point per 10 minutes)

---

## 8. Food & FEP System

### FEP (Food Event Points)
- 600+ unique foods in the game
- Each food provides FEPs toward specific attributes
- FEPs accumulate until reaching threshold (= highest attribute value)
- Then one attribute increases by 1 (probabilistic based on FEP distribution)
- After stat increase, all FEPs reset to zero

### Satiation System (18 categories)
Foods belong to categories; eating too much of one category reduces hunger restoration.

| Satiation Category | Examples |
|--------------------|----------|
| Bread | Bread, Flatbread, Bark Bread |
| Meat | Roast meat, Steaks, Sausages |
| Fish | All fish dishes |
| Greens/Veg | Salads, Boiled vegetables |
| Berries/Fruit | Blueberries, Grapes, Apples |
| Mushroom | All mushroom dishes |
| Cheese | All cheese types |
| Game | Wild animal meat |
| Seafood | Mussels, Oysters, Shrimp |
| Candy | Honey, Lollipops |
| ... | (18 total categories) |

### Hunger Levels

| Level | Effect |
|-------|--------|
| Ravenous | 300% FEP multiplier |
| Very Hungry | 200% |
| Hungry | 150% |
| Peckish | 100% |
| Not Hungry | 50% |
| Full | 25% |
| Overstuffed | 10% |

---

## 9. Drink & Buff System

Drinks provide temporary buffs and prevent certain satiations.

| Drink Type | Effects |
|------------|---------|
| **Wine** | Various attribute buffs, prevents % of satiation |
| **Beer** | Strength/Constitution buffs |
| **Mead** | Psyche/Will buffs |
| **Cider** | Perception/Agility buffs |
| **Tea** | Intelligence/Lore buffs |
| **Milk** | Constitution, basic sustenance |
| **Brandy** | Strong attribute buffs |

---

## 10. Curiosity & LP System

### Learning Points (LP)
- Primary progression currency
- Earned by: **Discovering** new items + **Studying** curiosities
- Spent on: Skills and Abilities

### Curiosity System
- 200+ curiosity items in the game
- **LP Formula:** `BaseLP × (Q/10)` — quality matters enormously
- **Mental Weight:** Each curiosity has weight; total limited by Intelligence
- **Study Time:** Requires Experience (XP) accumulated from doing activities
- **Study Desk:** Furniture for studying, Scholar credo adds extra row+column

### Key Early Curiosities
- Dandelion, Cone Cow, Dice, Ladybug (all low-cost, easy to find)
- Fish are also discoverable for LP

---

## 11. Credos

21 profession-based progression paths. Each has 5 levels with unique bonuses.

### Mechanics
- Pursue one credo at a time
- **Cost:** 10,000 LP + 10,000 LP per previously completed credo
- **Quests:** 10 quests per existing credo to complete (10 for first, 20 for second, etc.)
- **5 bonuses** unlocked linearly every 1/5 of questline completion
- Completed credos **fully inherited on death**
- Abandoning a credo loses a quest; abandoning first quest loses entire credo

### Dependency Tree
```
Forager (base) ─┬─ Farmer ─┬─ Tailor
                │          ├─ Gardener ──── Scholar (+ Quarryman)
                │          ├─ Potter
                │          └─ Cook (+ Hunter)
                ├─ Quarryman ─┬─ Gem Hunter ── Pearl Diver (+ Fisherman)
                │             └─ Miner ─┬─ Cave Hermit (+ Mystic)
                │                       └─ Blacksmith (+ Lumberjack)
                ├─ Lumberjack (+ Hunter) ── Strider (+ Fisherman)
                └─ Mystic (+ Fisherman)

Fisherman (base)
Hunter (base)

Nomad (Fisherman + Forager + Hunter) ─┬─ Herder (+ Farmer)
                                      └─ Wandering Sage (+ Mystic)
```

### Credo Bonuses Summary

| Credo | Key Bonuses |
|-------|-------------|
| **Forager** | Exploration +10, PER +10, chase speed, +20% herb quality, double herb chance |
| **Fisherman** | Less equipment loss, better trash recovery, rare catch chance, reduced Fisher's Request cost, +10% fish quality |
| **Hunter** | Marksmanship +20, fleeing animals take more damage, reduced Quell the Beast cost, more meat from butchering, ranged damage boost |
| **Farmer** | Farming +15, +20% milking quality, +10% crop growth, +20% crop yield, Farming +50 |
| **Lumberjack** | Lore +15, faster tree cutting, Carpentry +20, +50% board/block yield, STR +25 |
| **Mystic** | WIL +15, Lore +20, +5% study speed, +10% XP gain, -10% hearth magic cost |
| **Quarryman** | Masonry +20, reduced Mine Song cost, +40% mining strength, STR +20, Quarryartz chance |
| **Miner** | STR +15 & Masonry +15, localize cave-ins, +25% smelting speed, pulverize tiles, sense ore ahead |
| **Cook** | Faster cooking, CON +15, reduced satiation (4% vs 5%), Cooking +30, double output chance |
| **Blacksmith** | STR +15 & Smithing +15, more irrlights, reduced smithing time, 6×6 smelter inventory, double output chance |
| **Nomad** | Exploration +15, Survival +25, +1 inventory column, longer Pony Power, +1 inventory row |
| **Tailor** | Sewing +15, -5% gilding fail, double reslot chance, Sewing +25, first gilding always succeeds |
| **Gardener** | Plant Blood Sterns + Farming +5, Plant Cavebulbs + Farming +5, halved pot soil/water, Plant Chiming Bluebells + Farming +10, double pot yield |
| **Scholar** | INT +15 & Lore +15, avoid consuming curiosities, halved Contemplation cost, extra Study Desk row+column, Scholarly Accounts |
| **Strider** | +50% swimming, extra hides from flaying, reduced Game satiation, uninterrupted insect nest raids, +15% ranged damage |
| **Cave Hermit** | Increased troll mining risk, eat Stalagooms/Cave Slime, +1.5% study speed per cave level, +5% damage per cave level, trolls don't attack |
| **Herder** | Wild animals accept clover, branded animals eat 20% less, longer Pony Power, shorter gestation, +10% domestic meat quality |
| **Wandering Sage** | -20% Travel Weariness, +5% XP gain, reduced stamina drain walking, XP from Natural Wonders, +15% quest rewards |
| **Pearl Diver** | CON +10, halved drowning damage, +50% mussel/oyster quality, reduced Seafood satiation, increased pearl chance |
| **Gem Hunter** | Masonry +15, higher gem chance, larger gems, pear cut gems, gem doubling chance |
| **Potter** | Masonry +15, +10% ball clay quality, +25% acre clay quality, more cave/gray/pit clay, Potter's Clay ability |

---

## 12. Claims & Property

### Personal Claims
- **Requires:** Yeomanry skill
- **Build:** Claim Pole (Adventure > Haven & Hearth > Claim Land)
- **Initial size:** 5×5 around pole, expandable at 10 LP per square (non-refundable)
- **Additional claims:** 4,000 LP each beyond the first
- **No limit** on number of personal claims per character
- **Buffer:** 5 tiles between personal claims; cannot cover Localized Resources
- **Ratio requirement:** ~1:3 minimum (short side vs long side)

### Presence System
- Claims require **Presence** to stay active
- Presence decays gradually; reaches 0 in ~54 real-life days
- At 0 Presence: objects decay, no scents generated, pole can be bashed by hand
- **Activation:** New claims start inactive; become active after 8 hours + owner visit
- **Recharging:** Bond in Study Report grants free Presence per LP earned (divided among all bonds)
- **Emergency recharge:** At Claim Pole (costs LP)

### Permissions
- Color-based permission system linked to Kin screen (Ctrl+B)
- White permissions = default for all non-kinned players
- Can toggle party permissions (Adventure > Toggle > Toggle Party Permissions)

### Power Level
- Ranges 0 to 1, increases from 0 to 1 over 4 real weeks
- Stronger claim always applies in overlapping situations
- Battering Ram time scales with power level

---

## 13. Villages & Governance

### Village Claim
- **Size:** 101×101 tiles
- **Cost:** 30,000 LP divided among 1–5 founding members
- **Requirements:** All founders need Yeomanry; founder becomes Lawspeaker (needs Lawspeaking)
- **Build:** Village Idol on 2×3 paved area, 3 game-day cooldown before founding
- **Buffer:** 100 tiles between villages

### Authority
- Village resource; replaces Presence for village claims
- **Drain:** 5,000 per game day (15,000 per real day)
- **Natural Wonders:** Each covered drains 10,000 authority/real day
- **Initial:** 30,000; **Cap:** 250,000
- **Generated by:** Villagers gaining LP (multiplied by INT/CHA factor)

### Roles
- **Lawspeaker:** Village leader, one per person
- **Members:** Can be assigned ranks with varying permissions
- **Visitor buff:** Characters entering via visitor gates cannot commit crimes

---

## 14. Realms & Kingdoms

### Overview
- Large-scale territorial claims over **Provinces** (each has a Thingwall)
- Do NOT protect property or generate scents — purely political/economic
- **Establishing:** Wear Royal Crown, build Coronation Stone

### Authority Collection
- Generated when inhabitants gain XP (bonus, not taxed)
- Double contribution when both character AND hearth fire are in realm

### Challenging Thingwalls
- Plant War Flag near Thingwall; survive 3 RL hours
- Challenge windows: every in-game full/new moon (~1–2 per RL week)
- Only one War Flag per Realm at a time (max 3 provinces per window)
- Can only challenge adjacent provinces (or home province)
- Realms can have non-contiguous territory

### Realm Blessings (15 total)

Blessings require Grotesque Idols and Menhirs in controlled provinces.

| Blessing | Key Effect |
|----------|------------|
| Backwater | +10% onion/turnip/lettuce growth, +10% squirrel/mole/badger quality |
| Center of Learning | +3% learning bonus |
| Fecund Earth | +10% wheat/flax/grape/pumpkin growth |
| Founding Mythos | +6% hearth magic bonus |
| Game Keeping | +9% boar/deer/moose quality |
| Guarded Marches | War Flag must stand 1 extra hour |
| Heraldic Swan | +75% swan quality |
| Land of Milk & Honey | +5% honey/milk quality, +5% quest rewards |
| Local Cuisine | +3% food event bonus |
| Marriage of the Sea | +9% fish quality, +12% ship quality |
| Mountain Tradition | +3% mineral quality |
| Mushroom Kingdom | Increased mushroom quality/quantity |
| Myth of the Bull | +6% livestock birth rate |
| Woodland Realm | +6% tree growth, +3% woodland forage quality, +1.5% animal quality |

### Realm Authority Skills
- **From the Public Coffer** (2000, increases): 1000 XP
- **State Funeral** (50000): Full 45% LP/FEP inheritance without burial
- **Last-minute Diplomacy** (5000/cairn): Stall challenges by 1 RL hour

---

## 15. Farming

### Overview
- 20+ crops, 15 planted in soil, 5 on trellises
- **Requirements:** Farming skill, 5 seeds per tile, tilled ground

### Planting & Harvesting
- Till ground by hand or with plow (Wood Plow = Crawl speed, Metal Plow = Run speed)
- Left-click seeds, right-click tilled tile (Shift+right-click for area planting)
- Scythe harvests multiple crops at once
- Shift+right-click harvested crop for area harvesting

### Crop Table (partial — 30+ varieties)

| Crop | Growth Time | Key Products |
|------|------------|-------------|
| Barley | 3–4 days | 5–18 seeds, Straw |
| Beetroot | ~2 days | 1–4 beetroots, leaves |
| Carrot | 15–40 hours | 1–4 carrots, seeds |
| Flax | 2 days | Seeds, Flax Fibre |
| Hemp | 3 days 4 hours | Seeds, Hemp Fibre, Fresh Hemp Bud |
| Lettuce | 3 days 8 hours | Head of Lettuce, seeds |
| Pumpkin | 5–7 days | Giant Pumpkin (40 seeds, 8 flesh) |
| Wheat | 3 days 10 hours | 5–18 seeds, Straw |
| Grapes | 4 days 8 hours | 1–4 Grapes (trellis) |
| Cucumbers | 4 days 8 hours | Seeds, 1–3 cucumbers (trellis) |

### Seed Quality
- Planted crops get [-2, +5] quality change initially
- Additional random [-2, +5] changes over time (~2 hour cycles)
- If farming ability ≤ planted quality → positive boost ignored
- Server leader in crop quality: changes reduced to [-2, +2]
- Seeds stack up to 50 (inventory), 1000 (bucket), 10000 (barrel), 200000 (granary)

### Related Skills
- **Gardening:** Garden pots for forageables (berries, etc.)
- **Plant Lore:** Extra yield when harvesting
- **Druidic Rite:** Ultimate farming skill, further yield bonuses
- **Winemaking:** Required for grape seeds
- **Beekeeping:** Bee Skeps accelerate crop growth (except wheat/barley/millet/hemp)
- **Mound Bed:** Winter protection using compost mulch

---

## 16. Mining & Underground

### World Structure
- Surface + **9 underground levels**
- Deeper levels = harder rock, higher quality ore, more cave-in damage

### How Mining Works
- Build Mine Hole or find Cave entrance
- Equip axe or pickaxe, use Adventure > Mine on walls
- Cannot mine within 7×7 of cave entrance
- Walls have hardness; mining ability must exceed hardness

### Mining Ability Formula
```
Mining Ability = sqrt(Strength × AvgTool)
AvgTool = ToolQuality/3 for axes, ToolQuality×2/3 for pickaxes
```

### Mining Messages (each = ~10-15 more ability needed)
1. "You don't even manage to scratch that rock"
2. "That rock is very much too hard for you to mine"
3. "That rock is much too hard for you to mine"
4. "That rock is slightly too hard for you to mine"
5. "That rock is just slightly too hard for you to mine"

### Node System
- **Ore veins** — specific metal deposits
- **Rock quality nodes** — affects mined stone/ore quality
- **Hardness nodes** — affects difficulty
- Nodes are independent; a soft high-quality vein is rare luck
- Ore quality **hardcapped** by Masonry, **softcapped** by tool quality

### Cave-ins
- Random chance on first mine of any wall tile
- **Dust particles** = warning: 1+ of 8 surrounding tiles will cave in
- Mine Supports and Stone Columns protect (check radius with Adventure > Toggles)
- Supports take damage when preventing cave-ins
- Cave-in damage increases significantly per underground level
- Level 1: up to ~50 wound damage + 5%–20% MHP Concussion

### Ores → Metals
- Cassiterite → Tin
- Chalcopyrite → Copper
- Black Ore → Iron
- Galena → Lead
- Cinnabar → Mercury/Quicksilver
- And more (full metal tree available)

---

## 17. Fishing

### Two Methods

| Method | Tool | Mechanism | Best For |
|--------|------|-----------|----------|
| **Bait Fishing** | Bushcraft Fishingpole | Semi-automated, bait consumed per fish | Volume, variety |
| **Lure Fishing** | Primitive Casting-Rod | Manual, choose target fish, lures reusable | Rare/specific fish |

### Equipment Components
1. **Fishing Pole** — Bushcraft Fishingpole or Primitive Casting-Rod
2. **Fishing Line** — 8 types (Bushcraft, Farmer's, Fine, Macabre, Shepherd's, Shoreline, Tanner's, Woodsman's)
3. **Fish Hook** — Bone, Chitin, Gold, Metal
4. **Bait/Lure** — Many types (Earthworm, Entrails, Ant Larvae, etc. for bait; Copper Comet, Feather Fly, etc. for lures)

### Fish Locations
- **Fresh Water:** 20+ species (Asp, Bream, Catfish, Pike, Salmon, Sturgeon, Trout, etc.)
- **Ocean:** 12+ species (Bass, Cod, Eel, Haddock, Herring, Mackerel, etc.)
- **Cave:** 4+ species (Abyss Gazer, Cavelacanth, Cave Sculpin, Pale Ghostfish)

### Mechanics
- Fish are localized resources (see fish jumping = fish here)
- Quality hardcapped by Survival
- Lure fishing: list size = `ceil(Will × Survival / 20)`, max 10
- Line/hook combos affect which species are catchable
- Fisher's Request for a Catch (Hearth Magic) boosts catch chance significantly

### Fishing Trash
Items dropped in water enter global pool; can be fished up anywhere. Written parchments = message in a bottle.

---

## 18. Animal Husbandry

### Tamable Animals

| Wild | Domesticated |
|------|-------------|
| Boar | Pig |
| Mouflon | Sheep |
| Aurochs | Cattle |
| Wildhorse | Horse |
| Wildgoat | Goat |
| Reindeer | Tame Reindeer |

### Taming Process
1. Lure with Clover
2. Snare with Rope
3. Tie to Hitching Post
4. When animal is ready to fight, defeat it in combat
5. When it yields, yield back — repeat until quelled

### Products
- **Milk** (Cattle, Goats)
- **Wool** (Sheep)
- **Eggs** (Chickens via Chicken Coop)
- **Hides, Meat, Bone** (all domestic animals)
- **Butter** (from Churn)
- **Horses:** Rideable, Pony Power (speed boost)

### Feeding
- Food Trough or grassland required
- Branded animals (Herder credo) eat 20% less
- Domestic animals don't move when off-loaded but continue eating/breeding

### Unlocked Structures
- Chicken Coop, Churn, Food Trough, Hitching Post

---

## 19. Hearth Magic

### Requirements
- Hearth Magic skill (250 LP, requires The Will to Power)
- Eligible for quests after 10,000 LP gained

### Magic Abilities (16)

| Spell | Requirements | Effect |
|-------|-------------|--------|
| **Commune With Nature** | Hearth Magic | Sense natural environment |
| **Contemplation & Meditation** | Hearth Magic | Halve remaining study time on curiosity |
| **Dig Deeper** | Hearth Magic, Tunneling | Dig down to next underground level |
| **Fisher's Request for a Catch** | Fishing, Hearth Magic | Boost fishing success rate |
| **Gilding Song** | Hearth Magic, Deep Artifice | Add gilding slot to finished artifact |
| **Green Thumb** | Hearth Magic, Forestry | Cause stunted tree to grow again |
| **Horse Whisper** | Animal Husbandry, Hearth Magic | Convert horse energy to Pony Power |
| **Kindle** | Hearth Magic | Start fires |
| **Mine Song** | Mining, Hearth Magic | Locate ore deposits |
| **Polish the Silver** | Hearth Magic, Metallurgy | Repair symbel items |
| **Quell the Beast** | Animal Husbandry, Hearth Magic | Calm wild/tamed animals |
| **Raw Hide!** | Animal Husbandry, Hearth Magic | Stun nearby horses, empty stamina, remove Pony Power |
| **Star Gaze** | Hearth Magic, Deep Artifice | Celestial observation |
| **Vengeful Incantation** | Rage, Hearth Magic | Offensive combat magic |
| **Sense Coronation Stone** | (auto) | Locate nearest Coronation Stone |
| **Sense Thingwall** | (auto) | Locate nearest Thingwall |

### Hearth Magic Enables
- Dream Catchers → A Beautiful Dream! (needed for claims)
- Dowsing Rods (resource detection)
- Bear Tooth Talisman, Darkwood Ring, etc. (magic equipment)

---

## 20. Gilding & Equipment

### How Gilding Works
- Equipment has 1 guaranteed gilding slot
- Each gilding attempt has a chance to open additional slots
- If roll fails → equipment is permanently completed
- Chance based on matching attributes between gilding item and equipment

### Gilding Chance Formula
```
Base Range = low_equipment% × low_gilding% to high_equipment% × high_gilding%
```
Personal attribute levels affect where in the range you land (max ~400 attribute).

### Gilding Bonus Scaling
```
Bonus = sqrt(qItem/10) × (base_bonus_at_q10)
```
Gilding item quality softcapped by equipment quality.

### Additional Slot Methods
1. **Recycling:** Combine two identical gilded items → chance for extra slot
2. **Garment Needle:** Found in Dungeons
3. **Gilding Song:** Hearth Magic spell

### Gilding Items (50+)
Notable examples:
- **Bear Fur Trimmings** (50-90%): STR+1, Melee+1, Unarmed+1
- **Meteoric Studs** (65-100%): AGI+3, INT+3, CHA+4, Unarmed+5
- **Rose Gold Clasps** (50-90%): AGI+8, CHA+5, Marksmanship+10
- **Foul Smoke** (20-80%): INT+5, PSY+2, Stealth+3
- **Gold Cloth Pocket** (70-95%): Inventory+2
- **Troll Pocket** (70-95%): Inventory+2

### Gemstones (for rings only)
Diamond, Emerald, Ruby, Sapphire, Amethyst, Topaz, Jade, Opal, Moonstone, Onyx, Amber, Turquoise, Red Coral, Oyster Pearl, River Pearl, Sugar Diamond, Dust Jewel, Star Shard

---

## 21. The Symbel (Feasting)

### Overview
Feasting system that enhances food consumption with bonus FEPs.

### How It Works
1. Build a **Table** (various types with different bonuses)
2. Equip table with up to 9 Symbel items (cups, plates, cutlery, etc.)
3. Pull up a **Chair** and sit
4. Right-click table → click Feast → click food in table inventory
5. Quality of Symbel items determines bonus percentages

### Bonuses
- **Chair bonuses** vary by type (Royal Throne = best at 4/6)
- **Symbel items** provide FEP percentage bonuses based on quality
- **Bonfire** enhances party feasting
- **Party bonus** = hunger modifiers × average charisma of seated characters
- Host (bonfire lighter) charisma especially important

### Symbel Items (71 total)
Categories: Cutlery, Plates, Cups, Tablecloths, Napkins, Garlands, Trivets, Kettles, etc.

### Degradation
- Items degrade per food unit eaten (wear increases)
- Gold symbelware degrades slowest
- Polish the Silver (Hearth Magic) repairs symbel items
- Only one instance of each item type provides bonuses

---

## 22. Criminal Acts & Scents

### Toggle System
Adventure > Toggles > Toggle Criminal Acts (all or nothing)

### Crimes (in order of skill cost)

| Crime | Skill Cost | Action |
|-------|-----------|--------|
| **Trespassing** | 25,000 LP | Stepping on others' claimed land |
| **Vandalism** | 50,000 LP | Destroying claimed property |
| **Theft** | 50,000 LP | Stealing claimed property |
| **Assault** | 100,000 LP | Attacking another player (requires Rage) |
| **Battery** | 200,000 LP | Knocking unconscious (requires Rage) |
| **Murder** | 250,000 LP | Killing another player |

### Consequences
- Every crime = 1 SHP damage to perpetrator
- Each crime leaves a **Scent** on the claim
- Scents can be used to: Track perpetrator, track their hearthfire, or track stolen items
- **Nidbane Fetters:** Crafted from Vandalism/Battery/Theft/Murder scents
- **Nidbanes:** Summoned creatures that hunt the criminal; stats scale with crime severity; attempt to steal Hearthling Skull (denying 10% LP inheritance)

---

## 23. Swimming & Travel

### Swimming
- **Skill:** 500 LP (requires Fishing)
- **Enables:** Entering deep water, prerequisite for Boat Building
- **Toggleable:** Adventure > Toggles > Toggle Swimming
- **Stamina drain:** Reduced by Constitution and Swimming equipment bonuses
- **10 CON + full stamina** = ~10 tiles safely
- **DROWNING:** 10% MHP/second asphyxiation damage when stamina runs out in deep water = **death in 5 seconds**

### Speed System

| Speed | Tiles/sec | Notes |
|-------|-----------|-------|
| Crawl | 1.5 | Always available |
| Walk | 3.0 | Requires >10% stamina |
| Run | 4.5 | Requires >25% stamina |
| Sprint | 6.0 | Requires >50% stamina |

- Terrain limits maximum speed (e.g., swamps = Walk only)
- Stamina regenerates at 10% per 10% energy level

### Boats
- Requires Swimming → Boat Building skills
- Various boat types for river/ocean travel
- Items carried overhead while swimming are dropped if you drown

---

## 24. Terrain & World Structure

### World Layout
- Tile-based world composed of nested grids
- **Minimaps:** 100×100 tiles each
- **Supergrids:** 50×50 minimaps
- **World:** NxN supergrids (9×9 for W15/16)

### Terrain Categories (40+ biomes)

| Category | Speed Limit | Key Features |
|----------|-------------|-------------|
| **Forest** (Beech Grove, Deep Tangle, Wald, etc.) | Run | Many tree types, forageables, wildlife |
| **Grassland** (Green, Heath, Moor, etc.) | Sprint | Open terrain, fast travel |
| **Swamp** (Bog, Fen, Swamp, etc.) | Walk/Wade | Candleberry, Leech, Frog's Crown |
| **Barren** (Sand, Desert, etc.) | Sprint/Run | Limited resources |
| **Cave** (various types) | Sprint | Underground, mushroom trees, Gloomcap/Towercap |
| **Mountain** | Varies | Dwarf Pine, Wildgoat, Mammoth, Eagle |
| **Water** (Shallow, Deep, Ocean) | Swimming | Cattail, Mussel, Seal; drowning risk |

### Key Terrains
- **Beech Grove:** 30 tree types, fastest forest
- **Wald:** 45 tree types, most diverse
- **Deep Tangle:** 33 trees, dense forest
- **Mountain:** Dwarf Pine, Wildgoat, Mammoth, Eagle, rare minerals

---

## 25. Generics (Crafting Material Groups)

75 generic types that allow recipe flexibility:

| Generic | Example Items |
|---------|--------------|
| Any Flour (3) | Barley Flour, Wheat Flour, etc. |
| Any Mushroom (26) | Button Mushroom, Chantrelle, Morel, etc. |
| Any Onion (8) | Yellow Onion, Red Onion, Wild Onion, etc. |
| Any Pigment (15) | Various dyes and colors |
| Any Stuffing | Various meat/plant stuffings |
| Any Berry | Blueberry, Lingonberry, etc. |
| Any Hide | Various animal hides |
| Any Meat | All meat types |
| ... | (75 total categories) |

---

## 26. Energy, Stamina & Speed

### Energy System
- Scale: 0–10000%+
- **8000%+**: Healing state (SHP regenerates)
- **5000-**: Cannot perform hard labor
- **2000-**: Starving (HP drain begins)
- **0**: Rapid death
- Restored by eating food

### Stamina System
- **<50%**: Cannot use 4th speed (Sprint)
- **<25%**: Cannot use 3rd speed (Run)
- **<10%**: Crawl only
- **<5%**: Immobile
- Regeneration rate: 10% per 10% energy level
- Drained by running, swimming, combat, mining, etc.

---

## 27. Day/Night & Seasons

### Time
- Game runs **3.29× faster** than real time
- 24 haven hours ≈ 8 real hours
- Lunar cycle affects light level

### Seasons
- Affect crop growth, snow coverage, animal behavior
- Winter: Crops die without protection (fires or Mound Beds)
- Snow must be dug away in early spring before planting

---

## 28. Death & Inheritance

### Permadeath
- HHP reaches 0 → permanent character death
- Drowning, cave-ins, combat, starvation can all kill

### Inheritance (LP/FEP transfer to next character)

| Burial Method | Inheritance % |
|---------------|--------------|
| No burial | 15% |
| Simple burial/grave | Varies |
| Ancestral Shrine | Up to 45% |
| State Funeral (Realm skill) | 45% (no body needed) |

### Memories System
- **Memories of Kin:** Inherited social connections
- **Memories of Place:** Location knowledge
- **Memories of Seen:** Discovery data
- **Memories of War:** Combat experience

### Ancestral Shrine
- Requires Numen (earned through gameplay)
- Higher Numen = better inheritance quality
- Shrine receives descendant and grants inheritance bonuses

---

## 29. Custom Client Development

### Official Source
- **Repository:** `git://sh.seatribe.se/hafen-client`
- **Build:** Apache Ant (`Build.xml`)
- **Language:** Java (originally Java 8, modern clients use 15–21)
- **Disclaimer from loftar:** Custom clients NOT endorsed; resource files can contain executable code

### Setting Up Development Environment (from LostJustice tutorial)
1. Install JDK 8+ (modern clients use 21)
2. Clone: `git clone git://sh.seatribe.se/hafen-client`
3. Open in IntelliJ IDEA (or Eclipse)
4. Configure Ant build from `Build.xml`
5. Run: `java -Xms512m -Xmx1024m -jar hafen.jar -U https://game.havenandhearth.com/hres/ game.havenandhearth.com`

### Client Modding Approaches
1. **Pure Reflection** (LostJustice) — No source changes, use Java reflection to hook into game classes. Easier maintenance on updates, slower development.
2. **Source Code Changes** (Stya) — Direct modifications to game source. Faster development, harder to merge when game updates.
3. **Hybrid** — Middle ground, most popular approach.

### Major Custom Clients

| Client | Author | GitHub | Java | Key Features |
|--------|--------|--------|------|-------------|
| **Ender** | EnderWiggin | `EnderWiggin/hafen-client` | - | Minimap save, kin names, hide flavor objects, mass transfer (CTRL+ALT/SHIFT+ALT), numpad zoom, quick hand slots, chat timestamps |
| **Hurricane** | Nightdawg | `Nightdawg/Hurricane` | 15–21 (21 recommended) | Map integration, cookbook, Steam Workshop, Discord community |
| **Kami** | derkami | - | - | Popular custom client |
| **Nurgling II** | Katodiy | - | - | Feature-rich |
| **Purus Pasta** | shubla | - | - | Legacy client |
| **Maid** | - | - | Python API | Scripting/automation |
| **HeadlessHaven/WebHaven** | Razikus | - | - | Headless/web browser client |

### Performance Note
- GraalVM 21 gives ~15–20 extra FPS over standard JDK

### Discord Modding Community
- `discord.gg/JAjepQH` (client modding)

---

## 30. Server APIs & Technical Data

### Server Monitoring (from loftar)
- **Live stats (long-poll):** `http://www.havenandhearth.com/mt/srv-mon`
  - Text stream, long-polling
  - Returns player count, server status
- **Status page:** `http://www.havenandhearth.com/portal/index/status`
  - HTML-based, supports `?seq=N` parameter for long-polling updates

### Resource System
- Resources fetched from: `https://game.havenandhearth.com/hres/`
- **WARNING:** Resource files can contain executable code (loftar's explicit warning)
- Custom clients should sanitize/verify resources

### Map Topology
- Tiles: 100×100 pixel minimaps
- Sub-grid view system: 4 terrain lanes, 11 object lanes per minimap
- Coordinates: Signed integers, origin at world center

### External Map Services
- **Cediner's web map:** `Cediner/hnh-map-vuetify` (GitHub)
- **dafels' mapping service:** Community mapping
- **Public Mapping Service:** Community-maintained

### External Tools
- **Cediner's Cookbook:** `Cediner/hnh-food-book` (GitHub) — Food/FEP tracking
- **Build Planner** — Structure planning tool
- **3D Interactive World Map** — Visual world exploration

---

## Appendix A: Decay System

### Decay Patterns
1. **Resilient:** Only decays outside paved+claimed areas
2. **Indoor only:** Decays unless inside a building
3. **Fields/Terrain:** Transforms over time (e.g., tilled → untilled)
4. **Carcasses:** Always decay regardless of location

### Soak System (Building Resilience)
- Formula: `(soak + 1 - tool)^2 = required STR`
- Tools reduce effective soak:
  - Axe: -1
  - Pickaxe: -2
  - Sledgehammer: -4
  - Battering Ram: -20

### Fuel System
- Branch: 1 tick
- Coal: 1–2 ticks
- Block of Wood: 5 ticks
- Tarsticks: 20 ticks
- 1 tick ≈ 4 minutes 50 seconds

---

## Appendix B: Keyboard Shortcuts & Console Commands

Referenced on wiki at:
- https://ringofbrodgar.com/wiki/Keyboard_Shortcuts
- https://ringofbrodgar.com/wiki/Console_Commands

Common shortcuts include:
- **Ctrl+B** — Kin screen (permissions)
- **1–5** — Select combat deck
- **Shift+1–5** — Assign combat deck
- **Tab** — Toggle minimap
- Adventure menu — Access all player actions

---

## Appendix C: Localized Resources

Certain resources are location-bound and cannot be claimed by personal claims:
- Clay deposits, ore veins, fish nodes
- Natural Wonders (drain village authority if covered)
- Quest givers

---

*Research compiled February 2026. Sources: https://ringofbrodgar.com/wiki/ and https://www.havenandhearth.com/forum/*
