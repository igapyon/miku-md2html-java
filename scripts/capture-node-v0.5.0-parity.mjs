#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
let upstream = resolve(root, '../miku-md2html');
const upstreamArg = process.argv.indexOf('--upstream');
if (upstreamArg >= 0) {
  if (!process.argv[upstreamArg + 1]) throw new Error('--upstream requires a directory');
  upstream = resolve(process.argv[upstreamArg + 1]);
}
const fixtureDir = join(root, 'src/test/resources/parity/v0.5.0');
const expectedDir = join(fixtureDir, 'expected');
const expectedCommit = 'c397569016383c622aae4b187234e56f08a753fd';
const expectedRuntimeSha256 = 'adb7dbad356ea6b78be15a4a6c1a1a7a53532d2cb52b272945becb092fc8d436';
const expectedCliSha256 = 'c5af81eccba1ecddc348533e40517a2dbcfa92af552b6d7a02616421fcbc9b78';
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAO+/odcAAAAASUVORK5CYII=', 'base64');
const validSvg = await readFile(join(fixtureDir, 'valid.svg'));
const unsafeSvg = await readFile(join(fixtureDir, 'unsafe.svg'));
const invalid = Buffer.from('not a png');

function sha256(value) { return createHash('sha256').update(value).digest('hex'); }
function git(...args) { return execFileSync('git', ['-C', upstream, ...args], { encoding: 'utf8' }).trim(); }
function propertiesEscape(value, key = false) {
  let out = '';
  for (let i = 0; i < String(value).length; i += 1) {
    const c = String(value)[i];
    const n = c.charCodeAt(0);
    if (c === '\\') out += '\\\\';
    else if (c === '\n') out += '\\n';
    else if (c === '\r') out += '\\r';
    else if (c === '\t') out += '\\t';
    else if (c === '\f') out += '\\f';
    else if (c === ' ' && (key || i === 0)) out += '\\ ';
    else if ('=:#!'.includes(c) && (key || c !== '!')) out += `\\${c}`;
    else if (n < 0x20 || n > 0x7e) out += `\\u${n.toString(16).padStart(4, '0')}`;
    else out += c;
  }
  return out;
}
function flatten(result) {
  const p = new Map();
  const put = (k, v) => p.set(k, String(v));
  put('success', result.success);
  put('html.present', Object.hasOwn(result, 'html'));
  if (Object.hasOwn(result, 'html')) put('html.sha256', sha256(Buffer.from(result.html, 'utf8')));
  for (const [key, value] of Object.entries(result.metadata ?? {})) put(`metadata.${key}`, value);
  for (const [key, value] of Object.entries(result.stats ?? {})) put(`stats.${key}`, value);
  put('diagnostics.count', (result.diagnostics ?? []).length);
  (result.diagnostics ?? []).forEach((d, i) => {
    const prefix = `diagnostic.${i}.`;
    for (const key of ['severity', 'code', 'message']) if (d[key] !== undefined) put(prefix + key, d[key]);
    if (d.source) {
      for (const [key, value] of Object.entries(d.source)) {
        if (key === 'start' || key === 'end') {
          for (const [coordinate, number] of Object.entries(value)) put(`${prefix}source.${key}.${coordinate}`, number);
        } else put(`${prefix}source.${key}`, value);
      }
    }
    if (d.details) for (const [key, value] of Object.entries(d.details)) put(`${prefix}details.${key}`, value);
  });
  return [...p].map(([key, value]) => `${propertiesEscape(key, true)}=${propertiesEscape(value)}\n`).join('');
}

const commit = git('rev-parse', 'HEAD');
if (commit !== expectedCommit) throw new Error(`upstream HEAD is ${commit}; expected ${expectedCommit}`);
if (git('status', '--porcelain')) throw new Error('upstream checkout is dirty; use a clean pinned checkout');
const runtimePath = join(upstream, 'bundle/miku-md2html-runtime.mjs');
const cliPath = join(upstream, 'bundle/miku-md2html.mjs');
const runtimeBytes = await readFile(runtimePath);
const cliBytes = await readFile(cliPath);
const runtimeSha256 = sha256(runtimeBytes);
const cliSha256 = sha256(cliBytes);
if (runtimeSha256 !== expectedRuntimeSha256) throw new Error(`Node runtime hash is ${runtimeSha256}; expected ${expectedRuntimeSha256}`);
if (cliSha256 !== expectedCliSha256) throw new Error(`Node CLI hash is ${cliSha256}; expected ${expectedCliSha256}`);
const runtime = await import(pathToFileURL(runtimePath).href);
if (runtime.version !== '0.5.0' || typeof runtime.convertMarkdownToHtml !== 'function') throw new Error('pinned runtime version/export does not match v0.5.0');

