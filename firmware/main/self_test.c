#include "self_test.h"
#include "device_identity.h"
#include "hid_report.h"
#include "battery_service.h"
#include "diagnostics.h"

#include <stdio.h>
#include <string.h>

#define TEST_ASSERT(cond, msg) do { \
    if (!(cond)) { \
        printf("  [FAIL] %s (line %d): %s\n", __FUNCTION__, __LINE__, msg); \
        return false; \
    } \
} while (0)

static bool test_pnp_id_encoding(void)
{
    uint8_t pnp[7];
    device_identity_get_pnp_id_bytes(pnp);

    /* Expected: 02 71 01 43 04 00 01 */
    static const uint8_t expected[7] = { 0x02, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01 };
    TEST_ASSERT(memcmp(pnp, expected, 7) == 0, "PnP ID byte sequence mismatch with EVID-03/04");
    return true;
}

static bool test_amazon_manuf_data_encoding(void)
{
    uint8_t manuf[4];
    device_identity_get_manuf_adv_bytes(manuf);

    /* Expected: 71 01 04 43 (VID 0x0171 LE, PID 0x0443 BE per libbluetooth.so rev16) */
    static const uint8_t expected[4] = { 0x71, 0x01, 0x04, 0x43 };
    TEST_ASSERT(memcmp(manuf, expected, 4) == 0, "Amazon BLE Remote 0xFF Manufacturer data mismatch with libbluetooth.so");
    return true;
}

static bool test_eir_devid_encoding(void)
{
    uint8_t devid[8];
    device_identity_get_eir_devid_bytes(devid);

    /* Expected: 02 00 71 01 43 04 00 01 (per libbluetooth.so 0x0ac688..0x0acd8c) */
    static const uint8_t expected[8] = { 0x02, 0x00, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01 };
    TEST_ASSERT(memcmp(devid, expected, 8) == 0, "EIR Device ID (AD Type 0x10) mismatch with libbluetooth.so");
    return true;
}

static bool test_report_map_structure(void)
{
    size_t map_len = 0;
    const uint8_t *map = hid_report_get_map(&map_len);

    TEST_ASSERT(map != NULL, "Report map pointer is NULL");
    TEST_ASSERT(map_len == 92, "Composite report map length is not 92 bytes");
    /* Collection 1: Keyboard (67 bytes) */
    TEST_ASSERT(map[0] == 0x05 && map[1] == 0x01, "Missing Usage Page (Generic Desktop)");
    TEST_ASSERT(map[2] == 0x09 && map[3] == 0x06, "Missing Usage (Keyboard)");
    TEST_ASSERT(map[4] == 0xA1 && map[5] == 0x01, "Missing Collection (Application)");
    TEST_ASSERT(map[6] == 0x85 && map[7] == 0x01, "Missing Keyboard Report ID 1");
    TEST_ASSERT(map[66] == 0xC0, "Missing End Collection for Keyboard");
    /* Collection 2: Consumer Control (25 bytes) */
    TEST_ASSERT(map[67] == 0x05 && map[68] == 0x0C, "Missing Usage Page (Consumer)");
    TEST_ASSERT(map[69] == 0x09 && map[70] == 0x01, "Missing Usage (Consumer Control)");
    TEST_ASSERT(map[71] == 0xA1 && map[72] == 0x01, "Missing Collection (Application)");
    TEST_ASSERT(map[73] == 0x85 && map[74] == 0x02, "Missing Consumer Report ID 2");
    TEST_ASSERT(map[map_len - 1] == 0xC0, "Missing End Collection byte 0xC0");
    return true;
}

static bool test_keyboard_report_encoding(void)
{
    uint8_t frame[HID_KEYBOARD_INPUT_REPORT_LEN];

    /* PREV (PageUp = 0x4B) */
    hid_report_build_keyboard_frame(0x00, HID_USAGE_KB_PAGEUP, frame);
    static const uint8_t expected_prev[8] = { 0x00, 0x00, 0x4B, 0x00, 0x00, 0x00, 0x00, 0x00 };
    TEST_ASSERT(memcmp(frame, expected_prev, 8) == 0, "PREV frame mismatch with [00 00 4B 00 00 00 00 00]");

    /* NEXT (PageDown = 0x4E) */
    hid_report_build_keyboard_frame(0x00, HID_USAGE_KB_PAGEDOWN, frame);
    static const uint8_t expected_next[8] = { 0x00, 0x00, 0x4E, 0x00, 0x00, 0x00, 0x00, 0x00 };
    TEST_ASSERT(memcmp(frame, expected_next, 8) == 0, "NEXT frame mismatch with [00 00 4E 00 00 00 00 00]");

    /* POWER (KEY_POWER = 0x66) */
    hid_report_build_keyboard_frame(0x00, HID_USAGE_KB_POWER, frame);
    static const uint8_t expected_power[8] = { 0x00, 0x00, 0x66, 0x00, 0x00, 0x00, 0x00, 0x00 };
    TEST_ASSERT(memcmp(frame, expected_power, 8) == 0, "POWER frame mismatch with [00 00 66 00 00 00 00 00]");

    /* DUAL-KEY CHORD SLEEP (0x4B + 0x4E) */
    hid_report_build_chord_frame(0x00, HID_USAGE_KB_PAGEUP, HID_USAGE_KB_PAGEDOWN, frame);
    static const uint8_t expected_chord[8] = { 0x00, 0x00, 0x4B, 0x4E, 0x00, 0x00, 0x00, 0x00 };
    TEST_ASSERT(memcmp(frame, expected_chord, 8) == 0, "CHORD SLEEP frame mismatch with [00 00 4B 4E 00 00 00 00]");

    /* RELEASE */
    hid_report_build_keyboard_frame(0x00, HID_USAGE_KB_NONE, frame);
    static const uint8_t expected_rel[8] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    TEST_ASSERT(memcmp(frame, expected_rel, 8) == 0, "RELEASE frame mismatch with all zeros");
    return true;
}

