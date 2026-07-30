# コンテンツ草案 — 「ソース経済 × 束縛者攻略」（2026-07-30）

- **ステータス**: 草案（未実装・数値は初期値提案）
- **前提**: `tools/config-editor`（現状の editor）で**編集できる範囲だけ**で組み立てる。Java 改修が必要な箇所は §7 に隔離して明示する
- **置き場所**: 内容は TrinityForge 側の設計なので、合意後 `trinityforge/docs/` へ移すのが本来。EliteMobs フォーク側の指定ブランチにしか push できないため暫定でここに置いている
- **参照した現物**: `TrinityForge/src/main/resources/**`、`tools/config-editor/lib/registry.js`（編集可能ファイルの正）、`tools/config-editor/lib/schema.js`（各 yml のバリデータ＝実際に書ける形）、ArsPaper の `source/**`（生成レートの実測）

---

## 0. 全体像（5本柱のつながり）

```
      ①ソース強化ループ（1億ソース）        ⑤制作コンテンツ
        生産→貯蔵→儀式で消費→生産強化   ←→   儀式/合成/解体で素材を出口へ
              ▲                                    ▲
              │ 高世代の燃料・炉心には              │ 素材
              │ ダンジョン素材が必須                │
      ┌───────┴──────────────┬──────────────────────┴─────────┐
   ②束縛者ダンジョン（最高難易度）   ④構造物ルートチェスト（DnT）   ③ロール
   エンド素材・鍵・所有者バインド装備   引換証→ガチャ/交換で厳選     属性テーマと
              ▲                                              ヘイトで役割を要求
              │ 入場ゲート（combatレベル＋鍵）
      高レベル帯ドロップ（レベルテーブル）
```

一言でいうと **「ソースの縦ループ（時間で伸びる）」と「束縛者攻略（腕で越える）」を、素材と鍵で相互依存させる**。
憲法（`SERVER_CONTENT_SPEC.md` §0）との整合: ソースループの報酬は**生産設備と称号/QoL のみ**で、戦闘ステータスの縦強化を足さない。

---

## 1. 現状のエディタでできること（この草案が使う土台）

| 柱 | 使う設定ファイル（エディタのタブ） | 実装状態 |
|---|---|---|
| ①ソース | `sourcelinks.yml`（ソースリンク）/ `sourcejars.yml`（ソースジャー）/ `items/catalog.yml`（儀式レシピ・`source:` コスト）/ `items/material-lists.yml` | 編集可。**ドレイン上限のみ Java 定数**（§7-A） |
| ②束縛者 | `combat/mob-overrides.yml`（世界×モブ単位の強さ/ドロップ/EXP）/ `dungeon/gates.yml`（入場ゲート）/ `dungeon/themes.yml` | 編集可。**gates は現在 `gates: {}` = 空**、束縛者は防御率と EXP のみ設定済みで level/HP/attack/drops は未設定 |
| ③ロール | `progression/role-buffs.yml` / `hate/rates.yml` | 編集可。ロールは戦闘職3＋補助職3のみ、ヘイトは**バランス中立の初期値**（decay 無効） |
| ④チェスト | （追加注入の設定は**存在しない**） | **エディタ範囲外**。`LootGenerateEvent` は「削除」専用（`VanillaItemRemovalListener`）。§7-B に3案 |
| ⑤高レベル限定ドロップ | `combat/mob-level-table.yml`（`min-level` 帯＋`mobs`/`mob-ids` 絞り込み＋`add-drops`）/ `combat/mob-overrides.yml` の `drops` | 編集可。**現状 tiers は `vanilla-exp` だけで `add-drops` は全帯とも空** |
| ⑤制作 | `items/catalog.yml`（workbench / ritual / combine / netherite の4方式）/ `progression/crafting-features.yml`（解体・過剰エンチャ・コーティング・`gated-catalog-recipes`）/ `economy/villager-trades.yml` / `gacha.yml` / 各 `*-gimmick.yml` の `drop-tables` | 編集可 |
| 共通 | `progression/achievements.yml`（statistic/advancement/図鑑トリガ → 称号・アイテム・コマンド報酬）/ `progression/collection.yml`（図鑑→段階報酬）/ `progression/special-rewards.yml` | 編集可 |

補足（実装済みで使える"仕掛け"）:

