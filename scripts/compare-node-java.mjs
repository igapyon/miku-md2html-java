#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.argv.length !== 3) {
  console.error('usage: node scripts/compare-node-java.mjs <packaged-runtime-jar>');
  process.exit(2);
}

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureDir = join(root, 'src/test/resources/parity/v0.4.0');
const expectedDir = join(fixtureDir, 'expected');
const runtimeJar = resolve(process.argv[2]);
const manifest = JSON.parse(await readFile(join(expectedDir, 'provenance.json'), 'utf8'));
const cases = JSON.parse(await readFile(join(fixtureDir, 'cases.json'), 'utf8')).cases;
const hash = (value) => createHash('sha256').update(value).digest('hex');
let failures = 0;

for (const testCase of cases) {
  const expectedInfo = manifest.cases.find((entry) => entry.id === testCase.id);
  if (!expectedInfo) throw new Error(`provenance has no case ${testCase.id}`);
  const inputBytes = await readFile(join(fixtureDir, testCase.input));
  if (hash(inputBytes) !== expectedInfo.inputSha256) throw new Error(`fixture hash mismatch: ${testCase.id} input`);
  const expectedHtml = await readFile(join(expectedDir, expectedInfo.expectedHtml));
  const expectedResultBytes = await readFile(join(expectedDir, expectedInfo.expectedResult));
  if (hash(expectedResultBytes) !== expectedInfo.expectedResultSha256) throw new Error(`fixture hash mismatch: ${testCase.id} result`);
  const expectedJson = JSON.parse(expectedResultBytes.toString('utf8'));
  if (hash(expectedHtml) !== expectedInfo.expectedHtmlSha256) throw new Error(`fixture hash mismatch: ${testCase.id} HTML`);
  const work = await mkdtemp(join(tmpdir(), 'miku-md2html-parity-'));
  try {
    const inputPath = join(work, basename(testCase.inputName));
    const outputPath = join(work, 'output.html');
    const reportPath = join(work, 'report.json');
    await writeFile(inputPath, inputBytes);
    const args = ['-jar', runtimeJar, inputPath, '--out', outputPath, '--report-json', reportPath];
    if (testCase.options?.title) args.push('--title', testCase.options.title);
    if (testCase.options?.lang) args.push('--lang', testCase.options.lang);
    if (testCase.options?.toc === false) args.push('--no-toc');
    const run = spawnSync(process.env.JAVA_BIN || 'java', args, { encoding: 'utf8' });
    const problems = [];
    if (run.error) problems.push(run.error.message);
    if (run.status !== (expectedJson.success ? 0 : 1)) problems.push(`exit ${run.status}, expected ${expectedJson.success ? 0 : 1}`);
    if (expectedJson.success && run.status === 0) {
      const actualHtml = await readFile(outputPath);
      const actualReport = JSON.parse(await readFile(reportPath, 'utf8'));
      if (!actualHtml.equals(expectedHtml)) {
        let offset = 0;
        while (offset < actualHtml.length && offset < expectedHtml.length && actualHtml[offset] === expectedHtml[offset]) offset += 1;
        const start = Math.max(0, offset - 45);
        const nodeSnippet = expectedHtml.subarray(start, Math.min(expectedHtml.length, offset + 90)).toString('utf8').replace(/\n/g, '\\n');
        const javaSnippet = actualHtml.subarray(start, Math.min(actualHtml.length, offset + 90)).toString('utf8').replace(/\n/g, '\\n');
        problems.push(`HTML bytes differ at ${offset} (Node ${expectedHtml.length}, Java ${actualHtml.length}; Node=${JSON.stringify(nodeSnippet)}; Java=${JSON.stringify(javaSnippet)})`);
      }
      for (const key of ['title', 'lang']) {
        if (actualReport.metadata?.[key] !== expectedJson.metadata?.[key]) problems.push(`metadata.${key}: Node=${expectedJson.metadata?.[key]} Java=${actualReport.metadata?.[key]}`);
      }
      for (const key of ['headingCount', 'imageOccurrences', 'uniqueAssets', 'uniqueSourceAssetBytes', 'embeddedPayloadBytes', 'htmlBytes']) {
        if (actualReport.stats?.[key] !== expectedJson.stats?.[key]) problems.push(`stats.${key}: Node=${expectedJson.stats?.[key]} Java=${actualReport.stats?.[key]}`);
      }
      const nodeCodes = (expectedJson.diagnostics ?? []).map((item) => `${item.severity}:${item.code}`).sort();
      const javaCodes = (actualReport.diagnostics ?? []).map((item) => `${item.severity}:${item.code}`).sort();
      if (JSON.stringify(nodeCodes) !== JSON.stringify(javaCodes)) problems.push(`diagnostics differ (Node ${nodeCodes.join(',')}; Java ${javaCodes.join(',')})`);
    }
    if (problems.length) {
      failures += 1;
      console.error(`FAIL ${testCase.id}: ${problems.join('; ')}`);
    } else console.log(`PASS ${testCase.id}`);
  } finally {
    await rm(work, { recursive: true, force: true });
  }
}

console.log(`${cases.length - failures}/${cases.length} Node v${manifest.upstream.version} parity cases passed for ${runtimeJar}`);
process.exitCode = failures ? 1 : 0;
