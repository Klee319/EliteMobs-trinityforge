# コンテンツ詳細スペック — 追加アイテム / レシピ / 素材（2026-07-30）

- **親文書**: `CONTENT_DRAFT_2026-07-30_source_economy.md`（設計方針・なぜこの形か）
- **この文書の役割**: エディタにそのまま入力できる粒度まで落とした**アイテム台帳**。ID / Material / CMD / 表示名 / バインド / レシピ / ステ / 入手経路を全部書く
- **数値は初期値提案**。バランスは投入後に `火力シミュレータ` と実機で詰める
- **書式は実物に合わせてある**（`items/catalog.yml` は kebab-case・`recipe:`/`recipes:` 両対応、ArsPaper `materials.yml` は snake_case）

---

## 0. ID / CustomModelData の予約

現在の使用状況（`items/catalog.yml` 261件を実測）: `1〜200` / `1000番台` / `100000番台`（ArsPaper中間素材）/ `200000番台`（TF装備）/ `300000番台`（スレッド）/ `400000番台`。

→ **新規コンテンツは `210000` 番台を予約**（衝突なし。ArsPaper 側の追加素材のみ既存流儀に合わせて `100020〜`、スレッドは `300020〜`）。

| 帯 | 用途 | 置き場所 |
|---|---|---|
| `210040–210049` | ダンジョン/フィールド素材 | TF `items/catalog.yml` |
| `210020–210029` | ソース設備（ジャー/ソースリンク） | TF `items/catalog.yml` ＋ ArsPaper `sourcejars.yml` / `sourcelinks.yml` |
| `210070–210079` | 鍵・引換関連 | TF `items/catalog.yml` |
| `210100–210119` | エンド武器・触媒 | TF `items/catalog.yml` |
| `100020–100029` | ソース直挿し用の結晶（Ars中間素材） | ArsPaper `materials.yml` |
| `300020–300029` | 追加スレッド | TF `items/catalog.yml` |

> **リソースパックのモデルは別作業**。CMD を振っても 3D モデル/テクスチャが無ければ base Material の見た目のままになる（エディタの「リソースパック管理」タブで管理）。まずは見た目なしで機能検証してよい。

**なぜ2つのファイルに分けるか**: ジャーへの直挿し判定は Ars のカスタムアイテム判定（`ItemKeys.CUSTOM_ITEM_ID`）を見る既存経路に乗せるのが最小改修なので、**ソース結晶だけは ArsPaper `materials.yml`**（既存 `source_gem` の隣）に置く。品質ロール・`bind-type`・`use-level-requirement` が要るもの（素材・鍵・装備）は **TF `catalog.yml`**。

---

## 1. 素材（5種） — TF `items/catalog.yml`

すべて `bind-type: TRADEABLE`（憲法「素材・コア・スレッドは取引可」）。

| ID | 表示名 | Material | CMD | 主な入手 | 用途 |
|---|---|---|---|---|---|
| `aether_dust` | 精気の塵 | `GLOWSTONE_DUST` | 210041 | Lv65+ の敵全般 | S3 結晶 |
| `void_filament` | 虚無の糸 | `PHANTOM_MEMBRANE` | 210042 | Lv85+ の敵全般／束縛者の増援 | S4 結晶・スレッド |
| `sealed_ember` | 封緘の燼 | `BLAZE_POWDER` | 210043 | 束縛者のミニボス | S5 結晶 |
| `binding_link` | 束縛の鎖環 | `CHAIN` | 210044 | 束縛者の各段階ボス（確定） | S6 結晶・鍵・武器 |
| `world_seam` | 世界の継ぎ目 | `CONDUIT` | 210045 | 束縛者 最終段階（低確率） | エンド武器・制覇証 |

> **`NETHER_STAR` を避ける理由**: ArsPaper の `glyphs.yml` は `unlock-cost.materials` を **Material 名で**要求し、
> `NETHER_STAR` は 15 個のグリフ解放素材に使われている。TF 品でも Material が一致すれば解放素材として消費され得るため、
> エンド素材の base には使わない（`CONDUIT` は解放素材に未使用）。

```yaml
# items/catalog.yml → items:
  aether_dust:
    material: GLOWSTONE_DUST
    display-name: "<color:#8fd3ff>精気の塵</color>"
    custom-model-data: 210041
    bind-type: TRADEABLE
    lore:
      - "<gray><i>高位の魔物が纏う魔力の残滓。</i></gray>"
      - ""
      - "<dark_gray>Lv65以上の魔物から</dark_gray>"

  void_filament:
    material: PHANTOM_MEMBRANE
    display-name: "<color:#b18cff>虚無の糸</color>"
    custom-model-data: 210042
    bind-type: TRADEABLE
    lore:
      - "<gray><i>世界の裂け目からほつれ落ちた繊維。</i></gray>"
      - ""
      - "<dark_gray>Lv85以上の魔物・束縛者の眷属から</dark_gray>"

  sealed_ember:
    material: BLAZE_POWDER
    display-name: "<color:#ff9a3c>封緘の燼</color>"
    custom-model-data: 210043
    bind-type: TRADEABLE
    lore:
      - "<gray><i>封じられた炉の火。まだ熱を失っていない。</i></gray>"
      - ""
      - "<dark_gray>世界を繋ぐ者の聖所・ミニボスから</dark_gray>"

  binding_link:
    material: CHAIN
    display-name: "<color:#c0c0ff>束縛の鎖環</color>"
    custom-model-data: 210044
    bind-type: TRADEABLE
    enchant-glow: true
    lore:
      - "<gray><i>世界と世界を縫い合わせるための一環。</i></gray>"
      - ""
      - "<dark_gray>世界を繋ぐ者の各段階から確定で</dark_gray>"

  world_seam:
    material: CONDUIT
    display-name: "<gradient:#7a5cff:#00e5ff>世界の継ぎ目</gradient>"
    custom-model-data: 210045
    bind-type: TRADEABLE
    enchant-glow: true
    lore:
      - "<gray><i>縫い目そのもの。触れると向こう側の音がする。</i></gray>"
      - ""
      - "<dark_gray>世界を繋ぐ者 最終段階</dark_gray>"
```

---

## 2. ソース直挿しライン（6段） — ArsPaper `materials.yml`

**前提**: 親文書 §7-A の `insertables` 改修（ジャーへ直接投入できるアイテムと量の config 化）。
改修が入るまでは S1（ソースベリー100）のみが実運用可。

### 2-1. 価値と圧縮

**4個 → 1個・価値8倍**。差額の 4倍分が「この儀式で生まれるソース」＝**生産強化の実体**。
各段の儀式は**その段の素材1個を必ず要求する**ので、**素材の入手速度がそのままソース生産速度の上限になる**
（＝ダンジョン周回がソース経済に直結する。ここがこの設計の背骨）。