- **スキルツリーの `feature:` は閉じた26語彙**。ソース関連では `source-auto-consume`、素材解放では `drop:<表>`、取引解放では `trade:<職業>` が使える（`skilltree/_草案作成ブリーフ.md` §3-A）。新機構を書いても動かない
- **`removed-vanilla-items` は TF カタログ品を必ず除外する**（`VanillaItemRemover#isTfCatalogItem`）。つまり「この Material はバニラ入手を全部塞ぎ、TF レシピだけを入口にする」が設定だけで作れる ← §2/§3 の鍵と燃料でこれを使う
- **火力シミュレータ**タブがあるので、②の数値は実機投入前に机上検証できる

---

## 2. 柱① ソース強化ループ（目標：累計1億ソース）

### 2-1. まず数字の現実（重要）

ArsPaper のソースリンクは **100 tick（5秒）ごとに、1台あたり最大 50 ソースしかジャーへ排出しない**
（`SourcelinkTickTask.TICK_INTERVAL = 100` / `Sourcelink.MAX_DRAIN_PER_TICK = 50`）。
つまり **1台 = 10 ソース/秒 = 36,000 ソース/時**。

```
必要実効稼働時間[h] = 100,000,000 ÷ (設備数 N × ドレイン上限 D × 720)
```

| N（設備数） | D=50（現状） | D=100 | D=200 | D=400 |
|---|---|---|---|---|
| 4台 | 6,944 h | 3,472 h | 1,736 h | 868 h |
| 8台 | 3,472 h | 1,736 h | 868 h | **434 h** |
| 16台 | 1,736 h | 868 h | 434 h | 217 h |

**結論: 現状のレートでは 1億は事実上到達不能**（8台フル稼働で 3,472 時間）。
1億を掲げるなら次のどちらかが必要:

- **(A) ドレイン上限を設備ティア別に config 化する**（ArsPaper 側の小改修・§7-A）。推奨。
  例: 世代ごとに `50 → 100 → 200 → 400`（/5秒）→ 最終世代8台で約434時間。
  **40〜50時間規模にしたいなら D の最終値は 4,000 前後**（8台で 43 時間）。目標時間は要判断（§9）
- **(B) 目標の単位を「累計投資ソース」に読み替える**（貯蔵ではなく儀式で払った総額）。
  ソースジェム／結晶を経由した圧縮分も換算に含める前提にすれば、ドレイン上限を触らずに桁を作れるが、
  **"1億"の実感（メーターが増える気持ちよさ）は薄くなる**

以下は (A) 前提で書く。(B) を採るなら §2-3 のコスト表をそのまま「累計投資額」の定義として使えばよい。

### 2-2. ループの構造（クッキークリッカーの"3ノブ"に対応させる）

| インクリメンタルの要素 | この設計での実体 | 触るファイル |
|---|---|---|
| **クリック効率**（1回あたりの獲得） | 燃料1個あたりのソース価値（Material 単位） | `sourcelinks.yml` の `volcanic/mycelial/alchemical.materials` |
| **生産速度**（自動化） | ソースリンクのドレイン上限＋設備台数 | （§7-A）＋ 儀式で作る設備アイテム |
| **貯蔵上限**（詰まり解消） | ソースジャーの `capacity`（既定10,000、`-1`で無限） | `sourcejars.yml` の `jars.<id>.capacity` |
| **プレステージ/実績** | 図鑑登録＋アチーブメント（称号・パーティクル・QoL） | `collection.yml` / `achievements.yml` / `special-rewards.yml` |

**世代（Gen）を8段にして、コストも生産も ×4 で揃える**。こうすると「1世代を上げるのに必要な時間が一定」になり、
体感が"詰まらない"。8世代の儀式コスト累計がちょうど 1億。

### 2-3. 世代表（初期値提案）

