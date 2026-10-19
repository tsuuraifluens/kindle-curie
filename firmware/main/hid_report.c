#include "hid_report.h"
#include "ble_hid.h"
#include "diagnostics.h"

#include <string.h>
#include "esp_timer.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

static const char *TAG = "HID_RPT";

/**
 * Composite BLE HID Report Descriptor (Keyboard Report ID 1 + Consumer Control Report ID 2).
 * Total length: 92 bytes.
 * - Report ID 1: Standard Keyboard (8-byte 6KRO, Usage Page 0x07, Usages 0x00..0xFF, 67 bytes)
 *   Supports PageUp (0x4B), PageDown (0x4E), Power (0x66), and Dual-Key Chord Sleep (0x4B + 0x4E).
 * - Report ID 2: Consumer Control (2-byte 16-bit Usage, Usage Page 0x0C, Usages 0x0000..0x03FF, 25 bytes)
 *   Supports Display Brightness Increment (0x006F -> KEY_BRIGHTNESSUP 225)
 *   and Display Brightness Decrement (0x0070 -> KEY_BRIGHTNESSDOWN 224).
 */
static const uint8_t s_composite_report_map[92] = {
    /* -------------------------------------------------------------------------
     * Collection 1: Keyboard (Report ID 1) - 65 bytes
     * ------------------------------------------------------------------------- */
    0x05, 0x01,       /* Usage Page (Generic Desktop)                   */
    0x09, 0x06,       /* Usage (Keyboard)                               */
    0xA1, 0x01,       /* Collection (Application)                       */
    0x85, 0x01,       /*   Report ID (1)                                */
    /* Modifier byte (8 bits: Left Ctrl .. Right GUI) */
    0x05, 0x07,       /*   Usage Page (Keyboard/Keypad)                 */
    0x19, 0xE0,       /*   Usage Minimum (224 - Left Control)           */
    0x29, 0xE7,       /*   Usage Maximum (231 - Right GUI)              */
    0x15, 0x00,       /*   Logical Minimum (0)                          */
    0x25, 0x01,       /*   Logical Maximum (1)                          */
    0x75, 0x01,       /*   Report Size (1)                              */
    0x95, 0x08,       /*   Report Count (8)                             */
    0x81, 0x02,       /*   Input (Data, Variable, Absolute)             */
    /* Reserved byte (8 bits constant) */
    0x95, 0x01,       /*   Report Count (1)                             */
    0x75, 0x08,       /*   Report Size (8)                              */
    0x81, 0x01,       /*   Input (Constant, Array, Absolute)            */
    /* LED Output Report (5 bits + 3 bits padding) */
    0x95, 0x05,       /*   Report Count (5)                             */
    0x75, 0x01,       /*   Report Size (1)                              */
    0x05, 0x08,       /*   Usage Page (LEDs)                            */
    0x19, 0x01,       /*   Usage Minimum (1 - Num Lock)                 */
    0x29, 0x05,       /*   Usage Maximum (5 - Kana)                     */
    0x91, 0x02,       /*   Output (Data, Variable, Absolute)            */
    0x95, 0x01,       /*   Report Count (1)                             */
    0x75, 0x03,       /*   Report Size (3)                              */
    0x91, 0x01,       /*   Output (Constant, Array, Absolute)           */
    /* 6-byte Key Array (0x00..0xFF includes 0x4B PageUp, 0x4E PageDown, 0x66 Power) */
    0x95, 0x06,       /*   Report Count (6)                             */
    0x75, 0x08,       /*   Report Size (8)                              */
    0x15, 0x00,       /*   Logical Minimum (0)                          */
    0x26, 0xFF, 0x00, /*   Logical Maximum (255)                        */
    0x05, 0x07,       /*   Usage Page (Keyboard/Keypad)                 */
    0x19, 0x00,       /*   Usage Minimum (0)                            */
    0x2A, 0xFF, 0x00, /*   Usage Maximum (255)                          */
    0x81, 0x00,       /*   Input (Data, Array, Absolute)                */
    0xC0,              /* End Collection                                 */

    /* -------------------------------------------------------------------------
     * Collection 2: Consumer Control (Report ID 2) - 25 bytes
     * ------------------------------------------------------------------------- */
    0x05, 0x0C,       /* Usage Page (Consumer)                          */
    0x09, 0x01,       /* Usage (Consumer Control)                       */
    0xA1, 0x01,       /* Collection (Application)                       */
    0x85, 0x02,       /*   Report ID (2)                                */
    0x15, 0x00,       /*   Logical Minimum (0)                          */
    0x26, 0xFF, 0x03, /*   Logical Maximum (1023 / 0x03FF)              */
    0x19, 0x00,       /*   Usage Minimum (0)                            */
    0x2A, 0xFF, 0x03, /*   Usage Maximum (1023 / 0x03FF)                */
    0x75, 0x10,       /*   Report Size (16 bits)                        */
    0x95, 0x01,       /*   Report Count (1)                             */
    0x81, 0x00,       /*   Input (Data, Array, Absolute)                */
    0xC0               /* End Collection                                 */
};

