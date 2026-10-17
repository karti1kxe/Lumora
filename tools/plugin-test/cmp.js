const fs=require('fs');
const L=f=>JSON.parse(fs.readFileSync(f)).report;
const rows={};
for (const [tag,f] of [['old','report.old.json'],['new','report.new.json'],['oldWAF','report.old.waf.json'],['newWAF','report.new.waf.json']]) {
  const r=L(f); let ok=0,bad=0; for (const p in r) for (const fn in r[p]) (r[p][fn].ok?ok++:bad++);
  console.log(tag,'ok',ok,'fail',bad);
}
const o=L('report.old.waf.json'), n=L('report.new.waf.json');
console.log('\nWAF mode: differences old->new');
for (const p in n) for (const fn in n[p]) { const a=o[p][fn], b=n[p][fn]; const sa=a.ok?'ok':a.payload, sb=b.ok?'ok':b.payload; if (sa!==sb) console.log(p+'.'+fn,'\n   old:',String(sa).slice(0,110),'\n   new:',String(sb).slice(0,110)); }
