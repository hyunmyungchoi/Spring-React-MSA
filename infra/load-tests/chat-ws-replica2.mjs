import WebSocket from 'ws';
import { performance } from 'node:perf_hooks';

const url = process.env.CHAT_WS_URL ?? 'ws://user.localtest.me/bff/chat/ws?roomId=global';
const origin = process.env.CHAT_WS_ORIGIN ?? 'http://user.localtest.me';
const cookie = process.env.BFF_COOKIE;
const connections = numberEnv('CONNECTIONS', 2);
const messagesPerConnection = numberEnv('MESSAGES_PER_CONNECTION', 10);
const ratePerSecond = numberEnv('RATE_PER_SECOND', 10);
const connectTimeoutMs = numberEnv('CONNECT_TIMEOUT_MS', 10000);

if (!cookie) {
  console.error('BFF_COOKIE is required. Example: $env:BFF_COOKIE="BFFSESSIONID=..."');
  process.exit(1);
}

const totalMessages = connections * messagesPerConnection;
const intervalMs = Math.max(Math.floor(1000 / Math.max(ratePerSecond, 1)), 1);
const sentAtByClientMessageId = new Map();
const receivedClientMessageIds = new Set();
const latencies = [];
const sockets = [];

let opened = 0;
let closed = 0;
let failed = 0;
let sent = 0;
let received = 0;
let errors = 0;

console.log('Replica-2 WebSocket chat load test');
console.log(`url=${url}`);
console.log(`origin=${origin}`);
console.log(`connections=${connections}`);
console.log(`messagesPerConnection=${messagesPerConnection}`);
console.log(`ratePerSecond=${ratePerSecond}`);
console.log(`totalMessages=${totalMessages}`);

await openSockets();
await sendMessages();
await waitForReceives(10000);
closeSockets();

const uniqueReceived = receivedClientMessageIds.size;
const expectedReceives = totalMessages * connections;
const duplicateReceives = Math.max(received - uniqueReceived, 0);

console.log('');
console.log('Result');
console.log(`opened=${opened}`);
console.log(`closed=${closed}`);
console.log(`failed=${failed}`);
console.log(`sent=${sent}`);
console.log(`received=${received}`);
console.log(`uniqueReceived=${uniqueReceived}`);
console.log(`expectedReceives=${expectedReceives}`);
console.log(`duplicateReceives=${duplicateReceives}`);
console.log(`errors=${errors}`);
console.log(`p50Ms=${percentile(latencies, 50).toFixed(2)}`);
console.log(`p95Ms=${percentile(latencies, 95).toFixed(2)}`);
console.log(`p99Ms=${percentile(latencies, 99).toFixed(2)}`);

if (sent !== totalMessages || failed > 0 || errors > 0) {
  process.exitCode = 1;
}

async function openSockets() {
  const openTasks = Array.from({ length: connections }, (_, index) => openSocket(index + 1));
  await Promise.all(openTasks);
}

function openSocket(clientNo) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const socket = new WebSocket(url, {
      headers: {
        Cookie: cookie,
        Origin: origin,
      },
    });

    const timeout = setTimeout(() => {
      socket.terminate();
      fail(new Error(`client ${clientNo} connect timeout`));
    }, connectTimeoutMs);

    socket.clientNo = clientNo;

    socket.on('open', () => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      opened += 1;
      sockets.push(socket);
      resolve(socket);
    });

    socket.on('message', (data) => {
      handleServerMessage(data);
    });

    socket.on('error', (error) => {
      errors += 1;
      console.error(`client=${clientNo} websocket error: ${error.message}`);
      fail(error);
    });

    socket.on('unexpected-response', (_request, response) => {
      fail(new Error(`client ${clientNo} unexpected response ${response.statusCode}`));
    });

    socket.on('close', (code, reason) => {
      closed += 1;
      if (code !== 1000 && code !== 1005) {
        console.error(`client=${clientNo} closed code=${code} reason=${reason.toString()}`);
      }
    });

    function fail(error) {
      if (settled) {
        return;
      }

      settled = true;
      failed += 1;
      clearTimeout(timeout);
      reject(error);
    }
  });
}

async function sendMessages() {
  for (let messageNo = 0; messageNo < messagesPerConnection; messageNo += 1) {
    for (const socket of sockets) {
      if (socket.readyState !== WebSocket.OPEN) {
        failed += 1;
        continue;
      }

      const clientMessageId = `${socket.clientNo}-${messageNo + 1}-${Date.now()}`;
      sentAtByClientMessageId.set(clientMessageId, performance.now());
      socket.send(JSON.stringify({
        type: 'CHAT_MESSAGE',
        content: `load-test ${clientMessageId}`,
      }));
      sent += 1;

      await sleep(intervalMs);
    }
  }
}

function handleServerMessage(data) {
  received += 1;

  try {
    const parsed = JSON.parse(data.toString());
    if (parsed.type !== 'CHAT_MESSAGE' || !parsed.message?.content) {
      return;
    }

    const match = parsed.message.content.match(/^load-test (.+)$/);
    if (!match) {
      return;
    }

    const clientMessageId = match[1];
    receivedClientMessageIds.add(clientMessageId);
    const sentAt = sentAtByClientMessageId.get(clientMessageId);
    if (sentAt !== undefined) {
      latencies.push(performance.now() - sentAt);
    }

  } catch {
    errors += 1;
  }
}

async function waitForReceives(timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  const expectedReceives = totalMessages * connections;

  while (Date.now() < deadline) {
    if (received >= expectedReceives) {
      return;
    }
    await sleep(100);
  }
}

function closeSockets() {
  for (const socket of sockets) {
    if (socket.readyState === WebSocket.OPEN) {
      socket.close(1000, 'load test complete');
    }
  }
}

function numberEnv(name, defaultValue) {
  const value = Number.parseInt(process.env[name] ?? '', 10);
  return Number.isFinite(value) && value > 0 ? value : defaultValue;
}

function percentile(values, p) {
  if (values.length === 0) {
    return 0;
  }

  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(Math.ceil((p / 100) * sorted.length) - 1, sorted.length - 1);
  return sorted[index];
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
