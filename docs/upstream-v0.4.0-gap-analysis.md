# Node v0.4.0 と Java 版の差分調査

調査日: 2026-10-24。これは調査結果であり、実装済みの宣言ではない。
実行順と完了条件は [追従計画](upstream-v0.4.0-followup-plan.md) に定める。
互換性表の Java 欄は実装開始前に固定した baseline である。実装後の進捗は
追従計画の task table と [remaining items](remaining-migration-items.md) を参照する。

## 調査対象と確度

| 対象 | 固定した状態 |
| --- | --- |
| Node upstream | https://github.com/igapyon/miku-md2html |
| 今回の追従先 | v0.4.0 / b18a6e390425bd0e15387a2d608980a42a1809e9 |
| Java の調査対象 HEAD | b50e029b7d430b009bdc6c156863607528649665 |
| Java が現在記録している基準 | v0.3.5 / 065c828dc6e987a39bdd7a4a15ea48a97ca2eccd |
| 現地参照 | ../miku-md2html。調査時 HEAD は v0.4.0 の commit と一致し、worktree は clean |
| 実行比較 | Node の既存 bundle 0.4.0 と Java の既存 JAR 0.3.5 |

Java の既存 sources JAR に含まれる全 Java ソースと現在の src/main/java を比較し、
不一致がないことを確認してから実行比較した。調査中に製品ソース、POM、依存、
製品テストを変更せず、ビルドも実行していない。
19 個の代表入力を既存 CLI に与えた。試行用ファイルは
/private/tmp/miku-md2html-parity-audit-20261024 に置いたが、恒久的な前提にしない。
以下の入力・結果と固定 upstream が再現の根拠になる。

比較に使った artifact の SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| Node CLI 0.4.0 | 8f3d464d9fc25ff9790568003b8e3b287c77860caeb8e56ef870106095579331 |
| Node import runtime 0.4.0 | 4c5aaeac024b5c31c003e2df40d66d8e8d2e5ad4a4fbe4c2222619c83290f16c |
| Java CLI JAR 0.3.5 | 7d84f84d74518ceae61a246aabe9c941657e63a69fcb7785c771cf0c79656562 |

Node の両 bundle の SHA-256 は GitHub Release asset の公開 digest と一致した。

「実行確認」は今回の代表入力で差を確認したもの。「ソース確認」は両実装と
upstream テストから不足を確認したもの。全入力・全 OS の動作確認ではない。

## v0.3.5 から v0.4.0 で変わった範囲