| 世代 | 解放されるもの（儀式で作る） | 追加要求素材 | 儀式コスト（ソース） | 累計 |
|---|---|---|---|---|
| G1 | 基礎ソースリンク＋標準ジャー(10,000) | — | 4,600 | 4,600 |
| G2 | 燃料価値 45帯の解放（石炭ブロック運用）＋ジャー 40,000 | 鉄/銅 | 18,400 | 23,000 |
| G3 | 「ソース練炭」(価値500) ＋ ジャー 160,000 | ①フィールド高レベル素材 | 73,600 | 96,600 |
| G4 | ドレイン上限 T2（100/5秒） | 反復ダンジョン素材 | 294,000 | 390,600 |
| G5 | 「魔導練炭」(価値2,000) | 反復ダンジョンボス素材 | 1,180,000 | 1,570,600 |
| G6 | ドレイン上限 T3（200/5秒）＋ジャー 640,000 | 高レベル帯(Lv65+)限定素材 | 4,710,000 | 6,280,600 |
| G7 | 「次元炉心」(価値8,000) | **束縛者ダンジョン素材** | 18,800,000 | 25,080,600 |
| G8 | ドレイン上限 T4＋「世界樹の残滓」＋無限ジャー(`capacity: -1`) | **束縛者 最終段階素材** | 75,400,000 | **100,480,600** |

- **G7/G8 に束縛者素材を要求する**のがこの草案の背骨。ソースループを回すだけでは終われず、最高難易度に手を出す動機になる
- 逆に**束縛者の入場鍵は G6 相当の設備で作る**（§3-3）。相互依存でループが閉じる
- 報酬は生産設備・容量・称号・パーティクルのみ。**戦闘ステは一切上げない**（憲法「量で担保」）

### 2-4. 燃料ラダーの作り方（Material 単位である制約への対処）

ソースリンクの燃料値は **Material キー**で引く。かつ **Ars のカスタムアイテム（PDC付き）は燃料に使えない**が、
**TF カタログ品は Ars の判定に引っかからない**ので燃料として通る（`Sourcelink#isCustomItem` は `arspaper` 名前空間のキーだけを見る）。

したがって高価値燃料は「**専用の Material を1つ占有し、そのバニラ入手経路を塞ぎ、TF レシピだけを入口にする**」で作る:

```yaml
# 例: G5「魔導練炭」= Material: MAGMA_BLOCK を占有
# 1) sourcelinks.yml
volcanic:
  materials:
    MAGMA_BLOCK: 2000
# 2) crafting-features.yml — バニラ入手を塞ぐ（TFカタログ品は自動的に除外される）
removed-vanilla-items:
  - MAGMA_BLOCK
removed-vanilla-recipes:
  - minecraft:magma_block
# 3) catalog.yml — 唯一の入口を儀式レシピにする
items:
  arcane_briquette:
    material: MAGMA_BLOCK
    custom-model-data: 2105xx
    display-name: "<gold>魔導練炭</gold>"
    recipe:
      method: ritual
      source: 0            # 燃料の製造にソースを要求すると自己参照になるので0
      core-item: custom:source_briquette
      pedestal-items: [custom:source_briquette, custom:source_briquette, custom:<ダンジョン素材>]
```

占有候補（他用途が少なく塞ぎやすい順）: `MAGMA_BLOCK` / `DRIED_KELP_BLOCK`（既に価値10）/ `BLAZE_ROD`（既に価値10）/ `SHROOMLIGHT`。
**要確認**: `removed-vanilla-items` に入れた Material をクラフト材料として使うレシピが他に無いか（掃除対象は"アイテム"なので、素材として消える）。

### 2-5. マイルストーン（"数字が増える気持ちよさ"の担保）

現状 TF に**残高カウンタは無い**（`ACTIVE_RECORD.md` J-2「通貨機構が TF に存在しない」）。設定だけで作れる代替:

- 各世代の設備アイテムを `collection.yml` の `categories.items.source_economy` に登録 → 図鑑が進捗メーターになる
- `achievements.yml` で `trigger.type: static`（図鑑 scope=item / target=世代設備ID）→ 称号＋`broadcast: true` で全体告知
  - G3「ソースの徒」/ G5「炉の主」/ G7「次元の管財人」/ G8「億の管財人」
- 報酬は `special-rewards.yml` の titles / particles（＋恒久 QoL は図鑑 `reward-tiers` 側）

将来やるなら: 累計投資ソースの実カウンタ＋`/tf source top` ランキング（J-2 の通貨機構と同時に）。

---

## 3. 柱② 束縛者ダンジョンを最高難易度にする

### 3-1. 現状