typedef enum {
    IN_FLIGHT_NONE = 0,
    IN_FLIGHT_KEYBOARD,
    IN_FLIGHT_CONSUMER
} in_flight_report_type_t;

static volatile hid_report_state_t s_state = HID_REPORT_STATE_IDLE;
static in_flight_report_type_t s_in_flight_type = IN_FLIGHT_NONE;
static uint8_t s_last_input_report[HID_KEYBOARD_INPUT_REPORT_LEN] = {0};
static uint8_t s_last_consumer_report[HID_CONSUMER_INPUT_REPORT_LEN] = {0};
static esp_timer_handle_t s_state_timer = NULL;
static SemaphoreHandle_t s_hid_mutex = NULL;

/* Dynamic timing configuration per action type */
static uint32_t s_current_hold_ms = HID_DEFAULT_PRESS_HOLD_MS;
static uint32_t s_current_cooldown_ms = (HID_DEFAULT_DEBOUNCE_MS - HID_DEFAULT_PRESS_HOLD_MS);

/* Debounce & Sliding Window Rate Limiter (max 5 actions per 1000 ms) */
static int64_t s_last_trigger_time_us = -1000000LL;
static int64_t s_action_window_us[HID_MAX_ACTIONS_PER_SEC] = {0};
static size_t s_window_idx = 0;
static size_t s_window_filled = 0;

static void state_timer_cb(void *arg)
{
    (void)arg;
    if (s_state == HID_REPORT_STATE_PRESSED) {
        diag_stats_t *st = diagnostics_get_mutable_stats();
        esp_err_t err = ESP_OK;

        if (s_in_flight_type == IN_FLIGHT_CONSUMER) {
            uint8_t release_frame[HID_CONSUMER_INPUT_REPORT_LEN];
            hid_report_build_consumer_frame(HID_USAGE_CC_NONE, release_frame);
            memcpy(s_last_consumer_report, release_frame, sizeof(s_last_consumer_report));

            err = ble_hid_notify_consumer_report(release_frame, sizeof(release_frame));
            if (err == ESP_OK) {
                st->notify_tx_ok_count++;
                diag_record_event("HID", "Consumer RELEASE sent [00 00]");
            } else {
                st->notify_tx_fail_count++;
                diag_record_error((int)err, "Consumer RELEASE notify failed");
            }
        } else {
            /* Automatically send keyboard release frame (00 00 00 00 00 00 00 00) */
            uint8_t release_frame[HID_KEYBOARD_INPUT_REPORT_LEN];
            hid_report_build_keyboard_frame(0x00, HID_USAGE_KB_NONE, release_frame);
            memcpy(s_last_input_report, release_frame, sizeof(s_last_input_report));

            err = ble_hid_notify_input_report(release_frame, sizeof(release_frame));
            if (err == ESP_OK) {
                st->notify_tx_ok_count++;
                diag_record_event("HID", "Key RELEASE sent [00 00 00 00 00 00 00 00]");
            } else {
                st->notify_tx_fail_count++;
                diag_record_error((int)err, "Key RELEASE notify failed");
            }
        }

        s_in_flight_type = IN_FLIGHT_NONE;

        /* Enter cooldown for remaining debounce/e-ink refresh duration */
        s_state = HID_REPORT_STATE_COOLDOWN;
        uint32_t cooldown_ms = (s_current_cooldown_ms > 0) ? s_current_cooldown_ms : 20U;
        esp_timer_start_once(s_state_timer, (uint64_t)cooldown_ms * 1000ULL);
    } else if (s_state == HID_REPORT_STATE_COOLDOWN) {
        s_state = HID_REPORT_STATE_IDLE;
    }
}