[タグ間差分](https://github.com/igapyon/miku-md2html/compare/v0.3.5...v0.4.0) は
20 files、530 insertions、92 deletions。

- image-size を外し、assets/raster-dimensions.ts を追加。PNG/CgBI、JPEG/DNL、
  GIF、WebP の寸法読み取りを製品内に持った。対応する専用テストを追加。
- lowlight を外し、highlight.js の scope event から HAST を作る
  core/highlight-hast-emitter.ts を追加。言語 grammar は highlight.js 11.11.1。
- 直接利用していた unist-util-visit を core/tree.ts の反復巡回へ置換。
  変更対象は見出し、画像埋め込み後の再結合、参照収集、コードブロック収集。
  すべての再帰処理を廃止したわけではない。
- 直接の production dependency は 14 から 11。削除された直接依存が
  transitive dependency として残る場合もある。
- README、依存の説明、仕様、CLI failure fixture を更新。

構文ハイライト、脚注、完全な Markdown/YAML 解析などは v0.4.0 で初めて
追加された機能ではない。Java の初期移植に残っていた不足も今回の対象に含めた。

## 機能と互換性の不足一覧

Node のパスは src/ts 以下、Java のクラスは
src/main/java/jp/igapyon/mikumd2html 以下を表す。

| ID | Node で実装済みの振る舞い | Java の現在と根拠 | 実装方針・確度 |
| --- | --- | --- | --- |
| G01 | 空文字列を正常な Markdown として変換 | core/MarkdownConverter が source.charAt(0) を呼び、空入力は exit 3 | JDK だけで修正可能。実行確認 |
| G02 | CommonMark の Setext 見出し、インデントコード、改行、参照リンク、複雑な delimiter/escape/entity、入れ子・loose list、ordered list start | MarkdownParser / InlineMarkdownParser は行単位の subset。Setext、hard break、参照リンク、入れ子、start の欠落を実行確認 | Markdown parser の小規模ライブラリ採用を検証。単純な正規表現の追加で全対応とはしない |
| G03 | GFM bare autolink、表の正確な delimiter/escape、task list、脚注と backref/ID | 表・task list は一部のみ。脚注は本文のまま残る | G02 と同じ parser 系列の必要 extension だけ採用。実行・ソース確認 |
| G04 | YAML 1.2、block/flow collection、block scalar、quote/comment、unknown key の構造、duplicate key、alias 制限 | FrontMatterParser は scalar subset。block title と unknown nested map は exit 1。空 title は fallback せず空文字になる | YAML 1.2 parser を同梱する候補。実行確認 |
| G05 | parsed text から heading ID/TOC を作り、ハイフンを畳み、脚注を含む fragment を照合 | Slug は連続ハイフンを保持。heading text は regex 抽出。fragment 診断位置は概ね body 先頭 | slug は JDK で修正。他は parsed tree と source range に移す。連続ハイフンは実行確認 |
| G06 | 許可された raw HTML の構造・属性を維持し、危険な subtree/属性を除去 | ArticleSanitizer はほぼ全 raw tag を unwrap。details/summary/strong を失う | HTML5 parser と製品固有 allowlist を使用。実行確認 |
| G07 | inline SVG を検査・serialize し、data URL の img として数える | Java は raw HTML として除去し、画像にならない | G06 の tree 処理と JDK XML/SVG 検査を接続。実行確認 |
| G08 | SVG style 要素・属性・paint resource の CSS を構文解析して外部資源、animation、未解析部分を拒否 | ImageInspector の style 要素本文検査は属性ループ内にあり、属性のない style の @import を見逃す。CSS は regex 判定 | 優先度高。CSS parser を検証して置換。Node exit 1、Java exit 0 を実行確認 |
| G09 | SVG の全要素 namespace、PI、属性名、nested raster の本体、寸法、inline serialization を検査 | root 以外の namespace、PI、nested raster の実体検査が不足。SVG 属性・寸法・serialize にも差 | XML は JDK を継続。全規則と error code を移植。ソース確認 |
| G10 | CgBI IHDR、JPEG DNL、WebP chunk 巡回・padding・version、必須の正の raster 寸法 | ImageInspector は通常 PNG 固定 offset、JPEG SOF 即 return、最初の WebP chunk のみ。0×0 を許可 | 新 RasterDimensions へ upstream を直接移植。DNL/0×0 と CgBI の警告欠落を実行確認 |
| G11 | remote image は E_REMOTE_IMAGE、その他の URI error と区別 | remote image も E_IMAGE_URI | JDK だけで URI 分類を揃える。実行確認 |
| G12 | local describe を path ごとに cache、id の size 一貫性確認、read は id ごと、warning は asset ごと一度。resolver 契約違反は内部エラーとして扱う | describe は出現ごと。read cache はあるが、同 id の異なる size の検査と warning 抑制がない。想定外例外も広く E_IMAGE_INVALID にする | JDK で per-conversion state と例外境界を移植。ソース確認 |
| G13 | HTML skeleton + 全画像出現分の Base64 推定で cap 判定後に local bytes を read | Java は画像を直ちに read/encode し、完成 HTML のサイズを最後に検査 | prepare / estimate / embed の三段階を移植。ソース確認 |
| G14 | 全 local 画像参照の scan 完了状態、missing path、失敗途中でも output/report と画像の衝突を防止 | Java は describe された path のみ保持。例外経路の failure report に scan 完了 gate がない | 明示的な参照 tracker と CLI gate を追加。race は今回未注入。ソース確認 |
| G15 | describe/read 前後の identity、mtime/ctime、canonical path、IO error 分類 | Java は size、fileKey、ミリ秒 mtime のみ。IO 例外を広く E_IMAGE_NOT_FOUND にする | JDK の精度を保った属性比較と分類。OS に存在しない属性は差を記録。ソース確認 |
| G16 | HTML と report の両方を stage してから commit。report stage failure で HTML を公開しない | Java は HTML を公開後に report を stage。途中状態の扱いが異なる | Node と同じ publication state と IO seam を移植。ソース確認 |
| G17 | missing/invalid UTF-8 input でも安全を確認して failure report。internal failure は exit 3 を保持 | missing input は report target 準備前に失敗。report commit の内部例外も exit 1 になる経路 | CLI の順序と error 境界を移植。missing input は実行確認、注入失敗はソース確認 |
| G18 | JSON metadata.description、source.end、details、optional field の省略、整形、安定した診断順 | report が description/details/end を欠く。Diagnostic 自体にも end がない。未確定 bytes を 0 で埋める | fixed report writer を JDK で実装し、golden の bytes も比較。description は実行確認 |
| G19 | 13 言語 family の highlight、256 KiB/block・1 MiB/document の予算、原文検証、失敗 fallback、警告 | language class のみ。token span、予算、highlight 用診断がない。末尾改行も異なる | 条件付き。固定 highlight.js と Java 8 対応 JS engine の組合せを先に検証 |
| G20 | HAST の順序・空白・escape、CSP、TOC、画像、highlight を含む再現可能な全 HTML bytes | Java の手組み HTML に記事末尾改行、TOC、属性順などの差。見た目の同等だけでは bytes は合わない | 製品内 serializer と oracle fixtures。大規模汎用 HTML writer の新規自作はしない |
| G21 | 10 個の upstream test file による通常・境界・failure injection・contract golden | Java は 3 test class、初期 8 test。多くの failure/security/budget/contract case が未移植 | 各作業で該当テスト意図を移植。未テストを未実装と混同しない |

既存の help は Java の jar 呼出例を持つ意図的な差として記録済みである。
字句を Node の実行例へ書き換えて形式的な byte equality を作らない。
共有オプション・default・exit code と、差を許す行を検査する計画にする。

## 代表入力による再現結果

表の code は report の diagnostic code。成功 HTML の空白差は別の G20 として扱う。

| 入力 ID / 最小の内容 | Node v0.4.0 | Java 現行 |
| --- | --- | --- |
| empty / 0 byte の .md | exit 0、filename 由来 title | exit 3、E_INTERNAL、report なし |
| markdown / Setext、2-space hard break、bare URL、3. で始まる入れ子 list | 見出し・br・リンク・ol start・入れ子を生成 | いずれも構造・metadata に差 |
| reference / [guide][ref] と参照定義 | title 付き a を生成 | 参照記法と定義が本文に残る |
| footnotes / Text[^a] と [^a]: Note | sup/backref/脚注見出しを生成 | 脚注記法が本文に残る |
| yaml-block / title に literal block scalar | title は Hello World | E_FRONT_MATTER |
| yaml-unknown-map / other の下に nested: true | unknown map を読み飛ばして title を使用 | E_FRONT_MATTER |
| yaml-empty-title / title が空文字で本文に H1 | H1 へ fallback | title が空文字 |
| raw-html / details、summary、p、strong | 許可された構造を保持 | タグを除去し文字列を連結 |
| inline-svg / namespace 付き svg と rect | data URL の img を生成 | W_HTML_SANITIZED、空 paragraph |
| svg-external-css / SVG 内の属性なし style に @import url(https://example.invalid/x.css) | exit 1、E_SVG_EXTERNAL_RESOURCE | exit 0、警告なしで埋め込む |
| remote-image / https の Markdown 画像 | E_REMOTE_IMAGE | E_IMAGE_URI |
| metadata-report / description 付き front matter | report metadata に description あり | HTML の meta にはあるが report から欠落 |
| cgbi / CgBI を先頭 chunk に持つ 128×68 PNG | 正常 | 正常終了するがソース上は誤った寸法 offset を参照 |
| jpeg-dnl / SOF height=0、scan 後の DNL で height=24 | 正常 | E_IMAGE_INVALID |
| slug / a--b と a b の H1 | 同じ slug に suffix を付加 | 別 slug |
| code / java fence の public class Demo {} | hljs token と末尾改行 | plain code、末尾改行なし |
| large-cgbi / CgBI PNG 10000×5000 | W_LARGE_IMAGE_DIMENSIONS、details に寸法 | 警告なし |
| zero-png / IHDR 0×0、IEND あり | E_IMAGE_INVALID | exit 0 |
| missing-input / 存在しない .md と衝突しない report path | exit 1、E_INPUT_READ、failure report あり | 調査時は report なし。今回の実装で Node 同様に failure report を出す |

CgBI/JPEG fixture は upstream tests/raster-dimensions.test.ts の metadata-only
fixture と同じ構成方法を用いた。実際の画像表示やデコード品質を調べた試験ではない。
SVG は CLI が出力した bytes を調べており、外部 URL をブラウザで読み込んでいない。

## 実装可能性の判断

- G01、G05 の slug、G10～G18 は、主に upstream の自前処理を JDK に移す範囲。
  具体的な順序と試験を決めれば実装可能と判断する。
- G02～G04、G06～G09 は実装可能だが、汎用 parser を一から育てるより
  必要部分の library と製品固有 adapter を使う方が妥当。
- G19 は API、予算、fallback は実装可能。13 grammar の token 互換性は
  Java 8 対応 engine での再利用検証を通るまで確約しない。
- G20 の byte parity は serializer を製品側で固定して検査する。
  他 library の default HTML 出力が upstream と同じだとは仮定しない。
- filesystem race、全 YAML/Markdown 入力、全 OS、library の実 runtime
  互換性は今回の代表入力だけでは検証していない。計画の gate に残す。

## 一次資料

- [upstream release](https://github.com/igapyon/miku-md2html/releases/tag/v0.4.0)
- [固定 source tree](https://github.com/igapyon/miku-md2html/tree/b18a6e390425bd0e15387a2d608980a42a1809e9/src/ts)
- [固定 test tree](https://github.com/igapyon/miku-md2html/tree/b18a6e390425bd0e15387a2d608980a42a1809e9/tests)
- [runtime dependency の説明](https://github.com/igapyon/miku-md2html/blob/v0.4.0/docs/runtime-dependencies.md)
- [現在の Java 移植差分の記録](remaining-migration-items.md)
