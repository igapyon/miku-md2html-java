# Node v0.4.0 追従と未移植機能の実装計画

作成日: 2026-10-24。対象: miku-md2html-java。
実行担当を Luna と想定し、入力、変更対象、順序、合格条件を固定した計画。
その後、利用者の実装指示に基づく作業を開始した。以下の表は2026-10-24時点の進捗。
調査根拠と G01～G21 の意味は [差分調査](upstream-v0.4.0-gap-analysis.md) を参照。
CI と Release asset workflow の実装状況は
[GitHub Actions 計画](github-actions-plan.md) に記録した。

## 実装進捗

この表は計画のチェックボックスとは別に、今回の実装と未検証条件を示す。
task の合格条件をすべて満たしていない項目は未完了のままにする。

| Task | 状態 | 確認できた範囲 / 次の条件 |
| --- | --- | --- |
| T00 | 部分実装 | Node v0.4.0 の5 fixtureと provenance/hash を固定。比較4/5一致。残りの19代表入力・CLI失敗 golden は未登録。 |
| T01 | 部分実装 | 空入力、remote image 分類、encoded path、slug 連続 hyphen を変更。Unicode境界全件は未確認。 |
| T02 | 部分実装 | PNG/CgBI、JPEG DNL、WebP寸法readerを実装。upstream raster全ケースは未照合。 |
| T03 | 部分実装 | Flexmark/jsoup/SnakeYAML/ph-cssを固定しfat JARへ同梱。Java 8/25実runtime確認はCI未実行。 |
| T04 | 部分実装 | SVG XML/CSS/namespace/nested raster検査を実装。upstream SVG全コーパスは未照合。 |
| T05 | 部分実装 | SnakeYAML Engine CoreSchemaで front matterを解析。alias/resource境界全件は未確認。 |
| T06 | 未完了 | source/generated provenanceを持つ共通 article tree と walker が未実装。 |
| T07 | 部分実装 | Flexmarkを全Markdown入力へ適用。GFM oracleのtable pipe caseとreference spacingが不一致。 |
| T08 | 部分実装 | tables/autolink/strikethrough/task-list/footnotes extensionを適用。複数脚注境界は未確認。 |
| T09 | 部分実装 | jsoup allowlist、raw image、inline SVGを適用。provenanceと全属性契約が未完了。 |
| T10 | 部分実装 | heading IDs/TOC/fragment書換えを実装。Node serializerとの1ケース差分が残る。 |
| T11 | 部分実装 | pathごとのdescribe cache、idごとのsize一貫性/read cache、resolver契約違反の内部例外化を追加。prepare/estimate/embed順とbudget parityは未完了。 |
| T12 | 部分実装 | FileTime精度を保持し、missing/outside candidateも出力衝突確認用に記録。全参照scan/scan完了、ctime、race検査は未完了。 |
| T13 | 部分実装 | HTML/reportを両方stage後にcommitするseamとstage/commit/internal失敗テストを追加。missing inputのfailure reportも対応。参照scan gateとrace全件は未完了。 |
| T14 | 部分実装 | report schema v2、description/imageMode/拡張stats/details/source.end、未知input bytesの省略を追加。source.endを作るsource rangeと全bytes parityは未完了。 |
| T15 | gate未通過 | Rhinoはisolated probeのみ。Java 8/25、grammar、隔離、budget中断の条件未達。 |
| T16 | 未実装 | Syntax highlightingは未移植。 |
| T17 | 部分実装 | 5 oracle caseのうち4件がHTML bytesまで一致。commonmark-gfmはtable inline-code pipeとreference spacingが不一致。広いcontract manifestは未完了。 |
| T18 | 部分実装 | docs、license、ZIP、CI/Release workflowを追加。CI hosted run、Java 8/25 smoke、実asset添付は未確認。 |
| GA01 | ローカル実装済み | workflow YAMLとsmoke helperを追加。GitHub hosted run待ち。 |
| GA02 | ローカル実装済み | POM tag不一致negativeとpositive stageを確認。GitHub uploadは未実行。 |

## 1. 目的、固定条件、着手方法

目標は、現在の初期 Java 移植を Node v0.4.0 の動作へ近づけ、
実装した範囲を input / output / diagnostics / tests で説明できる状態にすること。
すべての Node 依存を Java で一から書き直す作業にはしない。

固定入力:

- upstream URL: https://github.com/igapyon/miku-md2html
- 追従先 tag: v0.4.0
- upstream commit: b18a6e390425bd0e15387a2d608980a42a1809e9
- 調査時 Java HEAD: b50e029b7d430b009bdc6c156863607528649665
- 計画開始時の Java baseline / artifact version: v0.3.5 / 0.3.5。2026-10-24に `0.3.6` へ更新済み。
- Java base package: jp.igapyon.mikumd2html
- CLI class: jp.igapyon.mikumd2html.cli.MikuMd2htmlCli
- Java 8 source / binary / runtime、Maven、JUnit Jupiter、単一 executable fat JAR を維持。
- core の公開入口 MikuMd2htmlCore.convertMarkdown と AssetResolver の既存呼出形を維持。
- core に CLI の Path、stdout/stderr、System.exit を持ち込まない。
- Node のブラウザ UI と Maven plugin の新設は対象外。
  HTML に埋め込む既存 viewer CSS/script は製品出力の一部として対象に含める。
- 今回の利用者指示により、従来の「runtime dependency は原則ゼロ」に対し、
  必要性を説明できる最小限の同梱 library を導入する。
  採用理由と保留gateは [dependency decisions](dependency-decisions.md) に記録する。