void hid_report_init(void)
{
    if (s_hid_mutex == NULL) {
        s_hid_mutex = xSemaphoreCreateMutex();
    }
    memset(s_last_input_report, 0, sizeof(s_last_input_report));
    memset(s_last_consumer_report, 0, sizeof(s_last_consumer_report));
    s_in_flight_type = IN_FLIGHT_NONE;
    s_state = HID_REPORT_STATE_IDLE;
    s_last_trigger_time_us = -1000000LL;
    memset(s_action_window_us, 0, sizeof(s_action_window_us));
    s_window_idx = 0;
    s_window_filled = 0;

    if (s_state_timer == NULL) {
        const esp_timer_create_args_t timer_args = {
            .callback = &state_timer_cb,
            .arg = NULL,
            .dispatch_method = ESP_TIMER_TASK,
            .name = "hid_key_fsm"
        };
        ESP_ERROR_CHECK(esp_timer_create(&timer_args, &s_state_timer));
    }

    diag_record_event("HID", "Report module initialized (map=%u bytes composite, PageUp=0x%02X, PageDown=0x%02X, BrightnessUp=0x%04X, BrightnessDown=0x%04X)",
                      (unsigned int)sizeof(s_composite_report_map),
                      HID_USAGE_KB_PAGEUP,
                      HID_USAGE_KB_PAGEDOWN,
                      HID_USAGE_CC_BRIGHTNESS_UP,
                      HID_USAGE_CC_BRIGHTNESS_DOWN);
}

const uint8_t *hid_report_get_map(size_t *out_len)
{
    if (out_len != NULL) {
        *out_len = sizeof(s_composite_report_map);
    }
    return s_composite_report_map;
}

void hid_report_build_keyboard_frame(uint8_t modifier, uint8_t keycode, uint8_t out_frame[HID_KEYBOARD_INPUT_REPORT_LEN])
{
    memset(out_frame, 0, HID_KEYBOARD_INPUT_REPORT_LEN);
    out_frame[0] = modifier;
    out_frame[1] = 0x00U; /* Reserved OEM byte */
    out_frame[2] = keycode;
}

void hid_report_build_chord_frame(uint8_t modifier, uint8_t key1, uint8_t key2, uint8_t out_frame[HID_KEYBOARD_INPUT_REPORT_LEN])
{
    memset(out_frame, 0, HID_KEYBOARD_INPUT_REPORT_LEN);
    out_frame[0] = modifier;
    out_frame[1] = 0x00U;
    out_frame[2] = key1;
    out_frame[3] = key2;
}

static bool check_and_record_rate_limit(int64_t now_us, diag_stats_t *st)
{
    /* 1. Check 80 ms debounce interval */
    if ((now_us - s_last_trigger_time_us) < ((int64_t)HID_DEFAULT_DEBOUNCE_MS * 1000LL)) {
        st->debounced_drop_count++;
        return false;
    }

    /* 2. Check 5 actions / 1000 ms sliding window rate limit */
    if (s_window_filled == HID_MAX_ACTIONS_PER_SEC) {
        int64_t oldest_us = s_action_window_us[s_window_idx];
        if ((now_us - oldest_us) < 1000000LL) {
            st->rate_limited_drop_count++;
            return false;
        }
    }

    s_last_trigger_time_us = now_us;
    s_action_window_us[s_window_idx] = now_us;
    s_window_idx = (s_window_idx + 1) % HID_MAX_ACTIONS_PER_SEC;
    if (s_window_filled < HID_MAX_ACTIONS_PER_SEC) {
        s_window_filled++;
    }
    return true;
}

void hid_report_build_consumer_frame(uint16_t usage, uint8_t out_frame[HID_CONSUMER_INPUT_REPORT_LEN])
{
    out_frame[0] = (uint8_t)(usage & 0xFFU);
    out_frame[1] = (uint8_t)((usage >> 8) & 0xFFU);
}

