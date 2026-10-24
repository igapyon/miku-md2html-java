# GitHub Actions の導入と実装状況

検討日・初回実装: 2026-10-24。workflow/helper をローカル実装し、静的検査とstage検証を行った。
GitHub hosted workflow run とReleaseへの実uploadは未実施。
[Node v0.4.0 追従計画](upstream-v0.4.0-followup-plan.md) を補完する。

## 結論

miku-soft の共有既定に沿う GA02 Release asset workflow と、
この repo の追従作業を支える GA01 CI workflow を実装した。
CI の常設を miku-soft 全体の必須要件とは扱わない。

| 実装済み file | 位置づけ | 目的 | 起動条件 | 権限 |
| --- | --- | --- | --- | --- |
| .github/workflows/ci.yml | この repo の追加提案 GA01 | test、package、同一 JAR の Java 8/25 実行確認 | devel 向け pull_request、devel への push、workflow_dispatch | contents: read |
| .github/workflows/release-cli-runtime.yml | 共有既定に沿う GA02 | 人が用意した Release に検証済み runtime/source JAR を添付 | 原則 release: published、v で始まる tag | asset 添付 job の contents: write |

両workflowはproductの振る舞いをworkflowへ重ねず、Mavenと共通smoke helperを呼ぶ。
CIのJava 8/25 hosted runとRelease asset uploadは、GitHubにworkflowを置いてから確認する。

Action refs are pinned to the verified full commits:

