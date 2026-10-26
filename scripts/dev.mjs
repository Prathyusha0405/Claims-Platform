#!/usr/bin/env node
// Starts the whole platform for local development: the three Spring Boot services, then the Angular dev server, each
// waiting for the previous health checks, with one Ctrl+C to stop everything. Needs only Node and a JDK (no other tools).
//
//   node scripts/dev.mjs [--no-frontend] [--skip-build] [--backend-dir <dir>] [--frontend-dir <dir>] [--verbose]
//
//   --no-frontend         start the services only
//   --skip-build          run the jars already in <backend>/<service>/target instead of building them first
//   --backend-dir <dir>   use this copy of backend/ (to run jars built somewhere else, e.g. away from an IDE that locks target/)
//   --frontend-dir <dir>  use this copy of frontend/claims-portal. Two dev servers started from the SAME folder share its
//                         .angular/cache, and the second one can break the first (dialogs stop opening, NG0201 in the console)
//   --verbose             show every service log line here (they are always written to .run/logs/)
//
// Ports can be changed with environment variables: AUTH_PORT (8084), CLAIM_PORT (8082), ASSESSMENT_PORT (8083) and
// FRONTEND_PORT (4200). The dev server is given a proxy file with the chosen ports, so the app keeps working.

import { spawn, spawnSync } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const isWindows = process.platform === 'win32';
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name) => {
  const at = args.indexOf(name);
  return at >= 0 ? args[at + 1] : undefined;
};

if (flag('--help') || flag('-h')) {
  console.log(readFileSync(fileURLToPath(import.meta.url), 'utf8').split('\n').filter((line) => line.startsWith('//')).map((line) => line.slice(3)).join('\n'));
  process.exit(0);
}

const backendDir = path.resolve(option('--backend-dir') ?? path.join(root, 'backend'));
const frontendDir = path.resolve(option('--frontend-dir') ?? path.join(root, 'frontend', 'claims-portal'));
const runDir = path.join(root, '.run');
const logDir = path.join(runDir, 'logs');
const verbose = flag('--verbose');

const ports = {
  auth: Number(process.env.AUTH_PORT ?? 8084),
  claim: Number(process.env.CLAIM_PORT ?? 8082),
  assessment: Number(process.env.ASSESSMENT_PORT ?? 8083),
  frontend: Number(process.env.FRONTEND_PORT ?? 4200),
};
const services = [
  { name: 'auth', dir: 'auth-service', port: ports.auth, env: {} },
  { name: 'claim', dir: 'claim-service', port: ports.claim, env: {} },
  // assessment-service finds claim-service through this address; its readiness stays DOWN until claim-service answers.
  { name: 'assessment', dir: 'assessment-service', port: ports.assessment, env: { CLAIM_SERVICE_URL: `http://localhost:${ports.claim}` } },
];

const children = [];
let stopping = false;

function say(message) {
  console.log(message);
}

function fail(message) {
  console.error(`\n${message}`);
  stopAll();
  process.exit(1);
}

function killTree(child) {
  if (child.exitCode !== null || child.pid === undefined) {
    return;
  }
  try {
    if (isWindows) {
      spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
    } else {
      process.kill(-child.pid, 'SIGTERM');
    }
  } catch {
    // already gone
  }
}

function stopAll() {
  stopping = true;
  children.forEach(({ child }) => killTree(child));
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    say('\nStopping...');
    stopAll();
    process.exit(0);
  });
}
process.on('exit', stopAll);