| 段 | ID | 表示名 | base Material | CMD | 1個の価値 | 作り方 | ベリー換算効率 |
|---|---|---|---|---|---|---|---|
| S1 | `source_berry`（既存） | ソースベリー | — | — | **100** | 栽培 | 100 /個 |
| S2 | `source_grain` | ソースの粒 | `PRISMARINE_CRYSTALS` | 100020 | **800** | ベリー×4 ＋ 鉄インゴット×1 | 200 |
| S3 | `source_crystal` | ソース結晶 | `AMETHYST_CLUSTER` | 100021 | **6,400** | 粒×4 ＋ `aether_dust`×1 | 400 |
| S4 | `dense_crystal` | 濃縮結晶 | `ECHO_SHARD` | 100022 | **51,200** | 結晶×4 ＋ `void_filament`×1 | 800 |
| S5 | `arcane_crystal` | 魔導結晶 | `HEART_OF_THE_SEA` | 100023 | **409,600** | 濃縮×4 ＋ `sealed_ember`×1 | 1,600 |
| S6 | `dimensional_crystal` | 次元結晶 | `END_CRYSTAL` | 100024 | **3,276,800** | 魔導×4 ＋ `binding_link`×1 | **3,200** |

- 効率は S1→S6 で **32倍**。「同じベリー農場のままでも、上の段を作れるようになるほど稼ぎが増える」＝クリッカーの強化感
- **S6 より上は作らない**。理由: 投入は容量でクランプされ、超過分は消滅するため、1個の価値がジャー容量を超えると損になる（§3 の容量ラダーが S6 の 3,276,800 を受けきる上限）

### 2-2. YAML

```yaml
# ArsPaper materials.yml → materials:
  source_grain:
    base_material: PRISMARINE_CRYSTALS
    custom_model_data: 100020
    display_name: "ソースの粒"
    name_color: "&b"
    lore:
      - "&7ベリーを圧縮した魔力の粒"
      - "&8ソースジャーに直接投入できる (800)"
    recipe:
      method: ritual
      core-item: "custom:source_berry"
      pedestal-items:
        - "custom:source_berry x3"
        - "IRON_INGOT x1"
      source: 100

  source_crystal:
    base_material: AMETHYST_CLUSTER
    custom_model_data: 100021
    display_name: "ソース結晶"
    name_color: "&b"
    lore:
      - "&7粒が結び合って結晶になった"
      - "&8ソースジャーに直接投入できる (6,400)"
    recipe:
      method: ritual
      core-item: "custom:source_grain"
      pedestal-items:
        - "custom:source_grain x3"
        - "custom:aether_dust x1"
      source: 400

  dense_crystal:
    base_material: ECHO_SHARD
    custom_model_data: 100022
    display_name: "濃縮結晶"
    name_color: "&3"
    lore:
      - "&7内側で魔力が渦を巻いている"
      - "&8ソースジャーに直接投入できる (51,200)"
    recipe:
      method: ritual
      core-item: "custom:source_crystal"
      pedestal-items:
        - "custom:source_crystal x3"
        - "custom:void_filament x1"
      source: 3200

  arcane_crystal:
    base_material: HEART_OF_THE_SEA
    custom_model_data: 100023
    display_name: "魔導結晶"
    name_color: "&d"
    lore:
      - "&7炉の火を閉じ込めた結晶"
      - "&8ソースジャーに直接投入できる (409,600)"
    recipe:
      method: ritual
      core-item: "custom:dense_crystal"
      pedestal-items:
        - "custom:dense_crystal x3"
        - "custom:sealed_ember x1"
      source: 25600

  dimensional_crystal:
    base_material: END_CRYSTAL
    custom_model_data: 100024
    display_name: "次元結晶"
    name_color: "&5"
    lore:
      - "&7ひとつの世界ぶんの魔力が入っている"
      - "&8ソースジャーに直接投入できる (3,276,800)"
    recipe:
      method: ritual
      core-item: "custom:arcane_crystal"
      pedestal-items:
        - "custom:arcane_crystal x3"
        - "custom:binding_link x1"
      source: 204800
```

### 2-3. 投入テーブル（`insertables` 改修後）

```yaml
# sourcejars.yml
insertables:
  custom:source_berry: 100        # 既存挙動を config へ移すだけ
  custom:source_grain: 800
  custom:source_crystal: 6400
  custom:dense_crystal: 51200
  custom:arcane_crystal: 409600
  custom:dimensional_crystal: 3276800
```

ホッパー1本（2.5個/秒）で流した場合の生産速度: S1 250/秒 → **S6 819万/秒**。
実際は結晶の生産量（＝素材の集まる速さ）が律速。

---

## 3. ソース設備 — `sourcejars.yml` / `sourcelinks.yml`

### 3-1. ジャー容量ラダー（`jars.<id>.capacity`）

投入1回の価値が容量を超えると**超過分は消滅**する（`addSource` のクランプ）。S段と対応させる。

| ID | 表示名 | base | CMD | capacity | 受けきれる最大投入 | 儀式コスト |
|---|---|---|---|---|---|---|
| `source_jar`（既存） | ソースジャー | `GLASS` 等 | 既存 | 10,000 | S1 ベリー | — |
| `source_jar_reinforced` | 補強ソースジャー | `TINTED_GLASS` | 210021 | 80,000 | S2 粒 | 30,000 |
| `source_jar_arcane` | 魔導ソースジャー | `TINTED_GLASS` | 210022 | 640,000 | S3 結晶 | 300,000 |
| `source_jar_dimensional` | 次元ソースジャー | `TINTED_GLASS` | 210023 | 5,120,000 | **S6 次元結晶** | 3,000,000 |
| `source_jar_infinite` | 無窮のソースジャー | `TINTED_GLASS` | 210024 | `-1`（無限） | — | 25,000,000 |

```yaml
# sourcejars.yml → jars:
  source_jar_dimensional:
    material: TINTED_GLASS
    display-name: "<color:#a06cff>次元ソースジャー</color>"
    custom-model-data: 210023
    capacity: 5120000
    lore:
      - "<gray>次元結晶をそのまま受けきる容量</gray>"
    recipe:
      method: ritual
      core-item: custom:source_jar_arcane
      pedestal-items:
        - custom:arcane_crystal x4
        - custom:binding_link x2
        - TINTED_GLASS x8
      source: 3000000
```

> `capacity: -1` は無限。無限ジャーは**書き込みをスキップする**実装なので、投入口としてではなく
> 「詠唱用の常設タンク」として機能する。制覇後のご褒美（QoL）として置くのが妥当。

### 3-2. ソースリンク増設（`sourcelinks.yml` の `items.<id>`）

1台10/秒の上限は変わらない（親文書 §2-1）。**台数を増やす sink** として使う。

