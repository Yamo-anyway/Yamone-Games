"""Rebuild transparent character resources from the preserved, approved 0.2.00 art.
Requires Pillow, numpy and opencv-python-headless. Never edits game data or fonts.
"""
from pathlib import Path
import io, subprocess
import cv2
import numpy as np
from PIL import Image, ImageFilter, ImageOps
ROOT = Path(__file__).resolve().parents[1]
BASE = '0664870ba7cd608b319346abad41760d44e1c628'
POLYGONS = {
 'seal': [(125,80),(169,61),(205,64),(241,83),(264,112),(277,165),(274,195),(278,210),(282,184),(297,168),(311,177),(316,212),(326,194),(345,190),(358,205),(351,232),(326,251),(325,276),(313,297),(304,317),(266,334),(241,323),(219,332),(176,332),(143,330),(123,323),(82,326),(58,316),(55,296),(63,268),(78,248),(83,214),(78,184),(73,157),(82,125),(99,101)],
 'bear': [(83,82),(107,74),(121,78),(145,60),(177,46),(208,48),(216,36),(238,31),(260,43),(271,62),(269,83),(267,90),(277,112),(289,151),(281,168),(293,189),(316,213),(312,226),(335,261),(328,280),(310,286),(291,266),(283,274),(295,295),(293,317),(281,337),(244,340),(219,328),(179,329),(159,336),(127,337),(103,325),(95,305),(99,276),(111,255),(114,219),(114,196),(99,186),(99,159),(87,138),(77,125),(72,103)],
 'seal_pink': [(139,64),(176,57),(206,65),(233,80),(255,109),(264,149),(261,177),(273,189),(277,153),(284,142),(297,142),(307,154),(316,167),(331,151),(348,145),(359,154),(363,173),(356,196),(340,216),(324,229),(327,257),(314,281),(310,301),(294,311),(268,313),(246,311),(223,321),(189,323),(157,317),(137,309),(113,304),(95,294),(82,305),(62,303),(47,291),(39,272),(44,251),(61,235),(80,232),(79,214),(66,184),(65,151),(77,116),(101,86)]
}
for kind in ('bear','seal'):
 for theme in ('mint','pink'):
    relative=f'games/sudoku/src/main/res/drawable-nodpi/yamone_{kind}_{theme}.webp'
    original=subprocess.check_output(['git','show',f'{BASE}:{relative}'],cwd=ROOT)
    rgb=np.array(Image.open(io.BytesIO(original)).convert('RGB'))
    special=kind=='seal' and theme=='pink'
    points=POLYGONS['seal_pink' if special else kind]
    cv2.setRNGSeed(1)
    shape=np.zeros(rgb.shape[:2],np.uint8); cv2.fillPoly(shape,[np.array(points,np.int32)],255)
    outer,inner=(5,7) if special else (7,9)
    mask=np.zeros(shape.shape,np.uint8)
    mask[cv2.dilate(shape,np.ones((outer,outer),np.uint8))>0]=cv2.GC_PR_BGD
    mask[shape>0]=cv2.GC_PR_FGD
    mask[cv2.erode(shape,np.ones((inner,inner),np.uint8))>0]=cv2.GC_FGD
    cv2.grabCut(rgb,mask,None,np.zeros((1,65)),np.zeros((1,65)),3,cv2.GC_INIT_WITH_MASK)
    a=np.uint8((mask==1)|(mask==3))*255
    count,labels,stats,_=cv2.connectedComponentsWithStats(a)
    if count>1: a=np.uint8(labels==1+np.argmax(stats[1:,cv2.CC_STAT_AREA]))*255
    alpha=Image.fromarray(a).filter(ImageFilter.GaussianBlur(.4))
    im=Image.fromarray(rgb).convert('RGBA'); im.putalpha(alpha)
    im=ImageOps.contain(im.crop(alpha.getbbox()),(350,350),Image.Resampling.LANCZOS)
    out=Image.new('RGBA',(384,384)); out.alpha_composite(im,((384-im.width)//2,(384-im.height)//2))
    out.save(ROOT/relative,quality=80,method=6)
    print('Prepared',relative)
