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

### 2-1. まず数字の現実（重要 / 2026-07-30 実コード確認）

ArsPaper のソース生成はこうなっている（`SourcelinkTickTask` / `Sourcelink` / `SourceNetwork`、公開リポジトリ2026-04版）:

| 事実 | 値 | 出典 |
|---|---|---|
| ソースリンクの処理間隔 | **100 tick（5秒）** | `SourcelinkTickTask.TICK_INTERVAL = 100` |
| 1台1回あたりのジャーへの排出上限 | **50** | `Sourcelink.MAX_DRAIN_PER_TICK = 50` |
| → **1台あたりの生産速度** | **10 ソース/秒 = 36,000/時（ハード上限）** | 上2つ |
| 燃料バッファ | **上限なし・繰り越される**（値の大きい燃料も無駄にならず、時間をかけて排出される） | `addToBuffer` / `drainBuffer` |
| 隣接ジャーが満杯のとき | **排出分は消滅**（`drainBuffer` で減算済みの `remaining` を捨てる） | `Sourcelink#supplyAdjacent` |
| ジャー→ジャーの転送 | **100 / 40tick = 2.5/秒**（リンク直結より遅い） | `SourceNetwork.TRANSFER_AMOUNT/INTERVAL` |
| アンロードチャンク | **処理されない** | `tick()` の `isChunkLoaded` チェック |
| **ソースベリー → ジャーへ直挿し** | **+100 / 個・上限ゲート無し**（右クリックとホッパーの両方） | `SourceJar.SOURCE_PER_BERRY = 100` / `CustomBlockListener#onInventoryMoveItem` |

**ソースをジャーへ入れる経路は2本あり、律速がまったく違う**。ここが設計の分岐点:

| 経路 | 素材の値をどこで決めるか | 速度の律速 | editor から触れるか |
|---|---|---|---|
| **A. ソースリンク経由**（燃料/食料/醸造素材を装置へ投入） | `sourcelinks.yml` の `<type>.materials`（Material→値） | **排出 50/5秒＝1台10/秒のゲートが後段にある** | **値は編集可**（ただし速度には効かない） |
| **B. ソースベリー直挿し**（ジャーへ右クリック／ホッパー） | `SourceJar.SOURCE_PER_BERRY`（**ハードコード 100**） | **ゲート無し**。ホッパー速度（1個/8tick＝2.5個/秒）＝**250 ソース/秒** | **量は編集不可**（機能アイテムタブでは表示名/material/レシピのみ） |

経路 A について「素材の値を上げれば速くなる」が成り立たない理由（決定的な例）:
`LAVA_BUCKET` の値を 100 → **1,000,000** にしても、バッファに 1,000,000 が積まれて **50/5秒で少しずつ**流れるだけ。
ジャーに全部届くのに 27.8 時間かかる。**平均は 10/秒のまま**。
→ 経路 A の値は「**上限を飽和させるまで**は効く（＝投入頻度と農場規模を減らす）」が、**上限を超える速度は作れない**。
（石炭ブロック1個/5秒＝価値45 でほぼ飽和。それ以上の値は自動化の手間を減らすだけ）

一方 **経路 B は 50/5秒のゲートを通らない**ので、`addSource` が直接ジャーへ書き込む。
ホッパー1本で 250/秒、4本で 1,000/秒。**1億は4本で約28時間**。

```
経路A: 必要時間[h] = 1億 ÷ (台数 × 36,000)     経路B: 必要時間[h] = 1億 ÷ (ホッパー数 × 900,000)
```

| | 経路A（ソースリンク） | 経路B（ベリー直挿し） |
|---|---|---|
| 1系統あたり | 36,000 /時 | **900,000 /時** |
| 8系統 | 347 h | **13.9 h** |
| 1億に必要な系統数（40時間想定） | 70台 | **3本** |

**結論**: 現状のまま1億を狙うなら**経路 B（ベリー農場＋ホッパー）が事実上の本線**になる。
ただし量が定数 100 で固定なので、**「素材ごとに量を設定して伸ばす」という強化ラダーは今は作れない**。
1億を掲げるなら次のどれか:

- **(A) ジャーへの直接投入を config 化する**（`sourcejars.yml` に `insertables: {<Material|catalogId>: <量>}`）。**推奨**。
  ゲートが無い経路なので、**「素材ごとのソース量」＝そのまま生産速度のノブ**になり、
  ご指摘の発想（投入素材ごとに量を決める）がそのまま強化ラダーになる。改修規模は定数1つ＋config読み出し
- **(B) ソースリンクのドレイン上限をティア別に config 化する**（§7-A）。
  「上位の炉は速い」の実感は作れるが、ゲートを緩める形なので (A) より一手多い
