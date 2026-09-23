import re

CYR = {
    'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd', 'е': 'e', 'ё': 'e', 'ж': 'j', 'з': 'z', 'и': 'i', 'й': 'y',
    'к': 'k', 'л': 'l', 'м': 'm', 'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u', 'ф': 'f',
    'х': 'h', 'ц': 'ts', 'ч': 'ch', 'ш': 'sh', 'щ': 'sch', 'ъ': '', 'ы': 'yi', 'ь': '', 'э': 'e', 'ю': 'yu', 'я': 'ya',
    'і': 'i', 'ї': 'yi', 'є': 'e', 'ґ': 'g', 'ў': 'u',
}

RULES = [
    ('shch', 'X'), ('sch', 'X'), ('sh', 'X'), ('zh', 'Z'), ('kh', 'h'), ('ch', 'Q'), ('tch', 'Q'), ('ts', 'C'), ('tz', 'C'),
    ('ck', 'k'), ('ph', 'f'), ('yu', 'u'), ('ju', 'u'), ('iu', 'u'), ('ya', 'a'), ('ja', 'a'), ('ia', 'a'),
    ('yo', 'o'), ('jo', 'o'), ('ye', 'e'), ('je', 'e'), ('yi', 'i'), ('dj', 'Z'), ('j', 'Z'), ('y', 'i'),
    ('c', 'k'), ('q', 'k'), ('x', 'ks'), ('w', 'v'),
]
RULE_RE = re.compile('|'.join(re.escape(a) for a, _ in RULES))
RULE_MAP = dict(RULES)
NONWORD = re.compile(r'[^0-9a-zA-Z]+')
DUP = re.compile(r'(.)\1+')


def translit(s):
    return ''.join(CYR.get(ch, ch) for ch in s.lower())


def key(s):
    t = translit(s)
    t = NONWORD.sub(' ', t)
    t = RULE_RE.sub(lambda m: RULE_MAP[m.group(0)], t)
    t = DUP.sub(r'\1', t)
    return t.strip()


def words(s):
    return [w for w in key(s).split() if w]
