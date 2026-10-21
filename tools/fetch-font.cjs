const https=require('https'),fs=require('fs');
function get(url){return new Promise((resolve,reject)=>https.get(url,{headers:{'User-Agent':'DriveBoard'}},r=>{let chunks=[];r.on('data',x=>chunks.push(x));r.on('end',()=>r.statusCode===200?resolve(Buffer.concat(chunks)):reject(Error(r.statusCode+' '+url)));}).on('error',reject));}
(async()=>{let files=JSON.parse(await get('https://api.github.com/repos/google/fonts/contents/ofl/atkinsonhyperlegiblenext'));for(let name of ['AtkinsonHyperlegibleNext[wght].ttf','OFL.txt']){let f=files.find(x=>x.name===name);let dest=name==='OFL.txt'?'licenses/atkinsonhyperlegiblenext/OFL.txt':'app/src/main/res/font/atkinson_next.ttf';fs.writeFileSync(dest,await get(f.download_url));console.log(dest);}})().catch(e=>{console.error(e);process.exitCode=1});

