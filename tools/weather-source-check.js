// Reproduce the weather API shape and the bundled fallback coordinates.
// Run: node tools/weather-source-check.js
const https = require('https');
const fs = require('fs');
const path = require('path');

function getJson(url) {
    return new Promise((resolve, reject) => {
        https.get(url, response => {
            let body = '';
            response.on('data', chunk => body += chunk);
            response.on('end', () => {
                if (response.statusCode !== 200) {
                    reject(new Error(`${url}: HTTP ${response.statusCode}`));
                } else {
                    resolve(JSON.parse(body));
                }
            });
        }).on('error', reject);
    });
}

function ringCentre(ring) {
    let doubleArea = 0;
    let x = 0;
    let y = 0;
    for (let i = 0; i < ring.length - 1; i++) {
        const first = ring[i];
        const second = ring[i + 1];
        const cross = first[0] * second[1] - second[0] * first[1];
        doubleArea += cross;
        x += (first[0] + second[0]) * cross;
        y += (first[1] + second[1]) * cross;
    }
    return doubleArea === 0 ? null : {
        area: Math.abs(doubleArea / 2),
        longitude: x / (3 * doubleArea),
        latitude: y / (3 * doubleArea)
    };
}

async function main() {
    const zoneFile = fs.readFileSync(path.join(__dirname,
        '../app/src/main/java/local/driveboard/ZoneCatalog.java'), 'utf8');
    const codes = [...new Set([...zoneFile.matchAll(/"([A-Z]{3}[0-9]{2})\|/g)]
        .map(match => match[1]))].sort();
    const geojson = await getJson('https://raw.githubusercontent.com/'
        + 'mptwaktusolat/jakim.geojson/master/malaysia.district-jakim.geojson');
    const sums = new Map();
    for (const feature of geojson.features) {
        const code = feature.properties.jakim_code;
        if (!code) continue;
        const polygons = feature.geometry.type === 'Polygon'
            ? [feature.geometry.coordinates] : feature.geometry.coordinates;
        for (const polygon of polygons) {
            const centre = ringCentre(polygon[0]);
            if (!centre) continue;
            const sum = sums.get(code) || {area: 0, latitude: 0, longitude: 0};
            sum.area += centre.area;
            sum.latitude += centre.latitude * centre.area;
            sum.longitude += centre.longitude * centre.area;
            sums.set(code, sum);
        }
    }
    for (const code of codes) {
        const sum = sums.get(code);
        if (sum) {
            console.log(`${code}|${(sum.latitude / sum.area).toFixed(5)}`
                + `|${(sum.longitude / sum.area).toFixed(5)}`);
        }
    }
    console.log('Missing polygons (bundled points use named locations):',
        codes.filter(code => !sums.has(code)).join(', '));

    const sample = await getJson('https://api.open-meteo.com/v1/forecast'
        + '?latitude=3.1390&longitude=101.6869'
        + '&current=temperature_2m,weather_code,is_day'
        + '&hourly=precipitation_probability&forecast_hours=2&timezone=auto');
    console.log('Open-Meteo sample current:', sample.current);
    console.log('Open-Meteo sample hourly:', sample.hourly);
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
