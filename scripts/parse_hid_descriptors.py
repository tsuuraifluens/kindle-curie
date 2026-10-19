import struct

# USB HID Item Constants
ITEM_TYPE_MAIN = 0
ITEM_TYPE_GLOBAL = 1
ITEM_TYPE_LOCAL = 2

GLOBAL_TAGS = {
    0: "Usage Page",
    1: "Logical Minimum",
    2: "Logical Maximum",
    3: "Physical Minimum",
    4: "Physical Maximum",
    5: "Unit Exponent",
    6: "Unit",
    7: "Report Size",
    8: "Report ID",
    9: "Report Count",
    10: "Push",
    11: "Pop"
}

LOCAL_TAGS = {
    0: "Usage",
    1: "Usage Minimum",
    2: "Usage Maximum",
    3: "Designator Index",
    4: "Designator Minimum",
    5: "Designator Maximum",
    7: "String Index",
    8: "String Minimum",
    9: "String Maximum",
    10: "Delimiter"
}

MAIN_TAGS = {
    8: "Input",
    9: "Output",
    10: "Collection",
    11: "Feature",
    12: "End Collection"
}

USAGE_PAGES = {
    0x01: "Generic Desktop Controls",
    0x02: "Simulation Controls",
    0x06: "Generic Device Controls",
    0x07: "Keyboard/Keypad",
    0x08: "LEDs",
    0x09: "Button",
    0x0C: "Consumer Devices",
    0x0D: "Digitizers",
    0xFF: "Vendor-defined"
}

def parse_hid_descriptor(desc_bytes, start_offset=0):
    lines = []
    i = 0
    indent = 0
    current_usage_page = 0
    report_id = None
    
    while i < len(desc_bytes):
        b0 = desc_bytes[i]
        if b0 == 0xFE: # Long item
            size = desc_bytes[i+1]
            tag = desc_bytes[i+2]
            val = desc_bytes[i+3 : i+3+size]
            lines.append("  " * indent + f"Long Item tag={tag} size={size}")
            i += 3 + size
            continue
            
        b_size = b0 & 0x03
        if b_size == 3: b_size = 4
        b_type = (b0 >> 2) & 0x03
        b_tag = (b0 >> 4) & 0x0F
        
        raw_val = 0
        val_bytes = desc_bytes[i+1 : i+1+b_size]
        if len(val_bytes) < b_size:
            break
        if b_size == 0:
            raw_val = 0
        elif b_size == 1:
            raw_val = struct.unpack('<B', val_bytes)[0]
        elif b_size == 2:
            raw_val = struct.unpack('<H', val_bytes)[0]
        elif b_size == 4:
            raw_val = struct.unpack('<I', val_bytes)[0]
            
        hex_str = ' '.join(f'{b:02X}' for b in desc_bytes[i : i+1+b_size])
        desc_str = f"Unknown (t={b_type}, tag={b_tag})"
        
        if b_type == ITEM_TYPE_GLOBAL:
            tag_name = GLOBAL_TAGS.get(b_tag, f"Global_{b_tag}")
            if tag_name == "Usage Page":
                current_usage_page = raw_val
                up_name = USAGE_PAGES.get(raw_val, f"Vendor/Other (0x{raw_val:04X})")
                desc_str = f"Usage Page ({up_name})"
            elif tag_name == "Report ID":
                report_id = raw_val
                desc_str = f"Report ID ({raw_val})"
            else:
                desc_str = f"{tag_name} ({raw_val})"
        elif b_type == ITEM_TYPE_LOCAL:
            tag_name = LOCAL_TAGS.get(b_tag, f"Local_{b_tag}")
            desc_str = f"{tag_name} (0x{raw_val:04X})"
        elif b_type == ITEM_TYPE_MAIN:
            tag_name = MAIN_TAGS.get(b_tag, f"Main_{b_tag}")
            if tag_name == "Collection":
                c_types = {0: "Physical", 1: "Application", 2: "Logical", 3: "Report"}
                desc_str = f"Collection ({c_types.get(raw_val, f'0x{raw_val:X}')})"
                lines.append(f"{start_offset+i:04X}: {hex_str:14s} " + "  " * indent + desc_str)
                indent += 1
                i += 1 + b_size
                continue
            elif tag_name == "End Collection":
                desc_str = "End Collection"
                indent = max(0, indent - 1)
                lines.append(f"{start_offset+i:04X}: {hex_str:14s} " + "  " * indent + desc_str)
                i += 1 + b_size
                continue
            else:
                desc_str = f"{tag_name} (0x{raw_val:X})"
                
        lines.append(f"{start_offset+i:04X}: {hex_str:14s} " + "  " * indent + desc_str)
        i += 1 + b_size
        
    return '\n'.join(lines)

def run_parser():
    with open('extracted/ufw_unpacked/files/app.bin', 'rb') as f:
        data = f.read()
        
    # Descriptors list with exact offsets and lengths
    descriptors = [
        ("Gamepad / X-Input Descriptor", 0x2248C, 0x22608 - 0x2248C),
        ("Standard Keyboard Descriptor", 0x22608, 0x22648 - 0x22608),
        ("Standard Mouse Descriptor", 0x22648, 0x2268C - 0x22648),
        ("Consumer / Media Descriptor 1", 0x2268C, 0x226A6 - 0x2268C),
        ("Touchscreen / Digitizer Descriptor", 0x226A6, 0x22766 - 0x226A6),
        ("Vendor Feature Descriptor", 0x22768, 0x2278C - 0x22768),
        ("IINE_reader Consumer Descriptor", 0x2280C, 0x22846 - 0x2280C),
        ("Touchscreen Coordinate Descriptor", 0x22846, 0x22896 - 0x22846),
    ]
    
    with open('evidence/07_all_hid_descriptors_parsed.txt', 'w', encoding='utf-8') as out:
        for name, off, length in descriptors:
            out.write("="*80 + "\n")
            out.write(f"HID DESCRIPTOR: {name}\n")
            out.write(f"app.bin Offset: 0x{off:05X} - 0x{off+length:05X} (Length: {length} bytes)\n")
            out.write(f"CPU Virtual:    0x{0x01E00120+off:08X}\n")
            out.write(f"Flash Physical: 0x{0x002120+off:08X}\n")
            out.write("="*80 + "\n")
            chunk = data[off : off+length]
            out.write(parse_hid_descriptor(chunk, off) + "\n\n")
            
    print("Parsed all 8 HID descriptors into evidence/07_all_hid_descriptors_parsed.txt")

if __name__ == '__main__':
    run_parser()