| Action | Release | Commit SHA |
| --- | --- | --- |
| `actions/checkout` | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | v6.1.0 | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` |
| `actions/upload-artifact` | v7.0.0 | `bbbca2ddaa5d8feaa63e36b76fdaad77386f024f` |
| `actions/download-artifact` | v7.0.0 | `37930b1c2abaa49bbe596cd826c3c89aef350131` |
| `softprops/action-gh-release` | v3.0.3 | `efb35369e0ad2afab669f228072c1b0d510eae64` |

## 現状と必要性

- `.github/workflows/ci.yml` と `release-cli-runtime.yml` を追加した。
- Maven は executable shaded JAR、sources JAR、local dist ZIP を生成できる。
  Release workflowは人が公開したReleaseへ検証済みJAR2本を添付する。
- source/API target は Java 8 だが、現在の記録は OpenJDK 17 での検証。
  同梱 library の採用では、実際の Java 8 での起動・変換検査が必要になる。
- 今後の parser、CSS、highlight、publication の変更には regression test が増える。
  CI があれば PR ごとに同じ手順で検査できる。
- 少数 library の同梱方針は、生成した JAR だけを別 directory へ持ち出して
  動かす smoke によって検査できる。

## 共有方針と参照実装

installed igapyon-miku-soft-developer の
references/30-java-straight-conversion-workflow.md と
assets/java-straight-conversion/.github/workflows/release-cli-runtime.yml を確認した。
共有方針は、人が GitHub UI で Release/tag を用意し、published event 後に
その tag から build して asset を添付する方式である。CI は別 workflow とする。

../miku-md2docx-java と ../miku-md2pptx-java の workflow も参照した。
これらには tag push/manual dispatch 型の旧構成があり、現在の共有既定を
置き換える根拠にはしない。

共有 template をこの repo へ適用する際は次を補う。

1. build JDK と packaged runtime の検査を分け、Java 8 に加えて Java 25 を検査する。
2. --version の終了成功だけでなく表示内容と実際の変換/report を検査する。
3. root `pom.xml` のartifactId/versionをPython標準XML readerで読み、uploadする2 fileを明示する。
4. tag/POM/runtime version の関係をこの repo の実装に合わせる。
5. Actionsは公式releaseを確認し、versionとcommit SHAを固定した。
   checkout v7.0.1、setup-java v6.1.0、upload/download-artifact v7.0.0、
   softprops/action-gh-release v3.0.3を使用する。

### miku-soft 原則との対応

R01～R08 は [参照資料一覧](miku-soft-reference.md) の ID。
共有原則、starter の例、今回の repo 向け提案を次のように区別する。

| 項目 | 根拠 | この計画での判断 |
| --- | --- | --- |
| Release 公開後に対象 tag から asset を生成 | R02 の共有既定 | GA02 の基本方式。immutable の条件が異なる場合だけ repo の例外として再検討 |
| CI と Release を別 workflow にする | R02 | GA01 は追加提案として独立。Release workflow に PR/branch の検証起動を混ぜない |
| runtime は executable fat JAR、sources は追跡用 artifact | R03 | executable と sources の内容・名前・役割を検査。通常 Maven 名から共有 Release 名へ stage |
| Java 8 と最新 LTS で packaged artifact を実行 | R03 | Temurin 8/25 で --version と代表変換。build JDK 21 は R07 も参考にした repo の選択 |
| stdout/stderr/exit、help、report を検査 | R05 | version の起動成功だけでなく、現在の製品契約を smoke で確認 |
| agent の作業はローカル準備と検証 | R06 | tag/Release は人が用意し、その後に選んだ workflow が asset を生成する |
| starter と姉妹 repo は実装資料 | R01/R07 | 旧 tag-push 型や Java 25 検証の欠落をそのまま継承しない |
| tag/POM/runtime の完全一致 | R02 の版対応を具体化する repo 向け提案 | 現在の固定 version 実装に合わせる。dot suffix を一律禁止する miku-soft 共通規則ではない |
| Action の現行 version と SHA 固定 | repo 向け提案 | 共通 starter の version を変更する作業ではなく、この repo の再現性の条件として記録 |

共有原則の参照元は、clean な igapyon-agent-skills checkout の commit
c61466dc03f7ed4a532fd6a2b37ef5a29be0071c と installed copy の一致を確認した。
GitHub の動作仕様と Action の現行 release は末尾の一次資料で補う。

## GA01 — CI baseline

- 状態: workflowとsmoke helperをローカル実装済み。GitHub hosted run待ち。
- 通常JUnitにNodeは必須化していない。

### 実行内容

1. ubuntu-24.04 の GitHub-hosted runner で対象 commit を checkout。
   checkout credential を後続 script へ残さない。
2. Temurin 21 を build JDK とし、Maven dependency cache を使用。
   Maven/JDK の実 version を log に残す。Java 9+ の既存 profile で --release 8 を適用。
3. mvn -B -ntp package を一度実行。JUnit と package の両方を検査し、
   続けて同じ test を理由なく再実行しない。
4. project.artifactId / project.version を `scripts/read-maven-coordinates.py` で取得。
   target の正確な runtime/source JAR path を特定し、runtime JAR の SHA-256 を記録。
5. Temurin 8 へ切り替え、作成済み runtime JAR を smoke helper で検査。
6. Temurin 25 へ切り替え、同じ bytes の runtime JAR を同じ helper で検査。
   runtime ごとに JAR を build し直さない。
7. JUnit report と smoke の失敗結果を診断できるように残す。
   Actions artifact を使う場合は report/生成物に限定し、source tree 全体を保存しない。

最初は 1 job の中で JDK を順に切り替える。複数 JDK で同じ Maven build を
繰り返す matrix や、build artifact の job 間搬送は初期構成に追加しない。
filesystem の OS 差の検査は T12/T13 の対象 case が揃った時点で
Windows/macOS の focused job として別途増やせる。

### 共通 smoke helper の契約

- 引数は runtime JAR の絶対 path と期待 product version。
- JAR と tracked な小さな Markdown/local PNG fixture を別の一時 directory に置く。
  追加 JAR、Node、npm、repository の target/classes を runtime classpath に使わない。
- --version は stdout が `miku-md2html <期待 version>` と末尾 LF、
  stderr が空、exit 0 であることを比較する。
- --help は exit 0、stdout の jar 呼出例と共有 option、stderr が空であることを確認。
- Markdown/local PNG を HTML と report へ変換し、exit 0、HTML の基本構造、
  image embedding、report の success/tool.version/output.written を確認する。
- 2 回目の force なし実行で既存出力を保護することを確認する。
- 採用後の library は、その機能を実行する fixture を smoke に追加する。
  jar --version が通るだけでは parser/highlight の依存を検査したとはしない。
- report の検査に runner の Python 等を使うことは可能だが、製品の runtime 要件にしない。
- Java 8 の version log は java -version を使用する。java --version は使用しない。

### CI の完了条件

- PR では write token や repository secret を必要としない。
- 対応済み test は成功し、T00 の pending case を「互換性検証成功」に数えない。
- package 失敗、Java 8/25 の smoke 失敗は job failure になる。
- CI concurrency は同一 PR/branch の古い run を中断する設定にする。
- workflow/共通 helper 自体の変更も CI 対象にする。
- YAML parsing、shell syntax、共通 helper のローカルsmoke、差分確認を行った。
  GitHubでの初回実行確認はbranch公開後に行い、その結果を追記する。

## GA02 — Release asset workflow

- 状態: workflow/helperをローカル実装済み。GitHub upload run待ち。
- CIと同じbuild JDK 21、runtime 8/25、共通smoke helperを使用する。

### 実行順

1. 人が GitHub UI で対象 v* tag と Release を用意して公開する。
   published event は stable/pre-release の両方を扱える。
2. github.event.release.tag_name の tag を checkout し、その commit を記録。
   動いている devel branch を build 対象にしない。
3. tag が v で始まることを検査し、POMのartifactId/versionを読む。
   `scripts/read-maven-coordinates.py` がPOMを読み、tag = v + project.versionを完全一致で検証する。
4. mvn -B -ntp package を実行し、成功した成果物だけを stage。
5. 下表の 2 file が存在して空でなく、JAR として読めることを検査する。
   runtime JAR に CLI class、viewer resource、採用済み dependency/license があるかも検査。
6. stage 済み runtime JAR の --version と変換/report を Java 8 と 25 で検査。
   両 JDK で同じ JAR hash を使う。
7. SHA-256 と tag/commit/POM version を run summary に残す。
8. 既存の同じ Release に 2 file を添付する。upload file を exact path で列挙し、
   unmatched file を failure とする。再実行時の同名 file は上書きする。
   Release の title/body/prerelease の状態は upload のために書き換えない。

| Maven build output | GitHub Release asset |
| --- | --- |
| `target/<artifactId>-<version>.jar` | `<artifactId>-<version>.jar` |
| `target/<artifactId>-<version>-sources.jar` | `<artifactId>-sources-<version>.jar` |

例えば version 0.4.0 なら miku-md2html-java-0.4.0.jar と
miku-md2html-java-sources-0.4.0.jar を添付する。
dist ZIP は現在の local distribution として維持し、初期 Release asset は
共有方針の runtime/source JAR の 2 本にする。

この repo は miku.release.version の上書き機構を持たず、
MikuMd2htmlMetadata.VERSION も固定文字列なので、pom.xml の製品 version と
同時に更新する。Release smoke は runtime の `--version` と POM version の一致を検査する。
したがって template の「POM version に dot suffix を足した tag も許可」を
そのまま採用すると、file 名と CLI の version が食い違う可能性がある。
GA02 の repo 向け案では完全一致を検査し、runtime 表示まで一致しなければ upload を止める。
これは共有 template の許容範囲を、この repo で実装されている版の仕組みに
合わせる提案であり、他の miku-soft 製品の tag 規約を変更するものではない。

### 起動と権限の境界

- Release workflow には PR/branch push 起動を混ぜない。
- 初期版は release: published のみ。手動復旧は同じ run の再実行を基本とする。
  別 tag を指定する workflow_dispatch は運用上の必要が確定してから追加する。
- root は contents: read、asset 添付 job だけ contents: write とする。
  token は upload action に渡し、checkout には persist-credentials: false を指定する。
- tag 名を shell command に直接式展開せず、env 経由で検査して quote する。
- Release ごとの concurrency group を使い、upload 途中の古い run を自動中断しない。
- workflow は tag や Release の作成を担当せず、人が作成した対象へ成果物を添付する。

### immutable Release の適合条件

2026-10-24にGitHub APIで確認した時点では、このrepositoryのimmutable releasesは
無効 (`enabled: false`, `enforced_by_owner: false`)。設定は変更していない。
GitHubのimmutable Releaseは公開後にassetを追加・置換できないため、設定が変われば運用を見直す。

- mutable なら共有既定の published → build/verify/attach を採用する。
- immutable なら published 後の添付方式を採用せず、既存 draft に
  build/verify/attach → 人が publish する案へ切り替える必要がある。
  その場合は draft を明示的に維持する upload と tag を指定する起動方法を
  別の運用決定として記録する。設定を勝手に無効化しない。

### Release workflow の完了条件

- 正常なtag/POM/runtime versionの組合せで正確に2 assetを準備できる。ローカルpositive stage済み。
- 不一致、存在しない JAR、元の unshaded JAR、smoke failure は upload 前に停止する。
- tag/POM不一致のnegative caseとsource JARのrenameをローカルで確認済み。
- uploadしないローカル検証とYAML/shell静的検査を完了させる。
- 実際の asset 添付の確認は、人が対象 workflow を含む tag/Release を公開した後に実施する。
  この実装ではReleaseの作成・公開、実workflow uploadは行っていない。

## この計画の実装範囲と保留

共有既定に沿う最小導入は、設定に適合する GA02。
今回の repo では、移植中の継続検証を行う GA01 を追加提案とする。
毎晩の upstream 更新検知、依存更新 bot、全 OS matrix、Node 最新版との自動比較は
初期 2 workflow に含めない。T00 の固定 oracle と、同梱 runtime の確実な検査を優先する。

GA01/GA02 を導入しても、Node 未移植機能の pending が解消するわけではない。
CI 成功と Node full parity 完了を区別して記録する。

## 一次資料

- [GitHub release event](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)
- [setup-java](https://github.com/actions/setup-java)
- [checkout v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1)
- [setup-java v6.1.0](https://github.com/actions/setup-java/releases/tag/v6.1.0)
- [upload-artifact v7.0.0](https://github.com/actions/upload-artifact/releases/tag/v7.0.0)
- [download-artifact v7.0.0](https://github.com/actions/download-artifact/releases/tag/v7.0.0)
- [softprops/action-gh-release v3.0.3](https://github.com/softprops/action-gh-release/releases/tag/v3.0.3)
- [Immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases)
- [Adoptium support roadmap](https://adoptium.net/support)
- [共有原則への入口](miku-soft-reference.md)