`combat/mob-overrides.yml` の `em_id_binder_of_worlds`（世界を繋ぐ者の聖所、18体）は
**防御率と `vanilla-exp` だけ**が入っている。コンセプトは既に「**魔法防御が厚い＝物理ビルドが活きる**」。

```
第2段階ボス: physical {0.068 / 0.204}  magical {0.374 / 0.306}
```

`level` / `max-health` / `attack` / `drops` は**未設定**（＝`mob-profiles.yml` の合成値そのまま。
標準ランプは `mob-import.yml` の `max-health 150×1.072^Lv` / `attack-power 7×1.03^Lv`）。
`dungeon/gates.yml` は `gates: {}` で**入場制限も無い**。

### 3-2. 調整方針（絶対値ではなく標準比で提案）

絶対値は装備帯と噛み合わせが要るので、**同レベル標準プロファイル比の倍率**で置き、火力シミュレータで詰める。

| 対象 | 項目 | 提案 | 意図 |
|---|---|---|---|
| 最終段階ボス | `max-health` | 標準 ×2.5 | 「長い」ではなく「事故が起きる時間が長い」 |
| 最終段階ボス | `attack.attack-power` | 標準 ×1.25 | 即死させない |
| 最終段階ボス | `attack.fixed-damage` | 小さく設定（全防御貫通） | **タンク/回復を意味あるものにする**（防御ステで消せない削り） |
| 最終段階ボス | `attack.crit-chance` / `crit-damage` | 0.15 / 0.5 | 防具強度（会心軽減）に意味を持たせる |
| 全段階ボス | `magical.defense-rate` / `resistance` | 0.45 / 0.35（現 0.374/0.306 から引き上げ） | 属性テーマを明確化 |
| 全段階ボス | `physical` | 現状維持（0.068 / 0.204） | 物理ビルドの正解を残す |
| 増援のうち1系統 | `physical.defense-rate` 0.45 に**逆張り** | `..._zapper_reinforcement_mythic` など1系統だけ | **持ち替え強制**（`DUNGEON_SPEC` §4「逆張り例外モブ」） |
| ミニボス | `max-health` ×1.5 | | 雑魚処理の緩急 |

- **`stats` は項目単位マージ**なので、書いた項目だけが効き、他は合成値のまま（安全）
- **`drops` は置換**。ここに束縛者限定素材を置く（§5）
- テーマ側を触るなら `dungeon/themes.yml` に `dimensional_ward`（魔法偏重の強化版）を新設し、
  `/trinityforge importmobs theme dimensional_ward <束縛者のモブフォルダ>` で一括再生成 → その上に overrides を重ねる

### 3-3. 入場ゲート（現在ゼロ → ここを新設）

```yaml
# dungeon/gates.yml
gates:
  binder_of_worlds_sanctum:
    required-combat-level: 90
    key-material: TRIAL_KEY      # ← Bukkit Material でしか指定できない（後述）
    key-amount: 1
    content-package: the_binder_of_worlds
```

**制約（実装確認済み）**: `key-material` は **Bukkit Material** で、所持判定は `inventory.contains(Material, amount)`、
消費は `removeItem(new ItemStack(Material, amount))`。**カタログID は指定できない**。
→ 対処は §2-4 と同じ手筋:

1. `catalog.yml` に `key_of_the_binder`（`material: TRIAL_KEY` ＋ CMD ＋ `bind-type: SOULBOUND`）を作る
2. `removed-vanilla-items: [TRIAL_KEY, OMINOUS_TRIAL_KEY]` でバニラ入手（トライアルチェンバー）を塞ぐ
3. 鍵のレシピは **儀式（G6 相当の設備＋ダンジョン素材）**。→ ソースループが入場権に直結する

難易度3段にするなら `TRIAL_KEY`（通常）/ `OMINOUS_TRIAL_KEY`（極）で別ゲートを切り、
overrides のワールドキーは**設計図ワールド名**（`em_id_binder_of_worlds`）なので**段位ごとの数値差はキー数を増やせない**点に注意。
段位差を付けるなら EliteMobs 側で別コンテンツパッケージに分ける必要がある（要判断・§9）。

### 3-4. 検証

1. エディタの**火力シミュレータ**で「想定装備帯 × 提案倍率」の被弾回数/撃破時間を出す
2. 実機は `ACTIVE_RECORD.md` W-1（`dropsVanillaLoot` のスモークテスト）と同時にやるのが安い
3. `mob-level-table.yml` の帯 EXP と二重にならないか確認（**モブ別の式が帯より後に走って勝つ**仕様）

