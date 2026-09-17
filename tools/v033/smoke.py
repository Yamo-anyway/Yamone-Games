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
    ensure('Success' in adb('install','-r','baseline/Yamone-Games.apk').decode(),'Baseline 0.3.02 installed')
    seed('yamone_games_settings','<string name="nickname">YamoneQA</string>')
    seed('yamone_online_ranking','<boolean name="enabled" value="false"/><boolean name="policy_migrated_v2" value="true"/>')
    def record(n):return html.escape(json.dumps([{'score':n,'ended_at':1789540000000,'nickname':'YamoneQA'}]))
    seed('yamone_arcade_records','<string name="records_ice_jump">'+record(123)+'</string><string name="records_snow_rush_shards_ms">'+record(32101)+'</string>')
    sol=list(map(int,''.join(['534678912','672195348','198342567','859761423','426853791','713924856','961537284','287419635','345286179'])))
    puzzle=[sol[i] if i in (0,20,40,71) else 0 for i in range(81)]
    body='<int name="best_normal" value="321"/><int name="completed_normal" value="1"/><int name="total_completed" value="1"/><string name="last_difficulty">NORMAL</string>'
    for key,value in [('puzzle',puzzle),('values',puzzle),('solution',sol),('notes',[0]*81)]:body+=f'<string name="slot_normal_{key}">'+','.join(map(str,value))+'</string>'
    body+='<int name="slot_normal_elapsed" value="123"/><int name="slot_normal_mistakes" value="0"/><boolean name="slot_normal_completed" value="false"/>'
    seed('yamone_sudoku_game',body)
    reward(0)
    ensure('Success' in adb('install','-r','apk/Yamone-Games.apk').decode(),'Same-signature data-preserving update to 0.3.03')
    launch();t=tree();get('야모네 게임',t=t);banner(True,'Expired timer: banner on home')
    for unwanted in ['나만의 작은 놀이터','우리의 놀이터','내 기록 ›']:ensure(not has(unwanted,t),'Removed home text: '+unwanted)
    ensure(bounds(get('상단 배너 광고 영역',t=t))[3] < bounds(get('스도쿠',t=t))[1],'Banner is above, not over, cards')
    ensure(has('00:00:00',t),'Header zero clock visible')
    shot('01-home-zero')
    tap('광고 없음 ',True);get('광고 없는 시간');get('광고 보고 +30분');shot('02-ad-details');tap('닫기')
    ensure(pvalue('yamone_ad_access','ad_free_until')=='0','Opening details does not grant a reward')
    for label,marker in [('랭킹','우리의 기록'),('설정','소리와 진동')]:
        launch();tap(label);get(marker);banner(True,'Expired timer: banner on '+label);shot('03-'+label)
    for title,start,pause in [('스도쿠',None,None),('빙하 점프','시작하기','Ⅱ'),('물고기 냠냠','일반 모드','Ⅱ'),('눈덩이 러시','시작하기','일시정지')]:
        open_game(title);banner(True,'Expired timer: immediate game entry and banner on '+title)
        if start:
            t=tree();p=xy(get(pause,t=t));b=xy(get(start,t=t));tapxy(*b);time.sleep(.2);tapxy(*p);time.sleep(.4)
            get('잠깐 쉬어가요');ensure(True,'Starts and pauses without forced ad: '+title)
        else:get('스도쿠 9×9 숫자판')
        shot('04-'+title)
    ensure(pvalue('yamone_ad_access','ad_free_until')=='0','Game starts never inject ad time')
    reward(int(time.time()*1000)+300000)
    launch();banner(False,'Positive reward hides home banner');shot('05-home-adfree')
    for title in ['스도쿠','빙하 점프','물고기 냠냠','눈덩이 러시']:
        open_game(title);banner(False,'Positive reward hides banner on '+title)
    for label in ['랭킹','설정']:
        launch();tap(label);banner(False,'Positive reward hides banner on '+label)
    # Let the stored reward expire while Sudoku is on-screen; selected cell must survive relayout.
    expiry=int(time.time()*1000)+23000;reward(expiry);open_game('스도쿠')
    tap('1행 1열,',True);t=tree()
    ensure('선택됨' in get('1행 1열,',True,t).get('content-desc',''),'Normal-input selection is highlighted')
    ensure('같은 숫자' in get('5행 5열,',True,t).get('content-desc',''),'Other matching numbers are highlighted')
    ensure('겹침' in get('4행 4열,',True,t).get('content-desc',''),'Other matching number 3x3 box is blocked')
    ensure('입력 가능' in get('3행 7열,',True,t).get('content-desc',''),'Legal empty location is clearly marked')
    banner(False,'Banner hidden before active-game expiry');shot('06-sudoku-guide-adfree')
    wait=(expiry-int(time.time()*1000))/1000+1.4
    if wait>0:time.sleep(wait)
    banner(True,'Banner appears automatically when reward expires during Sudoku')
    ensure('선택됨' in get('1행 1열,',True).get('content-desc',''),'Timer expiry does not reset current selection or game')
    shot('07-sudoku-guide-banner')
    tap('3행 7열,',True)
    tapxy(*xy(scroll_to('메모 OFF')));time.sleep(.3)
    tapxy(*xy(scroll_to('숫자 입력 9')));time.sleep(.3)
    ensure(int(pvalue('yamone_sudoku_game','slot_normal_notes').split(',')[24]) & (1<<9) != 0,'Memo input still saves notes')
    ensure(pvalue('yamone_sudoku_game','slot_normal_values').split(',')[24]=='0','Memo does not change cell value')
    tap('메모 ON');tap('지우기')
    tap('추측 시작');tapxy(*xy(scroll_to('숫자 입력 '+str(sol[24]))));time.sleep(.3)
    ensure(pvalue('yamone_sudoku_game','slot_normal_values').split(',')[24]==str(sol[24]),'Normal numeric input still saves')
    tap('돌아가기');time.sleep(.3)
    ensure(pvalue('yamone_sudoku_game','slot_normal_values').split(',')[24]=='0','Guess return restores checkpoint')
    ensure(json.loads(pvalue('yamone_arcade_records','records_ice_jump'))[0]['score']==123,'Existing ice ranking record retained')
    ensure(json.loads(pvalue('yamone_arcade_records','records_snow_rush_shards_ms'))[0]['score']==32101,'Existing millisecond snow record retained')
    ensure(pvalue('yamone_online_ranking','enabled')=='false','Ranking sharing OFF survives upgrade and play')
    # Layout at 360dp and larger system font. Device is a disposable emulator only.
    stop();shell('wm','size','1080x2160');shell('wm','density','480');shell('settings','put','system','font_scale','1.3')
    launch();get('야모네 게임');get('00:00:00');shot('08-home-360dp-font130')
    # Simulate cached store ownership in the emulator (not an actual purchase test).
    stop();seed('yamone_purchase_entitlement','<boolean name="permanent_ad_free_active" value="true"/><string name="permanent_ad_free_source">GOOGLE_PLAY</string><long name="permanent_ad_free_verified_at" value="1"/>')
    launch();banner(False,'Permanent ownership hides banner with zero timer');ensure(not has('00:00:00'),'Permanent ownership hides timer/button');shot('09-owned-home')
    logs=adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace');(OUT/'runtime.txt').write_text(logs)
    ensure('FATAL EXCEPTION' not in logs,'No fatal Android exception during update, ads or Sudoku tests')
    result={'status':'passed','checks':checks,'liveAdsTested':False,'storePurchaseTested':False,'rankingSharing':'OFF','physicalDeviceTested':False}
    (OUT/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False),flush=True)
except Exception as exc:
    try:shot('failure')
    except Exception:pass
    try:(OUT/'runtime.txt').write_bytes(adb('logcat','-d','-s','AndroidRuntime:E'))
    except Exception:pass
    (OUT/'report.json').write_text(json.dumps({'status':'failed','error':str(exc),'checks':checks},ensure_ascii=False,indent=2))
    raise