```yaml
# sourcelinks.yml → items:
  volcanic_sourcelink_mk2:
    type: volcanic
    material: BLAST_FURNACE
    display-name: "<color:#ff9a3c>強化ボルケニックソースリンク</color>"
    custom-model-data: 210026
    lore:
      - "<gray>燃料を消費してソースを生成する（増設用）</gray>"
    recipe:
      method: ritual
      core-item: BLAST_FURNACE
      pedestal-items:
        - custom:source_crystal x2
        - custom:aether_dust x4
      source: 50000
```

同型で `mycelial_sourcelink_mk2`（`SMOKER`, CMD 210027）も用意すると、補助職（農夫/漁師）の産物が活きる。

### 3-3. 燃料値の追加（`sourcelinks.yml` の `<type>.materials`）

**速度ではなく投入頻度**に効く（親文書 §2-1）。自動化の手間を減らすための調整。

```yaml
volcanic:
  materials:
    MAGMA_BLOCK: 2000      # ↓ 併せて crafting-features.yml でバニラ入手を封鎖する
mycelial:
  materials:
    GOLDEN_CARROT: 200
```

```yaml
# progression/crafting-features.yml
removed-vanilla-items:
  - MAGMA_BLOCK
removed-vanilla-recipes:
  - minecraft:magma_block
```

```yaml
# items/catalog.yml — MAGMA_BLOCK を占有する TF 品（唯一の入口）
  arcane_briquette:
    material: MAGMA_BLOCK
    display-name: "<gold>魔導練炭</gold>"
    custom-model-data: 210028
    bind-type: TRADEABLE
    lore:
      - "<gray><i>ソースリンクに1個入れるだけで長く燃える。</i></gray>"
    recipe:
      method: ritual
      core-item: COAL_BLOCK
      pedestal-items:
        - COAL_BLOCK x4
        - custom:aether_dust x1
      source: 0
      type: shapeless
      ingredients: []
```

> **要確認**: `removed-vanilla-items` に入れた Material を材料に使う他レシピが無いか（掃除対象は"アイテム"なので素材として消える）。TF カタログ品は必ず除外されるので、上の `arcane_briquette` 自身は消えない。

---

## 4. ソースの使い道（sink） — 累計ちょうど1億

「1億ソース」は**貯めるだけでなく使い切る**設計にする。以下の合計が目標値になる。

| # | 使い先 | 単価 | 個数 | 小計 | 累計 |
|---|---|---|---|---|---|
| 1 | 補強ジャー | 30,000 | 1 | 30,000 | 30,000 |
| 2 | 魔導ジャー | 300,000 | 1 | 300,000 | 330,000 |
| 3 | ソースリンク増設 | 50,000 | 4 | 200,000 | 530,000 |
| 4 | 次元ジャー | 3,000,000 | 1 | 3,000,000 | 3,530,000 |
| 5 | 束縛者の鍵（通常） | 500,000 | 10 | 5,000,000 | 8,530,000 |
| 6 | 束縛者の鍵（極） | 2,000,000 | 5 | 10,000,000 | 18,530,000 |
| 7 | エンド武器/触媒 | 5,000,000 | 3 | 15,000,000 | 33,530,000 |
| 8 | 無窮のソースジャー | 25,000,000 | 1 | 25,000,000 | 58,530,000 |
| 9 | **世界樹の種（制覇証）** | 40,000,000 | 1 | 40,000,000 | **98,530,000** |

**鍵が消耗品（周回コスト）である**のが要点。ダンジョンを回るとソースが減り、
ソースを増やすには素材が要り、素材はダンジョンから出る ＝ **ループが閉じる**。

### 4-1. 制覇証「世界樹の種」

```yaml
# items/catalog.yml
  world_tree_seed:
    material: ECHO_SHARD
    display-name: "<gradient:#00ffa3:#7a5cff>世界樹の種</gradient>"
    custom-model-data: 210110
    bind-type: OWNER_BOUND
    enchant-glow: true
    lore:
      - "<gray><i>一億の魔力を注ぎ込んで、ようやく芽の形になった。</i></gray>"
      - "<gray><i>これは強さではない。積み上げた時間そのものだ。</i></gray>"
      - ""
      - "<dark_gray>累計1億ソース到達の証</dark_gray>"
    recipe:
      method: ritual
      core-item: custom:world_seam
      pedestal-items:
        - custom:dimensional_crystal x4
        - custom:binding_link x8
        - custom:world_seam x2
      source: 40000000
      type: shapeless
      ingredients: []
```

戦闘ステは付けない（`item-stats.yml` に登録しない）。図鑑登録 → アチーブ（§9）で称号が出る。

### 4-2. 1億に必要な素材の総量（Bill of Materials）

sink 合計 9,853万を S6 次元結晶（3,276,800）で賄う場合 ≈ **30個**。1個の内訳から逆算:

| 必要物 | 1個の次元結晶あたり | 30個ぶん | 入手の目安 |
|---|---|---|---|
| `binding_link` | 1 | **30** | 束縛者ボス確定1〜2個 → **20〜30周** |
| `sealed_ember` | 4 | 120 | ミニボス 0.6×(1〜2) → 25周で足りる |
| `void_filament` | 16 | 480 | Lv85+ 0.12×(1〜2) ＋ 束縛者増援 0.35 |
| `aether_dust` | 64 | 1,920 | Lv65+ 0.25×(1〜3) → 約1,000体 |
| 鉄インゴット | 256 | 7,680 | 通常の採掘/交易 |
| ソースベリー | 1,024 | **30,720** | ベリー農場（1個/秒で約8.5時間） |

→ **束縛者20〜30周＋フィールド周回＋ベリー農場**で 1億に届く。ここが目標プレイ時間の主ノブ
（`binding_link` のドロップ数と鍵コストを触れば、周回回数が直接変わる）。

---

## 5. 束縛者ライン

### 5-1. 入場鍵（2種）

親文書 §3-3 のとおり、`key-material` は Material 限定＋消費が meta 一致なので、
**当面は「所持＝入場権」のライセンス方式**、`key-catalog:` 実装後は消費式に切り替える。

| ID | 表示名 | Material | CMD | ゲート |
|---|---|---|---|---|
| `key_of_the_binder` | 世界を繋ぐ者の鍵 | `TRIAL_KEY` | 210071 | combatLv 90 |
| `key_of_the_binder_omen` | 世界を繋ぐ者の凶鍵 | `OMINOUS_TRIAL_KEY` | 210072 | combatLv 95（極） |

