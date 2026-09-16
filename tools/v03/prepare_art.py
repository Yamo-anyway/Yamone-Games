"""Remove circular mascot backdrops without erasing white fur; preserve originals."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
import base64,cv2,numpy as np
ROOT=Path(__file__).resolve().parents[2]
DRAW=ROOT/'games/sudoku/src/main/res/drawable-nodpi'
OUTLINES={
'bear_mint':[(111,84),(132,63),(165,49),(204,49),(214,38),(239,36),(258,44),(266,64),(262,85),(252,91),(275,119),(285,150),(278,169),(280,187),(287,199),(307,218),(316,238),(327,262),(317,274),(289,273),(289,299),(281,321),(266,334),(242,335),(215,326),(178,325),(155,326),(141,334),(119,334),(104,324),(98,306),(99,287),(105,272),(111,267),(114,245),(120,225),(114,210),(114,192),(104,177),(101,147),(101,133),(91,133),(78,121),(74,103),(81,86),(95,79)],
'bear_pink':[(106,82),(139,62),(171,56),(199,56),(202,39),(221,29),(243,31),(260,45),(264,63),(253,82),(274,111),(286,143),(281,176),(264,192),(279,197),(278,218),(307,236),(322,254),(316,269),(319,284),(304,292),(289,286),(290,316),(275,337),(250,343),(225,340),(207,331),(177,333),(159,341),(127,340),(107,327),(97,310),(95,290),(99,275),(111,267),(112,246),(117,226),(113,211),(102,204),(97,185),(95,152),(99,133),(82,138),(67,126),(65,107),(73,88),(87,77)],
'seal_mint':[(111,111),(135,84),(169,69),(204,63),(234,70),(256,92),(267,118),(269,148),(264,177),(252,195),(279,216),(293,211),(308,191),(321,180),(333,185),(336,202),(350,190),(360,195),(366,211),(359,232),(337,247),(321,267),(305,287),(284,299),(278,317),(261,329),(236,333),(204,330),(177,337),(146,333),(117,325),(95,323),(80,311),(75,289),(85,270),(100,258),(110,233),(111,222),(106,210),(114,195),(103,175),(98,153),(101,128)],
'seal_pink':[(97,93),(129,72),(163,61),(194,62),(221,73),(241,91),(255,118),(260,151),(253,181),(244,198),(264,193),(278,204),(278,171),(278,150),(291,146),(308,161),(318,157),(345,151),(359,156),(360,176),(350,198),(334,214),(328,239),(320,260),(302,282),(290,296),(273,303),(244,303),(221,312),(190,321),(165,319),(146,313),(128,315),(101,308),(77,306),(55,299),(46,282),(45,261),(56,242),(69,235),(71,222),(66,208),(72,190),(61,176),(59,150),(68,126),(80,109)]}
for name,points in OUTLINES.items():
    source=Image.open(DRAW/f'yamone_{name}.webp').convert('RGBA');w,h=source.size;scale=4
    mask=Image.new('L',(w*scale,h*scale))
    ImageDraw.Draw(mask).polygon([(round(x*w/384*scale),round(y*h/384*scale)) for x,y in points],fill=255)
    rough=np.array(mask.filter(ImageFilter.GaussianBlur(.6*scale)).resize(source.size,Image.Resampling.LANCZOS))
    core=cv2.erode((rough>180).astype(np.uint8),np.ones((9,9),np.uint8))
    outer=cv2.dilate((rough>32).astype(np.uint8),np.ones((11,11),np.uint8))
    labels=np.where(rough>127,cv2.GC_PR_FGD,cv2.GC_PR_BGD).astype(np.uint8)
    labels[core>0]=cv2.GC_FGD;labels[outer==0]=cv2.GC_BGD
    cv2.setRNGSeed(42)
    cv2.grabCut(np.array(source.convert('RGB')),labels,None,np.zeros((1,65),np.float64),np.zeros((1,65),np.float64),4,cv2.GC_INIT_WITH_MASK)
    alpha=np.where((labels==cv2.GC_FGD)|(labels==cv2.GC_PR_FGD),255,0).astype(np.uint8)
    source.putalpha(Image.fromarray(alpha).filter(ImageFilter.GaussianBlur(.45)))
    source.save(DRAW/f'yamone_{name}_cutout.webp',lossless=True)
APP=ROOT/'app/src/main/res'
(APP/'drawable-nodpi').mkdir(parents=True,exist_ok=True)
(APP/'drawable-nodpi/v03_icon.webp').write_bytes(base64.b64decode((ROOT/'tools/v03/icon.webp.b64').read_text()))
(APP/'mipmap-anydpi-v26').mkdir(parents=True,exist_ok=True)
(APP/'mipmap-anydpi-v26/ic_launcher.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/v03_icon" />
    <foreground android:drawable="@android:color/transparent" />
</adaptive-icon>
''')
print('Prepared four transparent mascots and the approved launcher icon')