esp_err_t hid_report_send_chord(uint8_t key1, uint8_t key2)
{
    diag_stats_t *st = diagnostics_get_mutable_stats();

    /* Step 1: Verify BLE connection & CCCD */
    if (!st->connected) {
        st->not_connected_drop_count++;
        diag_record_error(ESP_ERR_INVALID_STATE, "Cannot send chord: BLE not connected");
        return ESP_ERR_INVALID_STATE;
    }

    if (!st->input_report_cccd_enabled) {
        st->not_connected_drop_count++;
        diag_record_error(ESP_ERR_INVALID_STATE, "Cannot send chord: Input Report CCCD not subscribed");
        return ESP_ERR_INVALID_STATE;
    }

    /* Step 2: Check state machine */
    if (s_state != HID_REPORT_STATE_IDLE) {
        st->debounced_drop_count++;
        ESP_LOGW(TAG, "Ignored chord trigger: state machine busy (%s)", hid_report_state_name(s_state));
        return ESP_ERR_INVALID_STATE;
    }

    int64_t now_us = esp_timer_get_time();
    if (!check_and_record_rate_limit(now_us, st)) {
        ESP_LOGW(TAG, "Ignored chord trigger: debounced or rate-limited");
        return ESP_ERR_INVALID_STATE;
    }

    /* Step 3: Build dual-key chord frame */
    uint8_t press_frame[HID_KEYBOARD_INPUT_REPORT_LEN];
    hid_report_build_chord_frame(0x00, key1, key2, press_frame);
    memcpy(s_last_input_report, press_frame, sizeof(s_last_input_report));

    s_state = HID_REPORT_STATE_PRESSED;
    s_in_flight_type = IN_FLIGHT_KEYBOARD;
    esp_err_t err = ble_hid_notify_input_report(press_frame, sizeof(press_frame));
    if (err != ESP_OK) {
        s_state = HID_REPORT_STATE_IDLE;
        s_in_flight_type = IN_FLIGHT_NONE;
        memset(s_last_input_report, 0, sizeof(s_last_input_report));
        st->notify_tx_fail_count++;
        diag_record_error((int)err, "Chord PRESS (0x%02X+0x%02X) notify failed", key1, key2);
        return err;
    }

    st->notify_tx_ok_count++;
    st->custom_key_action_count++;
    diag_record_event("HID", "Dual-Key Chord PRESS sent: [00 00 %02X %02X 00 00 00 00]", key1, key2);

    /* Step 4: Schedule release after 40 ms */
    esp_timer_stop(s_state_timer);
    esp_timer_start_once(s_state_timer, (uint64_t)HID_DEFAULT_PRESS_HOLD_MS * 1000ULL);
    return ESP_OK;
}

