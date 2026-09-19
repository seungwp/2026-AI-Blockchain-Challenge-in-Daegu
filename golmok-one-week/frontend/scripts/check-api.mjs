import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

// 외부 서버 없이 요청 합치기·캐시 만료·실패 복구를 검사한다.
let calls = 0, now = 0, fail = false, demo = false;
const client = { get: async () => {
  calls++;
  if (fail) throw { response: { status: 404 } };
  return { data: { reportId: 1, isDemoData: demo } };
} };
const exportsObject = {};
vm.runInNewContext(ts.transpileModule(fs.readFileSync('lib/api.ts', 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
}).outputText, {
  exports: exportsObject, process: { env: {} }, console,
  Date: { now: () => now },
  require: (name) => name === 'axios'
    ? { create: () => client, isAxiosError: () => true } : {},
});

(async () => {
  const { getReport } = exportsObject;
  await Promise.all([getReport(1), getReport(1)]);
  await getReport(1);
  assert.equal(calls, 1);
  now = 30_001;
  await getReport(1);
  assert.equal(calls, 2);
  await assert.rejects(getReport(NaN));
  assert.equal(calls, 2);
  fail = true;
  await assert.rejects(getReport(2));
  fail = false;
  await getReport(2);
  assert.equal(calls, 4);
  demo = true;
  await getReport(3);
  await getReport(3);
  assert.equal(calls, 6);
  console.log('API cache checks passed');
})().catch((error) => { console.error(error); process.exitCode = 1; });
