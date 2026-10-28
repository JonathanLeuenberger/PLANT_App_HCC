import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const PAGE_SIZE = 6;
const API = 'https://trefle.io/api/v1';
const text = value => typeof value === 'string' ? value.trim() : '';
const https = value => {
  try { const u = new URL(value); return u.protocol === 'https:' && !u.username && !u.password ? u.href : ''; }
  catch { return ''; }
};
const stripHtml = value => text(value).replace(/<[^>]*>/g, '').replace(/&amp;/g, '&').replace(/&quot;/g, '"');

export class ApiError extends Error {
  constructor(status, message, retryAfter = 0) { super(message); this.status = status; this.retryAfter = retryAfter; }
}

export async function readToken() {
  let token = process.env.TREFLE_TOKEN || '';
  if (!token) {
    try { token = await readFile(process.env.TREFLE_TOKEN_FILE || join(homedir(), '.config/worldplant-search/trefle.token'), 'utf8'); }
    catch { return ''; }
  }
  token = token.trim();
  return token && token !== 'PASTE_YOUR_TREFLE_TOKEN_HERE' ? token : '';
}

/** No token appears in a URL, response, repository file or log. */
export function createTrefleClient({ tokenReader = readToken, fetcher = fetch, now = Date.now } = {}) {
  const cache = new Map();
  const pending = new Map();
  let requests = [], pausedUntil = 0, lastToken = '';
  return async function get(path) {
    const token = await tokenReader();
    if (!token) throw new ApiError(503, 'Trefle is not configured on the search server.');
    if (token !== lastToken) { cache.clear(); pausedUntil = 0; lastToken = token; }
    const time = now();
    const saved = cache.get(path);
    if (saved && saved.until > time) return saved.value;
    if (pending.has(path)) return pending.get(path);
    if (time < pausedUntil) throw new ApiError(429, 'Trefle is busy. Please try again shortly.', Math.ceil((pausedUntil-time)/1000));
    requests = requests.filter(t => t > time - 60_000);
    if (requests.length >= 50) throw new ApiError(429, 'Please wait a minute before searching again.', 60);
    requests.push(time);
    const work = (async () => {
      let response;
      try {
        response = await fetcher(API + path, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'application/json', 'User-Agent': 'WorldPlant-class-project/1.0' },
          signal: AbortSignal.timeout(12_000), redirect: 'error'
        });
      } catch { throw new ApiError(502, 'Trefle could not be reached. Please try again.'); }
      if (!response.ok) {
        if (response.status === 404) throw new ApiError(404, 'This plant record is not available from Trefle.');
        if (response.status === 401 || response.status === 403) throw new ApiError(503, 'The search server needs a valid Trefle token.');
        if (response.status === 429) {
          const retry = Math.min(3600, Math.max(1, Number(response.headers.get('retry-after')) || 60));
          pausedUntil = now() + retry * 1000;
          throw new ApiError(429, 'Trefle is busy. Please try again shortly.', retry);
        }
        throw new ApiError(502, 'Trefle could not complete this search. Please try again.');
      }
      const reader = response.body.getReader();
      const chunks = []; let size = 0;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > 4_000_000) { await reader.cancel(); throw new ApiError(502, 'The plant record is too large to display.'); }
        chunks.push(Buffer.from(value));
      }
      let data;
      try { data = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
      catch { throw new ApiError(502, 'Trefle returned an unreadable response.'); }
      // Upstream links should not contain credentials; redact defensively before retaining data.
      data = JSON.parse(JSON.stringify(data).replaceAll(token, '[redacted]'));
      if (cache.size >= 120) cache.delete(cache.keys().next().value);
      cache.set(path, { value: data, until: now() + 10 * 60_000 });
      return data;
    })();
    pending.set(path, work);
    try { return await work; } finally { pending.delete(path); }
  };
}

/** Preserve copyright text. Without image credits, show the app's leaf placeholder. */
export function normalizePlant(row, detail, version = '') {
  const p = detail || row;
  const scientific = text(p.scientific_name) || text(row.scientific_name);
  const slug = text(p.slug) || text(row.slug);
  const name = text(p.common_name) || text(row.common_name) || scientific;
  const url = 'https://trefle.io/explore/species/' + encodeURIComponent(slug || String(row.id));
  const facts = [];
  if (text(p.family) || text(row.family)) facts.push('Family: ' + (text(p.family) || text(row.family)));
  if (text(p.genus) || text(row.genus)) facts.push('Genus: ' + (text(p.genus) || text(row.genus)));
  const growth = p.growth || {}, specs = p.specifications || {};
  if (text(growth.description)) facts.push(stripHtml(growth.description));
  if (text(p.observations)) facts.push(stripHtml(p.observations));
  if (text(specs.growth_habit)) facts.push('Growth habit: ' + text(specs.growth_habit));
  const native = p.distribution?.native;
  if (Array.isArray(native) && native.length) facts.push('Native to: ' + native.map(v => typeof v === 'string' ? v : text(v.name)).filter(Boolean).join(', '));
  if (Number.isInteger(growth.light) && growth.light >= 1 && growth.light <= 9)
    facts.push('Habitat light indicator: ' + growth.light + '/9 (deep shade to full sun). This ecological indicator is not an indoor care schedule.');
  const sourceLines = ['Data: Trefle.io (CC BY 4.0)', version ? 'Dataset: ' + version : ''];
  for (const s of Array.isArray(p.sources) ? p.sources : []) {
    const credit = stripHtml(s.citation) || text(s.name);
    const sourceUrl = https(s.url);
    if (credit) sourceLines.push(credit + (sourceUrl ? '\n' + sourceUrl : ''));
  }
  const pictures = Object.values(p.images || {}).flatMap(a => Array.isArray(a) ? a : []);
  // A copyright notice alone is not a reusable-photo license.
  const reusable = credit => /\bcc[\s-]*by(?:[\s-]*sa)?(?:[\s.)]|$)|\bcc0\b|public domain/i.test(credit);
  const photo = pictures.find(i => https(i.image_url) && reusable(text(i.copyright)));
  return {
    id: 'trefle:' + row.id, name, scientific,
    description: facts.join('\n\n') || 'No additional botanical information is available for this plant.',
    image: photo ? https(photo.image_url) : '',
    photoCredit: photo ? stripHtml(photo.copyright) : '',
    photoLicense: photo ? 'Original image copyright and license shown above' : '',
    photoSource: photo ? url + '#images' : '',
    sourceName: 'Trefle', sourceUrl: url, dataCredits: sourceLines.filter(Boolean).join('\n\n'),
    curated: false, water: '', light: '', careUrl: '', wikiUrl: ''
  };
}

