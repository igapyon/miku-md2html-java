# Markdown生成表の直前空行をNodeに合わせる計画・実装記録

計画作成: 2026-11-17。実装: 2026-11-18。
状態: **L00〜L06完了。Focused Node v0.6.0 oracleは24 exact / 7 regression / 2 known-difference**。
実装時のJava artifactは`0.5.0`。その後、別途明示された版番号整合の決定により現在は`0.6.0`。
互換性基準はNode v0.4.0のままで、全面的なv0.6.0互換を示すものではない。

## 実装の範囲

対象は`miku-md2html-java`のMarkdown生成表の直前空行だけである。
実装はL00の状態記録から開始した。調査用`/tmp/`の結果をfixtureにせず、
追跡対象fixtureとcapture scriptからNode期待値を再生成できるようにした。
L01のcapture/比較器作成とL02のhelper実装は厳密な直列順ではなく並行して進めた。
最終確認は追跡fixtureだけで再現できる。

### 維持する条件と範囲

- L00〜L06の実装範囲ではJava artifact `0.5.0`と互換性基準Node v0.4.0を維持し、
  空行出力の比較対象だけをNode v0.6.0に固定した。その後の版番号整合でartifactは`0.6.0`となったが、
  互換性基準は変えていない。
- Java 8 source/binary/runtime、Maven、JUnit Jupiter、既存のfat JAR構成を維持する。
- 公開Core/CLI API、画像モード、依存ライブラリとその版、CIを変更しない。
- 空行がないリスト内の表認識、inline-code内pipe、目次境界の完全bytes一致、
  一般的な診断source位置の改善は、この修正に抱き合わせない。
- 既存の未コミット文書を引き継ぎ、ユーザーの作業を保持する。
  commit、push、tag、Release操作は別途の依頼があったときに行う。

## 目的と調査結果

当初「参照定義後の空行差」と呼んだ問題は、参照定義を除いても再現した。
対象はMarkdownから生成した表の直前のHTML整形用改行である。
元Markdownの空行をそのまま復元する処理は、この問題の解決には不要。

Node v0.6.0と当時のJava配布JARを18入力で比較した。
全入力で変換は成功し、diagnosticsは両方とも空だった。
`toc: false`、`imageMode: embed`で目次や画像モードによる差を除いた。

| 入力 | NodeとJavaの結果 |
| --- | --- |
| 段落 → Markdown表（参照定義なし／あり） | Nodeは表の前がLF 2個、Javaは1個。参照定義の有無で結果は変わらない |
| 表だけ／参照定義 → 表 | Nodeの記事先頭はLF 2個、Javaは1個 |
| 使用する参照リンク → 表／未定義の参照リンク → 表 | 同じ空行差。リンクの解決状態に依存しない |
| 引用内の表（段落あり／なし、参照定義あり） | 同じ空行差 |
| 空行で段落と表を区切ったリスト項目（参照定義なし／あり） | 同じ空行差 |
| 表 → 段落／複数の表 | 表の前で同じ差。表の後の段落との間隔は一致 |
| 段落 → 段落、段落 → 参照定義 → 段落 | 一致 |
| 見出し → 参照定義 → 段落 | 一致 |
| 段落 → 改行を含まないraw HTML表 | 一致。表の前はLF 1個 |
| 段落と表の間に空行がないリスト項目 | Nodeは表を作るがJavaは段落の一部として扱う。空行の整形とは別の解析差 |

この表は18入力を条件別にまとめたもの。全Markdown入力での一致は未確認。

調査に使った固定点:

- Node: `v0.6.0`、commit `4a0918713f8b94fffe8d447e809800a2e096cb84`。
  ローカルcheckoutのHEADとtagが一致し、作業ツリーはclean。
- Node runtime SHA-256:
  `05b9284a3f796aa08848ce3bb7e2740a70b29103a901070700e95c69d7128cdd`。
- Java: commit `d1bdb7bdea8d6bba3fa45e673dd4a59a6ff6a338`、artifact `0.5.0`。
  比較JAR SHA-256:
  `f24ebdc975965eed842fc6cc1412c39e674f39dbff4b67edd11c64ba2e0824cd`。

## 確認できた処理経路