esp_err_t hid_report_send_action(page_turn_action_t action, uint8_t custom_usage)
{
    diag_stats_t *st = diagnostics_get_mutable_stats();

    /* Step 1: Verify BLE connection */
    if (!st->connected) {
        st->not_connected_drop_count++;
        diag_record_error(ESP_ERR_INVALID_STATE, "Cannot send action: BLE not connected");
        return ESP_ERR_INVALID_STATE;
    }

    /* Step 2: Handle optional dual-key Sleep chord action (diagnostic) */
    if (action == PAGE_TURN_ACTION_CHORD_SLEEP) {
        return hid_report_send_chord(HID_USAGE_KB_PAGEUP, HID_USAGE_KB_PAGEDOWN);
    }

    /* Step 3: Check state machine & debounce / rate limit */
    if (s_state != HID_REPORT_STATE_IDLE) {
        st->debounced_drop_count++;
        ESP_LOGW(TAG, "Ignored trigger: state machine busy (%s)", hid_report_state_name(s_state));
        return ESP_ERR_INVALID_STATE;
    }

    int64_t now_us = esp_timer_get_time();
    if (!check_and_record_rate_limit(now_us, st)) {
        ESP_LOGW(TAG, "Ignored trigger: debounced or rate-limited (max %u/sec)", HID_MAX_ACTIONS_PER_SEC);
        return ESP_ERR_INVALID_STATE;
    }

    /* Step 4: Handle Consumer Control actions (Brightness Up / Down) */
    if (action == PAGE_TURN_ACTION_BRIGHT_UP || action == PAGE_TURN_ACTION_BRIGHT_DOWN) {
        if (!st->consumer_report_cccd_enabled) {
            st->not_connected_drop_count++;
            diag_record_error(ESP_ERR_INVALID_STATE, "Cannot send brightness: Consumer Report CCCD not subscribed");
            return ESP_ERR_INVALID_STATE;
        }

        uint16_t cc_usage = (action == PAGE_TURN_ACTION_BRIGHT_UP)
                            ? HID_USAGE_CC_BRIGHTNESS_UP    /* 0x006F -> KEY_BRIGHTNESSUP (225) */
                            : HID_USAGE_CC_BRIGHTNESS_DOWN;  /* 0x0070 -> KEY_BRIGHTNESSDOWN (224) */
        const char *cc_label = (action == PAGE_TURN_ACTION_BRIGHT_UP) ? "BRIGHT_UP" : "BRIGHT_DOWN";

        uint8_t press_frame[HID_CONSUMER_INPUT_REPORT_LEN];
        hid_report_build_consumer_frame(cc_usage, press_frame);
        memcpy(s_last_consumer_report, press_frame, sizeof(s_last_consumer_report));

        s_state = HID_REPORT_STATE_PRESSED;
        s_in_flight_type = IN_FLIGHT_CONSUMER;
        s_current_hold_ms = HID_DEFAULT_PRESS_HOLD_MS;
        s_current_cooldown_ms = (HID_DEFAULT_DEBOUNCE_MS > HID_DEFAULT_PRESS_HOLD_MS)
                                ? (HID_DEFAULT_DEBOUNCE_MS - HID_DEFAULT_PRESS_HOLD_MS) : 40U;

        esp_err_t err = ble_hid_notify_consumer_report(press_frame, sizeof(press_frame));
        if (err != ESP_OK) {
            s_state = HID_REPORT_STATE_IDLE;
            s_in_flight_type = IN_FLIGHT_NONE;
            memset(s_last_consumer_report, 0, sizeof(s_last_consumer_report));
            st->notify_tx_fail_count++;
            diag_record_error((int)err, "Consumer PRESS (%s, usage=0x%04X) notify failed", cc_label, cc_usage);
            return err;
        }

        st->notify_tx_ok_count++;
        if (action == PAGE_TURN_ACTION_BRIGHT_UP) {
            st->bright_up_action_count++;
        } else {
            st->bright_down_action_count++;
        }
        diag_record_event("HID", "Consumer PRESS sent: %s usage=0x%04X [%02X %02X]",
                          cc_label, cc_usage, press_frame[0], press_frame[1]);

        /* Schedule release after hold */
        esp_timer_stop(s_state_timer);
        esp_timer_start_once(s_state_timer, (uint64_t)s_current_hold_ms * 1000ULL);
        return ESP_OK;
    }

    /* Step 5: Handle Keyboard actions (PageUp, PageDown, Sleep/Power, Custom) */
    if (!st->input_report_cccd_enabled) {
        st->not_connected_drop_count++;
        diag_record_error(ESP_ERR_INVALID_STATE, "Cannot send action: Input Report CCCD (0x2902) not subscribed");
        return ESP_ERR_INVALID_STATE;
    }

    uint8_t usage = HID_USAGE_KB_NONE;
    const char *action_label = "CUSTOM";
    s_current_hold_ms = HID_DEFAULT_PRESS_HOLD_MS;
    s_current_cooldown_ms = (HID_DEFAULT_DEBOUNCE_MS > HID_DEFAULT_PRESS_HOLD_MS)
                            ? (HID_DEFAULT_DEBOUNCE_MS - HID_DEFAULT_PRESS_HOLD_MS) : 40U;

    if (action == PAGE_TURN_ACTION_PREV) {
        usage = HID_USAGE_KB_PAGEUP; /* 0x4B -> KEY_PAGEUP (104) -> "PageUp" */
        action_label = "PREV (PageUp)";
        st->prev_action_count++;
    } else if (action == PAGE_TURN_ACTION_NEXT) {
        usage = HID_USAGE_KB_PAGEDOWN; /* 0x4E -> KEY_PAGEDOWN (109) -> "PageDown" */
        action_label = "NEXT (PageDown)";
        st->next_action_count++;
    } else if (action == PAGE_TURN_ACTION_SLEEP || action == PAGE_TURN_ACTION_POWER) {
        usage = HID_USAGE_KB_POWER; /* 0x66 -> KEY_POWER (116) -> Native Kindle Sleep */
        action_label = "SLEEP (0x66 Power)";
        s_current_hold_ms = HID_POWER_PRESS_HOLD_MS;       /* 70 ms hold */
        s_current_cooldown_ms = HID_POWER_SLEEP_COOLDOWN_MS; /* 450 ms cooldown */
        st->sleep_action_count++;
    } else {
        usage = custom_usage;
        st->custom_key_action_count++;
    }

    /* Build 8-byte Key Press frame and send GATT notification */
    uint8_t press_frame[HID_KEYBOARD_INPUT_REPORT_LEN];
    hid_report_build_keyboard_frame(0x00, usage, press_frame);
    memcpy(s_last_input_report, press_frame, sizeof(s_last_input_report));

    s_state = HID_REPORT_STATE_PRESSED;
    s_in_flight_type = IN_FLIGHT_KEYBOARD;
    esp_err_t err = ble_hid_notify_input_report(press_frame, sizeof(press_frame));
    if (err != ESP_OK) {
        s_state = HID_REPORT_STATE_IDLE;
        s_in_flight_type = IN_FLIGHT_NONE;
        memset(s_last_input_report, 0, sizeof(s_last_input_report));
        st->notify_tx_fail_count++;
        diag_record_error((int)err, "Key PRESS (%s, usage=0x%02X) notify failed", action_label, usage);
        return err;
    }

    st->notify_tx_ok_count++;
    diag_record_event("HID", "Key PRESS sent: %s usage=0x%02X [%02X %02X %02X %02X %02X %02X %02X %02X] (hold=%ums, cooldown=%ums)",
                      action_label, usage,
                      press_frame[0], press_frame[1], press_frame[2], press_frame[3],
                      press_frame[4], press_frame[5], press_frame[6], press_frame[7],
                      (unsigned int)s_current_hold_ms, (unsigned int)s_current_cooldown_ms);

    /* Schedule automatic Key Release after s_current_hold_ms hold */
    esp_timer_stop(s_state_timer);
    esp_timer_start_once(s_state_timer, (uint64_t)s_current_hold_ms * 1000ULL);
    return ESP_OK;
}

