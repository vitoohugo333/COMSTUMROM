import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve, relative } from 'node:path';

const root = resolve(process.argv[2] || '.');
const excludedDirectories = new Set(['.git', 'node_modules']);
const canonicalFiles = [
  'AGENTS.md',
  'SKILLS.md',
  'MOBILE_WORKFLOW.md',
  'TESTING_RULES.md',
  'ADB_RULES.md',
  'ROM_SAFETY_RULES.md',
  'LEARNING_RULES.md',
  'START_HERE.md',
];
const governanceVersionPattern = /COMSTUMROM_GOVERNANCE_VERSION:\s*\d{4}-\d{2}-\d{2}\.\d+/;

function walk(directory) {
  const result = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && excludedDirectories.has(entry.name)) continue;
    const absolute = resolve(directory, entry.name);
    if (entry.isDirectory()) result.push(...walk(absolute));
    else result.push(absolute);
  }
  return result;
}

for (const file of canonicalFiles) {
  const target = resolve(root, file);
  assert.ok(existsSync(target), `Arquivo canônico ausente: ${file}`);
  const content = readFileSync(target, 'utf8');
  assert.match(content, governanceVersionPattern, `Marcador de governança ausente ou inválido em ${file}`);
}

const projectState = resolve(root, 'PROJECT_STATE.md');
assert.ok(existsSync(projectState), 'PROJECT_STATE.md ausente');
const projectStateContent = readFileSync(projectState, 'utf8');
for (const heading of ['Estado', 'Notion sync', 'Próximo passo']) {
  assert.ok(projectStateContent.includes(heading), `PROJECT_STATE.md não contém ${heading}`);
}

const policyPath = resolve(root, 'ci/branch-policy.json');
assert.ok(existsSync(policyPath), 'ci/branch-policy.json ausente');
const policy = JSON.parse(readFileSync(policyPath, 'utf8'));
assert.equal(policy.schema, 1, 'schema de ci/branch-policy.json inválido');
assert.match(String(policy.branch || ''), /^[A-Za-z0-9._/-]+$/, 'branch inválida na política');
assert.ok(policy.role, 'role ausente na política');

const files = walk(root);
for (const file of files.filter(file => /\.(?:js|mjs)$/.test(file))) {
  const content = readFileSync(file, 'utf8');
  new Function(content.replace(/^import .*$/gm, '').replace(/^export .*$/gm, ''));
}
for (const file of files.filter(file => /\.json$/.test(file))) {
  JSON.parse(readFileSync(file, 'utf8'));
}

const forbiddenSecretPatterns = [
  /gh[pousr]_[A-Za-z0-9]{20,}/g,
  /github_pat_[A-Za-z0-9_]{20,}/g,
  /-----BEGIN (?:RSA |EC )?PRIVATE KEY-----/g,
  /sk-[A-Za-z0-9_-]{20,}/g,
];
for (const file of files.filter(file => /\.(?:md|yml|yaml|js|mjs|json|toml|sh)$/.test(file))) {
  const content = readFileSync(file, 'utf8');
  for (const pattern of forbiddenSecretPatterns) {
    assert.equal(pattern.test(content), false, `Possível segredo em texto claro: ${relative(root, file)}`);
    pattern.lastIndex = 0;
  }
}

console.log('Verificação determinística da governança CUSTOMROM concluída.');
