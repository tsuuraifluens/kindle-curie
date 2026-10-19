package com.kindle.pageturner.hid

/**
 * HID Key Codes and Usage Mappings for Kindle Paperwhite 2024 (12th Gen).
 *
 * Evidence Mapping:
 * - EVID-02: /etc/input_manager/categories.json
 *   capability_table = ["KEY_PAGEDOWN", "KEY_PAGEUP", "KEY_BRIGHTNESSUP", "KEY_BRIGHTNESSDOWN", "KEY_POWER"]
 * - EVID-06: /etc/upstart/makexconfig & /etc/udev/rules.d/99-kindle-input.rules
 * - EVID-10: libAccessories.so (loc.button.page_top, loc.button.page_bottom, nav.page_forward, nav.page_back)
 * - input_mapping.md Section 2 (Event Translation Matrix)
 * - hid_protocol_spec.md Section 6.1
 */
enum class HidKeyAction(
    val actionName: String,
    val usageCode: Byte,
    val linuxEvdevCode: Int,
    val x11KeyCode: Int,
    val semanticName: String,
    val description: String
) {
    PAGE_DOWN(
        actionName = "Next Page (Page Down)",
        usageCode = 0x4E.toByte(),
        linuxEvdevCode = 109, // KEY_PAGEDOWN
        x11KeyCode = 117,     // evdev + 8 -> XK_Page_Down (0xFF56)
        semanticName = "nav.page_forward",
        description = "Turn forward to next page (bottom button)"
    ),

    PAGE_UP(
        actionName = "Previous Page (Page Up)",
        usageCode = 0x4B.toByte(),
        linuxEvdevCode = 104, // KEY_PAGEUP
        x11KeyCode = 112,     // evdev + 8 -> XK_Page_Up (0xFF55)
        semanticName = "nav.page_back",
        description = "Turn backward to previous page (top button)"
    ),

    BRIGHTNESS_UP(
        actionName = "Brightness Up",
        usageCode = 0x6F.toByte(), // F14 / Brightness Up
        linuxEvdevCode = 225,       // KEY_BRIGHTNESSUP
        x11KeyCode = 233,           // XF86MonBrightnessUp
        semanticName = "ui.bright_up",
        description = "Increase frontlight brightness"
    ),

    BRIGHTNESS_DOWN(
        actionName = "Brightness Down",
        usageCode = 0x6E.toByte(), // F15 / Brightness Down
        linuxEvdevCode = 224,       // KEY_BRIGHTNESSDOWN
        x11KeyCode = 232,           // XF86MonBrightnessDown
        semanticName = "ui.bright_down",
        description = "Decrease frontlight brightness"
    ),

    POWER(
        actionName = "Power",
        usageCode = 0x66.toByte(), // Power
        linuxEvdevCode = 116,       // KEY_POWER
        x11KeyCode = 124,           // XF86PowerOff
        semanticName = "sys.power",
        description = "Wake or sleep display"
    );

    companion object {
        const val USAGE_NONE: Byte = 0x00.toByte()

        fun findByUsage(code: Byte): HidKeyAction? {
            return entries.find { it.usageCode == code }
        }
    }
}