実装を続ける担当は、この文書と差分調査を読み、既存差分を保持して
未完了taskを依存順に進める。合格条件を満たしたtaskだけcheckboxを進める。

共有原則は installed igapyon-miku-soft-developer の
references/30-java-straight-conversion-workflow.md と
references/miku-soft-basic/miku-soft-20-javaapp-design.md、
miku-soft-30-straight-conversion.md を 2026-10-24 に確認した。
installed skill 自体は Git checkout ではないが、追加照合で clean な source checkout の
commit c61466dc03f7ed4a532fd6a2b37ef5a29be0071c と参照内容が一致すると確認した。
照合した資料と R01～R08 の参照 ID は
[共有 reference の入口](miku-soft-reference.md) に記録した。
共通文書をこの repo にコピーしない。
姉妹 Java repo の README/POM は既存構成の参考に限り、product semantics は upstream を優先する。
今回は計画に限定し、無関係な maintenance uplift は実装しない。

### 共通原則のこの計画への適用

| この repo での扱い | 根拠と位置づけ |
| --- | --- |
| v0.4.0 の file/責務/診断/test への対応を維持 | R02/R04。Java の都合で公開 API、error result、CLI 語彙を新しい別契約へ作り替えない |
| 新 ArticleDocument / adapter を必要範囲へ限定 | R01/R04。Node の AST/HAST と library 境界の代替。無関係な汎用 framework の設計を追加しない |
| raster、URI、画像 state、NIO は自前処理の構造を追従 | R04。upstream 自前実装は、まず JDK へ同じ分岐・責務で移す |
| Markdown/YAML/HTML/CSS/grammar の library は個別に採否判定 | R04 と今回の利用者指示。候補名/version は本計画の提案であり、miku-soft が特定 library を必須としているわけではない |
| 単一 fat JAR と sources、Java 8 と最新 LTS の実動作検査 | R03。Temurin と現在の検査対象 25、build JDK の選択は repo の検査条件として記録する |
| JSON/report/diagnostics、UTF-16 順、CLI の stdout/stderr/exit を固定 | R01/R05。未実装機能を help や README で実装済みと記載しない |
| front matter は YAML として解析後、title/description/lang だけ抽出 | R08 と upstream。共通文書の推奨 field を追加の製品契約として自動導入しない |
| Release asset workflow は共有既定、CI は別の追加提案 | R02。GA02 と GA01 の位置づけは GitHub Actions 計画で区別する |
| 公開操作は人、agent はローカル準備と検証 | R06。workflow の準備と実際の GitHub 公開を混同しない |

共通 default と既存製品契約が異なる場合は、R01 に従って違いを記録する。
たとえば Node の E_OPTIONS を含む result 契約や Java jar 向け help を、
共通設計の一般論だけで例外型や Node 実行例へ置換しない。

## 2. 同梱 library の選択方針

### 採用した依存と未通過候補

Flexmark、jsoup、SnakeYAML Engine、ph-css は POM に固定してfat JARへ
同梱した。選択理由、closure、ライセンス、実測状況は
[dependency decisions](dependency-decisions.md) を参照する。
Rhinoだけは候補probeに留め、T15 gate未通過のため製品へ追加しない。

| 用途 | 状態 / version | 選ぶ理由と制約 |
| --- | --- | --- |
| CommonMark/GFM | 採用: com.vladsch.flexmark 0.62.2 | core + tables/autolink/strikethrough/tasklist/footnotes に限定。AST source offset はMarkdown原文へ結び付けておらず、oracle差分が残る |
| HTML5 の解析 | 採用: org.jsoup:jsoup 1.23.2 | 必須 runtime transitive dependency なし。構造解析とfragment sanitationに使う。製品 allowlist は製品側に保持 |
| YAML 1.2 | 採用: org.snakeyaml:snakeyaml-engine 2.10 | CoreSchemaとsafe data constructionを使用。alias/resource limitと全upstream corpus parityは未確認 |
| SVG 内 CSS | 採用: com.helger:ph-css 6.5.0 | stylesheet と declaration list を AST で検査する。ph-commons等を含む実測 closure はdependency decisionsに記録 |
| 固定 JS grammar の再利用 | org.mozilla:rhino:1.7.15.1 + upstream 固定 highlight.js 11.11.1 | 条件付き candidate。Java 8 対応系列を検証。Rhino の新系列は Java floor が上がっており、自動で置換しない。grammar の実行可否は未確認 |

Markdown の direct dependency は次の 6 artifact に限定して検証する。
共通 utility は実際の runtime transitive closure を inventory へ記録する。

~~~text
com.vladsch.flexmark:flexmark:0.62.2
com.vladsch.flexmark:flexmark-ext-tables:0.62.2
com.vladsch.flexmark:flexmark-ext-autolink:0.62.2
com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:0.62.2
com.vladsch.flexmark:flexmark-ext-gfm-tasklist:0.62.2
com.vladsch.flexmark:flexmark-ext-footnotes:0.62.2
~~~

flexmark-all、不要な converter、PDF/DOCX、formatter、renderer framework は導入しない。
「1 系列」でも複数の JAR を含むので、系列数を dependency 総数と取り違えない。
ph-css の logging dependency に追加対策が必要なら、同じ version に対応する
最小の no-op binding を検証して記録し、stdout/stderr に library log を混ぜない。

### 今回は採用しない案

- commonmark-java 最新版をそのまま使う案: 0.22 以降は Java 11 が必要。
  Java 8 系 0.21 には後発の公式 footnote extension がない。
  依存の少なさには利点があるが、今回は古い core と別世代 extension の backport を
  Luna の既定作業にしない。Flexmark candidate が不合格なら比較対象として記録する。
