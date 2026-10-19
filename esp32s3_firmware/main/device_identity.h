#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Provenance classification for each device identity parameter.
 * Ensures strict separation between reverse-engineered facts and testable hypotheses.
 */
typedef enum {
    IDENTITY_EVIDENCE_CONFIRMED = 0,   /* Proven by binary disassembly / rootfs config */
    IDENTITY_EVIDENCE_DERIVED   = 1,   /* Directly derived from HOGP/BT SIG spec + binary refs */
    IDENTITY_EVIDENCE_HYPOTHESIS = 2   /* Plausible default, configurable for experimentation */
} identity_provenance_t;

/**
 * BLE Advertising Manufacturer/EIR mode for Kindle BtDiscoveryFilter::Classify compatibility.
 *
 * Reverse Engineering Evidence (EVID-12, EVID-13):
 *   - acsbtfd BtDiscoveryFilter::Classify (0x22574) requires VP ID match (VID=0x0171, PID=0x0443)
 *     during BLE scanning before GATT connection occurs.
 *   - libbluetooth.so btif_dm_search_devices_evt (0x0ac680..0x0ad750) extracts VP ID from:
 *     (A) AD Type 0xFF (Manufacturer Specific Data): [0x71, 0x01, 0x04, 0x43]
 *         (VID 0x0171 LE + PID 0x0443 BE -> rev16 -> btif_in_is_amazon_ble_remote @ 0x0f89f8)
 *     (B) AD Type 0x10 (Device ID / PnP Info, 8 bytes): [0x02, 0x00, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01]
 */
typedef enum {
    BLE_ADV_MODE_AMAZON_MANUF_0XFF = 0, /* Default: Include 0xFF [71 01 04 43] in ADV_IND */
    BLE_ADV_MODE_EIR_DEVID_0X10    = 1, /* Include both 0xFF in ADV_IND and 0x10 in SCAN_RSP */
    BLE_ADV_MODE_GENERIC_HID_ONLY  = 2  /* Omit 0xFF/0x10 (for A/B testing discovery filter) */
} ble_adv_identity_mode_t;

#define DEVICE_NAME_MAX_LEN 29
#define DIS_STRING_MAX_LEN  31

/* Confirmed Identity Constants (EVID-01, EVID-02, EVID-03, EVID-04, EVID-12, EVID-13) */
#define KINDLE_CONFIRMED_VID_SOURCE  0x02U   /* USB Implementers Forum (EVID-03, EVID-04) */
#define KINDLE_CONFIRMED_VENDOR_ID   0x0171U /* Amazon / Lab126 (EVID-01, EVID-02, EVID-12) */
#define KINDLE_CONFIRMED_PRODUCT_ID  0x0443U /* Kindle Page Turner (EVID-01, EVID-02, EVID-12) */
#define KINDLE_DEFAULT_PRODUCT_VER   0x0100U /* 1.0.0 (Little-endian 0x00, 0x01 in 0x2A50) */
#define KINDLE_DEFAULT_APPEARANCE    0x03C1U /* HID Keyboard (961) */

typedef struct {
    /* BLE GAP Identity */
    char device_name[DEVICE_NAME_MAX_LEN + 1];
    uint16_t appearance;
    uint8_t adv_mode; /* ble_adv_identity_mode_t */

    /* DIS PnP ID (0x2A50) - 7 bytes packed little-endian */
    uint8_t vendor_id_source;
    uint16_t vendor_id;
    uint16_t product_id;
    uint16_t product_version;

    /* DIS Strings (0x180A) */
    char manufacturer_name[DIS_STRING_MAX_LEN + 1]; /* 0x2A29 */
    char model_number[DIS_STRING_MAX_LEN + 1];      /* 0x2A24 */
    char serial_number[DIS_STRING_MAX_LEN + 1];     /* 0x2A25 */
    char hw_revision[DIS_STRING_MAX_LEN + 1];       /* 0x2A27 */
    char fw_revision[DIS_STRING_MAX_LEN + 1];       /* 0x2A26 - Read by acsbtfd (EVID-09) */
    char sw_revision[DIS_STRING_MAX_LEN + 1];       /* 0x2A28 */
} device_identity_config_t;

void device_identity_init(void);
const device_identity_config_t *device_identity_get(void);

/**
 * Encodes the 7-byte GATT PnP ID (0x2A50) characteristic value.
 * Byte 0:   Vendor ID Source (0x02)
 * Byte 1-2: Vendor ID Little-Endian (0x71, 0x01 -> 0x0171)
 * Byte 3-4: Product ID Little-Endian (0x43, 0x04 -> 0x0443)
 * Byte 5-6: Product Version Little-Endian (0x00, 0x01 -> 0x0100)
 */
void device_identity_get_pnp_id_bytes(uint8_t out_buf[7]);

/**
 * Encodes the 4-byte Amazon BLE Remote Manufacturer Specific Data (AD Type 0xFF payload).
 * Evidence: libbluetooth.so 0x0ad270..0x0ad60c & btif_in_is_amazon_ble_remote (0x0f89f8).
 * Byte 0-1: Vendor ID Little-Endian (0x71, 0x01 -> 0x0171)
 * Byte 2-3: Product ID Big-Endian (0x04, 0x43 -> rev16(0x4304) == 0x0443)
 */
void device_identity_get_manuf_adv_bytes(uint8_t out_buf[4]);

/**
 * Encodes the 8-byte EIR Device ID (AD Type 0x10 payload).
 * Evidence: libbluetooth.so 0x0ac688..0x0acd8c.
 * Byte 0-1: Vendor ID Source Little-Endian (0x02, 0x00)
 * Byte 2-3: Vendor ID Little-Endian (0x71, 0x01)
 * Byte 4-5: Product ID Little-Endian (0x43, 0x04)
 * Byte 6-7: Version Little-Endian (0x00, 0x01)
 */
void device_identity_get_eir_devid_bytes(uint8_t out_buf[8]);

/* Runtime overrides for protocol experimentation via serial console */
esp_err_t device_identity_set_name(const char *name);
esp_err_t device_identity_set_vid_pid(uint16_t vid, uint16_t pid);
esp_err_t device_identity_set_adv_mode(uint8_t mode);
void device_identity_reset_defaults(void);
void device_identity_print_info(void);

#ifdef __cplusplus
}
#endif
