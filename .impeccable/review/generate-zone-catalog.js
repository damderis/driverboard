const https = require('https');
const fs = require('fs');
const url = 'https://api.waktusolat.app/zones';
https.get(url, response => {
  if (response.statusCode !== 200) throw new Error(`HTTP ${response.statusCode}`);
  let body = '';
  response.on('data', chunk => body += chunk);
  response.on('end', () => {
    const zones = JSON.parse(body);
    if (!Array.isArray(zones) || zones.length < 60) throw new Error('Unexpected catalog');
    const lines = zones.map(z => {
      if (!/^[A-Z]{3}\d{2}$/.test(z.jakimCode) || !z.daerah || !z.negeri) throw new Error('Invalid zone');
      return `${z.jakimCode}|${z.jakimCode === 'WLY01' ? z.daerah : z.negeri + ' · ' + z.daerah}`;
    });
    const data = lines.map((line, i) => `        ${JSON.stringify(line + (i === lines.length - 1 ? '' : '\n'))}${i === lines.length - 1 ? ';' : '+'}`).join('\n');
    const code = `package local.driveboard;

/** Bundled zone names from https://api.waktusolat.app/zones, captured 2026-10-21. */
public final class ZoneCatalog {
    public static final class Zone {
        public final String code,name;
        Zone(String code,String name){this.code=code;this.name=name;}
    }
    private static final String DATA=\n${data}
    private static final Zone[] OPTIONS=parse();
    private ZoneCatalog(){}
    private static Zone[] parse(){
        String[] lines=DATA.split("\\\\n");Zone[] zones=new Zone[lines.length];
        for(int i=0;i<lines.length;i++){int at=lines[i].indexOf('|');zones[i]=new Zone(lines[i].substring(0,at),lines[i].substring(at+1));}
        return zones;
    }
    public static Zone[] options(){return OPTIONS.clone();}
    public static String name(String code){for(Zone z:OPTIONS)if(z.code.equals(code))return z.name;return code;}
}
`;
    fs.writeFileSync('app/src/main/java/local/driveboard/ZoneCatalog.java', code);
    console.log(`Bundled ${zones.length} zones`);
  });
}).on('error', error => { console.error(error); process.exitCode = 1; });
