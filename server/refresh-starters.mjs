// Refresh the 16 offline botanical profiles. Existing credited photos and NC State care notes remain.
import { readFile, writeFile } from 'node:fs/promises';
import { createTrefleClient, normalizePlant } from './trefle.mjs';
const catalogFile = new URL('../app/src/main/assets/plantsearch/catalog.json', import.meta.url);
const starters = JSON.parse(await readFile(catalogFile, 'utf8'));
const get = createTrefleClient();
const updated = [];
for (const old of starters) {
  const slug = old.scientific.toLowerCase().replaceAll(' ', '-');
  let plant;
  try {
    let response;
    try { response = await get('/species/' + encodeURIComponent(slug)); }
    catch (error) {
      if (error.status !== 404) throw error;
      const found = await get('/species/search?q=' + encodeURIComponent(old.scientific));
      const exact = (found.data || []).find(p => p.scientific_name?.toLowerCase() === old.scientific.toLowerCase());
      if (!exact) throw new Error('No exact species match');
      response = await get('/species/' + exact.id);
    }
    if (!response.data?.scientific_name || !response.data.id) throw new Error('No species data');
    plant = normalizePlant(response.data, response.data, response.meta?.dataset_version || '');
  } catch (error) {
    if (error.status && error.status !== 404) throw new Error('Could not refresh ' + old.scientific + ': ' + error.message);
    console.log('No exact Trefle match for ' + old.scientific + '; retaining an original care overview.');
    plant = { scientific: old.scientific, description: old.name + ' (' + old.scientific + '). ' + old.water + ' ' + old.light,
      sourceName: 'NC State Extension', sourceUrl: old.careUrl,
      dataCredits: 'Original project care overview informed by NC State Extension. See the linked care source.', wikiUrl: '' };
  }
  updated.push({ ...plant, id: old.id, name: old.name, aliases: old.aliases, curated: true,
    water: old.water, light: old.light, careUrl: old.careUrl,
    image: old.image, photoCredit: old.photoCredit, photoLicense: old.photoLicense,
    photoLicenseUrl: old.photoLicenseUrl, photoSource: old.photoSource });
}
await writeFile(catalogFile, JSON.stringify(updated, null, 2) + '\n');
await writeFile(new URL('../docs/SEARCH_PLANT_SOURCES.json', import.meta.url), JSON.stringify(updated, null, 2) + '\n');
console.log('Refreshed ' + updated.length + ' offline profiles from Trefle. No token was written to the output.');
