#include "device_identity.h"
#include "diagnostics.h"

#include <stdio.h>
#include <string.h>

static device_identity_config_t s_identity;

void device_identity_reset_defaults(void)
{
    memset(&s_identity, 0, sizeof(s_identity));

    /* BLE GAP Identity */
    strncpy(s_identity.device_name, "Kindle Remote", DEVICE_NAME_MAX_LEN);
    s_identity.appearance = KINDLE_DEFAULT_APPEARANCE; /* 0x03C1 Keyboard */
    s_identity.adv_mode   = (uint8_t)BLE_ADV_MODE_AMAZON_MANUF_0XFF;

    /* DIS PnP ID (0x2A50): 02 71 01 43 04 00 01 */
    s_identity.vendor_id_source = KINDLE_CONFIRMED_VID_SOURCE; /* 0x02 */
    s_identity.vendor_id        = KINDLE_CONFIRMED_VENDOR_ID;  /* 0x0171 */
    s_identity.product_id       = KINDLE_CONFIRMED_PRODUCT_ID; /* 0x0443 */
    s_identity.product_version  = KINDLE_DEFAULT_PRODUCT_VER;  /* 0x0100 */

    /* DIS Strings (0x180A) */
    strncpy(s_identity.manufacturer_name, "Amazon", DIS_STRING_MAX_LEN);
    strncpy(s_identity.model_number,      "Kindle Remote", DIS_STRING_MAX_LEN);
    strncpy(s_identity.serial_number,     "KR000001", DIS_STRING_MAX_LEN);
    strncpy(s_identity.hw_revision,       "1.0", DIS_STRING_MAX_LEN);
    strncpy(s_identity.fw_revision,       "1.0.0", DIS_STRING_MAX_LEN);
    strncpy(s_identity.sw_revision,       "1.0.0", DIS_STRING_MAX_LEN);
}

void device_identity_init(void)
{
    device_identity_reset_defaults();
    diag_record_event("ID", "Identity initialized: name=\"%s\" VID=0x%04X PID=0x%04X",
                      s_identity.device_name, s_identity.vendor_id, s_identity.product_id);
}

const device_identity_config_t *device_identity_get(void)
{
    return &s_identity;
}

void device_identity_get_pnp_id_bytes(uint8_t out_buf[7])
{
    out_buf[0] = s_identity.vendor_id_source;
    out_buf[1] = (uint8_t)(s_identity.vendor_id & 0xFFU);
    out_buf[2] = (uint8_t)((s_identity.vendor_id >> 8) & 0xFFU);
    out_buf[3] = (uint8_t)(s_identity.product_id & 0xFFU);
    out_buf[4] = (uint8_t)((s_identity.product_id >> 8) & 0xFFU);
    out_buf[5] = (uint8_t)(s_identity.product_version & 0xFFU);
    out_buf[6] = (uint8_t)((s_identity.product_version >> 8) & 0xFFU);
}

void device_identity_get_manuf_adv_bytes(uint8_t out_buf[4])
{
    /*
     * libbluetooth.so (0x0ad270..0x0ad60c):
     *   - Bytes 0..1: Vendor ID Little-Endian (ldrh r0, [r10, #0] -> 0x0171)
     *   - Bytes 2..3: Product ID Big-Endian (ldrh r2, [r10, #2]; rev16 r2, r2 -> 0x0443)
     *   - Verified by btif_in_is_amazon_ble_remote (0x0f89f8, 0x0f8b3c: movw r2, #0x0443)
     */
    out_buf[0] = (uint8_t)(s_identity.vendor_id & 0xFFU);
    out_buf[1] = (uint8_t)((s_identity.vendor_id >> 8) & 0xFFU);
    out_buf[2] = (uint8_t)((s_identity.product_id >> 8) & 0xFFU);
    out_buf[3] = (uint8_t)(s_identity.product_id & 0xFFU);
}

void device_identity_get_eir_devid_bytes(uint8_t out_buf[8])
{
    /*
     * libbluetooth.so (0x0ac688..0x0acd8c):
     *   - AD Type 0x10 (8 bytes):
     *   - Bytes 0..1: Vendor ID Source LE (0x0002)
     *   - Bytes 2..3: Vendor ID LE (0x0171)
     *   - Bytes 4..5: Product ID LE (0x0443)
     *   - Bytes 6..7: Version LE (0x0100)
     */
    out_buf[0] = s_identity.vendor_id_source;
    out_buf[1] = 0x00U;
    out_buf[2] = (uint8_t)(s_identity.vendor_id & 0xFFU);
    out_buf[3] = (uint8_t)((s_identity.vendor_id >> 8) & 0xFFU);
    out_buf[4] = (uint8_t)(s_identity.product_id & 0xFFU);
    out_buf[5] = (uint8_t)((s_identity.product_id >> 8) & 0xFFU);
    out_buf[6] = (uint8_t)(s_identity.product_version & 0xFFU);
    out_buf[7] = (uint8_t)((s_identity.product_version >> 8) & 0xFFU);
}

