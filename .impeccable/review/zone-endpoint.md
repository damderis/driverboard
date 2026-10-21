# Prayer zone endpoint verification

Checked 2026-10-21 with Node's `https.get` against the live host.

- `GET https://api.waktusolat.app/zones/3.1390/101.6869` returned HTTP 200 and `{"zone":"WLY01","state":"KUL","district":"W.P. Kuala Lumpur"}`.
- `GET https://api.waktusolat.app/zones` returned HTTP 200 and 60 zone records with `jakimCode`, `negeri`, and `daerah`. `ZoneCatalog.java` was generated from that exact response with `.impeccable/review/generate-zone-catalog.js`; WLY01 displays its source district name, `Kuala Lumpur, Putrajaya`. The response includes PHG07, absent from older lists.
- The [API documentation](https://api.waktusolat.app/docs) lists `/zones/{lat}/{long}` for GPS resolution. The app uses HTTPS, validates coordinate bounds and the returned code against the bundled catalog, and keeps the existing zone on any location or network failure.

The lookup resolves the phone's approximate network location. Zone boundaries and car hardware results still need verification on the owner's device.