/** Starts a process, tags its output with `[name]`, and keeps every line in .run/logs/<name>.log. */
function start(name, command, argv, options = {}) {
  const log = createWriteStream(path.join(logDir, `${name}.log`));
  const settings = { ...options, detached: !isWindows, stdio: ['ignore', 'pipe', 'pipe'] };
  // With a shell, the arguments are only concatenated anyway; passing one string avoids Node's warning about that.
  const child = options.shell ? spawn([command, ...argv].join(' '), settings) : spawn(command, argv, settings);
  const record = { name, child, tail: [] };
  children.push(record);

  const onLine = (line) => {
    log.write(`${line}\n`);
    record.tail.push(line);
    if (record.tail.length > 30) {
      record.tail.shift();
    }
    if (verbose || options.echo === true || /\bERROR\b/.test(line)) {
      say(`[${name}] ${line}`);
    }
  };
  for (const stream of [child.stdout, child.stderr]) {
    let pending = '';
    stream.setEncoding('utf8');
    stream.on('data', (chunk) => {
      const lines = (pending + chunk).split(/\r?\n/);
      pending = lines.pop() ?? '';
      lines.forEach(onLine);
    });
    stream.on('end', () => pending && onLine(pending));
  }
  child.on('error', (error) => fail(`Could not start ${name}: ${error.message}`));
  child.on('close', (code) => {
    log.end();
    if (options.supervise && !stopping) {
      fail(`${name} stopped on its own (exit code ${code}). Its last lines:\n${record.tail.join('\n')}\nFull log: ${path.join(logDir, `${name}.log`)}\nStopping the rest.`);
    }
  });
  return record;
}

function runToCompletion(name, command, argv, options) {
  return new Promise((resolve, reject) => {
    const record = start(name, command, argv, { ...options, echo: true });
    record.child.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`${name} exited with code ${code}`))));
  });
}

/** True when something already accepts connections on the port (on either loopback address). */
function somethingListens(port, host) {
  return new Promise((resolve) => {
    const socket = net.connect({ port, host });
    socket.setTimeout(500);
    socket.once('connect', () => {
      socket.destroy();
      resolve(true);
    });
    socket.once('timeout', () => {
      socket.destroy();
      resolve(false);
    });
    socket.once('error', () => resolve(false));
  });
}

// Connecting finds a dev server that listens on one loopback address only, which binding the port would not.
async function portIsFree(port) {
  for (const host of ['127.0.0.1', '::1']) {
    if (await somethingListens(port, host)) {
      return false;
    }
  }
  return true;
}

async function waitUntil(label, url, record, seconds) {
  const started = Date.now();
  while (Date.now() - started < seconds * 1000) {
    if (record && record.child.exitCode !== null) {
      fail(`${label} stopped before it was ready (exit code ${record.child.exitCode}). Its last lines:\n${record.tail.join('\n')}\nFull log: ${path.join(logDir, `${record.name}.log`)}`);
    }
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000) });
      if (response.ok) {
        say(`  ${label} is up (${Math.round((Date.now() - started) / 1000)} s)`);
        return;
      }
    } catch {
      // not listening yet
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  fail(`${label} was not ready after ${seconds} s. See ${path.join(logDir, `${record?.name ?? 'web'}.log`)}`);
}

function jarOf(service) {
  const targetDir = path.join(backendDir, service.dir, 'target');
  const jars = existsSync(targetDir)
    ? readdirSync(targetDir).filter((file) => file.endsWith('.jar') && !/\.original$|-(sources|javadoc|plain)\.jar$/.test(file))
    : [];
  if (jars.length === 0) {
    fail(`No jar for ${service.dir} in ${targetDir}. Run without --skip-build to build it, or point --backend-dir at a build.`);
  }
  return path.join(targetDir, jars[0]);
}

// ---------------------------------------------------------------------------------------------------------------------

for (const [name, port] of Object.entries(ports)) {
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    fail(`The ${name} port must be a number from 1 to 65535 (got ${port}).`);
  }
}
if (new Set(Object.values(ports)).size !== Object.keys(ports).length) {
  fail(`Each part needs its own port, but two share one: ${JSON.stringify(ports)}.`);
}
if (spawnSync('java', ['-version'], { stdio: 'ignore' }).status !== 0) {
  fail('Java was not found on the PATH. The services need a JDK (the build is on JDK 27).');
}