```yaml
# items/catalog.yml
  key_of_the_binder:
    material: TRIAL_KEY
    display-name: "<color:#c0c0ff>世界を繋ぐ者の鍵</color>"
    custom-model-data: 210071
    bind-type: SOULBOUND
    enchant-glow: true
    lore:
      - "<gray><i>鍵穴のない扉に、鍵の形をした魔力を差し込む。</i></gray>"
      - ""
      - "<dark_gray>世界を繋ぐ者の聖所に入場できる</dark_gray>"
    recipe:
      method: ritual
      core-item: custom:binding_link
      pedestal-items:
        - custom:source_crystal x4
        - custom:sealed_ember x2
        - TRIAL_KEY x1
      source: 500000
      type: shapeless
      ingredients: []

  key_of_the_binder_omen:
    material: OMINOUS_TRIAL_KEY
    display-name: "<color:#9a4dff>世界を繋ぐ者の凶鍵</color>"
    custom-model-data: 210072
    bind-type: SOULBOUND
    enchant-glow: true
    lore:
      - "<gray><i>向こう側から、こちらを覗く音がする。</i></gray>"
      - ""
      - "<dark_gray>最高難易度（極）に入場できる</dark_gray>"
    recipe:
      method: ritual
      core-item: custom:key_of_the_binder
      pedestal-items:
        - custom:binding_link x4
        - custom:arcane_crystal x1
      source: 2000000
      type: shapeless
      ingredients: []
```

```yaml
# progression/crafting-features.yml — バニラのトライアルキー入手を塞ぐ（TF品は除外される）
removed-vanilla-items:
  - TRIAL_KEY
  - OMINOUS_TRIAL_KEY
```

```yaml
# dungeon/gates.yml
gates:
  binder_of_worlds_sanctum:
    required-combat-level: 90
    key-material: TRIAL_KEY
    key-amount: 1
    content-package: the_binder_of_worlds
```

### 5-2. エンド武器・触媒（4種）

**ドロップは素材（`world_seam`）で、完成品は儀式**にする。理由: ①品質厳選をやり直せる（外れたら解体して再挑戦）
②`OWNER_BOUND` の「自力で作った」感 ③ドロップテーブルを汚さない。

数値のアンカー（実測）: `NETHERITE_SWORD` = attack-power 3,780 / use-lv 70、
`NETHERITE_SWORD#144`（重量武器） = 17,136 / use-lv 85。→ **エンドは use-lv 90 帯で 20,000〜30,000**。

| ID | 表示名 | Material#CMD | スキル | 性格（何を捨てるか） |
|---|---|---|---|---|
| `seamweaver_blade` | 界縫いの刃 | `NETHERITE_SWORD#210101` | LIGHT_WEAPONS | 速い・回避寄り。単体火力は最低 |
| `dimension_reaper` | 次元断ちの大鎌 | `NETHERITE_AXE#210102` | HEAVY_WEAPONS | 最大火力＋AoE。遅い・移動速度低下 |
| `voidpiercer_bow` | 虚無を射る弓 | `BOW#210103` | ARCHERY | 貫通特化。会心倍率が低い |
| `binder_scepter` | 束縛者の錫杖 | `AMETHYST_SHARD#210104` | ARS_MAGIC | 魔法用。物理は一切乗らない |

```yaml
# items/catalog.yml
  seamweaver_blade:
    material: NETHERITE_SWORD
    display-name: "<gradient:#00e5ff:#7a5cff>界縫いの刃</gradient>"
    custom-model-data: 210101
    bind-type: OWNER_BOUND
    use-level-requirement: 90
    use-skill: LIGHT_WEAPONS
    enchant-glow: true
    lore:
      - "<gray><i>斬った跡が、一瞬だけ向こう側の景色になる。</i></gray>"
      - ""
      - "<dark_gray>所有者バインド / 修繕不可</dark_gray>"
    recipe:
      method: ritual
      core-item: custom:world_seam
      pedestal-items:
        - custom:binding_link x8
        - custom:dimensional_crystal x1
        - NETHERITE_INGOT x2
      source: 5000000
      type: shapeless
      ingredients: []

  dimension_reaper:
    material: NETHERITE_AXE
    display-name: "<gradient:#7a5cff:#ff3c6a>次元断ちの大鎌</gradient>"
    custom-model-data: 210102
    bind-type: OWNER_BOUND
    use-level-requirement: 90
    use-skill: HEAVY_WEAPONS
    enchant-glow: true
    lore:
      - "<gray><i>振り抜くたびに、空間が遅れて悲鳴を上げる。</i></gray>"
      - ""
      - "<dark_gray>所有者バインド / 修繕不可</dark_gray>"
    recipe:
      method: ritual
      core-item: custom:world_seam
      pedestal-items:
        - custom:binding_link x8
        - custom:dimensional_crystal x1
        - NETHERITE_INGOT x3
      source: 5000000
      type: shapeless
      ingredients: []
```

（`voidpiercer_bow` / `binder_scepter` も同型。`core-item` は同じ `world_seam`、
`pedestal-items` の金属部分だけ差し替える）

#### item-stats（`stats/item-stats.yml`）

```yaml
  "NETHERITE_SWORD#210101":          # 界縫いの刃
    fixed:
      attack-power: 21000
      attack-speed: 1.55
      attack-reach: 0.15
      crit-chance: 0.10
      crit-damage: 0.62
      penetration: 0.12
      damage-modifier: 0.85
      dodge-chance: 0.03
      durability: 1500
    per-quality:
      attack-power: 1000
      crit-chance: 0.004
      crit-damage: 0.02
      penetration: 0.005
      durability: 210
    use-level-requirement: 90
    use-skill: LIGHT_WEAPONS
    quality-mode-offset: -7
    random:
      attack-power: { min: -3360, max: 4200 }
      crit-damage:  { min: -0.12, max: 0.18 }
      dodge-chance: { min: -0.02, max: 0.04 }

  "NETHERITE_AXE#210102":            # 次元断ちの大鎌
    fixed:
      attack-power: 30000
      attack-speed: 0.72
      attack-reach: 0.35
      crit-chance: 0.04
      crit-damage: 0.75
      penetration: 0.08
      damage-modifier: 0.92
      aoe-radius: 3.5
      aoe-damage-rate: 0.45
      aoe-max-targets: 6
      move-speed: -0.022
      durability: 1500
    per-quality:
      attack-power: 1400
      crit-damage: 0.02
      penetration: 0.004
      durability: 210
    use-level-requirement: 90
    use-skill: HEAVY_WEAPONS
    quality-mode-offset: -7
    random:
      attack-power:    { min: -4800, max: 6000 }
      aoe-damage-rate: { min: -0.08, max: 0.12 }
      move-speed:      { min: -0.02, max: 0.0 }

  "BOW#210103":                      # 虚無を射る弓
    fixed:
      attack-power: 19000
      penetration: 0.28
      crit-chance: 0.12
      crit-damage: 0.40
      damage-modifier: 0.88
      durability: 1200
    per-quality:
      attack-power: 900
      penetration: 0.006
      durability: 180
    use-level-requirement: 90
    use-skill: ARCHERY
    quality-mode-offset: -7
    random:
      attack-power: { min: -3040, max: 3800 }
      penetration:  { min: -0.06, max: 0.09 }
```