- **(C) 目標の単位を「累計投資ソース」に読み替える**（貯蔵量ではなく儀式で払った総額）
- **(D) 1億という数字を下げる**（例: 100万）。改修ゼロで成立（経路Bのホッパー1本で約1.1時間）

以下は **(A) を第一候補、(B) を併用**として書く。(C) なら §2-3 のコスト表をそのまま「累計投資額」の定義に、
(D) なら全コストを 1/100 にすれば流用できる。

### 2-1b. 経路Bを主軸にした場合の注意点

- **ジャー容量が最大の制約になる**。250/秒で流し込むと既定容量 10,000 は **40 秒で満杯**。
  溢れた分は消滅（ベリーは消費される）ので、`sourcejars.yml` の `capacity` を上げる／ジャーを増やすのが必須
- **ジャー→ジャーの転送は 2.5/秒**しかないので、1つの大ジャーへ集約する構成は成立しない。
  **「投入口ごとにジャーを立てる」**構成になる（＝容量アップの動機が強く出るので、強化ラダーとしては好都合）
- ベリーは `functional-items.yml` で material / レシピ / 表示名を編集できるので、
  **上位結晶（別 Material）を `insertables` に足す**形で世代ラダーが作れる（(A) 前提）

### 2-2. ループの構造（クッキークリッカーの"3ノブ"に対応させる）

| インクリメンタルの要素 | この設計での実体 | 触るファイル | 現状 |
|---|---|---|---|
| **生産速度**（本体） | **ジャーへ直挿しできる素材と、その1個あたりの量** | `sourcejars.yml`（§7-A の `insertables` 改修が必要） | **ベリー100固定** |
| **生産速度**（副） | ソースリンクのドレイン上限（ティア別） | （§7-A-2 の改修が必要） | **1台10/秒で固定** |
| **生産系統数** | 儀式で作る追加ソースリンク／投入口 | `sourcelinks.yml` の `items.<id>` ＋ `type:` | 編集可 |
| **投入の手間**（自動化QoL） | ソースリンク燃料の1個あたり価値（Material 単位） | `sourcelinks.yml` の `volcanic/mycelial/alchemical.materials` | 編集可（**経路Aでは速度でなく頻度に効く**） |
| **貯蔵上限**（溢れ＝損失の防止） | ソースジャーの `capacity`（既定10,000、`-1`で無限） | `sourcejars.yml` の `jars.<id>.capacity` | 編集可 |
| **消費（sink）** | 儀式レシピの `source:` コスト | `items/catalog.yml` | 編集可 |
| **プレステージ/実績** | 図鑑登録＋アチーブメント（称号・パーティクル・QoL） | `collection.yml` / `achievements.yml` / `special-rewards.yml` | 編集可 |

**世代（Gen）を8段にして、コストも投入量も ×4 で揃える**。こうすると「1世代を上げるのに必要な時間が一定」になり、
体感が"詰まらない"。8世代の儀式コスト累計がちょうど 1億。
（**満杯ジャーは投入分が消滅する**ので、容量アップは"損失を止める強化"として体感が出る。詰まり解消のノブとして優秀）

### 2-3. 世代表（初期値提案）

**生産の主軸＝直挿し素材1個あたりの量**（§7-A の `insertables` 前提）。ジャー容量と燃料価値は「損失と手間」を減らす副軸。

| 世代 | 直挿し素材（1個あたりの量） | 副軸で解放されるもの | 追加要求素材 | 儀式コスト | 累計 |
|---|---|---|---|---|---|
| G1 | ソースベリー 100（現状） | 標準ジャー 10,000 | — | 4,600 | 4,600 |
| G2 | 「ソースの粒」 400 | ジャー 40,000 | 鉄・銅 | 18,400 | 23,000 |
| G3 | 「ソース結晶」 1,600 | 燃料 45帯運用（石炭ブロック） | ①フィールド高レベル素材 | 73,600 | 96,600 |
| G4 | 「濃縮結晶」 6,400 | ジャー 160,000 | 反復ダンジョン素材 | 294,000 | 390,600 |
| G5 | 「魔導結晶」 25,600 | 「魔導練炭」(価値 2,000) | 反復ダンジョンボス素材 | 1,180,000 | 1,570,600 |
| G6 | 「次元結晶」 102,400 | ジャー 640,000 | 高レベル帯(Lv65+)限定素材 | 4,710,000 | 6,280,600 |
| G7 | 「束縛の核」 409,600 | 「次元炉心」(価値 8,000) | **束縛者ダンジョン素材** | 18,800,000 | 25,080,600 |
| G8 | 「世界樹の種」 1,638,400 | 無限ジャー(`capacity: -1`) | **束縛者 最終段階素材** | 75,400,000 | **100,480,600** |

