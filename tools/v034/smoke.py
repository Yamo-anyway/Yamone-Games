"""Test the signed APK; no synthetic rankings or entitlement changes reach production."""
from pathlib import Path
import subprocess as sp, time, re, json, xml.etree.ElementTree as ET, html
OUT=Path('smoke-output');OUT.mkdir(exist_ok=True)
PKG='com.yamone.games'; checks=[]
def adb(*args,check=True):
    p=sp.run(['adb',*args],capture_output=True,timeout=45)
    if check and p.returncode:raise RuntimeError(p.stderr.decode(errors='replace')+p.stdout.decode(errors='replace'))
    return p.stdout

def shell(*a,**kw):return adb('shell',*a,**kw).decode(errors='replace')
def ensure(value,message):
    if not value:raise AssertionError(message)
    checks.append(message);print('PASS:',message,flush=True)
def shot(name): (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def tree():
    shell('uiautomator','dump','/sdcard/yamone-ui.xml',check=False)
    text=adb('exec-out','cat','/sdcard/yamone-ui.xml').decode(errors='replace')
    (OUT/'last-ui.xml').write_text(text)
    return ET.fromstring(text)
def nodes(t=None):return list((t if t is not None else tree()).iter('node'))
def get(text,prefix=False,t=None):
    for n in nodes(t):
        for key in ('text','content-desc'):
            v=n.get(key,'')
            if (v.startswith(text) if prefix else v==text):return n
    raise AssertionError('Missing UI: '+text)
def has(text,t=None):
    try:get(text,t=t);return True
    except AssertionError:return False

def bounds(n):return list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
def xy(n):
    a=bounds(n);return (a[0]+a[2])//2,(a[1]+a[3])//2

def tapxy(x,y):shell('input','tap',str(x),str(y))
def tap(text,prefix=False):tapxy(*xy(get(text,prefix)));time.sleep(.35)
def stop():shell('am','force-stop',PKG)
def launch():
    stop();ensure('Status: ok' in shell('am','start','-W','-n',PKG+'/.MainActivity'),'Activity launch')
    time.sleep(1.0)
def seed(name,body):
    p=OUT/(name+'.xml');p.write_text('<?xml version="1.0" encoding="utf-8" standalone="yes"?><map>'+body+'</map>')
    adb('push',str(p),'/data/local/tmp/'+p.name)
    shell('run-as',PKG,'mkdir','-p','shared_prefs')
    shell('run-as',PKG,'cp','/data/local/tmp/'+p.name,'shared_prefs/'+p.name);p.unlink()
def pref(name):return ET.fromstring(adb('exec-out','run-as',PKG,'cat','shared_prefs/'+name+'.xml').decode())
def pvalue(name,key):
    n=next(n for n in pref(name) if n.get('name')==key)
    return n.get('value',n.text)
def reward(expiry):
    stop();seed('yamone_ad_access',f'<long name="ad_free_until" value="{expiry}"/><boolean name="first_game_used" value="true"/>')
def banner(expected,label):ensure(has('상단 배너 광고 영역')==expected,label)
def open_game(title):launch();tap(title)
def scroll_to(text):
    for _ in range(3):
        t=tree()
        try:return get(text,t=t)
        except AssertionError:
            w,h=map(int,re.findall(r'(\d+)x(\d+)',shell('wm','size'))[-1])
            shell('input','swipe',str(w//2),str(int(h*.75)),str(w//2),str(int(h*.5)),'300')
    return get(text)
try:
    shell('svc','wifi','disable',check=False);shell('svc','data','disable',check=False)
    adb('logcat','-c')
    ensure('Success' in adb('install','-r','baseline/Yamone-Games.apk').decode(),'Baseline 0.3.03 installed')
    seed('yamone_games_settings','<string name="nickname">YamoneQA</string>')
    seed('yamone_online_ranking','<boolean name="enabled" value="false"/><boolean name="policy_migrated_v2" value="true"/>')
    data=html.escape(json.dumps([{'score':32101,'ended_at':1789540000000,'nickname':'YamoneQA'}]))
    seed('yamone_arcade_records','<string name="records_snow_rush_shards_ms">'+data+'</string>')
    seed('yamone_sudoku_game','<int name="best_normal" value="321"/><int name="completed_normal" value="1"/><int name="total_completed" value="1"/>')
    reward(0)
    ensure('Success' in adb('install','-r','apk/Yamone-Games.apk').decode(),'Same-signature update to 0.3.04')
    launch();get('야모네 게임');get('00:00:00');banner(True,'Stage-one zero-timer banner retained')
    shot('01-home-unchanged')
    tap('눈덩이 러시');get('최고 기록');get('00:32.101');get('좌우 이동 경계')
    banner(True,'No forced ad and top banner on Snow Rush')
    shot('02-snow-ready')
    t=tree();area=bounds(get('눈덩이 경기장',t=t));pauser=xy(get('일시정지',t=t));starter=xy(get('시작하기',t=t))
    x0,y0,x1,y1=area;cx=(x0+x1)//2;cy=(y0+y1)//2
    tapxy(*starter)
    time.sleep(.2)
    shell('input','swipe',str(cx),str(cy),str(x0),str(cy),'150')
    shot('03-snow-left-edge')
    shell('input','swipe',str(x0+5),str(cy),str(x1-5),str(cy),'150')
    shot('04-snow-right-edge')
    tapxy(*pauser);time.sleep(.4);get('잠깐 쉬어가요')
    paused1=tree();get('게임과 생존 시간은 멈춰 있어요.',t=paused1)
    # Stable paused timer from dialog: no time is gained while the app sleeps.
    clock1=[n.get('text') for n in nodes(paused1) if re.fullmatch(r'\d{2,}:\d{2}\.\d{3}',n.get('text',''))]
    time.sleep(1.1)
    clock2=[n.get('text') for n in nodes() if re.fullmatch(r'\d{2,}:\d{2}\.\d{3}',n.get('text',''))]
    ensure(clock1 and clock1==clock2,'Milliseconds stop while paused')
    shot('05-snow-pause')
    # A fresh round avoids UI-dump latency counting as gameplay. Keep player at x~=0.30.
    launch();tap('눈덩이 러시');t=tree()
    pauser=xy(get('일시정지',t=t));starter=xy(get('시작하기',t=t));a=bounds(get('눈덩이 경기장',t=t))
    x0,y0,x1,y1=a;cx=(x0+x1)//2;cy=(y0+y1)//2
    tapxy(*starter)
    shell('input','swipe',str(cx),str(cy),str(cx-int((x1-x0)*.20/1.1)),str(cy),'180')
    time.sleep(6.05)
    shot('06-snow-live-fragments')
    tapxy(*pauser);time.sleep(.4)
    get('잠깐 쉬어가요');ensure(True,'New round stays playable through first shard emissions')
    shot('07-snow-live-paused')
    tap('계속하기');time.sleep(.15)
    shell('input','keyevent','3');time.sleep(.6)
    shell('am','start','-W','-n',PKG+'/.MainActivity');time.sleep(.7)
    ensure(has('잠깐 쉬어가요'),'Background-return pauses the round')
    shot('08-background-return')
    ensure(json.loads(pvalue('yamone_arcade_records','records_snow_rush_shards_ms'))[0]['score']==32101,'Previous millisecond best preserved')
    ensure(pvalue('yamone_online_ranking','enabled')=='false','Sharing OFF preserved')
    ensure(pvalue('yamone_sudoku_game','best_normal')=='321','Sudoku record preserved')
    reward(int(time.time()*1000)+300000)
    launch();banner(False,'Positive reward still hides home banner')
    tap('눈덩이 러시');banner(False,'Positive reward still hides game banner');get('좌우 이동 경계');shot('09-snow-adfree')
    # Regressions: only Snow Rush changes this release; the other three screens still open.
    for title in ['스도쿠','빙하 점프','물고기 냠냠']:
        open_game(title);banner(False,'Stage-one ad-free behavior retained: '+title)
        ensure(shell('pidof',PKG).strip()!='','Unchanged game opens: '+title)
    # Narrow width + larger text with the same full-bleed background.
    stop();shell('wm','size','1080x2160');shell('wm','density','480');shell('settings','put','system','font_scale','1.3')
    open_game('눈덩이 러시');get('시작하기');get('좌우 이동 경계');shot('10-snow-360dp-font130')
    logs=adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace');(OUT/'runtime.txt').write_text(logs)
    ensure('FATAL EXCEPTION' not in logs,'No fatal Android exception')
    result={'status':'passed','checks':checks,'liveAdsTested':False,'physicalDeviceTested':False,'productionRanking':'not contacted'}
    (OUT/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
except Exception as exc:
    try:shot('failure')
    except Exception:pass
    try:(OUT/'runtime.txt').write_bytes(adb('logcat','-d','-s','AndroidRuntime:E'))
    except Exception:pass
    (OUT/'report.json').write_text(json.dumps({'status':'failed','error':str(exc),'checks':checks},ensure_ascii=False,indent=2))
    raise