static bool test_consumer_report_encoding(void)
{
    uint8_t frame[HID_CONSUMER_INPUT_REPORT_LEN];

    /* BRIGHTNESS UP (0x006F) */
    hid_report_build_consumer_frame(HID_USAGE_CC_BRIGHTNESS_UP, frame);
    static const uint8_t expected_bup[2] = { 0x6F, 0x00 };
    TEST_ASSERT(memcmp(frame, expected_bup, 2) == 0, "Consumer BRIGHT_UP mismatch with [6F 00]");

    /* BRIGHTNESS DOWN (0x0070) */
    hid_report_build_consumer_frame(HID_USAGE_CC_BRIGHTNESS_DOWN, frame);
    static const uint8_t expected_bdown[2] = { 0x70, 0x00 };
    TEST_ASSERT(memcmp(frame, expected_bdown, 2) == 0, "Consumer BRIGHT_DOWN mismatch with [70 00]");

    /* RELEASE (0x0000) */
    hid_report_build_consumer_frame(HID_USAGE_CC_NONE, frame);
    static const uint8_t expected_rel[2] = { 0x00, 0x00 };
    TEST_ASSERT(memcmp(frame, expected_rel, 2) == 0, "Consumer RELEASE mismatch with [00 00]");
    return true;
}

static bool test_battery_bounds(void)
{
    TEST_ASSERT(battery_service_set_level(50, false) == ESP_OK, "Failed to set valid battery 50%");
    TEST_ASSERT(battery_service_get_level() == 50, "Battery level mismatch after set 50%");
    TEST_ASSERT(battery_service_set_level(101, false) == ESP_ERR_INVALID_ARG, "Failed to reject invalid battery 101%");
    /* Restore to confirmed default 95% */
    TEST_ASSERT(battery_service_set_level(95, false) == ESP_OK, "Failed to restore battery to 95%");
    return true;
}

static bool test_identity_validation(void)
{
    TEST_ASSERT(device_identity_set_name("") == ESP_ERR_INVALID_ARG, "Failed to reject empty name");
    TEST_ASSERT(device_identity_set_name("ThisNameIsWayTooLongForKindleBleGapNameOverLimit") == ESP_ERR_INVALID_ARG,
                "Failed to reject name exceeding max length");
    device_identity_reset_defaults();
    const device_identity_config_t *id = device_identity_get();
    TEST_ASSERT(strcmp(id->device_name, "Kindle Remote") == 0, "Default device name is not 'Kindle Remote'");
    TEST_ASSERT(id->vendor_id == 0x0171, "Default VID is not 0x0171");
    TEST_ASSERT(id->product_id == 0x0443, "Default PID is not 0x0443");
    return true;
}

bool self_test_run_all(void)
{
    printf("\n==================== RUNNING SELF TESTS ====================\n");
    int passed = 0;
    int total = 0;

    struct {
        const char *name;
        bool (*func)(void);
    } tests[] = {
        { "test_pnp_id_encoding",           test_pnp_id_encoding },
        { "test_amazon_manuf_data_encoding", test_amazon_manuf_data_encoding },
        { "test_eir_devid_encoding",         test_eir_devid_encoding },
        { "test_report_map_structure",       test_report_map_structure },
        { "test_keyboard_report_encoding",   test_keyboard_report_encoding },
        { "test_consumer_report_encoding",   test_consumer_report_encoding },
        { "test_battery_bounds",             test_battery_bounds },
        { "test_identity_validation",        test_identity_validation }
    };

    total = sizeof(tests) / sizeof(tests[0]);
    for (int i = 0; i < total; i++) {
        printf("  [%d/%d] %-32s ... ", i + 1, total, tests[i].name);
        if (tests[i].func()) {
            printf("[PASS]\n");
            passed++;
        } else {
            printf("[FAIL]\n");
        }
    }

    printf("Result: %d / %d tests passed (%s)\n",
           passed, total, (passed == total) ? "ALL PASSED" : "FAILURES DETECTED");
    printf("============================================================\n");
    diag_record_event("TEST", "Self-test executed: %d/%d passed", passed, total);
    return (passed == total);
}