esp_err_t hid_report_force_release(void)
{
    if (s_state_timer != NULL) {
        esp_timer_stop(s_state_timer);
    }
    s_state = HID_REPORT_STATE_IDLE;
    s_in_flight_type = IN_FLIGHT_NONE;
    memset(s_last_input_report, 0, sizeof(s_last_input_report));
    memset(s_last_consumer_report, 0, sizeof(s_last_consumer_report));

    const diag_stats_t *st = diagnostics_get_stats();
    if (st->connected && st->input_report_cccd_enabled) {
        ble_hid_notify_input_report(s_last_input_report, sizeof(s_last_input_report));
    }
    if (st->connected && st->consumer_report_cccd_enabled) {
        ble_hid_notify_consumer_report(s_last_consumer_report, sizeof(s_last_consumer_report));
    }
    return ESP_OK;
}

void hid_report_on_disconnect(void)
{
    if (s_state_timer != NULL) {
        esp_timer_stop(s_state_timer);
    }
    s_state = HID_REPORT_STATE_IDLE;
    s_in_flight_type = IN_FLIGHT_NONE;
    memset(s_last_input_report, 0, sizeof(s_last_input_report));
    memset(s_last_consumer_report, 0, sizeof(s_last_consumer_report));
}

void hid_report_get_last_input_report(uint8_t out_frame[HID_KEYBOARD_INPUT_REPORT_LEN])
{
    memcpy(out_frame, s_last_input_report, HID_KEYBOARD_INPUT_REPORT_LEN);
}

void hid_report_get_last_consumer_report(uint8_t out_frame[HID_CONSUMER_INPUT_REPORT_LEN])
{
    memcpy(out_frame, s_last_consumer_report, HID_CONSUMER_INPUT_REPORT_LEN);
}

hid_report_state_t hid_report_get_state(void)
{
    return s_state;
}

const char *hid_report_state_name(hid_report_state_t state)
{
    switch (state) {
        case HID_REPORT_STATE_IDLE:     return "IDLE";
        case HID_REPORT_STATE_PRESSED:  return "PRESSED";
        case HID_REPORT_STATE_COOLDOWN: return "COOLDOWN (Debounce/E-ink Refresh)";
        default:                        return "UNKNOWN";
    }
}

