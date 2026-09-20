#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const AGENTS_DIR = path.join(ROOT_DIR, '.agents');

const DISPATCH_FILE = path.join(AGENTS_DIR, 'DISPATCH.jsonl');
const INBOX_FILE = path.join(AGENTS_DIR, 'INBOX.md');
const STATUS_FILE = path.join(AGENTS_DIR, 'STATUS.md');

function ensureDir() {
  if (!fs.existsSync(AGENTS_DIR)) {
    fs.mkdirSync(AGENTS_DIR, { recursive: true });
  }
}

function getTimestamp() {
  return new Date().toISOString();
}

function parseArgs(args) {
  const result = { _: [] };
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      const next = args[i + 1];
      if (next && !next.startsWith('--')) {
        result[key] = next;
        i++;
      } else {
        result[key] = true;
      }
    } else {
      result._.push(arg);
    }
  }
  return result;
}

function postMessage(from, to, subject, body, payload = {}) {
  ensureDir();
  const id = `msg-${Date.now()}`;
  const timestamp = getTimestamp();

  const record = {
    id,
    timestamp,
    from,
    to,
    subject,
    body,
    payload
  };

  fs.appendFileSync(DISPATCH_FILE, JSON.stringify(record) + '\n', 'utf8');

  // Also prepend/append to INBOX.md for instant markdown readability
  const mdEntry = `\n### [${timestamp}] ${from.toUpperCase()} -> ${to.toUpperCase()}: ${subject}\n\n${body.trim()}\n\n---\n`;
  if (!fs.existsSync(INBOX_FILE)) {
    fs.writeFileSync(INBOX_FILE, `# Tripp'in AI: Agent Collaboration Inbox\n${mdEntry}`, 'utf8');
  } else {
    fs.appendFileSync(INBOX_FILE, mdEntry, 'utf8');
  }

  console.log(`[agent-bus] Message posted: ${id} (${from} -> ${to})`);
}

function readMessages(count = 10) {
  if (!fs.existsSync(DISPATCH_FILE)) {
    console.log('[agent-bus] No messages found.');
    return;
  }
  const lines = fs.readFileSync(DISPATCH_FILE, 'utf8').trim().split('\n').filter(Boolean);
  const slice = lines.slice(-count);
  for (const line of slice) {
    try {
      const item = JSON.parse(line);
      console.log(`[${item.timestamp}] ${item.from} -> ${item.to}: ${item.subject}`);
      console.log(`  ${item.body.replace(/\n/g, '\n  ')}\n`);
    } catch {
      console.log(line);
    }
  }
}

const command = process.argv[2];
const parsed = parseArgs(process.argv.slice(3));

switch (command) {
  case 'post':
  case 'send': {
    const from = parsed.from || 'anonymous';
    const to = parsed.to || 'all';
    const subject = parsed.subject || 'Dispatch Update';
    const body = parsed.body || parsed._.join(' ') || '(no content)';
    postMessage(from, to, subject, body, parsed);
    break;
  }
  case 'read': {
    const count = parseInt(parsed.n || parsed.count || '10', 10);
    readMessages(count);
    break;
  }
  default: {
    console.log(`Tripp'in Agent Bus Utility
Usage:
  node scripts/agent-bus.mjs send --from <agy|hermes> --to <hermes|agy> --subject "<title>" --body "<content>"
  node scripts/agent-bus.mjs read [--count 10]
`);
    break;
  }
}