> **触媒（`binder_scepter`）のステは既存の触媒エントリからコピーして作ること**。
> 魔法側は `mana-bonus` / `magic-resistance` / `percent-bonus-damage` などキー体系が武器と違い、
> グリフ増減との二重計上を避ける層分離（`MAGIC_BALANCE_SPEC` §2）に従う必要がある。

> **武器に `thread-slots` を書いても効かない（2026-07-30 実装確認）**。
> `ThreadSlotPolicy#applyCategoryCap` は `progression/crafting-features.yml` の
> `thread-slots.max-by-category` を見て、**カテゴリ上限が 0 のときステ自体を削除する**。
> 現在の設定は `armor: 5 / weapon: 0 / tool: 0 / other: 0` なので、武器のスレッドスロットは常に消える
> （出荷済みの `NETHERITE_SWORD#144` に書かれている `thread-slots: 2` も実効ゼロ＝既存の記述ミス）。
> 上のエンド武器からは意図的に外してある。**武器にスレッドを持たせたいなら先に `weapon:` の上限を上げること**（要判断）。

### 5-3. 束縛者の強さ調整（`combat/mob-overrides.yml`）

既存の `em_id_binder_of_worlds`（防御率と EXP のみ設定済み）に、以下を**項目追加**する（stats は項目単位マージなので安全）。

```yaml
overrides:
  em_id_binder_of_worlds:
    display-name: "世界を繋ぐ者の聖所"
    mobs:
      em_id_binder_of_worlds_phase_4:                 # 最終段階
        stats:
          max-health: 0            # ← 標準比 ×2.5 の実数を入れる（シミュレータで確定）
          physical: { defense-rate: 0.068, resistance: 0.204 }
          magical:  { defense-rate: 0.45,  resistance: 0.35 }
          armor-strength: 0.25
          attack:
            crit-chance: 0.15
            crit-damage: 0.5
            fixed-damage: 12        # ← 防御ステで消せない削り＝タンク/回復の必然性
        drops:
          - { item: "custom:binding_link", chance: 1.0,  min: 1, max: 2 }
          - { item: "custom:world_seam",   chance: 0.35, min: 1, max: 1 }
      em_id_binder_of_worlds_phase_3:
        stats:
          magical: { defense-rate: 0.45, resistance: 0.35 }
        drops:
          - { item: "custom:binding_link", chance: 1.0, min: 1, max: 1 }
      em_id_binder_of_worlds_phase_1_melee_miniboss:
        drops:
          - { item: "custom:sealed_ember", chance: 0.6, min: 1, max: 2 }
      em_id_binder_of_worlds_phase_1_ranged_miniboss:
        drops:
          - { item: "custom:sealed_ember", chance: 0.6, min: 1, max: 2 }
      em_id_binder_of_worlds_phase_1_status_miniboss:
        drops:
          - { item: "custom:sealed_ember", chance: 0.6, min: 1, max: 2 }
      em_id_binder_of_worlds_phase_3_zapper_reinforcement_mythic:
        stats:                                        # ★逆張り（物理装甲厚）＝持ち替え強制
          physical: { defense-rate: 0.45, resistance: 0.30 }
          magical:  { defense-rate: 0.05, resistance: 0.10 }
        drops:
          - { item: "custom:void_filament", chance: 0.35, min: 1, max: 1 }
```

- `drops` は**置換**なので、既存の `drops: []` を上書きする形になる
- `max-health` は倍率で書けないので、シミュレータで同レベル標準値（`150×1.072^Lv`）×2.5 を計算して実数を入れる

### 5-4. 高レベル帯の素材（`combat/mob-level-table.yml`）

現状 tiers は `vanilla-exp` のみで `add-drops` が全帯空。既存の帯へ追記する。

```yaml
tiers:
  - min-level: 65
    vanilla-exp: 85
    add-drops:
      - { material: "custom:aether_dust", chance: 0.25, min: 1, max: 3 }
  - min-level: 85
    vanilla-exp: 140
    add-drops:
      - { material: "custom:aether_dust",   chance: 0.25, min: 2, max: 4 }
      - { material: "custom:void_filament", chance: 0.12, min: 1, max: 2 }
```

（既存の `mobs:` リストはそのまま残す。`add-drops` エントリ側に `mobs:`/`mob-ids:` を書けば個別に絞り込める）

---

## 6. 構造物ルートチェスト（Dungeons and Taverns）

`LootGenerateEvent` は削除専用のため、**チェストに TF アイテムを直接入れることはできない**（親文書 §7-B）。
**引換証方式**で、追加実装ゼロで旨みを作る。

### 6-1. 引換証＝「DnT のチェストに元から入るバニラ品」を指定する

TF 側は何も置かず、**両替レートだけを決める**。3段:

| 段 | 引換証（バニラ品） | 想定出所 | 交換先 |
|---|---|---|---|
| 小 | `ECHO_SHARD` | 深層系の構造物 | `tf_gacha_ticket_2` ×1 |
| 中 | `HEART_OF_THE_SEA` | 水中・宝物庫系 | `tf_gacha_ticket_4` ×1 |
| 大 | `ENCHANTED_GOLDEN_APPLE` | ボス部屋・隠し部屋 | `tf_gacha_ticket_treasure` ×1 |

> **注意**: `ECHO_SHARD` は §2 で `dense_crystal` の base Material に使っている。
> base Material が同じでも CMD で別物として扱われるが、**引換証は素のバニラ品**なので、
> 交換所の入力は `material:` 指定（バニラのみマッチ）で問題ない。混乱を避けたい場合は
> `dense_crystal` の base を `AMETHYST_SHARD` などに変えてよい。

### 6-2. 交換所（`economy/villager-trades.yml`）

```yaml
professions:
  CARTOGRAPHER:
    block-vanilla-trades: false
    trades:
      - input:  { material: ECHO_SHARD, amount: 1 }
        output: { catalog: tf_gacha_ticket_2, amount: 1 }
        max-uses: 16
        villager-xp: 4
      - input:  { material: HEART_OF_THE_SEA, amount: 1 }
        output: { catalog: tf_gacha_ticket_4, amount: 1 }
        max-uses: 8
        villager-xp: 8
      - input:  { material: ENCHANTED_GOLDEN_APPLE, amount: 1 }
        output: { catalog: tf_gacha_ticket_treasure, amount: 1 }
        max-uses: 4
        villager-xp: 12
```

`trade:CARTOGRAPHER` をスキルツリーのどこかのノードで参照して解放する（未参照の職業は常にロック）。

### 6-3. 宝札とプール（`items/catalog.yml` ＋ `gacha.yml`）

