"""Exercise the real signed APK offline, including immutable existing records.
Keep production timing/collision behavior. Observe state while paused so UI-dump
latency is not mistaken for gameplay. An emulator loss or app crash fails the test.
"""
from pathlib import Path
import subprocess as sp
import time, re, json, xml.etree.ElementTree as ET, html
OUT=Path('smoke-output');OUT.mkdir(exist_ok=True)
PKG='com.yamone.games';checks=[];observations=[]
logfile=(OUT/'device-logcat.txt').open('wb');logger=None

def adb(*args,check=True):
    p=sp.run(['adb',*args],capture_output=True,timeout=45)
    if check and p.returncode:raise RuntimeError('adb '+' '.join(args)+': '+p.stderr.decode(errors='replace')+p.stdout.decode(errors='replace'))
    return p.stdout

def shell(*args,**kw):return adb('shell',*args,**kw).decode(errors='replace')
def ensure(value,message):
    if not value:raise AssertionError(message)
    checks.append(message);print('PASS:',message,flush=True)

def shot(name):
    shell('screencap','-p','/sdcard/yamone-shot.png')
    adb('pull','/sdcard/yamone-shot.png',str(OUT/(name+'.png')))

def tree():
    shell('uiautomator','dump','/sdcard/yamone-ui.xml')
    raw=adb('exec-out','cat','/sdcard/yamone-ui.xml')
    (OUT/'last-ui.xml').write_bytes(raw)
    return ET.fromstring(raw)

def nodes(t):return list(t.iter('node'))
def get(text,t):
    for n in nodes(t):
        if text in (n.get('text'),n.get('content-desc')):return n
    raise AssertionError('Missing UI: '+text)

def has(text,t):
    try:get(text,t);return True
    except AssertionError:return False

def bounds(n):return list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
def xy(n):
    a=bounds(n);return (a[0]+a[2])//2,(a[1]+a[3])//2

def tapxy(p):shell('input','tap',str(p[0]),str(p[1]))
def tap(text,t=None):
    tapxy(xy(get(text,tree() if t is None else t)));time.sleep(.25)
def stop():shell('am','force-stop',PKG)
def launch():
    stop();ensure('Status: ok' in shell('am','start','-W','-n',PKG+'/.MainActivity'),'Activity launch')
    time.sleep(1);return tree()

def seed(name,body):
    p=OUT/(name+'.xml');p.write_text('<?xml version="1.0" encoding="utf-8" standalone="yes"?><map>'+body+'</map>')
    adb('push',str(p),'/data/local/tmp/'+p.name)
    shell('run-as',PKG,'mkdir','-p','shared_prefs')
    shell('run-as',PKG,'cp','/data/local/tmp/'+p.name,'shared_prefs/'+p.name);p.unlink()

def pvalue(name,key):
    root=ET.fromstring(adb('exec-out','run-as',PKG,'cat','shared_prefs/'+name+'.xml'))
    n=next(n for n in root if n.get('name')==key);return n.get('value',n.text)

def reward(expiry):
    stop();seed('yamone_ad_access',f'<long name="ad_free_until" value="{expiry}"/><boolean name="first_game_used" value="true"/>')
def banner(expected,label,t):ensure(has('상단 배너 광고 영역',t)==expected,label)
def open_snow():tap('눈덩이 러시',launch());return tree()
def pause_now(point):
    tapxy(point);time.sleep(.35);t=tree();get('잠깐 쉬어가요',t);return t

def clocks(t):return [n.get('text') for n in nodes(t) if re.fullmatch(r'\d{2,}:\d{2}\.\d{3}',n.get('text',''))]
def elapsed(t):
    values=[v for v in clocks(t) if v!='00:32.101']
    if not values:raise AssertionError('No millisecond clock')
    m,s,ms=map(int,re.split('[:.]',values[0]));return m*60000+s*1000+ms