---

## 4. 柱③ ロールを意味あるものにする

### 4-1. 現状

- `role-buffs.yml`: 戦闘職 剣闘士 / 魔術師 / タンク、補助職 斥候 / 農夫 / 採掘夫。獣使いはスコープ外
- `hate/rates.yml`: **意図的にバランス中立**（`decay.enabled: false`、`threat.per-damage: 1.0`）。タンクだけ `hate-threat-multiplier: 1.5`
- 設計上は LD-3（選択制クラス廃止＝条件付きバフ化）で再設計対象のまま

### 4-2. 「敵の側で役割を要求する」のが一番安く効く

ロールの数値を盛るのではなく、**②の敵の作りでロールが必要になる状況を作る**。エディタだけで完結する:

| 作りたい要求 | 実現方法 |
|---|---|
| タンクが要る | 束縛者に `attack.fixed-damage`（防御ステで消せない）＋増援を多数 → ヘイト管理が無いと後衛が溶ける |
| 回復/耐久が要る | `attack.crit-chance/crit-damage` を持たせ、被弾の分散を大きくする |
| 属性の持ち替えが要る | ダンジョンテーマ（魔法防御厚）＋逆張り増援1系統（§3-2） |
| 補助職の選択に意味 | ソース生産＝採取が土台なので、補助職の `exp-multiplier`/`potion-buff` が生産効率に直結する（採掘夫=HASTE、農夫=JUMP_BOOST…） |

### 4-3. `hate/rates.yml` の推奨変更

| キー | 現状 | 提案 | 理由 |
|---|---|---|---|
| `decay.enabled` | false | **true** | 減衰が無いと「最初に殴った人」が固定され、タンクの引き直しが成立しない |
| `decay.per-second` | 0.0 | 2〜5%/秒相当 | 戦闘が長い束縛者向け。要実測 |
| `threat.per-damage` | 1.0 | 1.0（据え置き） | 基準はいじらない |
| `combat-roles.tank.hate-threat-multiplier` | 1.5 | **2.0** | 火力役との綱引きに勝てる程度 |

### 4-4. 補助職の拡充（ソースループとの接続）

`support-roles` に **漁師（FISHING）/ 木匠（WOODCUTTING）/ 錬金師（ALCHEMY）** を追加。
`exp-skill` ＋ `exp-multiplier: 1.2` ＋ 排他 `potion-buff`（スキルツリー・魔法で取れない効果に限る＝仕様の条件）。
燃料（木材・海藻）と醸造素材がソース生産の主動線なので、**補助職の選択が「どの燃料経済を回すか」の選択になる**。

---

## 5. 柱⑤ EM／高レベル限定の素材・武器（＝②と①をつなぐ供給）

### 5-1. 高レベル帯限定素材（レベルテーブル）

`mob-level-table.yml` の tiers は現在 `vanilla-exp` のみで **`add-drops` が全帯空**。ここに素材を入れる。
帯そのものにも各エントリにも `mobs`（EntityType）/ `mob-ids`（EliteMobs モブID）で絞り込みが効く。

```yaml
tiers:
  - min-level: 65
    add-drops:
      - { material: "custom:aether_dust",  chance: 0.08, min: 1, max: 2 }     # 全高レベル敵
  - min-level: 85
    add-drops:
      - { material: "custom:void_filament", chance: 0.05, min: 1, max: 1 }
      # 束縛者のボスだけ確定で落とす素材（mob-ids は EliteMobs のモブIDでのみ指定可能）
      - { material: "custom:binding_link",  chance: 1.0,  min: 1, max: 2,
          mob-ids: [em_id_binder_of_worlds_phase_4] }
```

- `dungeon-only: true` にすると全ルールがダンジョン内限定になる（フィールドで湧く高レベル敵から漏らしたくない場合）
- `mob-overrides` の `drops` とは**独立に加算**される（片方が片方を消さない）。ダンジョン固有＝overrides、レベル帯共通＝level-table で使い分ける

### 5-2. 素材の階層（命名は仮）

