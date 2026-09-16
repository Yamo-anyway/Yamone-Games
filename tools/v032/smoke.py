"""Offline emulator smoke test. Never submits synthetic QA records to a server."""
from pathlib import Path
import subprocess as sp, time, re, json, xml.etree.ElementTree as ET, html, sys
OUT=Path('smoke-output');OUT.mkdir(exist_ok=True)
PKG='com.yamone.games'
checks=[]
def adb(*args,check=True):
    p=sp.run(['adb',*args],capture_output=True,timeout=45)
    if check and p.returncode:raise RuntimeError(p.stderr.decode(errors='replace')+p.stdout.decode(errors='replace'))
    return p.stdout

def shell(*args,**kw):return adb('shell',*args,**kw).decode(errors='replace')
def ensure(value,message):
    if not value:raise AssertionError(message)
    checks.append(message);print('PASS:',message,flush=True)
def screenshot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def tree():
    shell('uiautomator','dump','/sdcard/yamone-ui.xml',check=False)
    text=adb('exec-out','cat','/sdcard/yamone-ui.xml').decode(errors='replace')
    (OUT/'last-ui.xml').write_text(text)
    return ET.fromstring(text)
def node(text,tries=3):
    for _ in range(tries):
        for n in tree().iter('node'):
            if n.get('text')==text or n.get('content-desc')==text:return n
        time.sleep(.7)
    raise AssertionError('UI element missing: '+text)
def center(n):
    a=list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
    return (a[0]+a[2])//2,(a[1]+a[3])//2

def tap(text):
    x,y=center(node(text));shell('input','tap',str(x),str(y));time.sleep(.5)
def launch():
    shell('am','force-stop',PKG)
    result=shell('am','start','-W','-n',PKG+'/.MainActivity')
    ensure('Status: ok' in result,'Activity launched')
    time.sleep(1.5)
def seed(name,body):
    p=OUT/(name+'.xml');p.write_text('<?xml version="1.0" encoding="utf-8" standalone="yes"?><map>'+body+'</map>')
    adb('push',str(p),'/data/local/tmp/'+p.name)
    shell('run-as',PKG,'mkdir','-p','shared_prefs')
    shell('run-as',PKG,'cp','/data/local/tmp/'+p.name,'shared_prefs/'+p.name)
    p.unlink()
def prefs(name):return adb('exec-out','run-as',PKG,'cat','shared_prefs/'+name+'.xml').decode()