const wanted = flag('--no-frontend') ? services.map((s) => [s.name, s.port]) : [...services.map((s) => [s.name, s.port]), ['frontend', ports.frontend]];
const busy = [];
for (const [name, port] of wanted) {
  if (!(await portIsFree(port))) {
    busy.push(`  port ${port} (${name}) is already in use. If the service is running from your IDE, stop it, or choose another port, e.g. ${name === 'frontend' ? 'FRONTEND' : name.toUpperCase()}_PORT=${port + 10000}`);
  }
}
if (busy.length > 0) {
  fail(`Cannot start:\n${busy.join('\n')}`);
}

mkdirSync(logDir, { recursive: true });

if (!flag('--skip-build')) {
  say('Building the services (mvnw package, tests skipped)...');
  const command = isWindows ? `"${path.join(backendDir, 'mvnw.cmd')}"` : 'sh';
  const argv = [...(isWindows ? [] : [path.join(backendDir, 'mvnw')]), '-B', '-q', '-DskipTests', 'package'];
  try {
    await runToCompletion('build', command, argv, { cwd: backendDir, shell: isWindows });
  } catch (error) {
    fail(`${error.message}. If an IDE is holding backend/*/target, stop its run configurations, or use --skip-build / --backend-dir.`);
  }
}

say('Starting the services...');
const records = services.map((service) =>
  start(service.name, 'java', ['-jar', jarOf(service), `--server.port=${service.port}`], { env: { ...process.env, ...service.env }, supervise: true }),
);
await Promise.all(services.map((service, at) => waitUntil(`${service.name}-service`, `http://localhost:${service.port}/actuator/health/readiness`, records[at], 180)));

if (!flag('--no-frontend')) {
  say('Starting the Angular dev server (the first compile takes a minute)...');
  if (!existsSync(path.join(frontendDir, 'node_modules'))) {
    say('Installing the front-end dependencies (npm ci)...');
    await runToCompletion('npm', 'npm', ['ci'], { cwd: frontendDir, shell: true }).catch((error) => fail(error.message));
  }
  // The dev server forwards /api/* to the services; give it a copy of proxy.conf.json that uses the chosen ports.
  const proxy = JSON.parse(readFileSync(path.join(frontendDir, 'proxy.conf.json'), 'utf8'));
  const portFor = { 8084: ports.auth, 8082: ports.claim, 8083: ports.assessment };
  for (const rule of Object.values(proxy)) {
    rule.target = String(rule.target).replace(/:(\d+)$/, (_, port) => `:${portFor[port] ?? port}`);
  }
  const proxyFile = path.join(runDir, 'proxy.conf.json');
  writeFileSync(proxyFile, `${JSON.stringify(proxy, null, 2)}\n`);
  // A relative path has no spaces unless the front end is on another drive, where it becomes absolute; quote it then.
  const proxyArg = path.relative(frontendDir, proxyFile);
  const web = start('web', 'npx', ['ng', 'serve', '--port', String(ports.frontend), '--proxy-config', /\s/.test(proxyArg) ? `"${proxyArg}"` : proxyArg], {
    cwd: frontendDir,
    shell: true,
    supervise: true,
  });
  await waitUntil('the app', `http://localhost:${ports.frontend}/`, web, 300);
}

say('\nThe platform is running.');
if (!flag('--no-frontend')) {
  say(`  ${'App'.padEnd(19)} http://localhost:${ports.frontend}   (demo logins: see the README)`);
}
for (const service of services) {
  say(`  ${`${service.name}-service`.padEnd(19)} http://localhost:${service.port}/swagger-ui.html   health: /actuator/health`);
}
say(`  ${'Logs'.padEnd(19)} ${logDir}`);
say('  Stop everything with Ctrl+C.\n');

// Stay in the foreground. Every service and the dev server are supervised (see `start`): if one stops on its own the rest are
// stopped and this script exits with code 1. Ctrl+C is the normal way out.
await new Promise(() => {});