| ID | 名前 | 出所 | 使い道 |
|---|---|---|---|
| `aether_dust` | 精気の塵 | Lv65+ 全般 | G3〜G4 燃料/設備 |
| `void_filament` | 虚無の糸 | Lv85+ 全般 | G5〜G6 設備、スレッド素材 |
| `binding_link` | 束縛の鎖環 | 束縛者ボス（確定） | G7「次元炉心」＋入場鍵 |
| `world_seam` | 世界の継ぎ目 | 束縛者 最終段階（低確率） | G8「世界樹の残滓」＋エンド武器 |

### 5-3. エンド武器（所有者バインド）

`catalog.yml` ＋ `item-stats.yml` で作る。憲法「gear非依存」に反しないよう**倍率レイヤーは足さない**:

- `bind-type: OWNER_BOUND`（`/tf bind setowner` 運用。エンドコンテンツアイテムの仕様どおり）
- `use-level-requirement` ＋ `use-skill`（`use-requirements.yml` の `enforce: true` は既に有効）
- 味付けステは品質（0〜5）の振れで**横の多様性**として出す。`drops` 経由の入手は品質ロール付き
- 修繕は廃止方針なので**耐久は有限**（`repair-disabled` は EM フォーク側で既定 true）

---

## 6. 柱⑤ 制作コンテンツ（素材の出口）

| 方式 | エディタでの指定 | 何に使うか |
|---|---|---|
| **workbench** | `recipe.method: workbench`（shaped/shapeless） | 中間素材・圧縮燃料の量産 |
| **ritual** | `method: ritual` ＋ `core-item` / `pedestal-items` / **`source:`** | **ソースを払う唯一の口＝ループの sink**。世代設備・鍵はすべてこれ |
| **combine**（金床） | `source-item` / `addition-item` / `combine-exp` / `inherit-source-quality` | 品質の引き継ぎ・厳選のやり直し |
| **netherite**（鍛冶台） | `source-item` | 最終強化（品質は常に継承） |

組み合わせで作る"回る"仕組み:

- `gated-catalog-recipes`（`crafting-features.yml`）＋ スキルツリーの解放でレシピを段階公開
- **解体**（`disassembly`、`percent-per-level`）で失敗作を素材に戻す＝厳選の再挑戦コストを下げる
- `villager-trades.yml`（`trade:<職業>` 解放）で「素材→券/中間素材」の両替所を作る
- 各 `*-gimmick.yml` の `drop-tables`（`drop:<表>` で解放）＝**採取から素材を出す口**。既にガチャ券の表が入っており、同じ形でソース素材の表を足せる
- **スレッド厳選**: `thread-slots.max-by-category`（現状 armor 5 / 他 0）＋ `threads.yml` / `thread-sets.yml`。
  特殊効果系はクラフト、残りは厳選（憲法どおり）

---

## 7. エディタだけでは届かない箇所（Java／datapack が要る）

### 7-A. ソースリンクのドレイン上限（①の前提）

- 現状: `Sourcelink.MAX_DRAIN_PER_TICK = 50` / `SourcelinkTickTask.TICK_INTERVAL = 100`（どちらも定数）
- 必要: **ティア別に config 化**（`sourcejars.yml` の `capacity` と同じ発想で、`sourcelinks.yml` に `drain-per-tick` を持たせる）
- 規模: 定数1つを config 読み出しに変える程度＋エディタ側のフォームに数値欄1つ
- **要確認**: 稼働中の ArsPaper フォーク（`fork-handoff/arspaper/fork/`、公開リポジトリには含まれない）は、
  ここで参照した公開リポジトリ（2026-04）より新しい。`sourcejars.yml` の `capacity` はエディタ側に既にあるので、
  ドレインも既に config 化済みの可能性がある。**着手前に稼働フォークの実コードで確認**

### 7-B. 構造物ルートチェスト（Dungeons and Taverns）

`LootGenerateEvent` は **削除専用**で、追加注入の設定は無い。3案:

