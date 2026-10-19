import urllib.request, os

base_url = 'https://raw.githubusercontent.com/quarkslab/ghidra-jieli/main/data/languages/'
files = [
    'q32s.slaspec',
    'q32s_ins_loadimm.sinc',
    'q32s_ins_loadstore.sinc',
    'q32s_ins_move.sinc',
    'q32s_ins_progflow.sinc',
    'q32s_ins_arithops.sinc',
    'q32s_ins_stack.sinc'
]

os.makedirs('scripts/q32s_spec', exist_ok=True)
for fn in files:
    url = base_url + fn
    out_path = os.path.join('scripts/q32s_spec', fn)
    try:
        urllib.request.urlretrieve(url, out_path)
        print(f'Downloaded {fn}')
    except Exception as e:
        print(f'Failed {fn}: {e}')
