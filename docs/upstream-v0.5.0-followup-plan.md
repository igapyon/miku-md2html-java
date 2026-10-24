# Node v0.5.0 追従計画（Luna実行用）

作成日: 2026-10-24。作成時点の状態: **計画のみ。P00〜P10は未着手**。
実装作業はこの文書の順番で行う。
計画作成時には本書とTODOへのリンクだけを追加し、製品、テスト、script、
workflow、依存、バージョン、互換性基準は変更していなかった。

## 1. 固定する入力と到達点

| 項目 | 固定値 |
| --- | --- |
| Node upstream | <https://github.com/igapyon/miku-md2html> |
| 今回の追従先 | `v0.5.0` |
| Node commit | `c397569016383c622aae4b187234e56f08a753fd` |
| Release | <https://github.com/igapyon/miku-md2html/releases/tag/v0.5.0>。2026-10-24公開の正式Release |
| Node runtime | `bundle/miku-md2html-runtime.mjs` / Release asset `miku-md2html-runtime-0.5.0.mjs` |
| runtime SHA-256 | `adb7dbad356ea6b78be15a4a6c1a1a7a53532d2cb52b272945becb092fc8d436` |
| Node CLI | `bundle/miku-md2html.mjs` / Release asset `miku-md2html-0.5.0.mjs` |
| CLI SHA-256 | `c5af81eccba1ecddc348533e40517a2dbcfa92af552b6d7a02616421fcbc9b78` |
| 調査に使ったNode checkout | `../miku-md2html`。上記commitと一致、tracked変更なし。両bundleのhashも上記と一致 |
| 調査時Java HEAD | `6f36609f755cd85e1abab215d1ba875600730865` |
| Java artifact / 現在の互換性基準 | `0.5.0` / Node `v0.4.0`、`b18a6e390425bd0e15387a2d608980a42a1809e9` |
| Java公開入口 | `jp.igapyon.mikumd2html.core.MikuMd2htmlCore.convertMarkdown` |
| CLI | `jp.igapyon.mikumd2html.cli.MikuMd2htmlCli` |
| 維持する配布条件 | Java 8 source/binary/runtime、Maven、JUnit Jupiter、単一fat JAR、sources JAR |

目標は、v0.5.0で変わった画像モード、画像診断、読み込み制限、画像統計、
CSP、出力保護をJavaのCoreとCLIで再現すること。
下記の合格条件を満たした後に、互換性の参照先をv0.5.0へ進める。
**参照先の更新は完全互換の宣言ではない。**

Java artifactの版はNode版とは独立しており、現在は`0.5.0`。
この版番号はNode `v0.5.0`相当の実装完了を示すものではない。互換性基準はNode
`v0.4.0`のままとし、本計画の合格条件とGitHub Actions workflowの検証が完了した後に
`v0.5.0`へ更新する。Node版をJava版へそのまま転記しない。

## 2. 調査結果と今回の範囲