```yaml
# items/catalog.yml
  tf_gacha_ticket_treasure:
    material: PAPER
    display-name: "<gradient:#ffd76a:#ff7a3c>封蝋の宝札</gradient>"
    custom-model-data: 210081
    bind-type: TRADEABLE
    enchant-glow: true
    lore:
      - "<gray><i>右クリックで開封する。</i></gray>"
      - ""
      - "<dark_gray>構造物の宝箱由来</dark_gray>"
```

```yaml
# gacha.yml
tickets:
  tf_gacha_ticket_treasure:
    pool: treasure

pools:
  treasure:
    pity:
      threshold: 24            # 24連続で最高レア枠を外したら次回確定
    entries:
      - { item: "thread_seam_echo",  weight: 2,  amount: 1, quality-random: true }   # 最高レア枠
      - { item: "thread_void_weave", weight: 3,  amount: 1, quality-random: true }
      - { item: "void_filament",     weight: 10, amount: 2 }
      - { item: "aether_dust",       weight: 14, amount: 3 }
      - { item: "source_crystal",    weight: 6,  amount: 1 }
      - { item: "DIAMOND",           weight: 8,  amount: 4 }
```

- `quality-random` は **TF カタログ品にのみ**効く（0〜現在の最大品質をランダム付与。`quality.yml max-quality: 9`）→ **これが宝箱の厳選要素**
- `pity.threshold` は「最高レア枠（weight 最小）を連続で外した回数」の天井。プレイヤー毎・プール毎に PDC 永続化

### 6-4. ⚠ 参照だけあって定義が無いカタログID（先に埋める必要あり）

出荷config（`TrinityForge/src/main/resources` ＝ エディタの既定 basePath ＝ SoT）を実測したところ、
**他のconfigやJavaから参照されているのに `items/catalog.yml` に定義が無いID**が以下にあった（2026-07-30）。

| ID | 参照元 | 影響 |
|---|---|---|
| `tf_gacha_ticket`, `tf_gacha_ticket_1〜5` | `gacha.yml` / `mining-gimmick.yml` / `fishing-gimmick.yml` / `skilltree/fishing.yml` | **券が生成されず、右クリックしても抽選が走らない**（判定はカタログIDのPDCタグ） |
| `tf_scrap` | `villager-trades.yml` / `gacha.yml` / Java `FishingGimmickListener.CATALOG_SCRAP` | `junk-to-scrap` の差し替え先が作れない |
| `tf_core_wood`, `tf_core_jewelry`, `tf_core_vegetable` | `villager-trades.yml` / `gacha.yml` | 交換・景品が出ない |
| `example_sword`, `example_bow` | `gacha.yml pools.standard/tier2/tier3` | 標準プールの景品が引けない |

> **要確認（P0）**: 稼働サーバの `plugins/TrinityForge/items/catalog.yml` はエディタ経由で編集されているため、
> **実機側では既に定義済みの可能性がある**。着手前に稼働側を確認し、リポジトリ側と差分があれば埋め戻すこと。

§6-2 の交換所が `tf_gacha_ticket_2` / `_4` を出力に使うので、**少なくとも券5種は先に定義する**。

```yaml
# items/catalog.yml — 券の雛形（tier ごとに CMD と色だけ変える）
  tf_gacha_ticket_1:
    material: PAPER
    display-name: "<white>ガチャ券（初級）</white>"
    custom-model-data: 210082
    bind-type: TRADEABLE
    lore:
      - "<gray><i>右クリックで1回抽選する。</i></gray>"
  tf_gacha_ticket_2:
    material: PAPER
    display-name: "<green>ガチャ券（中級）</green>"
    custom-model-data: 210083
    bind-type: TRADEABLE
    lore:
      - "<gray><i>右クリックで1回抽選する。</i></gray>"
  tf_gacha_ticket_3:
    material: PAPER
    display-name: "<aqua>ガチャ券（上級）</aqua>"
    custom-model-data: 210084
    bind-type: TRADEABLE
    lore:
      - "<gray><i>右クリックで1回抽選する。</i></gray>"
  tf_gacha_ticket_4:
    material: PAPER
    display-name: "<light_purple>ガチャ券（特級）</light_purple>"
    custom-model-data: 210085
    bind-type: TRADEABLE
    lore:
      - "<gray><i>右クリックで1回抽選する。</i></gray>"
  tf_gacha_ticket_5:
    material: PAPER
    display-name: "<gold>ガチャ券（極級）</gold>"
    custom-model-data: 210086
    bind-type: TRADEABLE
    lore:
      - "<gray><i>右クリックで1回抽選する。</i></gray>"
```

券にレシピは付けない（採取 drop-tables・村人交換・ダンジョン報酬が入口）。
`tf_scrap` / `tf_core_*` / `example_*` も同様に、実在するIDへ差し替えるか定義を足す。

---

## 7. 追加スレッド（2種・宝箱限定） — `items/catalog.yml`

憲法「スレッドは特殊効果系のみクラフト・残りは厳選」に従い、**この2種はクラフト不可**（ガチャのみ）。
既存スレッドは `300001〜` を使用中なので `300020〜` を使う。防具のスレッドスロットは現在 5（`thread-slots.max-by-category.armor`）。

| ID | 表示名 | Material | CMD | 効果の方向 |
|---|---|---|---|---|
| `thread_seam_echo` | 継ぎ目の反響のスレッド | `SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE` | 300020 | 会心軽減（防具強度）寄り |
| `thread_void_weave` | 虚無織りのスレッド | `VEX_ARMOR_TRIM_SMITHING_TEMPLATE` | 300021 | 回避＋移動速度寄り |

```yaml
  thread_seam_echo:
    material: SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE
    display-name: "<color:#8fd3ff>継ぎ目の反響のスレッド</color>"
    custom-model-data: 300020
    bind-type: TRADEABLE
    enchant-glow: true
    lore:
      - "<gray><i>殴られた音が、殴られる前に返ってくる。</i></gray>"
      - "<dark_gray>防具のスレッドスロットにセット可能</dark_gray>"
      - ""
      - "<dark_gray>構造物の宝箱限定（クラフト不可）</dark_gray>"
    # recipe は書かない（＝厳選専用）
```

item-stats 側（`thread` カテゴリ）で `armor-strength` / `dodge-chance` を `per-quality` 中心に振ると、
**品質がそのまま価値になる**＝厳選が意味を持つ。

---

## 8. ドロップ / 入手経路の総括表