- Flexmark 0.64.x、SnakeYAML Engine 3.x、Rhino の新系列、
  ph-css 7/8 系を名前だけで最新へ上げる案: Java floor が異なる。
- HTML sanitizer の default safelist を丸ごと採用する案: Node の tag/attribute/
  footnote/raw/generated 判定と一致しない。
- SnakeYAML の YAML 1.1 default を YAML 1.2 とみなす案。
- 独自 regex だけで CommonMark、YAML、CSS、13 言語 grammar を完成させる案。
- Node subprocess を製品の実行要件にする案、Nashorn の常駐を仮定する案、
  WebView/GraalVM を必須化する案。
- report 出力だけのための Jackson/Gson、画像の寸法だけのための画像処理 framework、
  NIO 処理だけのための汎用 IO library。これらは JDK で実装する。

### 同梱方法

1. Java library は Maven の version を固定し、既存 maven-shade-plugin で
   executable JAR に class/resource を同梱する。利用者に追加 JAR の取得を要求しない。
2. 通常は library source の独自 fork や source の大量コピーを作らない。
   外部 package の型を公開 core API に漏らさず、内部 adapter で閉じる。
3. minimizeJar は既定で無効。reflection/resource を必要とする library を誤って削らない。
   service descriptor、multi-release entry、module-info の扱いは package 検証で確認する。
   namespace collision が再現しない限り一括 relocation を加えない。
4. repository root に THIRD_PARTY_NOTICES.md、配布 JAR 内に衝突しないパスの
   license 原文・copyright・origin/version、dist ZIP に notices を含める。
   Rhino/JS grammar を採用する場合は、それらの source と再生成手順の提供条件も確認する。
5. JS grammar は upstream lock の version/integrity と origin を固定した resource とし、
   build 用 script と source/origin 記録を通常の tracked path に置く。
   実行時の npm、CDN、download、Node は不要にする。
6. dependency の初回 inventory には direct/transitive の区別、runtime JAR 数、
   各 bytes、SHA-256、license、Java floor を保存する。推測で「小さい」と宣言しない。
7. library を外す選択は可能だが、その場合は対象機能を未実装として残す。
   plain text 出力を「highlight 完了」としない。