const manifest = JSON.parse(await readFile(join(fixtureDir, 'cases.json'), 'utf8'));
const provenance = {
  upstream: { version: runtime.version, commit, runtime: 'bundle/miku-md2html-runtime.mjs', runtimeSha256, cli: 'bundle/miku-md2html.mjs', cliSha256 },
  cases: [],
};
const resultByScenario = (scenario) => {
  if (['png', 'raw-img', 'svg-named-png', 'repeated-alias', 'old-png'].includes(scenario)) return { id: 'pixel-id', label: 'pixel.png', bytes: png };
  if (['svg', 'old-svg'].includes(scenario)) return { id: 'svg-id', label: 'image.svg', bytes: validSvg };
  if (scenario === 'unsafe-svg') return { id: 'unsafe-svg-id', label: 'unsafe.svg', bytes: unsafeSvg };
  if (scenario === 'invalid-png') return { id: 'invalid-id', label: 'invalid.png', bytes: invalid };
  return undefined;
};

await mkdir(expectedDir, { recursive: true });
for (const testCase of manifest.cases) {
  const asset = resultByScenario(testCase.scenario);
  const resolver = testCase.scenario === 'remote' || testCase.scenario === 'data-png' ? undefined : {
    async describe(path) {
      if (testCase.scenario === 'outside') throw new runtime.AssetAccessError('E_ASSET_OUTSIDE_ROOT', 'outside');
      if (testCase.scenario.startsWith('missing-')) throw new runtime.AssetAccessError('E_IMAGE_NOT_FOUND', 'missing');
      if (!asset) throw new Error(`No fixture resolver configured for ${testCase.scenario}`);
      const id = testCase.scenario === 'repeated-alias' ? 'shared-id' : asset.id;
      return { id, label: path, byteLength: asset.bytes.length };
    },
    async read(id, maxBytes) {
      if (testCase.scenario === 'outside') throw new runtime.AssetAccessError('E_ASSET_OUTSIDE_ROOT', 'outside');
      if (!asset) throw new Error(`No fixture bytes configured for ${testCase.scenario}`);
      if (asset.bytes.length > maxBytes) throw new runtime.AssetAccessError('E_OUTPUT_TOO_LARGE', 'too large');
      return new Uint8Array(asset.bytes);
    },
    async readPrefix(id, maxBytes) { return new Uint8Array(asset.bytes.subarray(0, maxBytes)); },
    async reference(path) {
      if (testCase.scenario === 'outside') throw new runtime.AssetAccessError('E_ASSET_OUTSIDE_ROOT', 'outside');
      return `./${path.split('/').map((part) => encodeURIComponent(part)).join('/')}`;
    },
  };
  const options = testCase.mode === 'reference' ? undefined : { imageMode: testCase.mode };
  const result = await runtime.convertMarkdownToHtml({ markdown: testCase.markdown, inputName: `${testCase.id}.md`, ...(resolver ? { assets: resolver } : {}), options });
  const html = result.html ?? '';
  const json = `${JSON.stringify(result, null, 2)}\n`;
  const htmlName = `${testCase.id}.node.html`;
  const resultName = `${testCase.id}.node.json`;
  const contractName = `${testCase.id}.contract.properties`;
  await writeFile(join(expectedDir, htmlName), html, 'utf8');
  await writeFile(join(expectedDir, resultName), json, 'utf8');
  await writeFile(join(expectedDir, contractName), flatten(result), 'utf8');
  provenance.cases.push({
    id: testCase.id, markdownSha256: sha256(Buffer.from(testCase.markdown, 'utf8')),
    scenarioAssetSha256: asset ? sha256(asset.bytes) : null,
    expectedHtml: htmlName, expectedHtmlSha256: sha256(html), expectedResult: resultName,
    expectedResultSha256: sha256(json), contract: contractName, contractSha256: sha256(await readFile(join(expectedDir, contractName))),
    success: result.success, scope: testCase.scope,
  });
}
await writeFile(join(expectedDir, 'provenance.json'), `${JSON.stringify(provenance, null, 2)}\n`, 'utf8');
console.log(`Captured ${provenance.cases.length} cases from Node v${runtime.version} ${commit}`);