| アイテム | 主経路 | 設定ファイル | 副経路 |
|---|---|---|---|
| `aether_dust` | Lv65+ 帯 0.25 (1〜3) | `mob-level-table.yml` | ガチャ treasure |
| `void_filament` | Lv85+ 帯 0.12 (1〜2) | `mob-level-table.yml` | 束縛者 増援 0.35 / ガチャ |
| `sealed_ember` | 束縛者 ミニボス 0.6 (1〜2) | `mob-overrides.yml` | — |
| `binding_link` | 束縛者 各段ボス 1.0 | `mob-overrides.yml` | — |
| `world_seam` | 束縛者 最終段階 0.35 | `mob-overrides.yml` | — |
| S2〜S6 結晶 | 儀式クラフト | ArsPaper `materials.yml` | ガチャ（S3のみ） |
| 鍵（通常/極） | 儀式クラフト | `catalog.yml` | — |
| エンド武器 | 儀式クラフト（`world_seam` 必須） | `catalog.yml` | — |
| 追加スレッド | ガチャ treasure のみ | `gacha.yml` | — |
| 引換証 | 構造物チェスト（バニラ品） | （DnT 側） | — |
| ガチャ券 | 村人交換 ＋ 既存の採取 drop-tables | `villager-trades.yml` / `*-gimmick.yml` | — |

---

## 9. 図鑑・アチーブメント・称号

```yaml
# progression/collection.yml → categories.items:
    source_economy:
      display-name: ソース経済
      order: 2
      entries:
        - source_grain
        - source_crystal
        - dense_crystal
        - arcane_crystal
        - dimensional_crystal
        - source_jar_reinforced
        - source_jar_arcane
        - source_jar_dimensional
        - source_jar_infinite
        - world_tree_seed
    binder:
      display-name: 世界を繋ぐ者
      order: 3
      entries:
        - binding_link
        - world_seam
        - key_of_the_binder
        - key_of_the_binder_omen
        - seamweaver_blade
        - dimension_reaper
        - voidpiercer_bow
        - binder_scepter
```

```yaml
# progression/special-rewards.yml
titles:
  title_furnace_master:
    display: "<gradient:#ff9a3c:#ffd76a>炉の主</gradient>"
  title_dimension_steward:
    display: "<gradient:#7a5cff:#00e5ff>次元の管財人</gradient>"
  title_hundred_million:
    display: "<gradient:#00ffa3:#7a5cff><b>億の管財人</b></gradient>"
particles:
  particle_seam:
    particle: END_ROD
    count: 6
    radius: 0.5
    interval-ticks: 8
    shape: aura
```

```yaml
# progression/achievements.yml → achievements:
  reach_arcane_crystal:
    display-name: 炉の主
    trigger:
      type: static
      collection: { scope: item, target: arcane_crystal, threshold: 1, percent: false }
    broadcast: false
    rewards:
      special: [title_furnace_master]

  reach_dimensional_crystal:
    display-name: 次元の管財人
    trigger:
      type: static
      collection: { scope: item, target: dimensional_crystal, threshold: 1, percent: false }
    broadcast: true
    rewards:
      special: [title_dimension_steward, particle_seam]

  reach_hundred_million:
    display-name: 億の管財人
    trigger:
      type: static
      collection: { scope: item, target: world_tree_seed, threshold: 1, percent: false }
    broadcast: true
    rewards:
      special: [title_hundred_million]
      commands: []
```

**縦強化は入れない**（称号・パーティクルのみ）。`rewards.permanent-buffs` は空のままにする。

---

## 10. 投入手順チェックリスト（エディタのどのタブで何をするか）

| # | タブ | 作業 | 依存 |
|---|---|---|---|
| 0 | — | 稼働サーバの `catalog.yml` と §6-4 の dangling ID を突き合わせる | — |
| 1 | — | `backups/` にバックアップ（K-4） | — |
| 2 | アイテムカタログ → 素材 | §1 の素材5種を追加 | — |
| 3 | モブダンジョン → レベルテーブル | §5-4 の `add-drops` を既存帯へ追記 | 2 |
| 4 | モブダンジョン → モブオーバーライド | §5-3 の drops / stats（束縛者） | 2 |
| 5 | ArsPaper → 中間素材 | §2-2 の結晶5種を追加 | 2 |
| 6 | ArsPaper → ソースジャー | §3-1 の容量ティア4種 | 5 |
| 7 | ArsPaper → ソースリンク | §3-2 の増設2種＋§3-3 の燃料値 | 5 |
| 8 | スキルギミック → その他のギミック | §3-3/§5-1 の `removed-vanilla-items` / `removed-vanilla-recipes` | — |
| 9 | アイテムカタログ → 補助 | §5-1 の鍵2種、§4-1 の制覇証、§6-3 の宝札 | 2,5 |
| 10 | モブダンジョン → ダンジョンゲート | §5-1 の gate 1件 | 9 |
| 11 | アイテムカタログ → 武器/触媒 | §5-2 の4種 | 2,5 |
| 12 | アイテムステータス → 武器 | §5-2 の item-stats | 11 |
| 13 | アイテムカタログ → スレッド | §7 の2種（レシピなし） | — |
| 14 | スキルギミック → ガチャ | §6-3 のプール `treasure` | 13 |
| 15 | スキルギミック → 村人取引 | §6-2 の交換所 | 9 |
| 16 | その他 → 図鑑 / 特殊報酬 / アチーブメント | §9 | 全部 |
| 17 | 戦闘ツール → 火力シミュレータ | §5-2 の武器と §5-3 のボスで撃破時間を確認 | 12 |
| 18 | — | `/trinityforge reload` ＋ `/ars reload` → `/trinityforge give <id>` で実物確認 | 全部 |

**注意**: エディタで保存すると yml 本文のコメントは消える（K-3）。解説は `docs/config-reference/` 側へ書く。

---

## 11. この台帳が前提にしている改修（親文書 §7）

| 前提 | 無いとどうなるか | 代替運用 |
|---|---|---|
| `insertables`（ジャー投入量の config 化） | §2 の結晶が「ただの素材」になり、ソース生産が S1（ベリー100固定）で止まる | 結晶を儀式素材としてだけ使い、目標を100万ソースへ下げる |
| `key-catalog:`（鍵にカタログID） | 鍵が消費されず恒久入場権になる | ライセンス方式として運用し、周回制限は EliteMobs の `dungeonLockoutMinutes` で担保 |
| `chest-loot.yml`（チェストへの追加注入） | 構造物チェストから TF 品が直接出ない | §6 の引換証方式（追加実装ゼロ） |

いずれも**この台帳の投入を止める理由にはならない**（2〜4/8〜17 は今日そのまま入れられる）。

---

## 12. 物量見積り（固有アイテムを何種類つくるか）

### 12-1. 母数（実測 2026-07-30）

| 軸 | 実数 | 内訳 |
|---|---|---|
| EliteMobs 導入済みダンジョン | **29エントリ / モブ396体** | 大型(10体以上) **12** ／ 中型(4〜9体) **6** ／ エンチャント課題(1〜3体) **10** ／ HUB 1（報酬対象外） |
| ワールドの構造物チェスト | **バニラの chest loot table 約35〜40**（＋ DnT で数十追加予定） | 村7職・要塞3・海底遺跡2・難破船3・要塞/砦4・古代都市2・試練の間数種 ほか |
| 採取スキルの既存ドロップ表 | **8カテゴリ** | 採掘4（ガチャ券×3・古代の残骸）／掘削1（`ruins_thread`）／伐採3（りんご3種）／釣り3（券・バニラ宝・バニラゴミ）／農業0 |
| グリフ | **117個すべてに `unlock-cost.materials`** | 現在はバニラ素材（`NETHER_STAR`×15、`LAPIS_BLOCK`×7、`FEATHER`×7 …） |

