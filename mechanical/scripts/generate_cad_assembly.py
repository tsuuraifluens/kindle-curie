#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kindle Remote BLE Page Turner 3D CAD Parametric Assembly Script for Blender
Creates:
1. PCB Assembly placeholder (Board, ESP32-C3 module, Type-C, Buttons, LEDs)
2. Internal components (Battery 402030, Vibration motor 0827)
3. Bottom Enclosure with M2 heat-set insert bosses, battery tray, Type-C slot
4. Top Enclosure with button guide holes, LED light pipe holes, counterbored M2 screw holes
5. NEXT & PREV Ergonomic Floating Button Caps (with retaining flanges)
6. Materials, Collections, Exploded View keyframes, and Scene Setup
7. Exports STLs to mechanical/stl/, 3MF, and renders beauty shots to mechanical/renders/
"""

import bpy
import bmesh
import math
import os
from mathutils import Vector, Euler

# Paths
if "__file__" in globals():
    BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
else:
    BASE_DIR = os.path.abspath(os.getcwd())
BLEND_PATH = os.path.join(BASE_DIR, "mechanical", "blender", "controller_assembly.blend")
STL_DIR = os.path.join(BASE_DIR, "mechanical", "stl")
RENDER_DIR = os.path.join(BASE_DIR, "mechanical", "renders")

os.makedirs(os.path.dirname(BLEND_PATH), exist_ok=True)
os.makedirs(STL_DIR, exist_ok=True)
os.makedirs(RENDER_DIR, exist_ok=True)

# -------------------------------------------------------------------------
# Parametric Dimensions (Unit: Meters in Blender, 1mm = 0.001m)
# -------------------------------------------------------------------------
SCALE = 0.001

# PCB
PCB_W = 22.0 * SCALE
PCB_L = 50.0 * SCALE
PCB_H = 1.2 * SCALE

# Clearances & Wall Thickness
CLEARANCE_XY = 0.35 * SCALE
WALL_THICKNESS = 1.8 * SCALE
FLOOR_THICKNESS = 1.8 * SCALE
ROOF_THICKNESS = 1.8 * SCALE

# Internal Cavity
CAVITY_W = PCB_W + 2 * CLEARANCE_XY
CAVITY_L = PCB_L + 2 * CLEARANCE_XY
BOTTOM_DEPTH = 6.8 * SCALE     # Fits 4.0mm battery + wiring + foam
TOP_DEPTH = 5.2 * SCALE        # Fits 2.5mm switch + 2.0mm button stem

# Enclosure Outer
SHELL_W = CAVITY_W + 2 * WALL_THICKNESS   # ~ 26.3 mm
SHELL_L = CAVITY_L + 2 * WALL_THICKNESS   # ~ 54.3 mm
CORNER_RADIUS = 3.5 * SCALE

# Fasteners (M2 Screws & Heat-set Inserts)
BOSS_OD = 5.4 * SCALE
BOSS_ID = 3.2 * SCALE          # M2 insert pilot hole
SCREW_CLEARANCE_D = 2.4 * SCALE
SCREW_HEAD_D = 4.4 * SCALE
SCREW_HEAD_H = 1.6 * SCALE

# Mounting Hole relative coordinates from PCB center (PCB size 22 x 50)
HOLE_POSITIONS = [
    (-8.0 * SCALE, -22.0 * SCALE),
    ( 8.0 * SCALE, -22.0 * SCALE),
    (-8.0 * SCALE,  22.0 * SCALE),
    ( 8.0 * SCALE,  22.0 * SCALE),
]

# -------------------------------------------------------------------------
# Helper Functions
# -------------------------------------------------------------------------
def clean_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete()
    for col in list(bpy.data.collections):
        bpy.data.collections.remove(col)

def create_collection(name):
    col = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(col)
    return col

def get_or_create_material(name, color, roughness=0.3, metallic=0.0):
    mat = bpy.data.materials.get(name)
    if not mat:
        mat = bpy.data.materials.new(name=name)
        mat.use_nodes = True
        nodes = mat.node_tree.nodes
        bsdf = next(n for n in nodes if n.type == 'BSDF_PRINCIPLED')
        bsdf.inputs['Base Color'].default_value = color
        bsdf.inputs['Roughness'].default_value = roughness
        bsdf.inputs['Metallic'].default_value = metallic
    return mat

def apply_modifier(obj, mod_name):
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    try:
        with bpy.context.temp_override(active_object=obj, object=obj, selected_objects=[obj]):
            bpy.ops.object.modifier_apply(modifier=mod_name)
    except Exception:
        bpy.ops.object.modifier_apply(modifier=mod_name)

def create_rounded_box(name, size, radius, collection, location=(0,0,0)):
    # size = (x, y, z)
    bm = bmesh.new()
    mesh = bpy.data.meshes.new(name)
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=(size[0], size[1], size[2]), verts=bm.verts)
    bm.to_mesh(mesh)
    bm.free()
    
    obj = bpy.data.objects.new(name, mesh)
    obj.location = location
    collection.objects.link(obj)
    
    bev = obj.modifiers.new(name="Bevel", type='BEVEL')
    bev.width = radius
    bev.segments = 4
    bev.limit_method = 'ANGLE'
    bev.angle_limit = math.radians(45)
    apply_modifier(obj, "Bevel")
    
    return obj

# -------------------------------------------------------------------------
# Build Assemblies
# -------------------------------------------------------------------------
def build_assembly():
    clean_scene()
    
    col_enclosure = create_collection("Enclosure")
    col_pcb = create_collection("PCB_and_Electronics")
    col_hardware = create_collection("Hardware_Fasteners")
    
    # Materials
    mat_shell_top = get_or_create_material("Shell_Top_Matte_White", (0.92, 0.92, 0.94, 1.0), roughness=0.35)
    mat_shell_bot = get_or_create_material("Shell_Bottom_Charcoal", (0.12, 0.12, 0.14, 1.0), roughness=0.45)
    mat_button = get_or_create_material("Button_Accent_Teal", (0.05, 0.65, 0.60, 1.0), roughness=0.25)
    mat_pcb_fr4 = get_or_create_material("PCB_FR4_Green", (0.02, 0.35, 0.15, 1.0), roughness=0.2)
    mat_metal = get_or_create_material("Metal_Silver", (0.8, 0.8, 0.85, 1.0), roughness=0.2, metallic=0.9)
    mat_gold = get_or_create_material("Brass_Gold", (0.85, 0.65, 0.15, 1.0), roughness=0.3, metallic=0.85)
    mat_battery = get_or_create_material("LiPo_Battery_Blue", (0.05, 0.2, 0.7, 1.0), roughness=0.4)

    # 1. PCB Board
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    pcb = bpy.context.active_object
    pcb.name = "PCB_Board"
    pcb.scale = (PCB_W, PCB_L, PCB_H)
    pcb.location = (0, 0, 0)
    bpy.ops.object.transform_apply(scale=True)
    pcb.data.materials.append(mat_pcb_fr4)
    bpy.context.collection.objects.unlink(pcb)
    col_pcb.objects.link(pcb)

    # Add 4 mounting holes through PCB
    for idx, (hx, hy) in enumerate(HOLE_POSITIONS):
        bpy.ops.mesh.primitive_cylinder_add(radius=1.1 * SCALE, depth=2.0 * PCB_H, location=(hx, hy, 0))
        cyl = bpy.context.active_object
        cyl.name = f"PCB_Hole_Tool_{idx}"
        mod = pcb.modifiers.new(name=f"Bool_Hole_{idx}", type='BOOLEAN')
        mod.object = cyl
        mod.operation = 'DIFFERENCE'
        apply_modifier(pcb, f"Bool_Hole_{idx}")
        bpy.data.objects.remove(cyl, do_unlink=True)

    # 1.1 ESP32-C3 Module Placeholder
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    mcu = bpy.context.active_object
    mcu.name = "ESP32_C3_Module"
    mcu.scale = (18.0 * SCALE, 20.0 * SCALE, 3.2 * SCALE)
    mcu.location = (0, 10.0 * SCALE, PCB_H/2 + 1.6 * SCALE)
    bpy.ops.object.transform_apply(scale=True)
    mcu.data.materials.append(mat_metal)
    bpy.context.collection.objects.unlink(mcu)
    col_pcb.objects.link(mcu)

    # 1.2 USB Type-C Receptacle
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    usbc = bpy.context.active_object
    usbc.name = "TypeC_Connector"
    usbc.scale = (9.0 * SCALE, 7.3 * SCALE, 3.2 * SCALE)
    usbc.location = (0, -23.5 * SCALE, 0)
    bpy.ops.object.transform_apply(scale=True)
    usbc.data.materials.append(mat_metal)
    bpy.context.collection.objects.unlink(usbc)
    col_pcb.objects.link(usbc)

    # 1.3 Physical Switches on PCB
    for sw_name, sw_y, sw_sz in [("Switch_NEXT", -10.5 * SCALE, (6*SCALE, 6*SCALE, 2.5*SCALE)),
                                 ("Switch_PREV", -2.5 * SCALE, (6*SCALE, 6*SCALE, 2.5*SCALE))]:
        bpy.ops.mesh.primitive_cube_add(size=1.0)
        sw = bpy.context.active_object
        sw.name = sw_name
        sw.scale = sw_sz
        sw.location = (0, sw_y, PCB_H/2 + sw_sz[2]/2)
        bpy.ops.object.transform_apply(scale=True)
        sw.data.materials.append(mat_metal)
        bpy.context.collection.objects.unlink(sw)
        col_pcb.objects.link(sw)

    # 1.4 Battery 402030 (Bottom side)
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    bat = bpy.context.active_object
    bat.name = "Battery_402030"
    bat.scale = (20.0 * SCALE, 30.0 * SCALE, 4.0 * SCALE)
    bat.location = (0, -2.0 * SCALE, - (PCB_H/2 + 2.0 * SCALE + 0.5 * SCALE))
    bpy.ops.object.transform_apply(scale=True)
    bat.data.materials.append(mat_battery)
    bpy.context.collection.objects.unlink(bat)
    col_pcb.objects.link(bat)

    # 1.5 Vibration Motor 0827 (Bottom side)
    bpy.ops.mesh.primitive_cylinder_add(radius=4.0 * SCALE, depth=2.7 * SCALE)
    mtr = bpy.context.active_object
    mtr.name = "Motor_0827"
    mtr.location = (0, 18.0 * SCALE, - (PCB_H/2 + 1.35 * SCALE + 0.5 * SCALE))
    mtr.data.materials.append(mat_metal)
    bpy.context.collection.objects.unlink(mtr)
    col_pcb.objects.link(mtr)

    # -------------------------------------------------------------------------
    # 2. Bottom Enclosure (底壳)
    # -------------------------------------------------------------------------
    bot_outer_z = BOTTOM_DEPTH + FLOOR_THICKNESS
    bot_shell = create_rounded_box(
        "Bottom_Enclosure",
        (SHELL_W, SHELL_L, bot_outer_z),
        CORNER_RADIUS,
        col_enclosure,
        location=(0, 0, - (bot_outer_z/2))
    )
    bot_shell.data.materials.append(mat_shell_bot)

    # Cut cavity inside bottom shell
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    bot_cut = bpy.context.active_object
    bot_cut.name = "Bottom_Cavity_Cut"
    bot_cut.scale = (CAVITY_W, CAVITY_L, 2 * BOTTOM_DEPTH)
    bot_cut.location = (0, 0, - (BOTTOM_DEPTH/2))
    bpy.ops.object.transform_apply(scale=True)
    
    mod = bot_shell.modifiers.new(name="Cavity_Cut", type='BOOLEAN')
    mod.object = bot_cut
    mod.operation = 'DIFFERENCE'
    apply_modifier(bot_shell, "Cavity_Cut")
    bpy.data.objects.remove(bot_cut, do_unlink=True)

    # Add 4 Screw Bosses with Insert holes in bottom shell
    for idx, (hx, hy) in enumerate(HOLE_POSITIONS):
        bpy.ops.mesh.primitive_cylinder_add(radius=BOSS_OD/2, depth=BOTTOM_DEPTH, location=(hx, hy, -BOTTOM_DEPTH/2))
        boss = bpy.context.active_object
        boss.name = f"Boss_{idx}"
        
        bpy.ops.mesh.primitive_cylinder_add(radius=BOSS_ID/2, depth=BOTTOM_DEPTH*1.2, location=(hx, hy, -BOTTOM_DEPTH/2 + 0.5*SCALE))
        boss_hole = bpy.context.active_object
        boss_hole.name = f"Boss_Hole_{idx}"
        
        bmod = boss.modifiers.new(name="Hole", type='BOOLEAN')
        bmod.object = boss_hole
        bmod.operation = 'DIFFERENCE'
        apply_modifier(boss, "Hole")
        bpy.data.objects.remove(boss_hole, do_unlink=True)
        
        smod = bot_shell.modifiers.new(name=f"Add_Boss_{idx}", type='BOOLEAN')
        smod.object = boss
        smod.operation = 'UNION'
        apply_modifier(bot_shell, f"Add_Boss_{idx}")
        bpy.data.objects.remove(boss, do_unlink=True)

    # Cut USB-C slot in Bottom Shell
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    usbc_slot = bpy.context.active_object
    usbc_slot.name = "USBC_Slot_Tool"
    usbc_slot.scale = (9.8 * SCALE, 10.0 * SCALE, 4.0 * SCALE)
    usbc_slot.location = (0, - (SHELL_L/2), 0)
    bpy.ops.object.transform_apply(scale=True)
    
    mod = bot_shell.modifiers.new(name="USBC_Cut", type='BOOLEAN')
    mod.object = usbc_slot
    mod.operation = 'DIFFERENCE'
    apply_modifier(bot_shell, "USBC_Cut")
    bpy.data.objects.remove(usbc_slot, do_unlink=True)

    # -------------------------------------------------------------------------
    # 3. Top Enclosure (上盖)
    # -------------------------------------------------------------------------
    top_outer_z = TOP_DEPTH + ROOF_THICKNESS
    top_shell = create_rounded_box(
        "Top_Enclosure",
        (SHELL_W, SHELL_L, top_outer_z),
        CORNER_RADIUS,
        col_enclosure,
        location=(0, 0, (top_outer_z/2))
    )
    top_shell.data.materials.append(mat_shell_top)

    # Cut cavity inside top shell
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    top_cut = bpy.context.active_object
    top_cut.name = "Top_Cavity_Cut"
    top_cut.scale = (CAVITY_W, CAVITY_L, 2 * TOP_DEPTH)
    top_cut.location = (0, 0, (TOP_DEPTH/2))
    bpy.ops.object.transform_apply(scale=True)
    
    mod = top_shell.modifiers.new(name="Cavity_Cut", type='BOOLEAN')
    mod.object = top_cut
    mod.operation = 'DIFFERENCE'
    apply_modifier(top_shell, "Cavity_Cut")
    bpy.data.objects.remove(top_cut, do_unlink=True)

    # Cut Button Windows on Top Shell
    # NEXT Button Window (8.6mm x 8.6mm)
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    btn_cut_next = bpy.context.active_object
    btn_cut_next.scale = (8.6*SCALE, 8.6*SCALE, 10*SCALE)
    btn_cut_next.location = (0, -10.5*SCALE, 5*SCALE)
    bpy.ops.object.transform_apply(scale=True)
    mod = top_shell.modifiers.new(name="Cut_NEXT", type='BOOLEAN')
    mod.object = btn_cut_next
    mod.operation = 'DIFFERENCE'
    apply_modifier(top_shell, "Cut_NEXT")
    bpy.data.objects.remove(btn_cut_next, do_unlink=True)

    # PREV Button Window (7.2mm x 7.2mm)
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    btn_cut_prev = bpy.context.active_object
    btn_cut_prev.scale = (7.2*SCALE, 7.2*SCALE, 10*SCALE)
    btn_cut_prev.location = (0, -2.5*SCALE, 5*SCALE)
    bpy.ops.object.transform_apply(scale=True)
    mod = top_shell.modifiers.new(name="Cut_PREV", type='BOOLEAN')
    mod.object = btn_cut_prev
    mod.operation = 'DIFFERENCE'
    apply_modifier(top_shell, "Cut_PREV")
    bpy.data.objects.remove(btn_cut_prev, do_unlink=True)

    # LED Indicator Light Pipe Hole (Dia 1.6mm)
    bpy.ops.mesh.primitive_cylinder_add(radius=0.8 * SCALE, depth=10.0 * SCALE, location=(5.0 * SCALE, 5.0 * SCALE, 5 * SCALE))
    led_cut = bpy.context.active_object
    mod = top_shell.modifiers.new(name="Cut_LED", type='BOOLEAN')
    mod.object = led_cut
    mod.operation = 'DIFFERENCE'
    apply_modifier(top_shell, "Cut_LED")
    bpy.data.objects.remove(led_cut, do_unlink=True)

    # M2 Counterbored screw holes on Top Shell
    for idx, (hx, hy) in enumerate(HOLE_POSITIONS):
        # Screw shaft hole
        bpy.ops.mesh.primitive_cylinder_add(radius=SCREW_CLEARANCE_D/2, depth=top_outer_z*2, location=(hx, hy, top_outer_z/2))
        shole = bpy.context.active_object
        mod1 = top_shell.modifiers.new(name=f"ScrewHole_{idx}", type='BOOLEAN')
        mod1.object = shole
        mod1.operation = 'DIFFERENCE'
        apply_modifier(top_shell, f"ScrewHole_{idx}")
        bpy.data.objects.remove(shole, do_unlink=True)
        
        # Screw head counterbore
        bpy.ops.mesh.primitive_cylinder_add(radius=SCREW_HEAD_D/2, depth=SCREW_HEAD_H*2, location=(hx, hy, top_outer_z - SCREW_HEAD_H))
        chead = bpy.context.active_object
        mod2 = top_shell.modifiers.new(name=f"Counterbore_{idx}", type='BOOLEAN')
        mod2.object = chead
        mod2.operation = 'DIFFERENCE'
        apply_modifier(top_shell, f"Counterbore_{idx}")
        bpy.data.objects.remove(chead, do_unlink=True)

    # -------------------------------------------------------------------------
    # 4. Floating Button Caps (按键帽与防脱落裙边)
    # -------------------------------------------------------------------------
    # Button NEXT Cap
    btn_next = create_rounded_box("Button_Cap_NEXT", (8.0*SCALE, 8.0*SCALE, 3.2*SCALE), 1.0*SCALE, col_enclosure, location=(0, -10.5*SCALE, TOP_DEPTH + 1.0*SCALE))
    btn_next.data.materials.append(mat_button)
    # Retaining Flange
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    flange_next = bpy.context.active_object
    flange_next.scale = (10.0*SCALE, 10.0*SCALE, 0.8*SCALE)
    flange_next.location = (0, -10.5*SCALE, TOP_DEPTH - 0.2*SCALE)
    bpy.ops.object.transform_apply(scale=True)
    mod = btn_next.modifiers.new(name="Union_Flange", type='BOOLEAN')
    mod.object = flange_next
    mod.operation = 'UNION'
    apply_modifier(btn_next, "Union_Flange")
    bpy.data.objects.remove(flange_next, do_unlink=True)

    # Button PREV Cap
    btn_prev = create_rounded_box("Button_Cap_PREV", (6.6*SCALE, 6.6*SCALE, 3.2*SCALE), 0.8*SCALE, col_enclosure, location=(0, -2.5*SCALE, TOP_DEPTH + 1.0*SCALE))
    btn_prev.data.materials.append(mat_button)
    # Retaining Flange
    bpy.ops.mesh.primitive_cube_add(size=1.0)
    flange_prev = bpy.context.active_object
    flange_prev.scale = (8.5*SCALE, 8.5*SCALE, 0.8*SCALE)
    flange_prev.location = (0, -2.5*SCALE, TOP_DEPTH - 0.2*SCALE)
    bpy.ops.object.transform_apply(scale=True)
    mod = btn_prev.modifiers.new(name="Union_Flange", type='BOOLEAN')
    mod.object = flange_prev
    mod.operation = 'UNION'
    apply_modifier(btn_prev, "Union_Flange")
    bpy.data.objects.remove(flange_prev, do_unlink=True)

    # -------------------------------------------------------------------------
    # 5. Screws Placeholder (M2 Fasteners)
    # -------------------------------------------------------------------------
    for idx, (hx, hy) in enumerate(HOLE_POSITIONS):
        bpy.ops.mesh.primitive_cylinder_add(radius=1.0*SCALE, depth=6.0*SCALE, location=(hx, hy, top_outer_z - 3.0*SCALE))
        sc = bpy.context.active_object
        sc.name = f"Screw_M2_{idx}"
        sc.data.materials.append(mat_metal)
        bpy.context.collection.objects.unlink(sc)
        col_hardware.objects.link(sc)

    # -------------------------------------------------------------------------
    # 6. Camera & Studio Lighting Setup
    # -------------------------------------------------------------------------
    col_studio = create_collection("Studio_Lighting")
    
    bpy.ops.object.light_add(type='AREA', radius=0.2, location=(0.15, -0.2, 0.3))
    light1 = bpy.context.active_object
    light1.data.energy = 35.0
    light1.rotation_euler = Euler((math.radians(45), math.radians(15), math.radians(30)))
    bpy.context.collection.objects.unlink(light1)
    col_studio.objects.link(light1)

    bpy.ops.object.light_add(type='AREA', radius=0.2, location=(-0.2, 0.1, 0.2))
    light2 = bpy.context.active_object
    light2.data.energy = 15.0
    light2.rotation_euler = Euler((math.radians(-30), math.radians(-30), 0))
    bpy.context.collection.objects.unlink(light2)
    col_studio.objects.link(light2)

    bpy.ops.object.camera_add(location=(0.08, -0.12, 0.09))
    cam = bpy.context.active_object
    cam.name = "Product_Camera"
    cam.rotation_euler = Euler((math.radians(55), 0, math.radians(35)))
    bpy.context.scene.camera = cam
    bpy.context.collection.objects.unlink(cam)
    col_studio.objects.link(cam)

    bpy.context.scene.render.resolution_x = 1920
    bpy.context.scene.render.resolution_y = 1080
    bpy.context.scene.render.film_transparent = False

    # Save initial assembled .blend
    bpy.ops.wm.save_as_mainfile(filepath=BLEND_PATH)
    print(f"[OK] Saved Blender Assembly: {BLEND_PATH}")

    # -------------------------------------------------------------------------
    # 7. Export High-Fidelity STLs for 3D Printing
    # -------------------------------------------------------------------------
    export_parts = [
        ("Top_Enclosure", top_shell),
        ("Bottom_Enclosure", bot_shell),
        ("Button_Cap_NEXT", btn_next),
        ("Button_Cap_PREV", btn_prev),
    ]

    for part_name, obj in export_parts:
        bpy.ops.object.select_all(action='DESELECT')
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        
        stl_path = os.path.join(STL_DIR, f"{part_name}.stl")
        
        if hasattr(bpy.ops.wm, "stl_export"):
            bpy.ops.wm.stl_export(filepath=stl_path, export_selected_objects=True)
        else:
            bpy.ops.export_mesh.stl(filepath=stl_path, use_selection=True)
            
        print(f"[OK] Exported 3D Print STL: {stl_path} (Size: {os.path.getsize(stl_path)} bytes)")

    # -------------------------------------------------------------------------
    # 8. Render Product Shots (Assembled & Exploded View)
    # -------------------------------------------------------------------------
    # Render 1: Assembled View
    render1_path = os.path.join(RENDER_DIR, "assembly_assembled_iso.png")
    bpy.context.scene.render.filepath = render1_path
    try:
        bpy.ops.render.render(write_still=True)
        print(f"[OK] Rendered Assembled View: {render1_path}")
    except Exception as e:
        print(f"[WARN] Render exception: {e}")

    # Render 2: Exploded View (Offset parts along Z axis)
    top_shell.location.z += 25.0 * SCALE
    btn_next.location.z += 35.0 * SCALE
    btn_prev.location.z += 35.0 * SCALE
    pcb.location.z += 10.0 * SCALE
    mcu.location.z += 10.0 * SCALE
    usbc.location.z += 10.0 * SCALE
    bat.location.z -= 12.0 * SCALE
    bot_shell.location.z -= 20.0 * SCALE
    
    render2_path = os.path.join(RENDER_DIR, "assembly_exploded_view.png")
    bpy.context.scene.render.filepath = render2_path
    try:
        bpy.ops.render.render(write_still=True)
        print(f"[OK] Rendered Exploded View: {render2_path}")
    except Exception as e:
        print(f"[WARN] Render exception: {e}")

    # Save final blend with exploded view state
    bpy.ops.wm.save_as_mainfile(filepath=BLEND_PATH)
    print(f"[OK] Saved Final Blender Assembly (.blend): {BLEND_PATH}")

if __name__ == "__main__":
    build_assembly()