一次資料:
[Flexmark requirements](https://github.com/vsch/flexmark-java#requirements)、
[CommonMark changelog](https://github.com/commonmark/commonmark-java/blob/main/CHANGELOG.md)、
[jsoup distribution](https://jsoup.org/download)、
[Engine 2.10 POM](https://repo.maven.apache.org/maven2/org/snakeyaml/snakeyaml-engine/2.10/snakeyaml-engine-2.10.pom)、
[ph-css](https://github.com/phax/ph-css)、
[Rhino release notes](https://github.com/mozilla/rhino/blob/master/RELEASE-NOTES.md)。
古い Java 8 系の採用可否は既知問題と対象 input を確認する gate に含める。

## 3. 実装で使う内部境界

core/、cli/、adapters/ は現在の base package 以下を指す。
実装済み/未実装の境界は [class mapping](upstream-class-mapping.md) と
[remaining items](remaining-migration-items.md) を参照する。以下のうち
ArticleDocument、source range、tree walker、final serializer 等は未実装。

| 境界 | 予定の責務 |
| --- | --- |
| core/SourceRange | 正規化 Markdown 内 UTF-16 offset と、元文書の 1-based start/end line/column |
| core/ArticleDocument | root / element / text、順序付き属性、SourceRange、raw/generated の由来を持つ小さな製品内 tree。汎用 parser framework にはしない |
| core/ArticleTreeWalker | upstream tree.ts に対応する source order の反復巡回。見出し・参照収集・pending 再結合・code source 収集に使う |
| core/markdown/FlexmarkAdapter | 必要 extension のみを有効化し、現在はlibrary ASTからHTMLを生成して内部でsanitize。ArticleDocumentへのsource/provenance写像は未実装 |
| core/html/Html5TreeAdapter | raw HTML と Markdown 生成要素を HTML5 として組み合わせる。source/provenance を sidecar で保持 |
| core/html/ArticleSerializer | Node の HAST 出力に合わせて escape、属性順、boolean、void tag、改行を固定 |
| core/RasterDimensions | raster-dimensions.ts の自前アルゴリズムを Java に直接対応させる |
| core/SvgInspector / SvgCssValidator | JDK XML と必要最小限の CSS parser を使い、SVG の製品規則を検査 |
| core/SyntaxHighlighter | grammar engine との内部境界、予算、token 検証、fallback |
| adapters/AssetReferenceTracker | optional capability interface。全参照・scan 完了・identity を保持。既存 AssetResolver の abstract method は増やさない |
| cli/MarkdownInput / PathIdentity / OutputFiles / Reporting | Node の同責務の helper に対応。FileConversion は順序と failure state を制御 |

HTML5 adapter で Markdown の生成 HTML と raw HTML を一つの buffer に並べる際は、
出力区間から元の Markdown 区間への sidecar map を作る。
jsoup の position tracking は「この buffer 内の offset」であり、そのまま
Markdown の行番号として返さない。AST の source offset と結び付ける。
raw/generated の信頼判定を、著者が書ける特別な HTML 属性や id に依存させない。
由来が確定できない要素を generated として優遇しない。

最終 pipeline の順序は Node core/converter.ts に合わせる。

~~~text
request validation / BOM・改行正規化 / input cap
front matter
Markdown AST・fence source の収集 / raw HTML の parse
製品 sanitizer・画像 prepare / reference scan 完了
heading・fragment・metadata・TOC
最終 allowlist と pending image node の再結合
syntax highlight
HTML skeleton + pending payload のサイズ見積り
local image read・embed
最終 HTML とサイズ・diagnostics・stats
~~~

## 4. Luna が実行する task

通常は T00 → T01 → … → T18 の順とする。
library gate が失敗した場合の進め方は第 6 節に定める。
各 task の変更に対応する focused test を先に通し、同じ phase の完了時に
mvn test を一度通す。新しい変更や失敗がないのに全試験を繰り返さない。

### T00 — upstream 固定と比較の土台

- [ ] 対応: G21。依存: なし。
- upstream は ../miku-md2html を読み取り専用で使う。HEAD が固定 commit と違う、
  dirty、または存在しない場合は、workplace/upstream/miku-md2html に別 checkout を作り、
  固定 tag/commit を checkout する。既存の sibling を reset/pull しない。
- src/test/resources/parity/v0.4.0/ に通常入力、期待 HTML、期待 core result、
  CLI stdout/stderr/report の fixture と provenance manifest を保存する。
  必須の期待値を workplace や /tmp だけに置かない。
- scripts/capture-node-parity.mjs は固定 Node runtime から oracle を生成する
  開発用 script とする。upstream source の改変や Java 出力からの期待値生成はしない。
  差分調査に記載した Release と一致する CLI/import runtime の SHA-256 を確認する。
  source から再生成する場合は別の artifact hash と生成条件を記録する。
- scripts/compare-node-java.mjs は packaged artifact を subprocess で比較する。
  通常の JUnit は固定 fixture を使い、Node 未導入でも実行可能にする。
- まず差分調査の 19 入力、続いて Node の 10 test file のケースを manifest に列挙する。
  各ケースを mapped / passed / pending にし、未対応を pass として数えない。
- 合格: fixture 再生成が固定 Node で再現し、manifest が artifact version、
  commit、input/expected SHA-256 を持つ。初期 Java の失敗一覧を保存できる。
  この段階では全 parity test が成功する必要はない。

### T01 — 小さな core/URI/slug の差を解消

- [ ] 対応: G01、G05 の slug 部分、G11。依存: T00。
- 変更: MarkdownConverter、Slug、ImageEmbeddingSession の URI 分類。
- 空入力で charAt(0) を呼ばず、空文書として filename title へ fallback する。
  BOM と CRLF の正規化・input cap の順序は Node を維持。
- 連続ハイフン、前後ハイフン、NFC、mark、Unicode whitespace と
  unique ID suffix を Node の順序で実装する。Java の Unicode version 差も fixture で確認。
- https/http と protocol-relative image を E_REMOTE_IMAGE にする。
  percent decode は一度だけ、encoded #/? は decoded filename として resolver に渡す。
- 追加 test: CoreValidationTest、SlugTest、ImageUriTest。
  focused: mvn -q -Dtest=CoreValidationTest,SlugTest,ImageUriTest test
- 合格: empty、slug、remote-image の oracle、encoded hash/question/percent、
  invalid UTF-8 percent、traversal/scheme の upstream ケースが一致。

### T02 — raster 寸法の直接移植

- [ ] 対応: G10。依存: T00。
- 変更: 新 RasterDimensions、ImageInspector の raster 部分。外部画像 library は追加しない。
- PNG の IHDR と optional CgBI、JPEG の frame/scan/byte stuffing/restart/DNL、
  WebP の RIFF 境界・chunk padding・VP8/VP8L/VP8X・version を
  upstream と同じ分岐で移植する。unsigned 32 bit は Java long で扱う。
- zero 寸法拒否と、container validation / dimension validation の分担を揃える。
  全形式に誤って SVG の「寸法未指定可」を適用しない。
- 追加 test: RasterDimensionsTest。upstream raster-dimensions.test.ts の全意図を移す。
  さらに large-cgbi、0×0、JUNK 後の WebP と bounds を確認する。
- focused: mvn -q -Dtest=RasterDimensionsTest test
- 合格: DNL が成功、CgBI 10000×5000 が一度警告、0×0 が E_IMAGE_INVALID。
  不正 chunk が配列例外で落ちず製品 error になり、画像 bytes は変更しない。

### T03 — parser library の導入 gate

- [ ] 対応: G02～G04、G06、G08 の事前条件。依存: T00。
- workplace/dependency-probes/ に隔離した Maven probe を作り、
  第 2 節の固定 candidate を使う。最初から製品 POM に全て追加しない。
- library ごとに runtime closure、license、class major/API の Java 8 対応、
  Java 8/25 の実行結果、必要な jar bytes、既知の関連問題を記録する。
  Java 8 の実 runtime がなければ互換性の gate は未完として明記する。
- R04 の判断基準に従い、upstream が library を使う箇所か自前処理か、
  Java 側候補の適合範囲、保守性、upstream 対応を維持する adapter を記録する。
  候補ごとの採用理由と、採用しない場合に残る機能を明示する。
- Markdown: 第 5 節の M 系 fixture、footnote の複数 paragraph・繰返し参照、
  tab・Unicode・source offset を検査。必要 extension の追加だけで足りるかを見る。
- jsoup: spanning raw inline/block、table の HTML5 補正、SVG、
  position tracking と由来の復元を検査。既定 safelist の採用試験ではない。
- YAML: YAML 1.2 CoreSchema、duplicate key、複数文書、unknown nested key、
  block/flow scalar、alias/recursive alias を検査。
- CSS: declaration/stylesheet の両方、escaped URL、nested function、
  malformed/未解析部分、at-rule/animation を検査。error recovery を黙って許可しない。
- 合格: docs/dependency-decisions.md に candidate ごとの採否、失敗 fixture、
  closure/サイズ、必要な adapter、Java 8/25 の証拠を残す。
  1 library の失敗で他 library の独立した調査を取りやめない。

### T04 — SVG の安全性と受理範囲を揃える

- [ ] 対応: G08、G09 の external/data SVG。依存: T02、T03 の CSS gate。
- 変更: ImageInspector から SvgInspector / SvgCssValidator を抽出。
- style 要素本文は属性数と無関係に検査する。style 属性と全 resource CSS 属性も同じ
  CSS AST 検査へ通す。@import/@font-face/keyframes、animation property、
  非 fragment URL、Raw/parse error を upstream の code で拒否する。
- JDK XML の外部 DTD/entity 無効を維持し、strict UTF-8、PI、
  全 descendant の namespace、case-sensitive element/attribute allowlist、
  nested data raster の MIME と実体検査を移す。
- SVG width/height は optional な正数とし、単位・小数・片側欠落の扱いを Node と揃える。
  raster 用の必須寸法規則を流用しない。
- test: SvgSafetyTest、SvgCssTest。safe style が受理されることも検査する。
  focused: mvn -q -Dtest=SvgSafetyTest,SvgCssTest test
- 合格: svg-external-css が E_SVG_EXTERNAL_RESOURCE。
  escaped external URL、namespace 混入、PI、偽 data raster を拒否。
  safe local fragment/filter/style と許可属性は保持し、外部 SVG の原 bytes を維持。

### T05 — YAML 1.2 front matter

- [ ] 対応: G04。依存: T03 の YAML gate。
- 変更: FrontMatterParser の内部 adapter。CoreSchema と safe construction を明示。
- root mapping、一文書、unique key、全構造の parse を先に行い、
  その後 title/description/lang のみ抽出。unknown key も構文検査する。
- block scalar・quote・comment を parser に任せる。空 title/description は未指定へ戻す。
  type warning は Node の message/source/details をそのまま対応させる。
- alias の設定名が似ていても Node の maxAliasCount=50 と同義とはみなさない。
  collection alias 数と展開量/循環の違いを probe で固定し、
  差が残る場合は gate 未完とし、無制限な展開で通さない。
- test: FrontMatterParserTest。focused: mvn -q -Dtest=FrontMatterParserTest test
- 合格: yaml-block / unknown-map / empty-title の差を解消し、
  duplicate、non-map、複数文書、alias 超過は E_FRONT_MATTER。body offset を保持。

### T06 — article tree と source/provenance

- [ ] 対応: G05、G06、G18、G20 の土台。依存: T03 の Markdown/jsoup gate。
- 作成: 第 3 節の ArticleDocument / SourceRange / parser・HTML5 adapter の内部境界。
  Diagnostic に optional end を追加し、既存 constructor を維持する。
- ArticleTreeWalker は ArrayDeque で children を逆順に積み、source order を維持する。
  upstream v0.4.0 の tree.ts と同じ用途に適用し、すべての処理を非再帰化する別作業に広げない。
- 最初は段落・見出し・リンク・fence・raw span/block の最小 fixture で tree を作る。
  元 source、正規化 source、front matter offset の区別を固定する。
- Markdown 生成要素、著者 HTML、後で生成する footnote/highlight を区別する。
  paragraph を単純連結して source line を失わず、nested list/blockquote の元 offset を保つ。
- test: ArticleDocumentTest、SourceMappingTest。
  focused: mvn -q -Dtest=ArticleDocumentTest,SourceMappingTest test
- 合格: front matter 後の nested fence、raw link、table cell の start/end が Node fixture と一致。
  著者による id/class/特殊属性の偽装で generated 判定が変わらない。

### T07 — CommonMark の構造

- [ ] 対応: G02。依存: T06。
- 変更: MarkdownParser / InlineMarkdownParser の解析責務を FlexmarkAdapter へ移す。
  parser library の CommonMark profile を明示し、不要 extension を有効にしない。
- Setext/ATX、indented/fenced code、soft/hard break、reference link/image、
  nested/loose list、ordered start、lazy blockquote、code span、
  delimiter・escape・entity を ArticleDocument に写す。
- 出力 HTML を外部 renderer からそのまま採用せず、product tree で制御する。
  既存 subset と新 parser の二重適用を残さない。
- test: MarkdownBlocksTest、MarkdownInlineTest。
  focused: mvn -q -Dtest=MarkdownBlocksTest,MarkdownInlineTest test
- 合格: markdown/reference probe と M 系 fixture を満たし、入力文字の欠落、
  code の末尾改行・tab・Unicode、見出し metadata が oracle と一致。

### T08 — GFM と脚注

- [ ] 対応: G03。依存: T07。
- 有効な機能は table/autolink/strikethrough/tasklist/footnote のみ。
- tables の delimiter column 数・escaped pipe・code span・alignment、
  disabled checkbox、bare URL/email を upstream fixture に合わせる。
- footnote の definition/reference/order、繰返し backref、複数 paragraph、
  generated heading、ID と aria を project tree で Node 形式へ写す。
- v0.4.0 は生成 section を unwrap する W_HTML_SANITIZED を持ち、
  Footnotes 見出しも heading/TOC に含める。調査で見えたこの挙動を独自に修正しない。
- test: GfmTest、FootnotesTest。focused: mvn -q -Dtest=GfmTest,FootnotesTest test
- 合格: GFM/footnote の本文、ID/backref、headingCount、診断が oracle と一致。

### T09 — raw HTML allowlist と inline SVG

- [ ] 対応: G06、G07、G09 の inline 部分。依存: T04、T06～T08。
- ArticleSanitizer を tree 処理に移す。Node article-sanitizer.ts の allowedTags、
  droppedTags、全 attribute/URL/footnote 規則を対応表で移す。
- active tag は subtree ごと除去、unsupported tag は子を保持、
  safe details/table/inline formatting は構造を保持。raw id/class と interactive input は拒否。
- Markdown img と raw img の両方を画像 session へ渡す。
  raw SVG は検査後に img へ変換し、aria-label/title から alt を得る。
- inline SVG は namespace 補完、validated root の serialize を行う。
  JDK XML の default serialization が Node と同じとは仮定せず、
  quotes/entities/attribute order/empty element/コメントを fixture で固定する。
- test: ArticleSanitizerTest、InlineSvgTest。
  focused: mvn -q -Dtest=ArticleSanitizerTest,InlineSvgTest test
- 合格: raw-html/inline-svg probe、active element、偽装 raw id、
  unsafe link、コメントの除去、診断 start/end/details が oracle と一致。

### T10 — heading、fragment、HTML serializer

- [ ] 対応: G05 の残り、G20 の基礎。依存: T07～T09。
- heading text は安全な tree から集め、image alt/entity/code/Unicode を反映。
  raw HTML heading と生成 footnote heading も Node の順序で処理する。
- fragment decode は decodeURIComponent 相当の strict UTF-8 とし、
  不正 encoding、heading/footnote 解決、診断 source を揃える。
- ArticleSerializer と HtmlDocumentRenderer の TOC、article newline、attribute order、
  escaping、boolean、void tag、CSS/script の末尾処理と CSP hash を固定する。
- test: DocumentStructureTest、HtmlDocumentRendererTest。
  focused: mvn -q -Dtest=DocumentStructureTest,HtmlDocumentRendererTest test
- 合格: text-only / metadata / GFM/raw HTML の HTML bytes が固定 oracle と一致し、
  各 source input を繰り返しても同じ bytes。viewer は外部通信しない既存構成を保つ。

### T11 — 画像 prepare / estimate / embed

- [ ] 対応: G12、G13。依存: T02、T04、T09、T10。
- ImageEmbeddingSession を Node と同じ段階へ分ける。
  path ごとの describe cache、id ごとの length 一貫性、
  id ごとの read、warning の重複抑制を変換ごとに保持する。
- sanitize 後に pending node を再結合し、画像を read する前に
  skeleton + 各出現分の payload lower bound を計算する。総計は long。
- unique asset bytes と occurrence ごとの Base64 bytes を分ける。
  data/inline SVG/local を含む統計と error source label を揃える。
- resolver の不正な metadata/null bytes や想定外の例外を画像形式 error に変換しない。
  AssetAccessException と内部契約違反を区別し、CLI 境界では後者を E_INTERNAL / exit 3 にする。
- test: ImageEmbeddingSessionTest、OutputBudgetTest。fake resolver で call 数と read 未実行を検査。
  focused: mvn -q -Dtest=ImageEmbeddingSessionTest,OutputBudgetTest test
- 合格: describe/read 回数、alias path、same-id size 不整合、exact cap、
  one byte over、repeated image と並行変換で upstream の統計・警告と一致。

### T12 — 全画像参照と filesystem identity

- [ ] 対応: G14、G15。依存: T09、T11。
- LocalAssetResolver に optional AssetReferenceTracker を実装。
  describe の成否とは独立に全 candidate/canonical path を収集し、scan 完了を保持する。
  front matter error 等の早期停止でも Node 同様に reference scan を実行する。
- FileTime の精度を維持し、fileKey/canonical path/size を比較。
  Unix ctime 等を取れる環境では対応させ、取得できない OS の差を記録する。
  E_IMAGE_NOT_FOUND / E_IMAGE_READ / E_ASSET_CHANGED を IO error から正しく分離する。
- PathIdentity は missing path の既存 ancestor を canonical 化できるようにする。
- test: LocalAssetResolverTest、AssetReferenceTrackerTest、PathIdentityTest。
  focused: mvn -q -Dtest=LocalAssetResolverTest,AssetReferenceTrackerTest,PathIdentityTest test
- 合格: traversal、outside symlink、encoded filename、hard-link identity、
  describe 後の size/identity/mtime 変化と missing candidate を検出。
  capability のない FS の skip は理由を出し、検査済みとは数えない。

### T13 — CLI の失敗状態と publication

- [ ] 対応: G16、G17、G14 の CLI gate。依存: T12。
- FileConversion を MarkdownInput / PathIdentity / OutputFiles / Reporting の責務に分ける。
  OutputFileOperations と convert の差替え seam を package 内で用意する。
- target 検査と input collision 確認を read 前に行い、missing/invalid UTF-8 でも
  安全な failure report の可否を判断する。scan 未完は画像に上書きしない側へ倒す。
- HTML/report の両方を stage 後、HTML、report の順に commit。
  state は not-committed / html-committed / complete。状態ごとの cleanup と
  stdout、failure report の有無、exit 1/3 を Node と同じにする。
- no-force の commit 時競合でも既存 file を置換しない。
  link 非対応等を理由に無検証の overwrite fallback を追加しない。
- test: CliFileConversionTest、CliFailureTest、OutputFilesTest。
  focused: mvn -q -Dtest=CliFileConversionTest,CliFailureTest,OutputFilesTest test
- 合格: report stage failure では HTML 未公開、report commit failure では HTML 維持・
  success summary なし、内部例外は exit 3。missing input probe の report が一致。
  既存出力・missing image path・後続画像を failure report で破壊しない。

### T14 — report と diagnostics の完全な形

- [ ] 対応: G18、G21。依存: T06、T13。
- Reporting に fixed schema writer を置き、description、source.end、
  details、未確定 field の omission、field order、2-space indent、末尾 LF を移植。
  JSON library を runtime に追加しない。
- stableDiagnostics は UTF-16 code unit 順、同値時の元順序を維持。
  message を Java の例外本文で置き換えない。未確定の bytes を 0 にしない。
- test: ReportingTest、DiagnosticsTest。JSON syntax は開発用 Node 比較でも parse する。
  focused: mvn -q -Dtest=ReportingTest,DiagnosticsTest test
- 合格: 成功・front matter/画像/IO/内部失敗・partial output report が schema と bytes で一致。
  絶対 temp path の差を比較対象から外す場合も、その field だけを明示し、診断を丸ごと消さない。

### T15 — 構文ハイライトの実行可否 gate

- [ ] 対応: G19 の実現性判定。依存: T00。製品への組込み前に実行する。
- 固定 highlight.js core + upstream が登録する 12 grammar を、
  13 language family に対応させる。HTML は XML grammar を共有する。
  upstream の code-languages.ts の alias と case/meta 規則を使う。
- workplace の隔離 probe で Rhino 1.7.15.1 上の読み込み、全 grammar、
  Unicode/RegExp、sub-language、upstream emitter の class 名を比較する。
  必要なら開発時だけの固定 transpiler を使用し、再生成 recipe を保存する。
- Java 8 と 25 の両方で同じ token tree / 原文を得られるかを確認する。
  user code は文字列として渡し、eval しない。trusted bundled JS のみ評価する。
- engine の host Java access、file/network/load 機能を公開しない。
  safe scope と ClassShutter 等の制限、処理予算の中断方法も検証する。
- 合格: 13 family の全 fixture で tokens/原文が一致し、
  offline・並行変換・予算中断が成立する。未実行環境は pass としない。
- 不合格: exact fixture と原因を記録し、T16 だけを保留。
  Java floor の引上げ、別 engine の追加、独自 13 grammar 実装は自動で始めない。
  他 task を進めたうえで、その判断だけを利用者に返す。

### T16 — highlight と fallback の移植

- [ ] 対応: G19。依存: T06～T10、T15 合格。
- SyntaxHighlighter に engine を閉じ、AST の fence source と安全化後の code を
  offset と exact text で突き合わせる。raw HTML code/inline/unknown language を処理しない。
- code text の末尾 LF を含めて UTF-8 256 KiB/block、1 MiB/document を計数。
  document budget は source order、失敗した engine 呼出も消費させる。
- span + 許可 class + text のみを受け入れ、連結 text が原文と違えば
  W_CODE_HIGHLIGHT_FAILED で plain code に戻す。例外詳細を露出しない。
- test: CodeLanguagesTest、SyntaxHighlightTest、SyntaxHighlightFailureTest。
  focused: mvn -q -Dtest=CodeLanguagesTest,SyntaxHighlightTest,SyntaxHighlightFailureTest test
- 合格: upstream syntax-highlight.test.ts の alias、exact boundary、
  later fitting block、injected invalid tokens/engine failure、
  Unicode/source line と deterministic token の各意図を満たす。

### T17 — 最終 pipeline と Node contract golden

- [ ] 対応: G13、G19、G20、G21。依存: T10～T14、T16 またはその明示的な保留記録。
- 第 3 節の pipeline 順を最終確認。highlight token の bytes を skeleton に含めてから
  image cap preflight を行う。sanitizer を後で掛け直して trusted token を削除しない。
- RefactoringContractTest で upstream refactoring-contract.test.ts の
  metadata/headings、GFM/raw HTML、repeated image、highlight の golden を比較。
  T16 保留なら highlight ケースは pending と記録し、full parity と表示しない。
- HTML は原則 byte 比較。JSON は schema/value の比較に加え、固定入力の report bytes も比較。
  time/path 等の差は理由と field を列挙する。空白や属性を全面 normalize して隠さない。
- focused: mvn -q -Dtest=RefactoringContractTest test
- 合格: 対応済み manifest の全 case が成功し、未対応は明示される。
  Node の実際の挙動が仕様説明と違う場合は follow-up に記録し、勝手に Java だけ直さない。

### T18 — 配布物・文書・基準の更新

- [ ] 対応: 全 G の状態記録。依存: 先行 task の完了または明示された保留。
- docs/upstream-snapshot.md の baseline を v0.4.0 と固定 commit へ更新。
  implementation status と baseline version は別に記録する。
  artifact version 0.4.0 を予定とし、実際の version 変更はその実装依頼の範囲で行う。
- README、CLI/development、class/test/CLI mapping、remaining-migration-items、
  follow-up log、TODO、dependency inventory、notices を実装結果に同期する。
  「JDK-only」「14 direct deps」など過去の説明を現状に合わせる。
- mvn package を実行し、JAR の resource/license、runtime closure、
  dist ZIP/source 配布内容を検査。クリーンな別 directory から JAR だけで
  --version、--help、代表変換、report、必要 grammar が動くことを確認する。
- Java 8 と最新 LTS の Java 25 で同じ packaged JAR を実行。
  build JDK の --release 8 だけで runtime 互換性を確認済みとしない。
  実 runtime 不在は未確認として残す。
- help は Java jar 呼出の意図的差を維持し、共有 option/default/exit code と
  runtime 固有行の allowlist を CliHelpContractTest で比較する。
- 合格: mvn test、mvn package、packaged smoke、対応する parity が成功。
  完了/保留/実行できなかった確認が docs と一致し、git diff/status に無関係な変更がない。
  エージェント自身による GitHub の PR、push、tag、Release 公開は行わない。
  人が Release を用意した後の asset 生成・添付 workflow は
  [別計画 GA02](github-actions-plan.md) の条件で準備する。

## 5. 最小の受入 fixture 群

T00 でこれを manifest に起こし、各 task が担当する case を追加していく。
小さな fixture の期待値を Node で採り、Java 実装と比較する。

| 群 | 必須の入力・確認 |
| --- | --- |
| V | empty、BOM、CRLF、invalid option、input cap、null/blank metadata、0×0 raster |
| M | Setext/ATX、indented/fenced code、soft/hard break、emphasis 境界、escaped punctuation/entity、reference link/image、nested/loose/start list、lazy quote |
| G | table alignment/escape/delimiter/不足 cell、bare URL/email、strikethrough、tasklist、footnote 複数 paragraph/重複参照/ID/backref/heading |
| Y | literal/folded scalar、quote/comment、flow/unknown nested map、empty title、non-string metadata、duplicate/recursive/over-limit alias、non-map、複数文書 |
| H | safe details/table/formatting、raw id/class/on*、script 等 subtree、unsafe href、comment、inline SVG、fence 風 raw HTML |
| S | safe SVG style/local fragment、escaped external URL、@import/font-face/keyframes/animation、malformed CSS、foreign namespace、DTD/entity/PI、nested raster、missing/decimal dimensions |
| R | PNG/CgBI、baseline/progressive/DNL JPEG、GIF、VP8/VP8L/VP8X WebP、JUNK/padding/length/version/truncation、40M pixel の直前/一致/超過 |
| I | path alias、同 id size 不整合、同画像繰返し、data と inline SVG、unique/payload 統計、5 MiB 直前/一致/超過、20 MiB、exact output cap、read 前の拒否、並行変換 |
| F | traversal/symlink/hard-link、describe 後の変更、missing 後続画像、report と画像 collision、input/output/report collision、no-force commit race |
| C | missing/invalid UTF-8 input、HTML/report stage failure、commit failure、内部例外、partial publication、cleanup、stdout/stderr/exit/report |
| L | 13 family/全 alias、unknown/plain/inline/raw code、末尾 LF/Unicode/tab、256 KiB と 1 MiB の境界、失敗後 budget、invalid token、fallback、決定性 |
| B | 全 HTML SHA-256、CSP hash と script bytes、JSON field/省略/order/改行、runtime 固有 help 行、offline fat JAR |

Node test file から Java test への対応は次の名前を既定とし、
実際に split/merge するなら docs/upstream-test-mapping.md を同時に更新する。

| Node test | Java test の主な予定 |
| --- | --- |
| core.test.ts | CoreValidationTest / FrontMatterParserTest / MarkdownBlocksTest / GfmTest / ArticleSanitizerTest / SvgSafetyTest |
| raster-dimensions.test.ts | RasterDimensionsTest |
| uri.test.ts | ImageUriTest |
| image-embedding.test.ts | ImageEmbeddingSessionTest / OutputBudgetTest |
| node-assets.test.ts | LocalAssetResolverTest / AssetReferenceTrackerTest |
| output-files.test.ts | OutputFilesTest / PathIdentityTest |
| cli.test.ts | CliFileConversionTest / ReportingTest / CliHelpContractTest |
| cli-failures.test.ts | CliFailureTest |
| syntax-highlight.test.ts | CodeLanguagesTest / SyntaxHighlightTest / SyntaxHighlightFailureTest |
| refactoring-contract.test.ts | RefactoringContractTest |

## 6. 停止条件と引継ぎ

- gate が失敗したら、その task の checkbox を進めない。
  expected/actual、固定 input、環境、candidate version、再現 command を
  docs/dependency-decisions.md または該当 test 記録へ残す。
- 失敗した library に依存しない T01/T02 や CLI/report の準備は継続できる。
  dependent task の代わりに未評価の library や違う runtime floor を選ばない。
- 新しい upstream が出ても、この計画の途中で latest へ乗り換えない。
  v0.4.0 追従を完了するか、別の追従計画へ明示的に切り替える。
- 不一致が Node 側の問題であれば file/責務/input を follow-up log に残す。
  Node の golden を Java に合わせて更新して pass にしない。
- 1 task の引継ぎには task ID、変更 file、通った focused command、
  残る case、次 task ID を書く。検証できていない runtime/OS はそのまま明記する。
- full parity 完了と言えるのは manifest の対象 case に unexplained pending がなく、
  正常・失敗・境界・packaged runtime の条件を満たしたとき。
  T15/T16 や特定 OS が保留なら、その範囲を残した部分移植として報告する。

## 7. 実装を開始する際の依頼文

~~~text
igapyon-miku-soft-developer を適用し、
docs/upstream-v0.4.0-gap-analysis.md と
docs/upstream-v0.4.0-followup-plan.md に従って実装してください。
T00 から順に進め、各 task の focused test と完了条件を満たしてください。
library は固定 candidate の gate を通った必要部分だけを fat JAR に同梱し、
Java 8 と upstream v0.4.0 の固定を維持してください。
gate が失敗したら依存しない作業を続け、失敗 fixture と判断が必要な点を残してください。
GitHub 公開操作は行わず、最後に完了 task、保留 task、確認結果を報告してください。
~~~