| 案 | 内容 | コスト | 評価 |
|---|---|---|---|
| **A. 引換証方式（Javaゼロ）** | DnT のチェストに元から入るバニラ品を"引換証"に指定し、`villager-trades` / `catalog` レシピ / **ガチャ券**へ両替。厳選は `gacha.yml` の `quality-random` ＋ `pity` で担保 | 0 | **すぐ出せる**。ただし「チェストを開けた瞬間の嬉しさ」は弱い |
| **B. datapack 側で完結** | DnT が datapack なら、その loot table JSON に「名前付きバニラ引換証」を追記。TF 側は A と同じ両替口 | 低（JSON編集） | チェストから専用アイテムが出る手触りになる。TF ステ付き品は出せない（PDC が無いため）ので**必ず両替を挟む** |
| **C. 追加注入を実装** | `chest-loot.yml` を新設し、`loot-table id`（または構造物名）→ 重み付きエントリ＋`drop:` ゲートで注入。`MobOverrideDropListener` と同じ形で書ける | 中（リスナー1本＋config1本＋エディタ1タブ） | **本命**。①③④が全部これに乗る |

推奨: **まず A/B で先行し、C を後追い**（C が来たら A/B の両替口はそのまま残す）。

### 7-C. 累計ソースの残高カウンタ（①の"数字"）

`ACTIVE_RECORD.md` J-2 の通貨機構と同じ話。図鑑＋アチーブで代替できるので**必須ではない**。

---

## 8. 実装順（各フェーズは単体で"遊べる状態"になる）

| フェーズ | 内容 | 触るもの | 完了条件 |
|---|---|---|---|
| **P0** | 稼働 ArsPaper フォークのドレイン/容量の実値確認、①の目標時間を決める | 実コード確認のみ | §9 の判断が埋まる |
| **P1** | 高レベル帯素材の供給を開く | `mob-level-table.yml` `add-drops`、`catalog.yml` 素材4種 | Lv65+ を狩ると素材が出る |
| **P2** | 束縛者の難易度と入場ゲート | `mob-overrides.yml`、`gates.yml`、`crafting-features.yml`（鍵Material封鎖）、`catalog.yml`（鍵） | 鍵なしで入れない／シミュレータで想定撃破時間に収まる |
| **P3** | ソース世代 G1〜G4（ドレイン config 化が前提の G4 は後ろ倒し可） | `sourcelinks.yml`、`sourcejars.yml`、`catalog.yml`（儀式）、`material-lists.yml` | 儀式でソースを払って設備が上がる |
| **P4** | ロール要求とヘイト調整 | `role-buffs.yml`、`hate/rates.yml` | タンクが引ける／持ち替えが要る |
| **P5** | 図鑑・アチーブ・称号でマイルストーン化 | `collection.yml`、`achievements.yml`、`special-rewards.yml` | 世代到達が全体告知される |
| **P6** | G5〜G8（束縛者素材要求）＋エンド武器 | 同上＋`item-stats.yml` | 累計1億の到達可能性が机上で示せる |
| **P7** | チェスト（§7-B の A/B → C） | datapack ＋（後で）新 config | 構造物漁りに旨みが出る |

**作業上の注意（既知の落とし穴）**

- エディタで保存すると **yml 本文コメントは消える**（`ACTIVE_RECORD.md` K-3）。説明は `docs/config-reference/` 側へ書く
- `mob-profiles.yml` は自動生成なので**直接編集しない**（overrides を使う）
- `mob-overrides.yml` のワールドキーは**設計図ワールド名**（実インスタンス名 `..._1` ではない）
- 全面書き換え前に `backups/` へバックアップ（K-4）

---

## 9. 要判断（こちらでは決められないもの）

1. **①の目標プレイ時間**。1億に何時間かけさせるか（8台稼働で 40h / 100h / 400h のどれか）。これが §7-A の `drain-per-tick` 最終値を決める
2. **1億の定義**。(A) 実際にジャーへ貯めた総量 か (B) 儀式で払った累計投資額 か
3. **束縛者の段位分け**。1ダンジョンで完結（overrides は設計図ワールド単位なので数値1本）か、EliteMobs 側でパッケージを分けて 通常/極 の2本立てにするか
4. **鍵に占有させる Material**。`TRIAL_KEY` 系を潰してよいか（トライアルチェンバーの旨みを消す判断）
5. **§7-B の採用案**。A/B 先行で良いか、C（`chest-loot.yml` 新設）を先に実装するか
6. **燃料に占有させる Material**（`MAGMA_BLOCK` 等）をバニラ入手不可にしてよいか
7. ロール再設計（LD-3）の扱い。本草案は現行 `role-buffs.yml` の形（明示ロール）に乗せている
