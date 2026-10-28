import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createTrefleClient, createServer, normalizePlant, ApiError } from './trefle.mjs';

const row = { id: 42, common_name: null, scientific_name: 'Examplea botanica', slug: 'examplea-botanica', family: 'Exampleaceae', genus: 'Examplea' };
const detail = { ...row, images: { habit: [{ image_url: 'https://example.test/plant.jpg', copyright: 'Photo: Example author, CC BY 4.0' }] }, growth: { light: 7 }, sources: [{ name: 'Example flora', citation: 'Example contributors', url: 'https://example.test/flora' }] };

test('missing common names use scientific names; image and upstream credits are preserved', () => {
  const p = normalizePlant(row, detail, 'test-version');
  assert.equal(p.name, row.scientific_name);
  assert.equal(p.sourceName, 'Trefle');
  assert.match(p.photoCredit, /Example author/);
  assert.match(p.dataCredits, /Example contributors/);
  assert.match(p.description, /ecological indicator is not an indoor care schedule/);
  assert.equal(p.water, '');
});

test('missing or insecure image credits use a placeholder instead of inventing attribution', () => {
  assert.equal(normalizePlant(row, { ...row, images: { leaf: [{ image_url: 'https://example.test/a.jpg' }] } }).image, '');
  assert.equal(normalizePlant(row, { ...row, images: { leaf: [{ image_url: 'http://example.test/a.jpg', copyright: 'Example' }] } }).image, '');
  assert.equal(normalizePlant(row, { ...row, images: { leaf: [{ image_url: 'https://example.test/a.jpg', copyright: 'Copyright owner. All rights reserved.' }] } }).image, '');
  assert.equal(normalizePlant(row, { ...row, images: { leaf: [{ image_url: 'https://example.test/a.jpg', copyright: 'Example author (cc-by-nc)' }] } }).image, '');
});

test('account token is sent only in a header; cache deduplicates requests and redacts reflected tokens', async () => {
  let calls = 0;
  const get = createTrefleClient({ tokenReader: async () => 'fake-test-secret', fetcher: async (url, options) => {
    calls++;
    assert.ok(!url.includes('fake-test-secret'));
    assert.equal(options.headers.Authorization, 'Bearer fake-test-secret');
    return new Response(JSON.stringify({ data: { note: 'fake-test-secret' } }));
  } });
  const [a, b] = await Promise.all([get('/species/42'), get('/species/42')]);
  assert.equal(calls, 1);
  assert.deepEqual(a, b);
  assert.equal(a.data.note, '[redacted]');
  await get('/species/42');
  assert.equal(calls, 1);
});

test('missing credentials, invalid credentials and rate limits produce helpful errors without secrets', async () => {
  const missing = createTrefleClient({ tokenReader: async () => '' });
  await assert.rejects(missing('/species/42'), e => e.status === 503);
  for (const status of [401, 429]) {
    let calls = 0;
    const get = createTrefleClient({ tokenReader: async () => 'fake-test-secret', fetcher: async () => {
      calls++; return new Response('secret upstream body', { status, headers: { 'retry-after': '30' } });
    } });
    await assert.rejects(get('/species/42'), e => !e.message.includes('secret') && e.status === (status === 401 ? 503 : 429));
    if (status === 429) {
      await assert.rejects(get('/species/43'), e => e.status === 429);
      assert.equal(calls, 1);
    }
  }
});

test('pagination crosses Trefle pages without dropping or repeating plants', async t => {
  const get = async path => {
    if (path.startsWith('/species/')) return { data: { ...detail, id: Number(path.split('/').at(-1)) } };
    const page = Number(new URL('https://example.test' + path).searchParams.get('page'));
    return { data: Array.from({ length: 20 }, (_, i) => ({ ...row, id: (page-1)*20+i+1 })), meta: { total: 45 } };
  };
  const server = createServer({ get, tokenReader: async () => 'fake-test-secret' });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); });
  const base = 'http://127.0.0.1:' + server.address().port;
  const third = await (await fetch(base + '/plants/search?q=example&page=3')).json();
  const fourth = await (await fetch(base + '/plants/search?q=example&page=4')).json();
  assert.deepEqual(third.data.map(p => p.id), ['trefle:13','trefle:14','trefle:15','trefle:16','trefle:17','trefle:18']);
  assert.deepEqual(fourth.data.map(p => p.id), ['trefle:19','trefle:20','trefle:21','trefle:22','trefle:23','trefle:24']);
  assert.equal(fourth.meta.hasMore, true);
  assert.equal((await fetch(base + '/plants/search?q=')).status, 400);
  assert.equal((await fetch(base + '/plants/search?q=x&page=-1')).status, 400);
  assert.equal((await fetch(base + '/other')).status, 404);
  assert.equal((await fetch(base + '/plants/search?q=x', { method: 'POST' })).status, 405);
  assert.equal((await fetch(base + '/plants/search?q=x', { headers: { Origin: 'https://example.test' } })).status, 403);
});

test('upstream response errors propagate without publishing raw upstream data', async t => {
  const server = createServer({ get: async () => { throw new ApiError(429, 'Please wait.', 10); } });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); });
  const response = await fetch('http://127.0.0.1:' + server.address().port + '/plants/search?q=example');
  assert.equal(response.status, 429);
  assert.equal(response.headers.get('retry-after'), '10');
  assert.deepEqual(await response.json(), { error: 'Please wait.' });
});