### 12-2. 3案の見積り

| | A. 最小（今日から） | B. 標準（推奨） | C. フル |
|---|---|---|---|
| 帯共通素材（Lv40/65/85） | 3 | 3 | 3 |
| ダンジョン系統素材（18ダンジョンを6系統に束ねる） | — | 6 | — |
| ダンジョン固有素材 | — | 12（大型のみ） | 18（全ダンジョン） |
| 課題ダンジョン | — | 1（共通「課題の証」） | 10 |
| 束縛者専用素材 | 3 | 3 | 3 |
| 採取スキル固有素材 | 3（釣/掘/伐 各1） | 10（5スキル×2） | 15（5スキル×3） |
| 固有スレッド | 3 | 8 | 12 |
| 固有装備（武器/防具/触媒） | 4（エンドのみ） | 13（系統6＋エンド4＋採取3） | 25 |
| 構造物の引換証／限定品 | — | 6 | 12 |
| 既存 dangling の穴埋め（ガチャ券等） | 5 | 5 | 5 |
| **合計** | **約21種** | **約67種** | **約103種** |

**編集量の目安**（B案）: `catalog.yml` 約500行、`item-stats.yml` 約330行、ドロップ定義 約120行、
図鑑/アチーブ 約80行、`glyphs.yml` の解放素材差し替え 約250行 → **合計 1,200〜1,300行の yml**。
純粋な機械作業なので、方針が決まっていれば1〜2日で埋まる規模。

### 12-3. コストを一番下げる判断: **素材は CMD を振らない**

`custom-model-data` は任意項目。**振らなければリソースパック作業がゼロになり、見た目はベースの Material のまま**になる。
素材は「よく使うバニラ素材を、固有名と lore を付けた別アイテムとして配る」形が最も安い
（PDC でカタログIDが刻印されるので、レシピ・ドロップ・図鑑では別物として扱われる）。

- **CMD なしにする**: 素材・引換証・ガチャ券（＝ 67種のうち約40種）
- **CMD を振る**: 装備・スレッド・ソース結晶（見た目が体験に直結するもの。約25種）

→ モデル作業は **25個分**で足りる。B案でも現実的。

### 12-4. グリフ解放素材の差し替え（大きな sink）

117 グリフの `unlock-cost.materials` を**ダンジョン素材へ差し替える**と、
「グリフを開けるためにダンジョンを回る」動線が一気に生まれる（＝固有ドロップに使い道が確定する）。
グリフの tier に合わせて素材帯を割るのが素直:

| グリフ tier | 現状の例 | 差し替え案 |
|---|---|---|
| tier 1（Novice） | `LAPIS_BLOCK` / `FEATHER` 等 | 帯共通素材（Lv40帯）×2〜4 |
| tier 2（Apprentice） | `ENDER_EYE` / `EMERALD_BLOCK` 等 | 系統素材 ×1〜2 ＋ 帯共通 ×4 |
| tier 3（Archmage） | `NETHER_STAR` ×15 等 | 束縛者素材（`binding_link` 等）×1 ＋ 系統素材 ×2 |

**注意**: 解放素材の判定は Material 名なので、**帯素材の base Material を「他で価値のあるバニラ素材」にすると
そのバニラ素材でも解放できてしまう**（§1 の `NETHER_STAR` 回避と同じ話）。
素材の base は「解放素材に使われていない Material」から選ぶこと。

---

## 13. 作業分担（どこまで私が入れてよいか）

### 13-A. 私が入れて良い（機械作業・方針が決まれば判断不要）

| 作業 | 理由 |
|---|---|
| 素材・引換証・券のカタログエントリ作成（Material / 名前 / lore / bind / CMD無し） | 定型。既存エントリのコピーで成立し、balance に触らない |
| 図鑑カテゴリ登録・称号/パーティクル定義・アチーブのトリガ配線 | 縦強化を含まない（報酬は称号・コスメのみ） |
| ドロップエントリの**配線**（どのモブ/帯にどの素材を載せるか）※確率はテンプレ値 | 構造の作業。数値は 13-B で確定 |
| 既存 dangling ID の穴埋め（`tf_gacha_ticket_*` / `tf_scrap` / `tf_core_*` / `example_*`） | 現に壊れているものの修復。挙動を戻すだけ |
| `material-lists.yml` の互換リスト追加 | レシピの書きやすさだけの問題 |
| グリフ解放素材の差し替え**作業**（tier→素材帯の対応表が決まった後） | 117件の単純置換。表があれば判断不要 |
| ダンジョン→系統の割り当て表、素材の命名・lore の起案 | 提案として出し、採否は判断してもらう |
| 一覧表・差分レポート・投入手順の生成 | ドキュメント |

### 13-B. 私が決めてはいけない（要判断）

| 事項 | なぜ |
|---|---|
| **ドロップ率と必要個数の最終値** | 周回時間＝サーバの寿命を決める。§4-2 の逆算を見て決めてもらう項目 |
| **装備のステ最終値・use-lv 帯の配置** | 火力バランスの根幹。`火力シミュレータ` の結果を見て人が決める |
| **束縛者の HP / 攻撃の最終値** | 同上（最高難易度の手触り） |
| **`removed-vanilla-items` への追加** | **既存プレイヤーの所持品を掃除する破壊的操作**。対象 Material の他用途も潰れる |
| **グリフ解放を重くする/軽くするの方向性** | 進行の壁。魔法の入口が閉まる/開きすぎる |
| **CMD 帯の確定とモデル制作の割り当て** | リソースパックと1:1で紐づくため、後から動かすと既存品の見た目が壊れる |
| **稼働サーバへの配備（deployPaths ミラー）と reload のタイミング** | プレイ中セッションに影響する |
| **1億の目標プレイ時間 / `insertables`・`key-catalog` の改修可否** | 親文書 §9 の未決事項 |

### 13-C. 手続き上の制約（今の権限）

- 私が push できるのは **`Klee319/EliteMobs-trinityforge` の `claude/editor-content-proposal-hbh569` ブランチのみ**。
  TrinityForge 本体（`Klee319/trinityforge`）の yml を直接更新するには、そのリポジトリへの push 許可が必要
- 許可が無い間は、**この文書に「そのまま貼れる yml」を置く**形で渡す（現状の形）
- TrinityForge リポジトリ側は `ACTIVE_RECORD.md` K-4 のとおり巻き戻し手段が `backups/` のみなので、
  全面書き換え前のバックアップは必須