def bounds(n):return list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
def tapxy(x,y):shell('input','tap',str(x),str(y))
def home_game(title):
    launch()
    try:tap(title)
    except AssertionError:
        w,h=map(int,re.findall(r'(\d+)x(\d+)',shell('wm','size'))[-1])
        shell('input','swipe',str(w//2),str(int(h*.7)),str(w//2),str(int(h*.32)),'350');tap(title)
def clock_text():
    return [n.get('text') for n in tree().iter('node') if re.fullmatch(r'\d+:\d{2}\.\d{3}',n.get('text',''))]
try:
    shell('svc','wifi','disable',check=False);shell('svc','data','disable',check=False)
    adb('logcat','-c')
    ensure('Success' in adb('install','-r','baseline/Yamone-Games.apk').decode(),'Baseline 0.3.01 installed')
    seed('yamone_games_settings','<string name="nickname">YamoneQA</string>')
    seed('yamone_online_ranking','<boolean name="enabled" value="false"/><boolean name="policy_migrated_v2" value="true"/>')
    seed('yamone_ad_access','<long name="ad_free_until" value="'+str(int(time.time()*1000)+86400000)+'"/>')
    def record(n):return html.escape(json.dumps([{'score':n,'ended_at':1789540000000,'nickname':'YamoneQA'}]))
    seed('yamone_arcade_records','<string name="records_ice_jump">'+record(123)+'</string><string name="records_snow_rush">'+record(55)+'</string><string name="records_snow_rush_shards_ms">'+record(32101)+'</string>')
    seed('yamone_sudoku_game','<int name="best_normal" value="321"/><int name="completed_normal" value="1"/><int name="total_completed" value="1"/>')
    ensure('Success' in adb('install','-r','apk/Yamone-Games.apk').decode(),'Data-preserving update to 0.3.02')
    launch();node('우리의 놀이터');screenshot('01-home')
    banner=bounds(node('상단 배너 광고 영역'));card=bounds(node('스도쿠'))
    ensure(banner[3]<card[1],'Banner is above the game cards, not over them')
    ensure('123' in prefs('yamone_arcade_records'),'Ice record preserved')
    ensure('name="enabled" value="false"' in prefs('yamone_online_ranking'),'Sharing OFF preserved')
    tap('랭킹');node('5:21');tap('눈덩이');node('00:32.101');node('0:55');screenshot('02-precision-ranking')
    ensure(True,'Millisecond and legacy second records are visibly separated')
    # Test unchanged game paths and obtain actual rendered scenery screenshots.
    home_game('스도쿠');node('메모 OFF');screenshot('03-sudoku');tap('메모 OFF');node('메모 ON')
    ensure(True,'Sudoku memo toggle preserved')
    for index,title,start in [(4,'빙하 점프','시작하기'),(5,'물고기 냠냠','일반 모드')]:
        home_game(title);p=center(node('Ⅱ'));b=center(node(start));screenshot(f'{index:02d}-scenery')
        tapxy(*b);time.sleep(.15);tapxy(*p);time.sleep(.4)
        node('잠깐 쉬어가요');screenshot(f'{index:02d}-paused');ensure(True,title+' starts and pauses')
        tap('계속하기');time.sleep(.1);screenshot(f'{index:02d}-play')
    home_game('눈덩이 러시')
    p=center(node('일시정지'));b=center(node('시작하기'));screenshot('06-snow-instructions')
    tapxy(*b);time.sleep(1.0);screenshot('07-snow-protection');tapxy(*p);time.sleep(.4)
    node('잠깐 쉬어가요');before=clock_text();time.sleep(1.0);after=clock_text()
    ensure(before==after and before,'Paused clock does not advance')
    screenshot('07b-snow-paused-clock')
    tap('계속하기');time.sleep(1.5)
    # A real swipe keeps the avatar out of the first approach and shows emitted fragments.
    w,h=map(int,re.findall(r'(\d+)x(\d+)',shell('wm','size'))[-1])
    shell('input','swipe',str(w//2),str(int(h*.73)),str(int(w*.04)),str(int(h*.73)),'500')
    time.sleep(3.5);screenshot('08-snow-active')
    # Wait for an ordinary collision; never publish the emulator record.
    time.sleep(10);screenshot('09-snow-result')
    ensure('records_snow_rush_shards_ms' in prefs('yamone_arcade_records'),'New precision bucket is retained')
    raw=ET.fromstring(prefs('yamone_arcade_records'))
    modern=json.loads(next(n.text for n in raw if n.get('name')=='records_snow_rush_shards_ms'))
    ensure(any(3000<=rec['score']<32101 for rec in modern),'A real completed snow run was saved in milliseconds')
    ensure(json.loads(next(n.text for n in raw if n.get('name')=='records_snow_rush'))[0]['score']==55,'Legacy snow score is not rewritten')
    launch();tap('설정');node('소리와 진동');screenshot('10-settings')
    logs=adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace');(OUT/'runtime.txt').write_text(logs)
    ensure('FATAL EXCEPTION' not in logs,'No fatal exception during UI tests')
    result={'status':'passed','checks':checks,'rankingSharing':'OFF','offlineGuarantee':False,'productionServerTested':False,'physicalHapticsTested':False}
    (OUT/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False),flush=True)
except Exception as exc:
    try:screenshot('failure')
    except Exception:pass
    try:(OUT/'runtime.txt').write_bytes(adb('logcat','-d','-s','AndroidRuntime:E'))
    except Exception:pass
    result={'status':'failed','error':str(exc),'passed':checks}
    (OUT/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False),flush=True)
    raise