esp_err_t device_identity_set_name(const char *name)
{
    if (name == NULL || name[0] == '\0' || strlen(name) > DEVICE_NAME_MAX_LEN) {
        return ESP_ERR_INVALID_ARG;
    }
    strncpy(s_identity.device_name, name, DEVICE_NAME_MAX_LEN);
    s_identity.device_name[DEVICE_NAME_MAX_LEN] = '\0';
    diag_record_event("ID", "Device name changed to \"%s\"", s_identity.device_name);
    return ESP_OK;
}

esp_err_t device_identity_set_vid_pid(uint16_t vid, uint16_t pid)
{
    s_identity.vendor_id  = vid;
    s_identity.product_id = pid;
    diag_record_event("ID", "VID/PID overridden to VID=0x%04X PID=0x%04X", vid, pid);
    return ESP_OK;
}

esp_err_t device_identity_set_adv_mode(uint8_t mode)
{
    if (mode > (uint8_t)BLE_ADV_MODE_GENERIC_HID_ONLY) {
        return ESP_ERR_INVALID_ARG;
    }
    s_identity.adv_mode = mode;
    diag_record_event("ID", "BLE adv_mode changed to %u", mode);
    return ESP_OK;
}

void device_identity_print_info(void)
{
    uint8_t pnp[7];
    uint8_t manuf[4];
    uint8_t devid[8];
    device_identity_get_pnp_id_bytes(pnp);
    device_identity_get_manuf_adv_bytes(manuf);
    device_identity_get_eir_devid_bytes(devid);

    const diag_stats_t *st = diagnostics_get_stats();

    printf("\n==================== DEVICE IDENTITY & EVIDENCE ====================\n");
    printf("Field                      | Value                        | Provenance   | Evidence ID\n");
    printf("---------------------------+------------------------------+--------------+-------------------\n");
    printf("Vendor ID (VID)            | 0x%04X (Lab126/Amazon)       | CONFIRMED    | EVID-01/02/03/12\n",
           s_identity.vendor_id);
    printf("Product ID (PID)           | 0x%04X (Kindle Page Turner)  | CONFIRMED    | EVID-01/02/03/12\n",
           s_identity.product_id);
    printf("Vendor ID Source           | 0x%02X (USB-IF)                | CONFIRMED    | EVID-03/04\n",
           s_identity.vendor_id_source);
    printf("Product Version            | 0x%04X                       | HYPOTHESIS   | Standard HOGP\n",
           s_identity.product_version);
    printf("DIS PnP ID (0x2A50, 7B)    | %02X %02X %02X %02X %02X %02X %02X         | CONFIRMED    | EVID-03/04 (reads=%lu)\n",
           pnp[0], pnp[1], pnp[2], pnp[3], pnp[4], pnp[5], pnp[6],
           (unsigned long)st->dis_pnp_id_reads);
    printf("DIS Firmware Rev (0x2A26)  | \"%s\"                      | CONFIRMED*   | EVID-09 (reads=%lu)\n",
           s_identity.fw_revision, (unsigned long)st->dis_fw_rev_reads);
    printf("DIS Manufacturer (0x2A29)  | \"%s\"                     | HYPOTHESIS   | Standard DIS (reads=%lu)\n",
           s_identity.manufacturer_name, (unsigned long)st->dis_mfg_name_reads);
    printf("DIS Model Number (0x2A24)  | \"%s\"              | DERIVED      | EVID-02 (reads=%lu)\n",
           s_identity.model_number, (unsigned long)st->dis_model_num_reads);
    printf("DIS Serial Number (0x2A25) | \"%s\"                   | HYPOTHESIS   | Standard DIS (reads=%lu)\n",
           s_identity.serial_number, (unsigned long)st->dis_serial_num_reads);
    printf("GAP Device Name (0x2A00)   | \"%s\"              | DERIVED      | EVID-02/10\n",
           s_identity.device_name);
    printf("GAP Appearance (0x2A01)    | 0x%04X (HID Keyboard)        | DERIVED      | HOGP Spec\n",
           s_identity.appearance);
    printf("ADV Manuf Data (0xFF, 4B)  | %02X %02X %02X %02X (mode=%u)         | CONFIRMED    | EVID-12/13 (libbluetooth)\n",
           manuf[0], manuf[1], manuf[2], manuf[3], s_identity.adv_mode);
    printf("EIR Device ID (0x10, 8B)   | %02X %02X %02X %02X %02X %02X %02X %02X   | CONFIRMED    | EVID-13 (libbluetooth)\n",
           devid[0], devid[1], devid[2], devid[3], devid[4], devid[5], devid[6], devid[7]);
    printf("====================================================================\n");
    printf("*Note: 0x2A26 GATT read by acsbtfd is CONFIRMED (EVID-09); string value \"1.0.0\" is default.\n");
}