async function mapLimited(items, limit, work) {
  const result = new Array(items.length); let next = 0;
  await Promise.all(Array.from({ length: Math.min(items.length, limit) }, async () => {
    while (next < items.length) { const i = next++; result[i] = await work(items[i]); }
  }));
  return result;
}

export function createServer({ get = createTrefleClient(), tokenReader = readToken, now = Date.now } = {}) {
  // This is a public, read-only class demo. Bound use of the shared account even if
  // a caller bypasses the Android interface; an Origin check is not authentication.
  let searches = [], activeSearches = 0;
  const server = http.createServer(async (req, res) => {
    let searching = false;
    const send = (status, data, retry = 0) => {
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff', ...(retry ? { 'Retry-After': String(retry) } : {}) });
      res.end(JSON.stringify(data));
    };
    try {
      // No browser origins, generic URL forwarding, writes or token-setting endpoint.
      if (req.headers.origin) throw new ApiError(403, 'Browser requests are not supported.');
      if (req.method !== 'GET') throw new ApiError(405, 'Only GET requests are supported.');
      const u = new URL(req.url, 'http://localhost');
      if (u.pathname === '/health') return send(200, { service: 'WorldPlant Trefle search', configured: Boolean(await tokenReader()) });
      if (u.pathname !== '/plants/search') throw new ApiError(404, 'Not found.');
      const q = (u.searchParams.get('q') || '').trim(), page = Number(u.searchParams.get('page') || 1);
      if (!q || q.length > 120 || !Number.isInteger(page) || page < 1 || page > 1000) throw new ApiError(400, 'Enter a plant name and valid results page.');
      const time = now();
      searches = searches.filter(t => t > time - 60_000);
      if (searches.length >= 30 || activeSearches >= 4)
        throw new ApiError(429, 'The shared search server is busy. Please try again in a minute.', 60);
      searches.push(time);
      activeSearches++;
      searching = true;
      const first = (page - 1) * PAGE_SIZE;
      const apiPage = Math.floor(first / 20) + 1;
      const response = await get('/plants/search?q=' + encodeURIComponent(q) + '&page=' + apiPage);
      if (!Array.isArray(response.data)) throw new ApiError(502, 'Trefle returned an unreadable plant list.');
      const offset = first % 20;
      let selected = response.data.slice(offset, offset + PAGE_SIZE);
      const total = Number(response.meta?.total) || response.data.length;
      if (selected.length < PAGE_SIZE && first + selected.length < total) {
        const extra = await get('/plants/search?q=' + encodeURIComponent(q) + '&page=' + (apiPage + 1));
        selected = selected.concat((extra.data || []).slice(0, PAGE_SIZE-selected.length));
      }
      const plants = await mapLimited(selected, 3, async row => {
        if (!Number.isSafeInteger(row.id) || row.id < 1) throw new ApiError(502, 'Trefle returned an invalid plant record.');
        const full = await get('/species/' + row.id);
        if (!full.data || typeof full.data !== 'object') throw new ApiError(502, 'Trefle returned an unreadable plant record.');
        return normalizePlant(row, full.data, response.meta?.dataset_version || '');
      });
      send(200, { data: plants, meta: { page, total, hasMore: first + plants.length < total } });
    } catch (e) {
      send(e instanceof ApiError ? e.status : 502,
        { error: e instanceof ApiError ? e.message : 'Plant search is temporarily unavailable.' }, e.retryAfter || 0);
    } finally {
      if (searching) activeSearches--;
    }
  });
  server.headersTimeout = 10_000;
  server.requestTimeout = 60_000;
  server.maxHeadersCount = 30;
  return server;
}

export function listenOptions(env = process.env) {
  const port = Number(env.PORT || 8765);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid server port.');
  // Render must bind a public interface; local demos stay on loopback by default.
  return { port, host: env.HOST || (env.RENDER === 'true' ? '0.0.0.0' : '127.0.0.1') };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { port, host } = listenOptions();
  createServer().listen(port, host, () => console.log('WorldPlant Trefle server listening on ' + host + ':' + port));
}
