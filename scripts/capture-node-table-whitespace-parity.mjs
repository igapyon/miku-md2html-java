#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureDir = join(root, 'src/test/resources/parity/v0.6.0/table-whitespace');
const expectedDir = join(fixtureDir, 'expected');
let upstream = resolve(root, '../miku-md2html');
const expectedCommit = '4a0918713f8b94fffe8d447e809800a2e096cb84';
const expectedRuntimeSha256 = '05b9284a3f796aa08848ce3bb7e2740a70b29103a901070700e95c69d7128cdd';
const args = process.argv.slice(2);
const upstreamIndex = args.indexOf('--upstream');
if (upstreamIndex >= 0) {
  if (!args[upstreamIndex + 1]) throw new Error('--upstream requires a directory');
  upstream = resolve(args[upstreamIndex + 1]);
}
const initialize = args.includes('--init');
for (const arg of args) if (!['--init', '--upstream'].includes(arg) && arg !== args[upstreamIndex + 1]) throw new Error(`Unknown argument: ${arg}`);

const table = '| A | B |\n| --- | --- |\n| a | b |';
const definition = '[site]: https://example.org "Example"';
const commonmark = [
  'Setext heading', '=============', '',
  'An [inline reference][site] and https://example.com.', '',
  '[site]: https://example.org "Example"', '',
  '| left | center | right |', '| :--- | :----: | ----: |', '| ~~old~~ | `code|span` | value |', '',
  '- [x] done', '- [ ] pending', '', 'Footnote[^note].', '',
  '[^note]: First paragraph.', '', '    Second paragraph.', '',
].join('\n');
const specs = [
  ['T01', table],
  ['T02', `Before\n\n${table}`],
  ['T03', `Before\n\n${definition}\n\n${table}`],
  ['T04', `${definition}\n\n${table}`],
  ['T05', `[Link][site]\n\n${definition}\n\n${table}`],
  ['T06', `[Link][site]\n\n${table}`],
  ['T07', `> Before\n>\n> ${table.replaceAll('\n', '\n> ')}`],
  ['T08', `> Before\n>\n> ${definition}\n>\n> ${table.replaceAll('\n', '\n> ')}`],
  ['T09', `> ${table.replaceAll('\n', '\n> ')}`],
  ['T10', `- Before\n\n  ${table.replaceAll('\n', '\n  ')}`],
  ['T11', `- Before\n\n  ${definition}\n\n  ${table.replaceAll('\n', '\n  ')}`],
  ['T12', `${table}\n\nAfter`],
  ['T13', `${table}\n\n${table}`],
  ['T14', '| A | B |\n| --- | --- |', 'regression', false, 'embed', 'jsoup serializes an empty tbody after the header-only table', 'html.sha256,stats.htmlBytes'],
  ['T15', '| A |\n| --- |\n| a |\n| b |'],
  ['T16', '| `code` | Center | Right |\n| :--- | :---: | ---: |\n| x | y | z |'],
  ['C01', 'Before\n\nAfter'],
  ['C02', `Before\n\n${definition}\n\nAfter`],
  ['C03', `# Before\n\n${definition}\n\nAfter`],
  ['C04', 'Before\n\n<table><tr><td>x</td></tr></table>'],
  ['C05', `<table><tr><td>raw</td></tr></table>\n\n${table}`],
  ['C06', '```text\n| A | B |\n| --- | --- |\n| a | b |\n```', 'regression', false, 'embed', 'Java emits a warning for the stripped fenced-code language class while HTML bytes match Node', 'diagnostics.count,diagnostic.0.severity,diagnostic.0.code,diagnostic.0.message,diagnostic.0.source.label,diagnostic.0.source.start.line,diagnostic.0.source.start.column,diagnostic.0.details.count'],
  ['C07', '<pre><code>first\n\n| A | B |\n| --- | --- |\n| a | b |\n</code></pre>'],
  ['C08', ''],
  ['R01-embed', `![before](missing-before.png)\n\n${table}\n\n![after](missing-after.png)`, 'exact', false, 'embed'],
  ['R01-reference', `![before](missing-before.png)\n\n${table}\n\n![after](missing-after.png)`, 'exact', false, 'reference'],
  ['R02', `<img src="missing-before.png" alt="before">\n\n${table}\n\n<img src="missing-after.png" alt="after">\n\n| Image |\n| --- |\n| <img src="missing-cell.png" alt="cell"> |`, 'regression', false, 'embed', 'Raw HTML image diagnostics use rendered-fragment positions in Java; compare all fields except source coordinates.', 'diagnostic.0.source.start.line,diagnostic.0.source.start.column,diagnostic.0.source.end.line,diagnostic.0.source.end.column,diagnostic.1.source.start.line,diagnostic.1.source.start.column,diagnostic.1.source.end.line,diagnostic.1.source.end.column,diagnostic.2.source.start.line,diagnostic.2.source.start.column,diagnostic.2.source.end.line,diagnostic.2.source.end.column'],
  ['R03', `---\ntitle: Mixed 🌙\n---\n\nBefore\n\n${table}\n\n<a href="javascript:alert(2)">危険</a>\n`, 'regression', false, 'embed', 'Java and Node raw/front-matter unsafe-link diagnostic coordinates differ; compare diagnostic content and full HTML.', 'diagnostic.0.source.start.line,diagnostic.0.source.start.column,diagnostic.0.source.end.line,diagnostic.0.source.end.column'],
  ['R04', `<table data-miku-generated-table-test="t0"><tr><td>raw</td></tr></table>\n\n\x60\x60\x60\ndata-miku-generated-table-test="t0"\n\x60\x60\x60\n`, 'regression', false, 'embed', 'Raw unsupported-attribute diagnostics use different attribute-name wording and have no Java source end range.', 'diagnostic.0.message,diagnostic.0.source.end.line,diagnostic.0.source.end.column'],
  ['R05-leading-h1', `# Heading\n\n${table}`, 'regression', true, 'embed', 'TOC/article-fragment boundary differs by one LF; compare that bounded delta and table spacing.'],
  ['R05-h1-body', `# Heading\n\nBody\n\n${table}`, 'regression', true, 'embed', 'TOC/article-fragment boundary differs by one LF; compare that bounded delta and table spacing.', 'html.sha256,stats.htmlBytes'],
  ['K01', `- Before\n  | A | B |\n  | --- | --- |\n  | a | b |`, 'known-difference', false, 'embed', 'No blank line before the list table: Node recognizes a table and Flexmark keeps the lines in the paragraph.'],
  ['K02', commonmark, 'known-difference', true, 'embed', 'Copied commonmark-gfm fixture: inline-code pipe tokenization and selected TOC placement remain known differences.'],
];

