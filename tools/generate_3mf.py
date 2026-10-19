#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generates standard 3MF (3D Manufacturing Format) package from exported STLs
for Bambu Lab, PrusaSlicer, and Cura slicers.
"""

import os
import zipfile
import struct
import xml.etree.ElementTree as ET

STL_DIR = "mechanical/stl"
OUTPUT_3MF = "mechanical/3mf/controller_assembly.3mf"

os.makedirs(os.path.dirname(OUTPUT_3MF), exist_ok=True)

def parse_binary_stl(filepath):
    vertices = []
    triangles = []
    vert_map = {}
    
    with open(filepath, "rb") as f:
        header = f.read(80)
        num_triangles = struct.unpack("<I", f.read(4))[0]
        
        for _ in range(num_triangles):
            # Normal vector (3 floats)
            n = struct.unpack("<3f", f.read(12))
            # 3 vertices (9 floats)
            v1 = struct.unpack("<3f", f.read(12))
            v2 = struct.unpack("<3f", f.read(12))
            v3 = struct.unpack("<3f", f.read(12))
            # Attribute byte count (2 bytes)
            attr = f.read(2)
            
            tri_indices = []
            for v in (v1, v2, v3):
                # Round to 5 decimal places
                v_rounded = (round(v[0], 5), round(v[1], 5), round(v[2], 5))
                if v_rounded not in vert_map:
                    vert_map[v_rounded] = len(vertices)
                    vertices.append(v_rounded)
                tri_indices.append(vert_map[v_rounded])
            triangles.append(tri_indices)
            
    return vertices, triangles

def build_3mf():
    stl_files = [
        ("Top_Enclosure.stl", "Top_Enclosure", "1"),
        ("Bottom_Enclosure.stl", "Bottom_Enclosure", "2"),
        ("Button_Cap_NEXT.stl", "Button_Cap_NEXT", "3"),
        ("Button_Cap_PREV.stl", "Button_Cap_PREV", "4")
    ]
    
    # Root 3D model XML
    model_xml = ['<?xml version="1.0" encoding="UTF-8"?>']
    model_xml.append('<model unit="millimeter" xml:lang="en-US" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">')
    model_xml.append('  <metadata name="Title">Kindle Remote Controller 3D Enclosure Assembly</metadata>')
    model_xml.append('  <metadata name="Designer">Antigravity Industrial Engineering</metadata>')
    model_xml.append('  <resources>')
    
    components_xml = []
    
    for stl_name, part_name, obj_id in stl_files:
        stl_path = os.path.join(STL_DIR, stl_name)
        if not os.path.exists(stl_path):
            continue
        verts, tris = parse_binary_stl(stl_path)
        
        model_xml.append(f'    <object id="{obj_id}" name="{part_name}" type="model">')
        model_xml.append('      <mesh>')
        model_xml.append('        <vertices>')
        # STLs exported in meters, scale to mm for 3mf unit="millimeter"
        scale = 1000.0
        for vx, vy, vz in verts:
            model_xml.append(f'          <vertex x="{vx*scale:.4f}" y="{vy*scale:.4f}" z="{vz*scale:.4f}" />')
        model_xml.append('        </vertices>')
        model_xml.append('        <triangles>')
        for v1, v2, v3 in tris:
            model_xml.append(f'          <triangle v1="{v1}" v2="{v2}" v3="{v3}" />')
        model_xml.append('        </triangles>')
        model_xml.append('      </mesh>')
        model_xml.append('    </object>')
        
        components_xml.append(f'    <item objectid="{obj_id}" />')
        
    model_xml.append('  </resources>')
    model_xml.append('  <build>')
    model_xml.extend(components_xml)
    model_xml.append('  </build>')
    model_xml.append('</model>')
    
    content_types_xml = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml" />
  <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml" />
</Types>"""

    rels_xml = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel" />
</Relationships>"""

    with zipfile.ZipFile(OUTPUT_3MF, "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", content_types_xml)
        z.writestr("_rels/.rels", rels_xml)
        z.writestr("3D/3dmodel.model", "\n".join(model_xml))
        
    print(f"[OK] Generated Standard 3MF File: {OUTPUT_3MF} (Size: {os.path.getsize(OUTPUT_3MF)} bytes)")

if __name__ == "__main__":
    build_3mf()
