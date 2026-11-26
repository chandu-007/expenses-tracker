from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset
import os
uni=list(range(0x20,0x7F))+list(range(0xA0,0x180))+[0x2013,0x2014,0x2018,0x2019,0x201C,0x201D,0x2022,0x2026,0x2212,0x20B9,0x00B7,0x2190,0x2192,0x2191,0x2193,0x25B2,0x25BC,0x2713,0x2715,0x00D7,0x00F7,0x2044]
def tnum_map(f):
    g=f['GSUB'].table; m={}
    feats=[fr for fr in g.FeatureList.FeatureRecord if fr.FeatureTag=='tnum']
    idx=set(i for fr in feats for i in fr.Feature.LookupListIndex)
    for i in idx:
        lk=g.LookupList.Lookup[i]
        for st in lk.SubTable:
            if lk.LookupType==7: st=st.ExtSubTable
            if hasattr(st,'mapping'): m.update(st.mapping)
    return m
def make(src,weights,name,extra={}):
    for w in weights:
        f=TTFont(src); loc={'wght':w}; loc.update(extra)
        f=instancer.instantiateVariableFont(f,loc)
        tm=tnum_map(f); cmap=f.getBestCmap(); n=0
        # freeze tabular figures: the digits' cmap entries point at the tabular glyphs, in every cmap subtable
        for t in f['cmap'].tables:
            if not t.isUnicode(): continue
            for cp in range(0x30,0x3A):
                if cp in t.cmap and t.cmap[cp] in tm: t.cmap[cp]=tm[t.cmap[cp]]; n+=1
        o=subset.Options(); o.layout_features=['kern','case','zero','liga','calt','locl','mark','mkmk']; o.name_IDs=['*']; o.notdef_outline=True; o.glyph_names=False; o.hinting=False
        s=subset.Subsetter(o); s.populate(unicodes=uni); s.subset(f)
        out=f"{name}-{w}.ttf"; f.save(out)
        g=TTFont(out); cm=g.getBestCmap(); hm=g['hmtx']
        adv={hm[cm[c]][0] for c in range(0x30,0x3A)}
        print(out, os.path.getsize(out), 'digits remapped',n,'digit advances',adv,'rupee',0x20B9 in cm)
        assert len(adv)==1, 'digits not equal'
make('inter.ttf',[400,500,600,700],'Inter',{'opsz':14})
make('sg.ttf',[500,600,700],'SpaceGrotesk')