function sha256(value) { return createHash('sha256').update(value).digest('hex'); }
function git(...gitArgs) { return execFileSync('git', ['-C', upstream, ...gitArgs], { encoding: 'utf8' }).trim(); }
function escapeProperties(value, key = false) {
  let output = '';
  for (let i = 0; i < String(value).length; i += 1) {
    const char = String(value)[i];
    const code = char.charCodeAt(0);
    if (char === '\\') output += '\\\\';
    else if (char === '\n') output += '\\n';
    else if (char === '\r') output += '\\r';
    else if (char === '\t') output += '\\t';
    else if (char === ' ' && (key || i === 0)) output += '\\ ';
    else if ('=:#!'.includes(char) && (key || char !== '!')) output += `\\${char}`;
    else if (code < 0x20 || code > 0x7e) output += `\\u${code.toString(16).padStart(4, '0')}`;
    else output += char;
  }
  return output;
}
function flatten(result) {
  const values = new Map();
  const put = (key, value) => values.set(key, String(value));
  put('success', result.success);
  put('html.present', Object.hasOwn(result, 'html'));
  if (Object.hasOwn(result, 'html')) put('html.sha256', sha256(Buffer.from(result.html, 'utf8')));
  for (const [key, value] of Object.entries(result.metadata ?? {})) put(`metadata.${key}`, value);
  for (const [key, value] of Object.entries(result.stats ?? {})) put(`stats.${key}`, value);
  put('diagnostics.count', (result.diagnostics ?? []).length);
  (result.diagnostics ?? []).forEach((diagnostic, index) => {
    const prefix = `diagnostic.${index}.`;
    for (const key of ['severity', 'code', 'message']) if (diagnostic[key] !== undefined) put(prefix + key, diagnostic[key]);
    if (diagnostic.source) {
      for (const [key, value] of Object.entries(diagnostic.source)) {
        if (key === 'start' || key === 'end') {
          for (const [coordinate, number] of Object.entries(value)) put(`${prefix}source.${key}.${coordinate}`, number);
        } else put(`${prefix}source.${key}`, value);
      }
    }
    if (diagnostic.details) for (const [key, value] of Object.entries(diagnostic.details)) put(`${prefix}details.${key}`, value);
  });
  return [...values].map(([key, value]) => `${escapeProperties(key, true)}=${escapeProperties(value)}\n`).join('');
}
function manifestText() {
  const lines = [`case.ids=${specs.map(([id]) => id).join(',')}`];
  for (const [id, , scope = 'exact', toc = false, imageMode = 'embed', reason, ignore] of specs) {
    lines.push(`case.${id}.input=inputs/${id}.md`);
    lines.push(`case.${id}.inputName=${id}.md`);
    lines.push(`case.${id}.scope=${scope}`);
    lines.push(`case.${id}.toc=${toc}`);
    lines.push(`case.${id}.imageMode=${imageMode}`);
    if (reason) lines.push(`case.${id}.reason=${reason}`);
    if (ignore) lines.push(`case.${id}.ignore=${ignore}`);
  }
  return `${lines.join('\n')}\n`;
}
function parseProperties(content) {
  const result = new Map();
  for (const line of content.split(/\r?\n/)) {
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const match = line.match(/^([^=:]+)[=:](.*)$/);
    if (!match) throw new Error(`Invalid cases.properties line: ${line}`);
    result.set(match[1], match[2]);
  }
  return result;
}

