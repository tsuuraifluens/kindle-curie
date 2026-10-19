#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Report ID & Payload Lengths (hid_protocol_spec.md Section 5.1 & 5.2) */
#define HID_KEYBOARD_REPORT_ID        0x01U
#define HID_KEYBOARD_INPUT_REPORT_LEN 8U
#define HID_KEYBOARD_OUTPUT_REPORT_LEN 1U

#define HID_CONSUMER_REPORT_ID        0x02U
#define HID_CONSUMER_INPUT_REPORT_LEN 2U

/* Confirmed Page Turn HID Usages (Usage Page 0x07 Keyboard/Keypad, EVID-02/05/06) */
#define HID_USAGE_KB_NONE             0x00U
#define HID_USAGE_KB_PAGEUP           0x4BU /* -> Linux KEY_PAGEUP (104)        -> "PageUp"   */
#define HID_USAGE_KB_PAGEDOWN         0x4EU /* -> Linux KEY_PAGEDOWN (109)      -> "PageDown" */
#define HID_USAGE_KB_POWER            0x66U /* -> Linux KEY_POWER (116)         -> "Power" (Sleep) */

/* Confirmed Consumer Control HID Usages (Usage Page 0x0C Consumer Device, HUT v1.12) */
#define HID_USAGE_CC_NONE             0x0000U
#define HID_USAGE_CC_BRIGHTNESS_UP    0x006FU /* -> Linux KEY_BRIGHTNESSUP (225)   (Display Brightness Increment) */
#define HID_USAGE_CC_BRIGHTNESS_DOWN  0x0070U /* -> Linux KEY_BRIGHTNESSDOWN (224) (Display Brightness Decrement) */

/* Additional Keyboard Usages Mapped in input_manager (EVID-02, for diagnostic testing) */
#define HID_USAGE_KB_RIGHT_ARROW      0x4FU /* -> Linux KEY_RIGHT (106)    -> "Right"    */
#define HID_USAGE_KB_LEFT_ARROW       0x50U /* -> Linux KEY_LEFT (105)     -> "Left"     */
#define HID_USAGE_KB_DOWN_ARROW       0x51U /* -> Linux KEY_DOWN (108)     -> "Down"     */
#define HID_USAGE_KB_UP_ARROW         0x52U /* -> Linux KEY_UP (103)       -> "Up"       */
#define HID_USAGE_KB_SPACE            0x2CU /* -> Linux KEY_SPACE (57)     -> "Space"    */

/* Timing & Rate-Limit Parameters (hid_protocol_spec.md Section 5.2 & Task Spec 5.4) */
#define HID_DEFAULT_PRESS_HOLD_MS     40U   /* Hold standard page turn key down for 40 ms before release */
#define HID_POWER_PRESS_HOLD_MS       70U   /* Hold Power/Sleep key for 70 ms (sweet spot: 50ms~100ms) */
#define HID_DEFAULT_DEBOUNCE_MS       80U   /* Minimum 80 ms between standard page turn triggers */
#define HID_POWER_SLEEP_COOLDOWN_MS   450U  /* 450 ms cooldown after Power/Sleep for E-ink screen refresh */
#define HID_MAX_ACTIONS_PER_SEC       5U    /* Max 5 page turns per 1000 ms */

typedef enum {
    HID_REPORT_STATE_IDLE     = 0,
    HID_REPORT_STATE_PRESSED  = 1,
    HID_REPORT_STATE_COOLDOWN = 2
} hid_report_state_t;

typedef enum {
    PAGE_TURN_ACTION_PREV            = 0,
    PAGE_TURN_ACTION_NEXT            = 1,
    PAGE_TURN_ACTION_BRIGHT_UP       = 2,
    PAGE_TURN_ACTION_BRIGHT_DOWN     = 3,
    PAGE_TURN_ACTION_SLEEP           = 4, /* 单键 KEY_POWER (0x66) 熄屏/休眠 (Kindle 实测原生支持) */
    PAGE_TURN_ACTION_POWER           = 5, /* 单键 KEY_POWER (0x66) 别名 */
    PAGE_TURN_ACTION_CUSTOM          = 6,
    PAGE_TURN_ACTION_CHORD_SLEEP     = 7  /* 双键 (0x4B + 0x4E) 组合（备用诊断） */
} page_turn_action_t;

void hid_report_init(void);

/**
 * Returns pointer and size of the 65-byte Standard Keyboard HID Report Map (0x2A4B).
 * Matches hid_protocol_spec.md Section 5.1 byte-for-byte.
 */
const uint8_t *hid_report_get_map(size_t *out_len);

/**
 * Builds an 8-byte Keyboard Input Report frame:
 * [modifier, 0x00, key0, 0x00, 0x00, 0x00, 0x00, 0x00]
 */
void hid_report_build_keyboard_frame(uint8_t modifier, uint8_t keycode, uint8_t out_frame[HID_KEYBOARD_INPUT_REPORT_LEN]);
void hid_report_build_chord_frame(uint8_t modifier, uint8_t key1, uint8_t key2, uint8_t out_frame[HID_KEYBOARD_INPUT_REPORT_LEN]);
void hid_report_build_consumer_frame(uint16_t usage, uint8_t out_frame[HID_CONSUMER_INPUT_REPORT_LEN]);

/**
 * Triggers a complete press -> hold (40ms) -> release cycle for PREV (0x4B), NEXT (0x4E),
 * Brightness Up (0x006F), Brightness Down (0x0070), or Sleep.
 * Enforces connection check, CCCD check, state machine lock, 80ms debounce, and 5/s rate limit.
 */
esp_err_t hid_report_send_action(page_turn_action_t action, uint8_t custom_usage);

/**
 * Sends two keycodes simultaneously in a single 8-byte Keyboard Input Report frame:
 * [modifier, 0x00, key1, key2, 0x00, 0x00, 0x00, 0x00]
 * followed by 40ms hold and automatic all-zero release frame.
 */
esp_err_t hid_report_send_chord(uint8_t key1, uint8_t key2);

/**
 * Forces an immediate release report (00 00 00 00 00 00 00 00 / 00 00) and resets state machine.
 */
esp_err_t hid_report_force_release(void);

/**
 * Called by BLE stack on disconnect to cancel any in-flight timers and reset state.
 */
void hid_report_on_disconnect(void);

/**
 * Copies the most recently generated 8-byte Input Report for GATT Read requests on 0x2A4D.
 */
void hid_report_get_last_input_report(uint8_t out_frame[HID_KEYBOARD_INPUT_REPORT_LEN]);

/**
 * Copies the most recently generated 2-byte Consumer Report for GATT Read requests on 0x2A4D.
 */
void hid_report_get_last_consumer_report(uint8_t out_frame[HID_CONSUMER_INPUT_REPORT_LEN]);

hid_report_state_t hid_report_get_state(void);
const char *hid_report_state_name(hid_report_state_t state);

#ifdef __cplusplus
}
#endif