- 各世代の結晶は**下位結晶×3＋その世代の要求素材**で儀式クラフト（＝価値は×4なので圧縮するほど得）
- ホッパー1本（2.5個/秒）で流した場合の生産速度: G1=250/秒 → G8=**409万/秒**。
  実際にはジャー容量と結晶の生産量（＝素材の集まる速さ）が律速になるので、**素材側の供給が実質の難易度**
- 所要時間はこの表のまま組むと **40〜60 時間規模**。目標時間の決定（§9-1）に合わせて ×4 を ×3／×5 に振って調整する

- **G7/G8 に束縛者素材を要求する**のがこの草案の背骨。ソースループを回すだけでは終われず、最高難易度に手を出す動機になる
- 逆に**束縛者の入場鍵は G6 相当の設備で作る**（§3-3）。相互依存でループが閉じる
- 報酬は生産設備・容量・称号・パーティクルのみ。**戦闘ステは一切上げない**（憲法「量で担保」）

### 2-4. 燃料ラダーの作り方（Material 単位である制約への対処）

> **役割の再確認**: 燃料価値は**生産速度ではなく投入頻度**に効く（§2-1）。
> ドレイン上限 D を飽和させるのに必要な投入は `D / 5秒`。
> 例: D=1,600 なら 1,600 相当/5秒 → 石炭ブロック(45)では36個/5秒で非現実的、価値2,000の燃料なら1個/5秒。
> **つまり高世代では「高価値燃料が無いと上げたドレイン上限を使い切れない」**という形で必然性が生まれる。ここが噛み合わせの要。

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

**制約（2026-07-30 実コード確認）**: `key-material` は `Material.matchMaterial` でしか解釈されず（`DungeonGateConfig#parseKeyMaterial`）、
**カタログID / `custom:` は指定できない**。エディタ側のフォームも Bukkit Material のサジェストのみ（`tf-dungeon-forms.js`）。

さらに **所持判定と消費で判定基準が食い違っている**（`DungeonGateService#evaluateAndConsume`）:

| 処理 | 呼び出し | 判定基準 |
|---|---|---|
| 所持チェック | `inventory.contains(Material, amount)` | **Material のみ**（meta 無視） |
| 消費 | `inventory.removeItem(new ItemStack(Material, amount))` | Bukkit の `removeItem` は **`isSimilar` = Material ＋ meta 一致** |

→ **TF カタログ品（表示名/CMD/PDC を持つ＝meta あり）を鍵にすると、入場チェックは通るが消費されない**（無限に使える鍵になる）。
素の（meta なしの）バニラアイテムを鍵にしたときだけ、意図どおり1個消費される。既存テストは config パースのみで、この経路は覆っていない。

したがって現実的な選択は3つ:

| 案 | 内容 | 改修 | 備考 |
|---|---|---|---|
| **①ライセンス方式** | TF カタログ鍵（`material: TRIAL_KEY` ＋CMD＋`SOULBOUND`）＋`removed-vanilla-items: [TRIAL_KEY]` でバニラ入手を封鎖。**消費されない前提で「所持＝入場権」**として設計する | 0 | 今日できる。1回作れば恒久入場権。周回制限は EliteMobs 側の `dungeonLockoutMinutes` で担保する |
| **②消費式（バニラ鍵）** | 素のバニラアイテムを鍵にし、供給を `villager-trades` の `output: { material: ... }`（＝meta なしで出せる唯一の口）に限定 | 0 | 消費は効くが、**`removed-vanilla-items` は使えない**（TF品でない鍵まで掃除されるため）。他の入手経路が残る |
| **③`key-catalog:` 対応** | 鍵にカタログIDを許可し、消費も Material 一致で行うよう `DungeonGateService` を修正 | 小（1ファイル数十行＋エディタのピッカー1個） | **本命**。「儀式で鍵を作って1回入る」が正しく成立する |

推奨: **③を入れる前提で設計し、入るまでは①で運用**（①→③の移行は yml のキー追加だけで済む）。
鍵のレシピは **儀式（G6 相当の設備＋ダンジョン素材）** にして、ソースループが入場権に直結する形にする。

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

### 7-A. ジャーへの直接投入を config 化する（①の前提・最重要 / 推奨）

- 現状: ジャーへ直接入れられるのは **ソースベリーのみ・+100 固定**（`SourceJar.SOURCE_PER_BERRY = 100`、
  右クリック経路とホッパー経路の両方でこの定数を参照）。エディタの機能アイテムタブでは
  `source_berry` の表示名 / material / レシピしか編集できず、**量は触れない**
- 必要: **投入可能アイテムと量のテーブル化**。例:

  ```yaml
  # sourcejars.yml
  insertables:
    custom:source_berry: 100
    custom:source_crystal: 1600
    custom:world_seed: 1638400
  ```