[v0.4.0→v0.5.0](https://github.com/igapyon/miku-md2html/compare/v0.4.0...v0.5.0)
は2 commits、32 files、1589 additions / 233 deletions。
Release本文は版番号だけなので、固定commitの実装・API/CLI文書・テストを仕様の根拠にする。
Nodeの`docs/image-reference-plan.md`も参考にするが、計画中の案と実装が異なる箇所は
固定commitのコードとテストを優先する。

| 変更領域 | Node v0.5.0 | 現在のJava | この計画の作業 |
| --- | --- | --- | --- |
| 画像モード | Core/CLIの既定が`reference`、`embed`を選択可能 | モードなし、常に埋め込み | API/CLI全経路へ追加 |
| ローカルラスター | 元ファイルをHTML出力位置からの相対URLで参照、画像をコピーしない | 読み込み直後にBase64化 | URL生成と参照経路を追加 |
| SVG/data URL | 両モードで検証・埋め込み。拡張子より内容を優先 | 検証・埋め込みはある | モードを越えて同じ検証へ接続 |
| resolver | optional `reference(decodedRelativePath)` / `readPrefix(id,maxBytes)` | `describe` / `read`だけ | Javaの既存resolverとの互換性を保って追加 |
| file問題 | 限定したE系をW系へ変換し、warningだけなら成功 | `AssetAccessException`を原則error化 | 両モードに限定した診断変換を移植 |
| 巨大ラスターの参照 | 256-byte prefix、100 MiBの全体検査境界 | 常に出力cap以内の全体読み込み | 出力capと独立した検査経路を追加 |
| embedの処理順 | prepare→見積り→read/embed→最終cap | read/encode→完成HTMLでcap | 今回必要な範囲で旧T11を進める |
| report / stats | schema `2`、選択mode、embedded/referenced/omitted | schema `2`と欄はあるがmodeは`embed`固定、referencedは0 | 既存欄の値と集計時点を合わせる |
| CSP | reference: `img-src data: file: 'self'`、embed: `img-src data:` | `img-src data:`固定 | modeをrendererまで渡す |
| 出力保護 | 全参照候補とscan完了を追跡、欠落画像も衝突対象 | describeで遭遇した候補だけ。scan gateなし | 旧T12/T13の関連部分を必須化 |

既存の[v0.4.0計画](upstream-v0.4.0-followup-plan.md)のT11/T12/T13/T14/T17/T18は
今回と重なる部分だけを本計画で進め、両方の進捗を更新する。
T06の完全なsource/generated provenance tree、GFMの既知差分、脚注全件、
構文ハイライト/Rhino、全upstream corpusは既存backlogに残す。
全T00〜T18の完了を今回の着手条件にしない。

## 3. 先に固定する実装判断

### 3.1 Core/CLIとoptional resolver

- `ConvertOptions`へ`String imageMode = "reference"`とgetter/setterを追加する。
  既存bean形式に合わせ、`reference`と`embed`の完全一致だけを受け付ける。
  明示的なnull、空文字、大文字、前後空白はCoreの`E_OPTIONS`とする。
  options自体の省略は従来どおりdefault objectを使う。
- CLIへ`--image-mode <mode>`を追加し、空白形式と`--image-mode=embed`を受理する。
  不正値・重複・値欠落はexit 2。`CliOptions`からCore/reportへ同じ値を渡す。
- 公開`MikuMd2htmlCore.convertMarkdown`の呼出形は維持する。
  `AssetResolver`へJava 8のdefault methodとして
  `String reference(String decodedRelativePath)`を追加する。
  default bodyは`E_ASSET_REFERENCE_REQUIRED`を投げる。メッセージはNode実装に合わせる。
- optional prefixは新しい`core.PrefixAssetResolver extends AssetResolver`の
  `byte[] readPrefix(String id, long maxBytes)`で表す。
  sessionは`instanceof PrefixAssetResolver`で有無を判定する。
  nullや空配列を「未実装」の印にしない。不正な戻り値と未実装を区別するためである。
- `LocalAssetResolver`は`PrefixAssetResolver`を実装する。
  既存の`create(markdownFile, assetRoot)`を残し、出力先を渡す3引数版を追加する。
  2引数版もembedでは使え、referenceを求めた時だけ`E_IMAGE_REFERENCE`となる。
- 既存のdescribe/readだけのresolverは、明示的`embed`で引き続き利用できる。
  reference未実装だけを理由にSVG/data URLの変換を拒否しない。
  Coreに`Path`、ファイルI/O、stdout/stderr、jsoupの公開型を追加しない。

### 3.2 相対URLとfilesystem

新規`adapters.LocalAssetPaths`を`src/ts/adapters/node-paths.ts`に対応させる。
`canonicalPotentialPath`、`isWithin`、`relativeImageHref`を集約し、CLIとresolverで共有する。

1. 画像の入力pathは既存URI分類で**一度だけ**percent-decodeし、Markdown親から解決する。
2. 欠落pathは既存の最深祖先をrealpath化し、未存在suffixを戻してcanonical候補を作る。
   missing/not-directoryだけをこのfallbackにする。アクセス拒否やsymlink loopを欠落扱いしない。
3. root内かの判定はNode同様、canonical候補で確認する。
   lexical候補は衝突検査用にも保存する。root外のbytesを読まない。
4. 出力先もNode `create`同様にcanonicalPotentialPathで解決する。
   URLはそのHTML親から**画像のlexical候補**への相対pathを使う。
   resolver IDやroot相対labelをURLに流用しない。root内symlinkの参照名も保持する。
5. 各segmentをNodeの`encodeURIComponent`と同じUTF-8規則でencodeし、`/`で結合する。
   `A-Z a-z 0-9 - _ . ! ~ * ' ( )`をそのまま残し、他は大文字hexのpercent encoding。
   `..`を維持し、親参照で始まらない場合は`./`を付ける。
   `URLEncoder`は空白の`+`化などが異なるため使わない。
6. Windowsの異なるdrive/providerなどでrelativizeできない場合、空の相対path、
   不正surrogateをencodeできない場合は`E_IMAGE_REFERENCE`。
   画像をコピー・再圧縮せず、画像用directoryも生成しない。
7. Coreも戻りhrefの型、`./`/`../`開始、raw control character、既存local URI分類を検査する。
   callbackが不正hrefを返した場合は契約違反として例外を送出する。
   callback自身が想定した`E_IMAGE_REFERENCE`を投げた場合だけW系へ変換する。

`readPrefix`は要求が正の整数であることを検査し、
**ちょうどmin(maxBytes, describe時のサイズ)**をbounded readする。
Coreの要求は256 bytes。short readを正常扱いしない。
read/readPrefixの前後でcanonical path、regular-file、size、fileKey、精度を落とさない
FileTimeを照合する。取得できるfilesystemでは`unix:ctime`もsnapshotへ含める。
サポートされない属性は明示的に記録し、基本属性の比較まで省略しない。
resolverではI/O由来のE系を保ち、W系への変換はCoreに置く。

### 3.3 画像処理順と既存HTMLへの接続

`ImageEmbeddingSession`の名前を維持し、Nodeの同名責務へ寄せる。
今回、汎用article treeや新しいHTML parserは作らず、既存jsoupを再利用する。

- sessionに`prepare`、`pendingSizeDelta`、`embedPending`、stats snapshotを持たせる。
  describe/referenceはdecoded pathごと、full readはIDごとのcacheとし、失敗結果もcacheする。
  同じIDで異なるbyteLength、null metadata、空ID/null label、不正read/prefix結果は
  Coreの契約違反として送出し、CLIではexit 3にする。
  byteLengthは`0..9007199254740991`に限定し、Javaのlongへ入るだけでは受理しない。
- `FlexmarkAdapter.replaceImage`はaltだけのtextへ置換する方式を改め、
  元のimgの検証済みalt/title/width/heightを保持してsrcを設定・除去する。
  `src=""`、入力の未検査src、pending markerを最終HTMLへ残さない。
- embedのローカル画像はdescribeまででpendingにし、一意な内部markerをsrcへ設定する。
  Nodeと同じ下限見積り（受理MIMEの最短data prefix + `4*ceil(byteLength/3)`）を、
  **全出現分**加算する。ID重複で読み込みは1回でもpayloadは出現ごとに数える。
- 新規の内部helper `core.markdown.PendingImageBindings`で、fragment rewrite後のarticleから
  登録済みmarkerを持つimgだけをjsoupのtracked opening-tag rangeへ結び付ける。
  embed後はその開始tagだけを後ろのoffsetから置換し、他のarticle bytesを保つ。
  全文の`String.replace`、入力text/code中のmarker置換、全文の再sanitizationは行わない。
  markerの重複/欠落など内部整合性の破綻は例外とする。
  Nodeの`rebindPendingNodes`に対応する、Javaの既存文字列pipeline用adapterである。
- Converterはheading/fragment診断を一度だけ確定し、mode別CSPを含むskeletonをrenderする。
  skeleton UTF-8 bytes + pendingSizeDeltaがcap超過ならread/embed前に`E_OUTPUT_TOO_LARGE`。
  続いてembedPending、最終render、最終bytes判定を行う。等しいcapは成功、超過だけ失敗。
- referenceモードではNode実装どおり、SVG候補と100 MiB以下の検査をprepare中に行う。
  SVGをすべてembed用pendingへ回す別の処理順に変更しない。
  入力data URLとinline SVGも両モードでその場で検証・埋め込みする。
- `InlineMarkdownParser`など残る内部call siteをコンパイル・動作上確認する。
  公開Coreから使われるFlexmark経路と異なる画像規則を二重に実装しない。

### 3.4 画像診断のsource位置

新しい画像warningのsourceを、現在の「render済みHTML上の位置」から作ると、
Markdownの`![...]`に対するcolumn/endがNodeとずれる。
新規`core.markdown.ImageSourceIndex`を画像に限定した内部adapterとして追加する。

1. Flexmarkの`Image`/`ImageRef` nodeの原文start/end offsetを登録する。
   `HtmlRenderer.Builder.attributeProviderFactory`と`AttributeProvider.setAttributes`で
   画像出力に内部source IDを付け、DOMのimgと対応付ける。
   これらのAPIは採用済みFlexmark 0.62.2のローカルclassで存在を確認済み。
2. raw HTMLのimgも、該当HtmlInline/HtmlBlock nodeの原文とjsoupのtracked tag rangeから
   start/endを取得する。raw fragment出力では必要なimg開始tagへの内部ID挿入だけを行い、
   他のraw markupを再serializeしない。画像以外の全source mapは今回作らない。
3. ID用属性名は変換ごとの128-bit乱数をhex化した識別値と登録済みID集合で確認する。
   入力の同名風属性を信頼しない。内部属性は製品sanitizerの通常属性診断より前に消費・除去し、
   最終HTMLやwarningへ出さない。入力由来の未登録属性は従来のallowlistで扱う。
4. normalized Markdown本文のoffsetを1-based line/UTF-16 columnへ変換し、
   front matter lineOffsetを一度加える。source labelはNodeと同じ`markdown`。
   assetのlabel/絶対pathをdiagnostic sourceの代わりに使わない。
5. sessionへstartとendの両方を渡し、重複warningは最初の画像位置を保持する。
   同じsrcの複数箇所、raw img、front matter、非BMP文字、参照形式画像で検査する。

inline SVGを含むすべての壊れたraw HTMLのprovenanceは旧T06の課題に残す。
新しい厳密oracleには、sourceの扱いが未定義な壊れたraw HTMLを採用しない。
inline SVGは両modeで埋め込む既存機能の回帰試験を行う。
Node XMLSerializerと現在のJava serializerの全bytes互換性を今回新たに約束せず、
厳密oracleのSVGはまずMarkdown画像経由の元bytes埋め込みを使う。
inline SVGのserializer/provenance差は旧backlogへ明示する。

### 3.5 referenceモードの読み込み境界

定数はNodeと同じbytes値で固定する。
`prefix=256`、`inspectionLimit=104857600`、`largeImage=5242880`、
`largePixels=40000000`。100 MiBの検査境界はHTML出力capと独立している。

1. describe失敗は第4節へ。read可能ならprefixでSVG/raster/unknownを判別する。
   `ImageInspector.sniffImageKind`を追加し、全体inspectとmagic/SVG判定を共有する。
   SVGの候補判定は先頭256 bytesのNode規則に合わせ、BOM/空白/XML/comment/DOCTYPEも扱う。
2. prefix readerがなければ、サイズが100 MiB以下の時だけreadをfallbackに使う。
   そのbytesをcacheして、全体検査で二重readしない。
3. prefix readerなし、100 MiB超、`.svg`以外は
   `W_IMAGE_INSPECTION_SKIPPED`でsrc省略。拡張子が`.png`という理由だけで参照を残さない。
4. 100 MiB超でもprefixで既知rasterなら全体readをせず、
   `W_LARGE_IMAGE`と`W_IMAGE_INSPECTION_SKIPPED`、安全な参照srcを出す。
   unknownで`.svg`でもない場合はinspection warningとsrc省略。
5. その他のraster/unknownは100 MiBをcapに全体inspectする。**100 MiBちょうども検査する。**
   reference検査のreadだけが`E_OUTPUT_TOO_LARGE`を通知した場合はinspection warningへ変換し、
   prefixでrasterと分かった時だけ参照を残す。追加readはしない。
6. SVG候補（内容判定または`.svg` hint）はHTML出力capで全体read・既存SVG検査をする。
   prefix readerなしで100 MiB超の`.svg`も、このcapでreadを試みる。
   SVG/embed/最終HTMLの`E_OUTPUT_TOO_LARGE`はerrorのままにする。
7. 内容がSVGなら`.png`名でもSVGとして検証・埋め込みする。
   `.svg`名でraster magicの場合も、最終inspectの内容に従う。
   SVGの安全性をprefixだけで判断しない。

## 4. 警告・エラー・srcの確定表

この変換は**ローカルfile処理の限定箇所**だけへ適用し、data URLや一般の例外mapを
一括warning化しない。各メッセージ・details・並び順は固定Node実装から写す。

| 状況 | 診断 | referenceのsrc | embedのsrc |
| --- | --- | --- | --- |
| 有効なローカルraster | 必要に応じlarge warning | encoded相対URL | 元bytesのdata URL |
| 検証済みSVG / data URL / inline SVG | 既存診断 | data URL | data URL |
| `E_IMAGE_NOT_FOUND` | `W_IMAGE_NOT_FOUND` | 安全な非SVG候補なら参照、`.svg`は省略 | 省略 |
| `E_IMAGE_READ` | `W_IMAGE_READ` | 非SVG候補で安全なら参照、SVG候補は省略 | 省略 |
| ローカル`E_IMAGE_FORMAT` | `W_IMAGE_FORMAT` | Nodeのkind/svgHint分岐に従い安全な非SVG候補を参照 | 省略 |
| ローカル`E_IMAGE_INVALID` | `W_IMAGE_INVALID` | 同上 | 省略 |
| `E_ASSET_OUTSIDE_ROOT` | `W_ASSET_OUTSIDE_ROOT` | 省略、root外readなし | 省略、root外readなし |
| `E_ASSET_CHANGED` | `W_ASSET_CHANGED` | Nodeの失敗段階・kind分岐で参照を再確認できる時だけ保持 | 省略 |
| callbackの`E_IMAGE_REFERENCE` | `W_IMAGE_REFERENCE` | 省略 | callbackを呼ばない |
| reference全体検査の容量skip | `W_IMAGE_INSPECTION_SKIPPED` | 第3.5節の判断 | このskip方式は使わない |
| `E_SVG_*`（XML/CSS/外部resource/非対応構造） | errorを維持 | 成功HTMLなし | 成功HTMLなし |
| remote / 不正入力URI / 不正data URL | `E_REMOTE_IMAGE` / `E_IMAGE_URI`等 | error | error |
| resolverがない | `E_ASSET_RESOLVER_REQUIRED` | error | error |
| reference実装がない | `E_ASSET_REFERENCE_REQUIRED` | raster参照が必要になった時error | 不要 |
| 不正metadata・href・prefix・想定外例外 | Coreは例外、CLI `E_INTERNAL` / exit 3 | 成功HTMLなし | 同左 |
| HTML/report衝突・書込み失敗 | 既存E系 | error | error |

src省略時も`<img alt="...">`と許可された属性を保つ。altだけのtextにはしない。
warningだけの結果はsuccess=true、CLI exit 0、summaryあり、report success=true。
同一変換内の同じasset/path・同じ理由のwarningはNodeと同じkeyで1回にし、最初のsourceを使う。
絶対内部path、画像bytes、Base64、stackを新しい画像warningへ含めない。

metadata不整合と実ファイル変更を混同しない。
同じIDを異なるサイズでdescribeした契約違反は例外だが、
read結果のサイズがdescribe値から変わったケースはNodeの`W_ASSET_CHANGED`経路にする。
100 MiB検査capを超えた戻り値はNodeと同じcap error経路、
prefixの長さ不一致/nullは契約違反経路、と区別する。

## 5. 全参照の記録と出力保護

新規`core.ImageReferenceTracker` interfaceを内部adapter契約として用意し、
`recordReference(decodedRelativePath)`と`completeReferenceScan()`を定義する。
Coreは具体的な`LocalAssetResolver`に依存せず、interfaceがある場合だけ通知する。
Nodeの`core/image-references.ts`と`NodeAssetResolver`の役割に対応する。

- 正常経路ではsanitizerが処理対象にする画像参照を、describe/readと独立して記録する。
  全対象の走査後だけscan completeにする。例外時にfinallyでcompleteにしない。
  active subtreeなどNodeが通常除去する領域を、通常の画像処理対象へ増やさない。
- front matter errorの早期returnでも、Node同様に本文をparseし、local imgの参照を
  **画像I/Oなし**で記録してscan completeにする専用経路を加える。
  フェンス内の画像風textをregexで収集しない。
- resolverはlexical attempted paths、canonical paths、root内と確認したsafe paths、
  described assetのsnapshot/identity、scan completeを別々に保持する。
- `FileConversion`はHTMLとreportの両方に対して全候補のlexical/canonical/同一fileを検査する。
  missing画像への新規HTML/report、hardlink、root内symlinkも衝突対象。
  `--force`でも拒否し、既存画像bytesと未存在pathを保つ。
- 出力先自身のsymlinkは`NOFOLLOW_LINKS`で拒否する。
  現在の`canonicalTarget`は親だけcanonical化するので、続く`validateTarget`/
  `samePath`も含めてNodeのtarget identityと照合する。
- 画像状態の再確認と出力衝突を区別する。変換後の画像消失/変更は
  `W_ASSET_CHANGED`を加えて後続候補の検査を続ける。
  canonicalの解決失敗等で出力の安全を確定できない時は出力errorとする。
  CLIで完成HTMLの画像srcを再編集しない。
- success/failure report両方へ同じ衝突保護を適用する。
  resolverが存在しscan未完了、safe未確認の候補が残る、または衝突する場合はfailure reportを書かない。
  input読取り前の失敗でresolverがまだない場合は、入力/出力衝突確認後の既存failure reportを維持する。
- HTML/reportを両方stageしてからcommitする既存処理を維持する。
  report stage失敗ではHTML未公開、report commit失敗では保存済みHTMLを保持。
  内部例外のexit 3をreport失敗経路でexit 1へ変えない。

## 6. 集計・report・CSP

`ConversionStats`の既存欄を使い、schemaVersionは文字列`"2"`のままにする。

- 成功時は`embeddedImageOccurrences + referencedImageOccurrences + omittedImageOccurrences
  == imageOccurrences`。failureの途中statsへこの等式を強制しない。
- uniqueAssets/source bytesはdescribeに成功したIDと、検証済みembedded content IDを1回集計する。
  describe後に画像検査が失敗しても既知サイズの記録は残す。欠落fileは加算しない。
- data URLのIDはNodeの`embedded:<mime>:<sha256>`へ合わせ、inline SVGとの重複を扱う。
- embeddedPayloadBytesは埋め込みBase64の出現回数分だけ。
  参照画像サイズをHTML予算やBase64 payloadへ加算しない。
- 5 MiB/40 million pixelsは**超えた時だけ**warning。bytes/detailsとmode別messageも合わせる。
- `FileConversion.reportJson`の`output.imageMode`を選択値へ変更する。
  Coreを呼べなかったfailure reportでも選択modeを残す。未知bytesの省略、description、
  diagnostic details/source、安定順を既存契約として維持する。
- `HtmlDocumentRenderer.render`へmodeを渡し、referenceでは
  `img-src data: file: 'self'`、embedでは`img-src data:`を出す。
  script hash、connect/object/base/form制限、CSS/viewerの同梱を維持する。
  CSPに許可があっても、入力のfile/remote URLを受理する規則には変えない。

## 7. Lunaの実行順と変更ファイル

`core.*`等は`src/main/java/jp/igapyon/mikumd2html/`以下。
「新規」は今回の実装時に作る予定のファイルであり、現時点では存在しない。
チェックは各項目の合格条件を満たしてから付ける。

| ID | 依存 | 作業・主な変更対象 | 合格条件 |
| --- | --- | --- | --- |
| P00 | — | status/HEAD/hash確認、現行test/package、旧oracle比較を保存。製品変更前の証跡を`workplace/v0.5.0-followup/`へ置く | upstream2bundleのhash一致。既存失敗を新規失敗と区別できる |
| P01 | P00 | 新規`src/test/resources/parity/v0.5.0/`、新規`scripts/capture-node-v0.5.0-parity.mjs`、新規`scripts/compare-node-v0.5.0-java.mjs` | 固定Nodeのexpected HTML/JSON/provenanceを再生成可能。まだJava結果でgoldenを作らない |
| P02 | P01 | `core.ConvertOptions/MarkdownConverter`のoption検証、`core.AssetResolver`、新規`core.PrefixAssetResolver`、`cli.CliArguments/CliOptions/MikuMd2htmlCli` | Core invalid modeはE_OPTIONS。CLI両記法/default/invalid/duplicateをテスト。既存resolverは明示embedで利用可能 |
| P03 | P02 | 新規`adapters.LocalAssetPaths`、`adapters.LocalAssetResolver`、`cli.FileConversion`のresolver生成 | 複雑なfilename、出力位置違い、欠落先、root外symlink、prefix length/変更検出をテスト |
| P04 | P03 | `core.ImageEmbeddingSession/MarkdownConverter/MarkdownDocument/MarkdownParser`、`core.markdown.FlexmarkAdapter`、新規`core.markdown.PendingImageBindings/ImageSourceIndex`、必要な既存内部call site | prepare→estimate→embed順、cap前readなし、placeholder/内部source属性漏れなし。画像sourceを原文へ結び、既存parser出力の無関係なbytesを変えない |
| P05 | P04 | `core.ImageInspector`のsniff共有、`ImageEmbeddingSession`のreference・warning・cache・stats | 第3.5/4/6節、下の画像試験が合格。SVG errorをwarningへ落とさない |
| P06 | P05 | 新規`core.ImageReferenceTracker`、Flexmarkのscan、resolver追跡、`cli.FileConversion`の衝突/report gate、試験用convert seam | 第5節。画像が途中で消えても後続report衝突を見落とさない。内部失敗exit 3 |
| P07 | P06 | `core.HtmlDocumentRenderer/MarkdownConverter`、`cli.FileConversion` report writer | mode別CSP、全統計、成功/failure report。全JVMから同じ意味の出力 |
| P08 | P07 | 新規/既存JUnit、両parity scripts、`docs/upstream-*-mapping.md` | v0.5 focused oracle全件一致。旧5件はembed指定で既存4件一致と既知1件だけの差分を維持 |
| P09 | P08 | `scripts/smoke-packaged-runtime.sh`、`.github/workflows/ci.yml`、`docs/github-actions-plan.md` | final JARでdefault reference/explicit embedを検査。Java8/25同一JAR smokeとWindows path検査を定義 |
| P10 | P09 | README/CLI/development/dependency-decisions/snapshot/remaining/log、旧計画進捗、TODO | 実装/検証事実を反映してv0.5.0参照を記録。残る旧差分を明記し、未検証を完了扱いしない |

各P作業の開始時は対象の固定Nodeファイルと該当テストを読んでからJavaを変更する。
P04〜P07は同じpipelineを構成するため、途中段階を完成機能として説明しない。
失敗を出力の許容リスト追加やexpectedのJava結果への置換で解消しない。
既存テストの変更は、今回変わるlocal file warning、mode default、resolver契約違反の
期待値だけに限定して理由を記す。adapter単体のE系までW系に変えず、
remote/data URL/unsafe SVGのerror試験や既存の出力保護試験を削除しない。

### P00の開始コマンド（実装指示後）

```sh
git status --short --branch
git rev-parse HEAD
git -C ../miku-md2html status --short --branch
git -C ../miku-md2html rev-parse HEAD
shasum -a 256 ../miku-md2html/bundle/miku-md2html-runtime.mjs ../miku-md2html/bundle/miku-md2html.mjs
mvn -B -ntp package
node scripts/compare-node-java.mjs target/miku-md2html-java-0.5.0.jar
```

旧比較は文書上4/5一致、`commonmark-gfm`がHTML/関連htmlBytesで不一致。
これは今回まだ再実行していない記録値なのでP00で確認する。
他の失敗があれば原因を記録・切り分けしてから進む。
upstream HEAD/hashが違う場合はそのcheckoutを書き換えず、無視済み`workplace/`配下に
固定commitの独立checkoutを用意する。新capture toolの`--upstream`へそのパスを渡す。
upstreamへcommit/reset/install/buildを行うことを前提にしない。

## 8. 固定oracleの作成と比較方法

### v0.5.0の新しいoracle

新capture/comparison toolのCLIを次で固定する。

```sh
node scripts/capture-node-v0.5.0-parity.mjs --upstream ../miku-md2html
node scripts/compare-node-v0.5.0-java.mjs target/miku-md2html-java-0.5.0.jar
```

captureはNode22以上のmaintainer toolとし、HEAD、clean状態、runtime/CLI hash、
runtime version/exportを検証**してから**expected directoryへ書く。
`cases.json`にcase ID、入力、options/mode、resolver scenario、比較scopeを宣言する。
小さい画像とMarkdownはtracked fixtureへ、大きい画像は再現可能なテスト内生成へ置く。
巨大画像の試験ではfake resolverのmetadata/prefixを使い、100 MiB超のbinaryをGitへ入れない。

focused oracleのcase IDを次の表で固定する。`ref-`はmodeを省略してdefaultを検査し、
`embed-`は明示的に`embed`を渡す。両方欄はprefixを付けた2件を作る。
小さい画像は現在のPNG fixtureと、同じく自己完結する有効SVGを使用し、
各caseは原則画像1個だけのMarkdownにして無関係なparser差分を混ぜない。

| suffix / 単独ID | mode | 内容・scope |
| --- | --- | --- |
| `png` | 両方 | 有効local PNG。CoreとCLIの厳密比較 |
| `svg` | 両方 | 有効local SVG。両modeで元bytesのdata URL |
| `svg-named-png` | 両方 | 同じSVG bytesを`.png`名で与える |
| `data-png` | 両方 | resolverなしのPNG data URL |
| `raw-img` | 両方 | `<img src="pixel.png" alt="画素" title="tiny" width="1" height="1">` |
| `missing-png` | 両方 | missing callback/未存在path。warningとmode別src |
| `missing-svg` | 両方 | 未存在`.svg`。両方warning+src省略 |
| `invalid-png` | 両方 | PNG magicを持たない短いtext bytes。W_IMAGE_FORMAT |
| `outside` | 両方 | root外参照。W_ASSET_OUTSIDE_ROOT、src省略 |
| `remote` | 両方 | `https://example.invalid/a.png`。E_REMOTE_IMAGE |
| `unsafe-svg` | 両方 | local SVG内のonload。E_SVG_UNSAFE |
| `repeated-alias` | 両方 | 同path2出現+別path同ID1出現。unique1、出現3、path別href |
| `ref-no-reference` | reference | Coreだけ。describe/readしかないresolverでrasterを指定してE_ASSET_REFERENCE_REQUIRED |
| `embed-old-resolver` | embed | Coreだけ。同resolverで成功 |
| `ref-svg-old-resolver` | reference | Coreだけ。同resolverでSVG成功、reference不要 |

残るJPEG/GIF/WebP、巨大画像、source位置の複雑な境界、失敗注入、inline SVGは
第9節のJUnitで検査する。これらを失敗後にoracleから削除する手順にはしない。

各caseで次を保存する。

- Nodeの`<id>.node.html`（failureはHTMLなしとして記録）と`<id>.node.json`。
- JUnitで新しいJSON libraryなしに読める`<id>.contract.properties`。
  Node結果からsuccess、metadata、全stats、順序を保ったdiagnostics
  （severity/code/message/source/details）をflattenして生成する。
  Core契約例外caseは例外発生という結果を明記し、通常failureへ変換しない。
- `provenance.json`: commit、version、bundleのSHA-256、入力/画像/expected各fileのhash、
  Node実行版、case IDとscope。runtime生成日時など出力を揺らす値をgoldenへ混ぜない。

新規`core.UpstreamV050ParityTest`はtracked fixtureと同じscenarioのin-memory resolverを使い、
expected HTML bytesとpropertiesのCore結果を比較する。
公開APIへjsoupやJSON parserを追加しない。通常の`mvn test`はNodeと兄弟checkoutを要求しない。
scenarioの対応漏れはmanifestのcase ID一覧とJUnitが処理したID集合の一致で検出する。

新comparison scriptはpackaged JARを一時directoryで実行し、CLIについてstdout/stderr、exit、
HTML/reportの有無、mode、metadata、全stats、diagnosticsを比較する。
reportはNodeのCLIでもcaptureし、Core resultのJSONと混同しない。
tool.versionとinput/outputの一時rootだけを、指定したfieldで正規化する。
message全体のpath削除、diagnosticのsortし直し、CSP/whitespace除去で一致を作らない。
Java固有のhelp呼出例とartifact versionは既存の意図した差として記録する。
JSONは構造を比較し、既存reportの空白整形だけを変更する作業は追加しない。

新suiteは単純なMarkdownと画像のcaseを中心にし、今回無関係な未移植highlightや
GFM tableを混ぜて既知失敗を許容する方式にしない。新suiteは**全件合格、除外0件**を条件にする。
境界・例外・filesystem故障の詳細はJUnitでも直接試験する。

### v0.4.0の履歴を保存する

`src/test/resources/parity/v0.4.0/expected/`は書き換えない。
旧`capture-node-parity.mjs`のv0.4.0 pinも維持する。
P08で旧`compare-node-java.mjs`のJAR引数へ明示的に`--image-mode embed`を追加し、
旧CSPで比較する。v0.5.0のdefault変更を旧goldenの更新で消さない。
`commonmark-gfm`のNode/Java差分とJava HTML hashをP00で保存し、最終比較でも同じ既知差分だけか確認する。
旧比較のexit 1は記録された1件のみに限定し、新suiteの失敗を許容する理由にはしない。

## 9. 必須試験表

testは内部helperの実装を写すだけのassertではなく、出力、diagnostics、読み込み回数、
書込み保護を観測する。Node側ファイル名は`tests/`以下。

| ID | 入力/操作と期待結果 | Javaの置き場所（予定） | Node根拠 |
| --- | --- | --- | --- |
| V01 | mode省略→reference、explicit embed、両CLI記法、重複/不正/空/大文字/空白→usage 2、Core不正→E_OPTIONS | 新規`ImageModeTest`、`CliArgumentsTest` | `core.test.ts` / CLI args |
| V02 | PNG/JPEG/GIF/WebPを両mode。referenceは相対src/payload0、embedは元bytesのdata URL | `UpstreamV050ParityTest`、`ImageEmbeddingSessionTest` | `image-embedding.test.ts` |
| V03 | SVG、SVG内容の`.png`、raster内容の`.svg`、data URL。両modeで内容に応じた経路。inline SVGは検証/埋め込み/altの既存動作とmode間不変を確認し、serializer全bytesは旧backlogと区別 | `InlineSvgTest`、`SvgSafetyTest`、新oracle | 同上 / `core.test.ts` |
| V04 | raw imgのtitle/width/height、altの`&`/quote/日本語、src省略でも属性保持。画像sourceのstart/end、非BMP/参照形式/front matter、内部IDを模倣する入力、code内marker非置換 | `ArticleSanitizerTest`、新規`ImageSourceIndexTest`、新oracle | `core.test.ts` / sanitizer / hast-utils |
| V05 | Markdown親とHTML親が違う。`図 a%?#.png`を一度decode後`../docs/images/%E5%9B%B3%20a%25%3F%23.png`へ。画像bytes不変、コピーなし | 新規`LocalAssetPathsTest`、`LocalAssetResolverTest` | `node-assets.test.ts` |
| V06 | 同じfolderなら`./`、親参照`../`、`+ ! ' ( )`とpercent、invalid surrogate、Windows異drive→E_IMAGE_REFERENCE | `LocalAssetPathsTest` | `node-paths.ts` / URI tests |
| V07 | missing rasterはreferenceでW_IMAGE_NOT_FOUND+src保持、embedでsrc省略。missing SVGは両方省略 | 新oracle、`ImageEmbeddingSessionTest`、`FileConversionTest` | image/CLI tests |
| V08 | 非regular/unreadable/unknown format/truncated/changedを第4節へ。warningだけならHTML/report成功・exit0 | 新規`LocalImageWarningTest`、`FileConversionTest` | image tests / API contract |
| V09 | root外およびroot外symlinkはW_ASSET_OUTSIDE_ROOT、src省略、read/reference callbackなし | resolver/session tests | node-assets / image tests |
| V10 | unsafe SVG/CSS/外部resourceはE_SVG_*、remote/不正data/不正URIはerror。HTMLを公開しない | `SvgSafetyTest`、`ImageUriTest`、CLI tests | SVG/URI/core tests |
| V11 | resolver無し→E_ASSET_RESOLVER_REQUIRED、prefix無し旧resolver+embed成功、reference無しraster→E_ASSET_REFERENCE_REQUIRED、SVGはreference無しで成功 | `ImageModeTest`、session tests | image/API tests |
| V12 | reference戻り値のabsolute/remote/file/control/不正escape/nullを契約例外へ。callback E_IMAGE_REFERENCEはwarning+省略 | `ImageModeTest`、`LocalImageWarningTest` | `validateReferenceHref` |
| V13 | metadata null/空ID/null label/負数/JS safe integer超過/同ID別サイズ、prefix null/短い、read null/想定外例外はCore例外・CLI内部失敗 | session tests、CLI injection | refactoring-contract / image tests |
| V14 | 同path繰返し・別path同ID。describeはpathごと、readはIDごと、hrefはpathごと。異なるhrefを保持、warning重複なし、conversion間cache分離 | session tests | image tests |
| V15 | prefixはmin(256,size)ちょうど。size100MiBではread実施、超過既知rasterはprefixのみで参照+skip/large warning | 新規`ImageReferenceBudgetTest`、resolver tests | image boundary tests |
| V16 | >100MiBでprefix無し・unknownはskip+省略。prefix無し巨大SVG hintはHTML capでread。SVGがcap超過ならerror | `ImageReferenceBudgetTest` | image boundary tests |
| V17 | 5MiB/40M pixelsの等値と+1、warning detailsとmode別message、重複IDの集計、欠落fileのunique0 | budget/session tests、新oracle | image tests |
| V18 | embedのexact HTML capとcap-1、同ID複数出現でpayload複数、見積り超過ならread0回、final capも検査 | budget/session tests | `accepts exact output caps...` |
| V19 | default referenceでは画像総サイズがHTML cap超でもHTML内なら成功、payload0。reference SVG/dataのpayloadはcap対象 | budget tests | converter/image tests |
| V20 | image/pathとHTML/reportのlexical/canonical/hardlink衝突、missing画像と新規report衝突、--forceでも元bytes/未存在維持 | `FileConversionTest`、resolver tests | `cli-failures.test.ts` |
| V21 | first imageを変換直後に削除、second image=report。W_ASSET_CHANGEDの後も検査してE_OUTPUT_COLLISION、second bytes維持 | CLI convert seam試験 | `does not replace a later referenced image...` |
| V22 | scan途中の内部例外と不正front matter。未完了scanでreportを書かず、専用scan完了後は全candidateを検査。フェンス内画像textは候補にしない | CLI/core scan tests | image-references / CLI failures |
| V23 | output自身のsymlink/非regularを拒否、report stage/commit失敗、内部例外exit3、no-force競合で既存bytes維持 | 既存`FileConversionTest`を拡張 | CLI failure suite |
| V24 | schema2/default/failure mode、全stats・metadata/diagnostics、両mode CSP、旧4一致+既知1差分だけ | new oracle/comparison、既存comparison | reporting / html-document |
| V25 | final fat JAR単体で両mode、別作業directory、同一JARのJava8/25動作 | packaged smoke / existing CI | Node bundle smoke意図 |

V08/V21/V22の失敗注入は`FileConversion`にpackage-privateのconvert dependencyを加えて
再現する。既存OutputFileOperations seamを維持し、production entryは実Coreを渡す。
chmodだけのunreadable試験をroot権限に依存させず、resolverの故障注入も使う。
symlink非対応環境のskipは理由と対象を記録し、検査済みという扱いにしない。

## 10. 依存・同梱方針

**今回の追従では新しいproduction/test libraryを追加しない。**
Nodeのpackage/lock差分も版とdescriptionだけで、依存変更はない。

- Path/realpath/identity/prefixはJDK NIO、URI encodeとBase64/hashもJDKで実装する。
- HTMLは既存jsoupを内部adapterで再利用する。
  XML/CSS/Markdown/YAMLは現在同梱している実装を継続する。
- JSON比較はmaintainer用Node tool、JUnitはJDK Propertiesと直接result比較を使う。
  reportのためだけにJackson/Gsonを導入しない。
- Rhino、高画素decode、画像変換framework、Node子プロセスによる製品変換を追加しない。
- 既存runtime依存は今のshade設定でfat JARへ同梱する。
  配布JARはNode/npm、別のclasspath、ネットワークを実行要件にしない。
  既存第三者noticesとsources配布を維持し、依存inventoryが変わらないことを最終diffで確認する。

## 11. GitHub Actionsと最終検証

既存の`.github/workflows/ci.yml`と`release-cli-runtime.yml`を使う。
P09では新機能に必要な検査だけを加える。

1. `smoke-packaged-runtime.sh`に、既存data URLに加えて小さいlocal PNGを作るcaseを追加する。
   default referenceのsrc/CSP/report referenced countと、explicit embedのdata URL/CSP/payloadを検査。
   元画像のhash不変と追加画像directoryなしも確認する。
2. isolated copyしたfat JARで実行し、source treeと外部classpathに依存しないことを確認する。
3. 既存Ubuntu jobでTemurin21により一度packageし、同じJARをTemurin8/25でsmokeする。
   runtime JARのhashを切替前後で照合する。新しいbuildをruntime切替後に行わない。
4. `.github/workflows/ci.yml`へWindows用のfocused test jobを1個追加する。
   `windows-latest`、既存と同じ固定SHAのcheckout/setup-java、Temurin21、
   `mvn -B -ntp "-Dtest=LocalAssetPathsTest,LocalAssetResolverTest,ImageModeTest,FileConversionTest" test`。
   実Windowsのseparator/drive/root処理を検査し、Java floorの変更はしない。
5. Release workflowは既存published event・tag checkout・exact2 assetsを維持する。
   更新した共通smokeをstaged runtimeにも実行する。workflowの公開/権限/asset命名を追加変更しない。
6. 通常JUnitとCIでoracleの再取得やNode downloadを行わない。
   Node runtimeのcaptureはmaintainerが固定hashで行い、tracked fixtureをレビューする。

実装完了時のローカルコマンドは次の順番にする。
各task途中は`-Dtest=<対象class>`でfocused testを実行し、最後に一度全packageへ進む。

```sh
mvn -B -ntp package
bash scripts/smoke-packaged-runtime.sh target/miku-md2html-java-0.5.0.jar 0.5.0
node scripts/compare-node-v0.5.0-java.mjs target/miku-md2html-java-0.5.0.jar
node scripts/compare-node-java.mjs target/miku-md2html-java-0.5.0.jar
git diff --check
git status --short
```

旧比較の既知exit1だけは第8節どおり証跡を照合する。
新比較、JUnit、package、smokeの失敗をその例外へ含めない。
Java8/25がlocalにない場合はCI実行結果を別の未検証項目として残し、
`--release 8`コンパイル成功だけでruntime確認済みとしない。

ブラウザー受入では生成物を`file://`で開き、referenceで実画像を読み込めること、
embedのHTMLを画像のない別directoryへコピーしても表示できること、
JavaScript無効でも本文/画像が読めることを確認する。
狭い幅と印刷で既存viewerの回帰も確認し、使用browserと結果をlogへ記録する。
このために製品runtimeへbrowser libraryを追加しない。

## 12. 完了条件・文書更新・残す課題

- [ ] P00〜P10の合格条件とV01〜V25を満たし、新oracleは全件一致している。
- [ ] report/画像の衝突試験で、元画像とmissing pathが保護されている。
- [ ] 新しいhrefやwarningを理由にremote入力やunsafe SVGを受理していない。
- [ ] 旧4件のHTML/結果が明示embedで保たれ、既知GFM差分だけが残っている。
- [ ] 新規runtime/test dependencyなし。fat JAR単体smokeに成功している。
- [ ] Java8/25、Windows、browserで実施したこと/未実施のことを区別して記録した。
- [ ] `docs/upstream-snapshot.md`、README、developmentでv0.5.0 commit/hashを参照する。
  実装以前のv0.4.0調査文書とoracleの履歴は保持する。
- [ ] class/test/CLI mappingに新しいURL/prefix/scan責務と試験を追加した。
- [ ] `docs/cli.md`とhelpにdefault reference、持ち運びにはembed、画像warning、
  report mode、元画像をコピーしない点を記した。
- [ ] `docs/dependency-decisions.md`、remaining items、follow-up log、旧計画の関連task、TODOを
  実装と検証事実に合わせた。完全互換や未実施CI成功を宣言していない。

旧backlogのうち未移植highlight、GFM table/reference spacing、inline SVG serializer、全provenance、
脚注境界、全Markdown/YAML/SVG corpus、OS依存identityの差は引き続き明記する。
今回の画像pipeline/scanだけで旧T06/T17全体などを完了にしない。
版更新、stage/commit/push、PR、Release公開はこの実装手順へ自動追加しない。

## 13. 共有原則と一次資料

この計画は、継続適用中の`igapyon-miku-soft-developer`について2026-10-24に
installed SKILL、architecture、maintenance/adoption、Java straight conversion、
Java application/straight conversion basic、repo operationsを読み、既存の
[共有参照一覧](miku-soft-reference.md)と[依存判断](dependency-decisions.md)を参照して作成した。
installed skillにはGit metadataがないため、今回新たなskill commit同一性は宣言しない。
以前の照合revisionは共有参照一覧に残す。

適用する原則はCoreとadapterの分離、固定upstreamとの追跡可能性、Java8/fat JAR、
決定的なUTF-16順、最小の同梱依存、実装/検証状態の正確な記録。
Maintenance Uplift Checkでは今回に隣接するpackaged runtime検査をP09へ含め、
artifact改名、公開trigger変更、一般的な依存更新などは別作業とした。
共有原則本文はこのrepoへコピーしない。

一次資料（すべて固定v0.5.0）:

- [API](https://github.com/igapyon/miku-md2html/blob/v0.5.0/docs/api.md)、[CLI](https://github.com/igapyon/miku-md2html/blob/v0.5.0/docs/cli.md)
- [types](https://github.com/igapyon/miku-md2html/blob/v0.5.0/src/ts/types.ts)、[converter](https://github.com/igapyon/miku-md2html/blob/v0.5.0/src/ts/core/converter.ts)
- [image session](https://github.com/igapyon/miku-md2html/blob/v0.5.0/src/ts/core/image-embedding.ts)、[reference scan](https://github.com/igapyon/miku-md2html/blob/v0.5.0/src/ts/core/image-references.ts)
- [filesystem resolver](https://github.com/igapyon/miku-md2html/blob/v0.5.0/src/ts/adapters/node-assets.ts)、[path helpers](https://github.com/igapyon/miku-md2html/blob/v0.5.0/src/ts/adapters/node-paths.ts)
- [image tests](https://github.com/igapyon/miku-md2html/blob/v0.5.0/tests/image-embedding.test.ts)、[resolver tests](https://github.com/igapyon/miku-md2html/blob/v0.5.0/tests/node-assets.test.ts)、[CLI failures](https://github.com/igapyon/miku-md2html/blob/v0.5.0/tests/cli-failures.test.ts)
