"""Deterministic original music loops and effects; no external recordings."""
from pathlib import Path
import math, wave, array
RATE=22050
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'app/src/main/res/raw'
OUT.mkdir(parents=True,exist_ok=True)
def note(buffer,start,duration,midi,gain=.14,bell=True):
    frequency=440*2**((midi-69)/12)
    begin=int(start*RATE)
    for i in range(int(duration*RATE)):
        t=i/RATE
        envelope=min(1,t/.012)*min(1,(duration-t)/.045)*math.exp(-t*(3 if bell else .8))
        sample=math.sin(2*math.pi*frequency*t)+(.25 if bell else .10)*math.sin(2*math.pi*2*frequency*t)
        buffer[(begin+i)%len(buffer)]+=sample*envelope*gain

def save(name,buffer):
    peak=max(.85,max(abs(x) for x in buffer))
    pcm=array.array('h',(round(max(-1,min(1,x/peak*.78))*32767) for x in buffer))
    import sys
    if sys.byteorder!='little':pcm.byteswap()
    with wave.open(str(OUT/(name+'.wav')),'wb') as f:
        f.setnchannels(1);f.setsampwidth(2);f.setframerate(RATE);f.writeframes(pcm.tobytes())
tracks={
 'home':(96,[72,76,79,76,74,77,81,77,71,74,79,74,72,76,79,84]),
 'sudoku':(72,[72,0,76,79,74,0,77,81,71,0,74,79,72,76,0,79]),
 'ice':(112,[79,76,72,76,81,77,74,77,79,74,71,74,84,79,76,72]),
 'fish':(120,[72,76,79,84,81,77,74,77,79,83,86,83,84,79,76,79]),
 'snow':(108,[76,79,84,79,77,81,84,81,74,79,83,79,76,79,84,72])}
for name,(tempo,melody) in tracks.items():
    beat=60/tempo;buffer=[0.]*round(beat*32*RATE)
    for bar in range(8):
        root=[48,53,55,48][bar%4]
        for chord_note in (root,root+7,root+12):note(buffer,bar*4*beat,beat*3.5,chord_note,.045,False)
        for step in range(4):
            midi=melody[(bar*2+step//2)%len(melody)]
            if midi:note(buffer,(bar*4+step)*beat,beat*.84,midi,.11)
        if name not in ('home','sudoku'):
            for step in (0,2):note(buffer,(bar*4+step)*beat,.10,36,.055,False)
    save('ym_bgm_'+name,buffer)
for name,pitches,step in [('tap',[81],.06),('collect',[76,84],.08),('error',[55,51],.12),('success',[72,76,79,84],.15),('jump',[72,79],.06),('start',[72,76,79],.12),('finish',[67,64,60],.15),('record',[72,76,79,84,88,91],.12)]:
    buffer=[0.]*round((len(pitches)*step+.32)*RATE)
    for i,midi in enumerate(pitches):note(buffer,i*step,.26,midi,.18)
    save('ym_'+name,buffer)
print('Generated',len(list(OUT.glob('ym_*.wav'))),'original audio resources')
