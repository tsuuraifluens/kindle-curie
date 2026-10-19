import os, sys, shutil, hashlib, struct

base_dir = r'kindle_click_jieli_port'
os.makedirs(os.path.join(base_dir, 'firmware'), exist_ok=True)
os.makedirs(os.path.join(base_dir, 'tools'), exist_ok=True)
os.makedirs(os.path.join(base_dir, 'patches'), exist_ok=True)

# Copy jieli_tools into tools/jieli_tools for self-contained execution
shutil.copytree(r'scripts/jieli_tools', os.path.join(base_dir, 'tools', 'jieli_tools'), dirs_exist_ok=True)

print("Directories created and helper libraries staged.")