- 規模: 定数参照2箇所をテーブル引きに変える＋`sourcejars.yml` のパーサ追加＋エディタに行編集UI1つ
- **なぜこれが第一候補か**: この経路には排出ゲートが無いので、**「素材ごとの投入量」がそのまま生産速度になる**。
  §2-3 の世代ラダーがこの1機能だけで成立する

### 7-A2. ソースリンクのドレイン上限（①の副軸）

- 現状: `Sourcelink.MAX_DRAIN_PER_TICK = 50` / `SourcelinkTickTask.TICK_INTERVAL = 100`（**どちらもハードコード定数**）
  → 1台10/秒で固定。**カスタムソースリンクを定義しても1台の速度は変えられず、燃料の値を上げても平均速度は変わらない**（§2-1）
- 必要: **ティア別に config 化**（`sourcelinks.yml` の各 type / 各 `items.<id>` に `drain-per-tick`）。
  `items.<id>` 側にも書けると「上位炉は速い」が作れる
- 規模: 7-A と同程度
- 併せて検討: **満杯ジャーへの排出が消滅する**挙動（`supplyAdjacent` の `remaining` 破棄）。
  バッファへ戻すようにすると「溢れて損した」事故が消える。容量アップの動機は残したいので、
  **戻すか捨てるかは設計判断**（捨てる＝容量が意味を持つ、戻す＝優しい）
- **要確認（P0）**: 稼働中の ArsPaper フォーク（`fork-handoff/arspaper/fork/`、公開リポジトリには含まれない）は、
  ここで参照した公開リポジトリ（2026-04）より新しい。`sourcejars.yml` の `capacity` はエディタ側に既にあるのに
  公開版は `SourceJar.MAX_SOURCE = 10000` の定数なので、**フォークでは既に config 化が進んでいる**。
  ドレインや投入量も同様に対応済みの可能性があるため、**着手前に稼働フォークの実コードで確認**

### 7-A2. ダンジョンゲートの鍵（②の前提）

`key-material` が Material 限定であること、および `contains`（Material のみ）と `removeItem`（`isSimilar`＝meta 含む）の
不一致で **meta 付きアイテムが消費されない**問題（§3-3 の表）。`key-catalog:` 対応＋Material 一致での消費に直せば解決する。
規模は1ファイル数十行＋エディタのピッカー1個。

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
| **P0** | 稼働 ArsPaper フォークの**投入量／ドレイン／容量**の実値確認、①の目標時間と生産ノブを決める | 実コード確認のみ | §9 の判断が埋まる |
| **P1** | 高レベル帯素材の供給を開く | `mob-level-table.yml` `add-drops`、`catalog.yml` 素材4種 | Lv65+ を狩ると素材が出る |
| **P2** | 束縛者の難易度と入場ゲート | `mob-overrides.yml`、`gates.yml`、`crafting-features.yml`（鍵Material封鎖）、`catalog.yml`（鍵） | 鍵なしで入れない／シミュレータで想定撃破時間に収まる |
| **P3** | ソース世代 G1〜G4（`insertables` 改修が入るまでは G1 のみ実運用可） | `sourcelinks.yml`、`sourcejars.yml`、`catalog.yml`（儀式）、`material-lists.yml` | 儀式でソースを払って設備が上がる |
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

1. **①の目標プレイ時間**。1億に何時間かけさせるか（40h / 100h / 400h）。これが §2-3 の世代ラダーの倍率を決める。
   **改修ゼロで行くなら目標を100万ソースへ下げる**（§2-1 案D。ベリー農場＋ホッパー1本で約1.1時間）のも選択肢
1b. **①の生産ノブをどちらにするか**。§7-A（ジャー直挿しの量を config 化＝推奨）か §7-A2（ソースリンクのドレイン上限）か、両方か
2. **1億の定義**。(A) 実際にジャーへ貯めた総量 か (B) 儀式で払った累計投資額 か
3. **満杯ジャーの排出分**を捨てたまま（容量に意味を持たせる）か、バッファへ戻すか（§7-A）
4. **束縛者の段位分け**。1ダンジョンで完結（overrides は設計図ワールド単位なので数値1本）か、EliteMobs 側でパッケージを分けて 通常/極 の2本立てにするか
5. **鍵の方式**（§3-3 の①ライセンス / ②消費式バニラ鍵 / ③`key-catalog:` 実装）と、占有させる Material（`TRIAL_KEY` 系を潰してよいか）
6. **§7-B の採用案**。A/B 先行で良いか、C（`chest-loot.yml` 新設）を先に実装するか
7. **燃料に占有させる Material**（`MAGMA_BLOCK` 等）をバニラ入手不可にしてよいか
8. ロール再設計（LD-3）の扱い。本草案は現行 `role-buffs.yml` の形（明示ロール）に乗せている
