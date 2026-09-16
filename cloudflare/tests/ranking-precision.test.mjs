import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { webcrypto } from 'node:crypto';
globalThis.crypto ??= webcrypto;
const source = readFileSync(new URL('../ranking-worker.js',import.meta.url),'utf8');
const {default:worker} = await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
function environment() {
  const db=new DatabaseSync(':memory:');
  db.exec(`CREATE TABLE leaderboard(player_id TEXT,nickname TEXT,country_code TEXT,game_id TEXT,mode_id TEXT,best_score INTEGER,achieved_at INTEGER,updated_at INTEGER,PRIMARY KEY(player_id,game_id,mode_id));`);
  return {RANKING_SIGNING_SECRET:'local-test-only-not-production',DB:{prepare(sql){
    let values=[];const stmt=db.prepare(sql);return {
      bind(...args){values=args;return this;},async first(){return stmt.get(...values)??null;},
      async all(){return {results:stmt.all(...values)};},async run(){stmt.run(...values);return {success:true};}
    };
  },async batch(statements){return Promise.all(statements.map(s=>s.run()));}}};
}
async function request(env,path,method='GET',body) {
  const response=await worker.fetch(new Request('https://local.test'+path,{method,headers:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)}),env);
  return {status:response.status,data:await response.json()};
}
const player='yamone-local-test-player-0001';
function score(n,id=player,mode='shards_ms',unit='milliseconds') {
  return {playerId:id,nickname:id.slice(-4),gameId:'snow_rush',modeId:mode,score:n,scoreUnit:unit};
}
test('milliseconds sort without truncating to seconds',async()=>{
  const env=environment();
  await request(env,'/v1/ranking/submit','POST',score(32101));
  await request(env,'/v1/ranking/submit','POST',score(32109,'yamone-local-test-player-0002'));
  const r=await request(env,'/v1/ranking/snow_rush/shards_ms?playerId='+player);
  assert.equal(r.status,200);assert.deepEqual(r.data.top.map(x=>x.score),[32109,32101]);
  assert.equal(r.data.me.rank,2);assert.equal(r.data.me.score,32101);
});
test('legacy seconds are neither deleted nor mixed with new rule scores',async()=>{
  const env=environment();
  await request(env,'/v1/ranking/submit','POST',score(55,player,'normal','seconds'));
  await request(env,'/v1/ranking/submit','POST',score(32109));
  const old=await request(env,'/v1/ranking/snow_rush/normal');const modern=await request(env,'/v1/ranking/snow_rush/shards_ms');
  assert.equal(old.data.top[0].score,55);assert.equal(modern.data.top[0].score,32109);
  assert.equal(old.data.totalPlayers,1);assert.equal(modern.data.totalPlayers,1);
});
test('sub-second improvements update the same player, worse results do not',async()=>{
  const env=environment();
  for(const n of [32101,32109,32099]) await request(env,'/v1/ranking/submit','POST',score(n));
  const r=await request(env,'/v1/ranking/snow_rush/shards_ms');assert.equal(r.data.top[0].score,32109);assert.equal(r.data.top.length,1);
});
test('wrong units, fractions and duration beyond one day are rejected',async()=>{
  const env=environment();
  assert.equal((await request(env,'/v1/ranking/submit','POST',score(32,player,'shards_ms','seconds'))).data.error,'INVALID_SCORE_UNIT');
  assert.equal((await request(env,'/v1/ranking/submit','POST',score(32100.5))).data.error,'INVALID_SCORE');
  assert.equal((await request(env,'/v1/ranking/submit','POST',score(86400001))).data.error,'SCORE_OUT_OF_RANGE');
  assert.equal((await request(env,'/v1/ranking/submit','POST',score(86400000))).status,200);
});
test('selected deletion supports both snow generations plus the other three modes',async()=>{
  const env=environment();
  await request(env,'/v1/ranking/submit','POST',score(55,player,'normal','seconds'));
  await request(env,'/v1/ranking/submit','POST',score(32109));
  const records=[['ice_jump','normal'],['fish_munch','normal'],['fish_munch','time_attack'],['snow_rush','normal'],['snow_rush','shards_ms']].map(([gameId,modeId])=>({gameId,modeId}));
  const r=await request(env,'/v1/ranking/player/games','DELETE',{playerId:player,records});assert.equal(r.status,200);
  assert.equal((await request(env,'/v1/ranking/snow_rush/shards_ms')).data.totalPlayers,0);
  assert.equal((await request(env,'/v1/ranking/snow_rush/normal')).data.totalPlayers,0);
});
test('health advertises supported rule and precision without changing DB schema',async()=>{
  const r=await request(environment(),'/health');assert.equal(r.data.snowRushPrecision,'milliseconds');assert.ok(r.data.snowRushRules.includes('shards_ms'));
});