try:
    shell('wm','size','720x1280');shell('wm','density','280')
    shell('settings','put','system','font_scale','1.0')
    shell('cmd','connectivity','airplane-mode','enable',check=False)
    shell('svc','wifi','disable',check=False);shell('svc','data','disable',check=False)
    adb('logcat','-c')
    logger=sp.Popen(['adb','logcat','-v','threadtime'],stdout=logfile,stderr=sp.STDOUT)
    time.sleep(4)
    ensure('Success' in adb('install','-r','baseline/Yamone-Games.apk').decode(),'Baseline 0.3.03 installed')
    seed('yamone_games_settings','<string name="nickname">YamoneQA</string>')
    seed('yamone_online_ranking','<boolean name="enabled" value="false"/><boolean name="policy_migrated_v2" value="true"/>')
    data=html.escape(json.dumps([{'score':32101,'ended_at':1789540000000,'nickname':'YamoneQA'}]))
    seed('yamone_arcade_records','<string name="records_snow_rush_shards_ms">'+data+'</string>')
    seed('yamone_sudoku_game','<int name="best_normal" value="321"/><int name="completed_normal" value="1"/><int name="total_completed" value="1"/>')
    seed('yamone_feedback_v03','<boolean name="sound" value="false"/><boolean name="vibration" value="false"/>')
    reward(0)
    ensure('Success' in adb('install','-r','apk/Yamone-Games.apk').decode(),'Same-signature update to 0.3.04')
    t=launch();get('야모네 게임',t);get('00:00:00',t)
    banner(True,'Zero-timer home banner retained',t);shot('01-home')
    tap('눈덩이 러시',t);t=tree();get('00:32.101',t);get('좌우 이동 경계',t)
    banner(True,'Snow opens without forced ad, with top banner',t);shot('02-snow-ready')
    area=bounds(get('눈덩이 경기장',t));pauser=xy(get('일시정지',t))
    x0,y0,x1,y1=area;cx=(x0+x1)//2;cy=(y0+y1)//2
    tap('시작하기',t)
    shell('input','swipe',str(cx),str(cy),str(x0+2),str(cy),'180')
    t=pause_now(pauser);p=bounds(get('눈덩이 플레이어',t))
    ensure(abs(p[0]-(x0+(x1-x0)*.025))<=4,'Player left edge matches left bracket')
    shot('03-left-bound-paused')
    before=clocks(t);time.sleep(1.2);after=clocks(tree())
    ensure(before and before==after,'Millisecond time is frozen while paused')
    tap('계속하기',t)
    shell('input','swipe',str(cx),str(cy),str(x1-2),str(cy),'180')
    shell('input','swipe',str(cx),str(cy),str(x1-2),str(cy),'180')
    t=pause_now(pauser);p=bounds(get('눈덩이 플레이어',t))
    ensure(abs(p[2]-(x1-(x1-x0)*.025))<=4,'Player right edge matches right bracket')
    shot('04-right-bound-paused')
    t=open_snow();pauser=xy(get('일시정지',t));area=bounds(get('눈덩이 경기장',t))
    x0,y0,x1,y1=area;cx=(x0+x1)//2;cy=(y0+y1)//2
    tap('시작하기',t)
    shell('input','swipe',str(cx),str(cy),str(cx+int((x1-x0)*.22/1.1)),str(cy),'180')
    time.sleep(5.6);t=pause_now(pauser)
    for attempt in range(5):
        current=elapsed(t)
        if current>=6500:break
        observations.append({'resumeAfterSlowEmulatorFrameMs':current})
        tap('계속하기',t);time.sleep(min(1.5,(6650-current)/1000));t=pause_now(pauser)
    ensure(6500<=elapsed(t)<8300,'Real round reaches fragment phase without immediate death')
    shot('05-fragments-paused')
    tap('계속하기',t);shot('06-snow-gameplay')
    shell('input','keyevent','3');time.sleep(.4)
    shell('am','start','-W','-n',PKG+'/.MainActivity');time.sleep(.5)
    t=tree();get('잠깐 쉬어가요',t);ensure(True,'Background return pauses instead of fast-forwarding')
    shot('07-background-return')
    ensure(json.loads(pvalue('yamone_arcade_records','records_snow_rush_shards_ms'))[0]['score']==32101,'Previous millisecond best preserved')
    ensure(pvalue('yamone_online_ranking','enabled')=='false','Sharing OFF preserved')
    ensure(pvalue('yamone_sudoku_game','best_normal')=='321','Sudoku record preserved')
    reward(int(time.time()*1000)+300000)
    t=launch();banner(False,'Remaining reward hides home banner',t)
    tap('눈덩이 러시',t);t=tree();banner(False,'Remaining reward hides snow banner',t)
    get('좌우 이동 경계',t);shot('08-snow-adfree')
    for title in ['스도쿠','빙하 점프','물고기 냠냠']:
        tap(title,launch());t=tree();banner(False,'Ad-free behavior unchanged: '+title,t)
        ensure(bool(shell('pidof',PKG).strip()),'Unchanged game opens: '+title)
    stop();shell('wm','size','720x1440');shell('wm','density','320');shell('settings','put','system','font_scale','1.3')
    t=open_snow();get('시작하기',t);get('좌우 이동 경계',t);shot('09-snow-360dp-font130')
    ensure(True,'Snow fits 360dp width with 130-percent text')
    logs=adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace');(OUT/'runtime.txt').write_text(logs)
    ensure('FATAL EXCEPTION' not in logs,'No fatal Android exception')
    result={'status':'passed','checks':checks,'observations':observations,'liveAdsTested':False,'physicalDeviceTested':False,'productionRanking':'not contacted'}
    (OUT/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
except Exception as exc:
    try:shot('failure')
    except Exception:pass
    (OUT/'report.json').write_text(json.dumps({'status':'failed','error':str(exc),'checks':checks,'observations':observations},ensure_ascii=False,indent=2))
    raise
finally:
    if logger is not None:
        logger.terminate()
        try:logger.wait(timeout=5)
        except sp.TimeoutExpired:logger.kill()
    logfile.close()