Nodeの`remark-rehype`は生成表の`table`、`thead`、`tr`などの間にLFを作る。
`rehype-raw`実行後にはそのLFが表の前のテキストへ移っていた。
単純な2列1行の表では、段落と表の間にLF 14個が集まることを確認した。
最後に`normalizeStructuralWhitespace`が、ブロック間の連続LFを2個へ縮める。

Javaの`FlexmarkAdapter.normalizeGeneratedHtml`は、表内部のタグ間空白を
削除して表を詰める。その空白を表の前へ渡さないため、通常のブロック末尾の
LF 1個だけが残る。参照定義の位置情報を復元しても、この経路は変わらない。

追加fixtureでは、表の前がraw inline `<img>`の場合にNodeの最終整形がこの改行列を
縮めないことも確認した。2列1行表では14個、1列1行表では12個のLFが残る。
そのためinline境界では表の行・セル構造から改行数を導き、既存の境界改行へ足す。
段落などのblock境界では従来どおり最終出力をLF 2個に合わせる。

参照先:

- [Node converter](https://github.com/igapyon/miku-md2html/blob/v0.6.0/src/ts/core/converter.ts)
- [Node whitespace](https://github.com/igapyon/miku-md2html/blob/v0.6.0/src/ts/core/html-whitespace.ts)
- [Node document renderer](https://github.com/igapyon/miku-md2html/blob/v0.6.0/src/ts/core/html-document.ts)
- Java: `core/markdown/FlexmarkAdapter.java`、`core/HtmlStructuralWhitespace.java`。

## 変更対象

パスはrepository rootからの相対パス。新設名は以下で固定し、同じ役割の重複を作らない。

| 対象 | 変更する責務 |
| --- | --- |
| `src/main/java/jp/igapyon/mikumd2html/core/markdown/FlexmarkAdapter.java` | 生成表の登録、HTMLの受け渡し、既存sanitize後の間隔適用 |
| 新規 `src/main/java/jp/igapyon/mikumd2html/core/markdown/GeneratedTableWhitespace.java` | package-private helper。内部属性の登録と除去、生成表の追跡、直前の整形用空白の調整 |
| 新規 `src/test/java/jp/igapyon/mikumd2html/core/GeneratedTableWhitespaceParityTest.java` | 固定Node fixtureとの比較。JUnitだけで動く |
| 新規 `src/test/java/jp/igapyon/mikumd2html/core/markdown/GeneratedTableWhitespaceTest.java` | 内部属性の完全除去と座標不変を確認する境界テスト |
| 新規 `src/test/resources/parity/v0.6.0/table-whitespace/` | 入力・Node期待値・比較scope・hash・provenance |
| 新規 `scripts/capture-node-table-whitespace-parity.mjs` | maintainer用。固定Nodeからのみ期待値を取得する |
| `TODO.md`、本書、`docs/remaining-migration-items.md`、`docs/upstream-test-mapping.md`、`docs/upstream-class-mapping.md`、`docs/upstream-followup-log.md`、`docs/development.md` | 実装・確認した範囲と再実行コマンドの記録 |

`HtmlStructuralWhitespace.java`と`HtmlDocumentRenderer.java`は既存の最終整形を使う。
変更が必要になったら理由と影響するfixtureを先に記録し、目次や一般空白の
挙動へ修正範囲を広げない。画像source indexは今回のhelperへ統合しない。

## 固定する実装方針

所有箇所はproduct core内のMarkdownアダプターとHTML整形処理。
既存のFlexmark/jsoupを使い、Java 8の範囲で実装する。

### 1. 生成表を登録する

既存のattribute providerに`TableBlock`の分岐を追加する。
画像用の`Image`/`ImageRef`分岐を保持し、既存画像属性や診断を変えない。
helperは変換ごとに新規作成し、内部属性名には変換ごとの識別子を使う。
同一ASTノードの登録は同じIDを返し、登録したIDだけを有効とする。
固定名の`data-generated=true`や全`table`への適用で代用しない。
1つのIDは1つの生成表に対応させ、重複出現や必要な範囲の未追跡は検出する。
未知IDや、コード・コメント内に現れただけの文字列を生成表の根拠にしない。

### 2. 内部属性を取り除いてから診断用のHTMLをparseする

実装した処理順は次のとおり。`normalizeGeneratedHtml`による既存の表内圧縮は維持する。

```text
Flexmark AST → 内部属性付きHTML
 → 既存footnote変換・normalizeGeneratedHtml
 → normalize後の文字列をquote-aware tag scannerで走査
 → helperが登録済み内部属性だけを除去し、clean HTMLと生成table offsetを返す
 → clean HTMLをjsoupで位置追跡付きparse
 → offsetで生成tableのElementを識別
 → 既存sanitizeChildren・画像処理・診断収集
 → 保持した生成tableの直前空白を調整
 → 既存serialize・heading処理・document renderer・最終空行制限
```

jsoupで内部属性を検出するとsource rangeが属性挿入でずれるため、DOM parseの前に
quote-aware scannerで`<table>`開始タグと属性値を走査する。helperが登録した属性名とIDの
組だけを、属性前の区切り空白を含めて元文字列から除去する。未知IDは登録済みの根拠に
使わない。先行する除去区間の長さを差し引いてclean HTML内のtable開始offsetへ換算する。
テストでmarker除去後のHTML bytesとclean source rangeを検証する。

これにより、診断用のparseには新しい内部属性も空白補正も入らない。
helperの準備段階で返すclean HTMLは、属性なしで同じ処理をしたHTMLとbytes一致を
必須にする。実装時に「値をDOMから削除しただけ」で終えない。
DOMの属性を消しても追跡済みの元座標は元に戻らないためである。
表がない入力は追加parseを避ける。

### 3. 生成表のブロック境界だけに間隔を補う

- 直前の兄弟TextNodeのうち、空白・tab・LFだけの連続部分を扱う。
  root、引用、リスト項目などの構造コンテナーに限定する。
- 直前がblock要素または存在しない境界なら、LF 2個になるまで補う。
  直前がinline要素またはtextなら、Flexmark `TableBlock`配下のtable/section/row/cell数から
  Nodeが生成する構造LFを算出し、直前のLF列へ加える。既存の末尾indentは保持する。
  記事先頭はrendererの先頭LFも加わるが、最終空行制限後にLF 2個となることを検査する。
- block境界のLFが2個以上なら補わず、既存の最終空行制限へ渡す。
  inline境界はNode側も最終空行制限の対象外なので、生成表の構造LFを保持する。
  表の前が既に空行を持つ場合や、複数表で改行を累積させない。
- 本文テキストやinline要素に接する境界、raw HTML表、セルの内容、
  `pre`/`code`内部の改行や属性値には触れない。
- 生成tableのElementがsanitize後も対象DOMに残っていることを確かめてから適用する。

Nodeの最終出力を合わせる局所処理である。block境界では中間LF 14個等を作らず、
既存の最終制限を利用する。inline境界のみ、HAST構造の各子リストが生成するLF数を
Java側で算出して保持する。

## 固定fixtureと比較方式

L01で作成したfixture構成:

```text
src/test/resources/parity/v0.6.0/table-whitespace/
  cases.properties
  inputs/<case-id>.md
  expected/<case-id>.node.html
  expected/<case-id>.node.json
  expected/<case-id>.contract.properties
  expected/provenance.json
```

`cases.properties`にはcase ID、inputName、options、比較scopeを明記し、
JUnitは`Properties.load`で読む。JSON処理のための製品依存は追加しない。
ID一覧は`case.ids`、各設定は`case.T01.input`、`case.T01.inputName`、
`case.T01.scope`、`case.T01.toc`、`case.T01.imageMode`の形にする。
列挙順を明示し、scopeが未確定ならcapture前にエラーにする。
capture scriptは既存の`capture-node-v0.5.0-parity.mjs`のhash確認と
properties escape/flatten処理を参考にする。古いcapture scriptを変更して流用しない。

Node commit・runtime hash・export versionを確認してから固定順で期待値を出力する。
provenanceには取得元、入力・HTML・JSON・contractのhash、case ID、scopeを記録する。
JUnitでも入力・HTML・JSONのhashを検証できるように、対応するhashを各contractの
`oracle.*`メタデータにも保存する。比較器はこの接頭辞をNodeのresult fieldと区別する。
hash不一致やdirtyなupstreamでは期待値を書き始めずに終了する。
通常JUnitは追跡対象のfixtureだけを読み、Node実行・download・期待値更新を行わない。
Javaの結果をNode期待値へ書き戻す経路は作らない。

scopeは次の3種類に固定する。

| scope | 比較の条件 |
| --- | --- |
| `exact` | 原則`toc=false`、`imageMode=embed`。R01は`embed`と`reference`を別caseで比較する。HTML全bytes/hash、metadata、diagnostics全項目、statsを同じoptionsの固定Nodeと比較する。`htmlBytes`は実bytes長とも一致させる |
| `regression` | 既知のsource位置差や目次境界差がある混在入力。生成表境界と不変条件を比較し、比較対象外のフィールドと既存差をcaseごとに記録する。一括のsource除外やtrimは禁止 |
| `known-difference` | 今回直さない解析差。Node結果と差分理由を保存し、合格したexact件数へ加えない。新たな差があれば報告する |

最初のdiagnostic混在比較で差がなければ`exact`にする。
既存差があることを記録せず、失敗を避けるためにscopeを弱めない。
`exact`の出力ではHTMLをDOM化・trim・空白正規化してから比較しない。

### 必須ケース

基本の表Tは`| A | B |\n| --- | --- |\n| a | b |`、
参照定義Dは`[site]: https://example.org "Example"`とする。
通常のブロック間は`\n\n`でつなぎ、quoteは各行に`> `、
listは先頭`- Before`と表各行の2スペースindentで表現する。

| ID | 入力・狙い | scope |
| --- | --- | --- |
| T01 | Tのみ。記事先頭のLF 2個 | exact |
| T02 | `Before` → T | exact |
| T03 | `Before` → D → T。T02と同じ間隔 | exact |
| T04 | D → T。T01と同じ間隔 | exact |
| T05 | `[Link][site]` → D → T。使用する参照定義 | exact |
| T06 | `[Link][site]` → T。未定義の参照リンク | exact |
| T07 | quote内の`Before` → T | exact |
| T08 | quote内の`Before` → D → T | exact |
| T09 | quote内のTのみ | exact |
| T10 | 空行で区切ったlist項目の`Before` → T | exact |
| T11 | 同じlist項目の`Before` → D → T | exact |
| T12 | T → `After`。表の後を変えない | exact |
| T13 | T → T。複数の生成表 | exact |
| T14 | ヘッダーと区切りだけの表 | regression：jsoupが空の`tbody`を保持 |
| T15 | 1列の表と複数データ行 | exact |
| T16 | 左・中央・右の配置指定を持つ表。pipeなしのinline codeも含める | exact |
| C01 | `Before` → `After`。通常段落 | exact |
| C02 | `Before` → D → `After` | exact |
| C03 | `# Before` → D → `After` | exact |
| C04 | `Before` → `<table><tr><td>x</td></tr></table>` | exact |
| C05 | raw HTML表とTを同じ記事に置く | exact |
| C06 | fenced code内の表に見える文字列（languageはtext） | regression：Javaのlanguage class除去warningだけ差 |
| C07 | raw pre/code内の連続LFと表に見える文字列 | exact |
| C08 | 空入力 | exact |
| R01 | Tの前後にmissing Markdown画像。両image modeでsource start/endを確認 | exact |
| R02 | Tの前後とセル内にmissing raw img。表の14/12 LFと画像診断を確認 | regression：raw img source座標 |
| R03 | front matter・非BMP文字とTの後ろのunsafe raw link | regression：raw HTML source座標 |
| R04 | 内部属性に似た固定名を持つraw table、コード内の同名文字列 | regression：診断文言/source end |
| R05 | 先頭H1 → T、先頭H1 → 本文 → T。`toc=true`で目次の位置を維持 | regression：文書境界のLF 1個 |
| K01 | 空行なしのlist項目の`Before` → T | known-difference：表認識が異なる |
| K02 | 旧`commonmark-gfm`を入力として複製。未エスケープpipeを含む | known-difference：table tokenizationと目次境界等 |

L01でscopeを確定した。fixtureは24 exact、7 regression、2 known-difference。
R01は欠損画像resolverを明示して両image modeで診断を比較する。
regressionの無視項目は`cases.properties`のcase単位で列挙し、出力HTML全体を
無条件にtrim/normalizeして通す処理は設けていない。R04はhelper単体テストで
登録ID・未知ID・似たraw属性を確認し、乱数の偶然一致に依存しない。

## 実施順序と合格条件

| ID | 依存 | 実施内容 | 合格条件 |
| --- | --- | --- | --- |
| L00 | — | branch/HEAD/status、Java 17、package、56件suite、配布JAR、旧v0.4比較を記録 | **完了**。baselineは本文上部に記録 |
| L01 | L00 | capture script、fixture、JUnit比較器、scopeを固定 | **完了**。Node v0.6.0固定値で33ケース。修正前差は先行18入力調査で確認し、fixture後は実装と比較を反復 |
| L02 | L01 | helperで登録表を追跡し、markerを診断parse前に除去 | **完了**。byte保持・source offset・複数表・unknown ID・raw lookalikeを5 helper testsで確認 |
| L03 | L02 | sanitize後に構造境界別の改行を復元 | **完了**。24 exact caseでHTML全bytes、診断、statsが一致。inline境界はHAST相当の構造LF数を保持 |
| L04 | L03 | 対照・混在caseと反復変換を確認 | **完了**。反復変換のHTML/byte数一致、marker漏れなし。raw HTML座標の既知差はcase単位で明記 |
| L05 | L04 | package、JAR smoke、旧v0.4比較 | **完了**。package/smoke成功、旧比較3/5。残り2件のTOC差とGFM既知差を記録 |
| L06 | L05 | TODO・mapping・log・本書を実績へ更新しdiff確認 | **完了**。実装時のartifact `0.5.0`、baseline v0.4.0。後日、別決定で番号を`0.6.0`へ整合。Java 8/25実行は未実施 |

L00時点で以前の文書変更があることだけを理由に作業を止めない。
実装対象コードにも無関係な変更がある場合は、それを保持して重ならない作業を進める。
Node固定値の不一致は取得段階で止め、`../miku-md2html`をreset/build/installしない。
必要な固定checkout・bundleは独立した`workplace/`で準備し、取得経路を記録する。
helperの範囲追跡が失敗した場合、全tableへの補正や診断位置の除外で通さず、
最小の再現入力・失敗段階・残る作業を記録する。

### 再確認コマンド

固定fixtureの再取得・全package・確認に使う。

```sh
# packageと配布JAR確認
mvn -f pom.xml package
bash scripts/smoke-packaged-runtime.sh target/miku-md2html-java-0.6.0.jar 0.6.0
node scripts/compare-node-java.mjs target/miku-md2html-java-0.6.0.jar

# L01。指定した固定checkoutからだけ取得
node scripts/capture-node-table-whitespace-parity.mjs --upstream ../miku-md2html

# focused確認
mvn -f pom.xml -Dtest=GeneratedTableWhitespaceTest,GeneratedTableWhitespaceParityTest,HtmlOutputV060Test,UpstreamV050ImageParityTest test

# L06
git diff --check
git status --short
```

旧Node v0.4比較は差分監視用で、今回も3/5だった。
Node v0.6由来の空行補正で旧v0.4から差が増える場合は、その入力とbytes差を
記録し、v0.6の対応fixtureで正しいことを示す。
旧golden更新や比較条件の緩和で件数を合わせない。

### 実装後に返す内容

- 実装したL00〜L06と、固定Nodeに一致したcase ID・scope・件数。
- source位置、raw HTML、code、画像ケースを保持した確認結果。
- packageとJAR smoke、旧v0.4比較の結果と残差。
- 変更ファイル、実行できなかった確認、今回の対象外として残した差。
- Java 8/25で未実行ならその事実。Java 17の`--release 8`だけでruntime互換を断定しない。

## 規模・残る論点

局所修正と固定出力比較による小〜中規模の作業を見込む。
主なリスクは、生成表とraw HTML表の混同、整形用LFによる診断位置のずれ、
引用・リスト境界での空行過剰追加である。

空行がないリスト内での表認識差は別の解析課題として記録する。
GFM表のinline-code内pipe処理、目次境界のbytes、Node v0.6全体の互換確認も
独立した課題として残す。Java artifact versionは`0.6.0`だが、既存の互換性基準Node v0.4.0は維持する。

この作業は空行差への対応計画に限定し、追加の共通保守改善は範囲拡大を避けるため省略した。
