#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const upstream = resolve(root, '../miku-md2html');
const fixtureDir = join(root, 'src/test/resources/parity/v0.4.0');
const expectedDir = join(fixtureDir, 'expected');
const expectedCommit = 'b18a6e390425bd0e15387a2d608980a42a1809e9';
const expectedRuntimeSha256 = '4c5aaeac024b5c31c003e2df40d66d8e8d2e5ad4a4fbe4c2222619c83290f16c';
await mkdir(expectedDir, { recursive: true });

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function git(...args) {
  return execFileSync('git', ['-C', upstream, ...args], { encoding: 'utf8' }).trim();
}

const commit = git('rev-parse', 'HEAD');
if (commit !== expectedCommit) throw new Error(`upstream HEAD is ${commit}; expected ${expectedCommit}`);
if (git('status', '--porcelain')) throw new Error('upstream checkout is dirty; use a clean pinned checkout');

const runtimePath = join(upstream, 'bundle/miku-md2html-runtime.mjs');
const runtimeBytes = await readFile(runtimePath);
const runtimeSha256 = sha256(runtimeBytes);
if (runtimeSha256 !== expectedRuntimeSha256) throw new Error(`Node runtime hash is ${runtimeSha256}; expected ${expectedRuntimeSha256}`);
const runtime = await import(pathToFileURL(runtimePath).href);
if (typeof runtime.convertMarkdownToHtml !== 'function') throw new Error('pinned runtime has no convertMarkdownToHtml export');

const cases = JSON.parse(await readFile(join(fixtureDir, 'cases.json'), 'utf8')).cases;
const provenance = {
  upstream: { version: '0.4.0', commit, runtime: 'bundle/miku-md2html-runtime.mjs', runtimeSha256 },
  cases: [],
};

for (const testCase of cases) {
  const input = await readFile(join(fixtureDir, testCase.input));
  const markdown = input.toString('utf8');
  const result = await runtime.convertMarkdownToHtml({ markdown, inputName: testCase.inputName, options: testCase.options });
  const html = result.html ?? '';
  const json = `${JSON.stringify(result, null, 2)}\n`;
  const htmlName = `${testCase.id}.node.html`;
  const resultName = `${testCase.id}.node.json`;
  await writeFile(join(expectedDir, htmlName), html, 'utf8');
  await writeFile(join(expectedDir, resultName), json, 'utf8');
  provenance.cases.push({
    id: testCase.id,
    input: testCase.input,
    inputSha256: sha256(input),
    expectedHtml: htmlName,
    expectedHtmlSha256: sha256(html),
    expectedResult: resultName,
    expectedResultSha256: sha256(json),
    success: result.success,
  });
}

await writeFile(join(expectedDir, 'provenance.json'), `${JSON.stringify(provenance, null, 2)}\n`, 'utf8');
console.log(`Captured ${provenance.cases.length} cases from Node v0.4.0 ${commit}`);