const commit = git('rev-parse', 'HEAD');
if (commit !== expectedCommit) throw new Error(`upstream HEAD is ${commit}; expected ${expectedCommit}`);
if (git('status', '--porcelain')) throw new Error('upstream checkout is dirty; use a clean pinned checkout');
const runtimePath = join(upstream, 'bundle/miku-md2html-runtime.mjs');
const runtimeBytes = await readFile(runtimePath);
const runtimeSha256 = sha256(runtimeBytes);
if (runtimeSha256 !== expectedRuntimeSha256) throw new Error(`Node runtime hash is ${runtimeSha256}; expected ${expectedRuntimeSha256}`);
const runtime = await import(pathToFileURL(runtimePath).href);
if (runtime.version !== '0.6.0' || typeof runtime.convertMarkdownToHtml !== 'function') throw new Error('pinned runtime version/export does not match v0.6.0');

await mkdir(join(fixtureDir, 'inputs'), { recursive: true });
await mkdir(expectedDir, { recursive: true });
const propertiesPath = join(fixtureDir, 'cases.properties');
if (initialize) {
  try { await readFile(propertiesPath); throw new Error('--init refuses to overwrite existing cases.properties'); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  await writeFile(propertiesPath, manifestText(), 'utf8');
  for (const [id, markdown] of specs) await writeFile(join(fixtureDir, 'inputs', `${id}.md`), markdown, 'utf8');
}
const manifest = parseProperties(await readFile(propertiesPath, 'utf8'));
const ids = manifest.get('case.ids')?.split(',') ?? [];
if (ids.join(',') !== specs.map(([id]) => id).join(',')) throw new Error('cases.properties does not match the fixed capture case order');

const provenance = {
  upstream: { version: runtime.version, commit, runtime: 'bundle/miku-md2html-runtime.mjs', runtimeSha256 },
  cases: [],
};
for (const [id, specifiedMarkdown, defaultScope = 'exact', defaultToc = false, defaultMode = 'embed'] of specs) {
  const prefix = `case.${id}.`;
  const inputPath = manifest.get(prefix + 'input');
  if (inputPath !== `inputs/${id}.md`) throw new Error(`${prefix}input must be inputs/${id}.md`);
  const inputBytes = await readFile(join(fixtureDir, inputPath));
  const markdown = inputBytes.toString('utf8');
  if (markdown !== specifiedMarkdown) throw new Error(`${inputPath} no longer matches this capture script's case definition`);
  const scope = manifest.get(prefix + 'scope');
  if (!['exact', 'regression', 'known-difference'].includes(scope)) throw new Error(`${prefix}scope is invalid`);
  const toc = manifest.get(prefix + 'toc');
  const imageMode = manifest.get(prefix + 'imageMode');
  if (toc !== String(defaultToc) || imageMode !== defaultMode || scope !== defaultScope)
    throw new Error(`${prefix} options/scope differ from the fixed capture definition`);
  const result = await runtime.convertMarkdownToHtml({
    markdown,
    inputName: manifest.get(prefix + 'inputName'),
    ...(['R01-embed', 'R01-reference', 'R02'].includes(id) ? {
      assets: {
        async describe() { throw new runtime.AssetAccessError('E_IMAGE_NOT_FOUND', 'missing fixture image'); },
        async read() { throw new runtime.AssetAccessError('E_IMAGE_NOT_FOUND', 'missing fixture image'); },
        async reference(path) { return `./${path}`; },
      },
    } : {}),
    options: { toc: toc === 'true', imageMode },
  });
  const html = result.html ?? '';
  const json = `${JSON.stringify(result, null, 2)}\n`;
  const htmlBytes = Buffer.from(html, 'utf8');
  const jsonBytes = Buffer.from(json, 'utf8');
  const htmlName = `${id}.node.html`;
  const jsonName = `${id}.node.json`;
  const contractName = `${id}.contract.properties`;
  const contract = flatten(result)
    + `oracle.input.sha256=${sha256(inputBytes)}\n`
    + `oracle.html.sha256=${sha256(htmlBytes)}\n`
    + `oracle.json.sha256=${sha256(jsonBytes)}\n`;
  const contractBytes = Buffer.from(contract, 'utf8');
  await writeFile(join(expectedDir, htmlName), htmlBytes);
  await writeFile(join(expectedDir, jsonName), jsonBytes);
  await writeFile(join(expectedDir, contractName), contractBytes);
  provenance.cases.push({
    id, scope, input: inputPath, inputSha256: sha256(inputBytes), expectedHtml: htmlName,
    expectedHtmlSha256: sha256(htmlBytes), expectedResult: jsonName, expectedResultSha256: sha256(jsonBytes),
    contract: contractName, contractSha256: sha256(contractBytes), success: result.success,
  });
}
const provenanceBytes = Buffer.from(`${JSON.stringify(provenance, null, 2)}\n`, 'utf8');
await writeFile(join(expectedDir, 'provenance.json'), provenanceBytes);
console.log(`Captured ${provenance.cases.length} table-whitespace cases from Node v${runtime.version} ${commit}${initialize ? ' (initialized fixed inputs)' : ''}`);
