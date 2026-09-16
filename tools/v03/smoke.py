"""Offline emulator smoke test. Never submits synthetic QA records to a server."""
from pathlib import Path
import subprocess as sp, time, re, json, xml.etree.ElementTree as ET, html
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
            labels=n.get('text','').split('\n')+n.get('content-desc','').split('\n')
            if text in labels:return n
        time.sleep(.5)
    raise AssertionError('UI element missing: '+text)
def center(n):
    a=list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
    return (a[0]+a[2])//2,(a[1]+a[3])//2

def tap(text):
    x,y=center(node(text));shell('input','tap',str(x),str(y));time.sleep(.4)
def launch():
    shell('am','force-stop',PKG)
    result=shell('am','start','-W','-n',PKG+'/.MainActivity')
    ensure('Status: ok' in result,'Activity launched')
    time.sleep(1.2)
def seed(name,body):
    p=OUT/(name+'.xml');p.write_text('<?xml version="1.0" encoding="utf-8" standalone="yes"?><map>'+body+'</map>')
    adb('push',str(p),'/data/local/tmp/'+p.name)
    shell('run-as',PKG,'mkdir','-p','shared_prefs')
    shell('run-as',PKG,'cp','/data/local/tmp/'+p.name,'shared_prefs/'+p.name)
    p.unlink()
def prefs(name):return adb('exec-out','run-as',PKG,'cat','shared_prefs/'+name+'.xml').decode()
def scroll_home():
    size=list(map(int,re.findall(r'(\d+)x(\d+)',shell('wm','size'))[-1]))
    w,h=size
    shell('input','swipe',str(w//2),str(int(h*.70)),str(w//2),str(int(h*.36)),'400')
    time.sleep(.4)
try:
    shell('svc','wifi','disable',check=False);shell('svc','data','disable',check=False)
    shell('settings','put','global','airplane_mode_on','1',check=False)
    adb('logcat','-c')
    ensure('Success' in adb('install','-r','baseline/Yamone-Games.apk').decode(),'Baseline 0.2.00 installation')
    seed('yamone_games_settings','<string name="nickname">YamoneQA</string>')
    seed('yamone_online_ranking','<boolean name="enabled" value="false"/><boolean name="policy_migrated_v2" value="true"/>')
    data=html.escape(json.dumps([{'score':123,'ended_at':1789540000000,'nickname':'YamoneQA'}]))
    seed('yamone_arcade_records','<string name="records_ice_jump">'+data+'</string>')
    seed('yamone_sudoku_game','<int name="best_normal" value="321"/><int name="completed_normal" value="1"/><int name="total_completed" value="1"/>')
    ensure('Success' in adb('install','-r','apk/Yamone-Games.apk').decode(),'Data-preserving update installation')
    launch();node('우리의 놀이터');screenshot('01-home')
    ensure('123' in prefs('yamone_arcade_records'),'Arcade record preserved after update')
    ensure('321' in prefs('yamone_sudoku_game'),'Sudoku record preserved after update')
    ensure('name="enabled" value="false"' in prefs('yamone_online_ranking'),'Ranking sharing remains OFF after restart')
    tap('랭킹');node('우리의 기록');node('5:21');screenshot('02-sudoku-records')
    tap('빙하');node('123m');screenshot('03-ice-records')
    tap('설정');node('소리와 진동');screenshot('04-sound-settings')
    switches=[n for n in tree().iter('node') if n.get('class')=='android.widget.Switch']
    if len(switches)<2:switches=[n for n in tree().iter('node') if n.get('checkable')=='true']
    ensure(len(switches)>=2,'Sound controls are exposed to accessibility')
    x,y=center(switches[1]);shell('input','tap',str(x),str(y));time.sleep(.5)
    launch()
    ensure('name="music" value="false"' in prefs('yamone_feedback_v03'),'BGM OFF preference persists')
    game_errors=[]
    for index,title,start in [(5,'스도쿠',None),(6,'눈덩이 러시','시작하기'),(7,'물고기 냠냠','일반 모드'),(8,'빙하 점프','시작하기')]:
        try:
            launch()
            if title in ('물고기 냠냠','빙하 점프'):
                scroll_home();screenshot('01-home-scrolled')
            tap(title)
            if start:
                pause_point=center(node('Ⅱ'))
                tap(start)
                screenshot(f'{index:02d}-play')
                shell('input','tap',str(pause_point[0]),str(pause_point[1]));time.sleep(.4)
                node('잠깐 쉬어가요');screenshot(f'{index:02d}-pause')
                ensure(True,title+' starts and pauses')
                tap('계속하기')
            else:
                node('메모 OFF');screenshot('05-sudoku');ensure(True,'Sudoku board and controls rendered')
                tap('메모 OFF');node('메모 ON');ensure(True,'Sudoku memo input toggle works')
            ensure(shell('pidof',PKG).strip()!='',title+' process remains alive')
        except Exception as exc:
            game_errors.append(title+': '+str(exc));screenshot(f'{index:02d}-failure')
    logs=adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace')
    (OUT/'runtime.txt').write_text(logs)
    ensure('FATAL EXCEPTION' not in logs,'No fatal Android runtime exception during smoke test')
    ensure(not game_errors,'All four game screen checks passed: '+str(game_errors))
    (OUT/'report.json').write_text(json.dumps({'status':'passed','checks':checks,'network':'disabled','physical_audio_haptics':'not tested'},ensure_ascii=False,indent=2))
except Exception as exc:
    try:screenshot('failure')
    except Exception:pass
    try:(OUT/'runtime.txt').write_bytes(adb('logcat','-d','-s','AndroidRuntime:E'))
    except Exception:pass
    (OUT/'report.json').write_text(json.dumps({'status':'failed','error':str(exc),'passed':checks},ensure_ascii=False,indent=2))
    raise
