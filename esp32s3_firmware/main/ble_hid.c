#include "ble_hid.h"
#include "device_identity.h"
#include "hid_report.h"
#include "battery_service.h"
#include "diagnostics.h"

#include <stdio.h>
#include <string.h>
#include "esp_log.h"
#include "esp_err.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/ble_uuid.h"
#include "host/ble_gap.h"
#include "host/ble_gatt.h"
#include "host/util/util.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

/* Forward declaration from ESP-IDF NimBLE store_config component */
void ble_store_config_init(void);

static const char *TAG = "BLE_HID";

/* Standard 16-bit Service UUIDs */
#define UUID_SVC_DIS                   0x180AU /* Device Information Service (EVID-03/08/09) */
#define UUID_SVC_BAS                   0x180FU /* Battery Service            (EVID-07/08/10) */
#define UUID_SVC_HIDS                  0x1812U /* HID Service                (EVID-02..08)   */

/* DIS Characteristic UUIDs (0x180A) */
#define UUID_CHR_DIS_MODEL_NUM         0x2A24U
#define UUID_CHR_DIS_SERIAL_NUM        0x2A25U
#define UUID_CHR_DIS_FW_REV            0x2A26U /* Actively read by acsbtfd (EVID-09) */
#define UUID_CHR_DIS_HW_REV            0x2A27U
#define UUID_CHR_DIS_SW_REV            0x2A28U
#define UUID_CHR_DIS_MFG_NAME          0x2A29U
#define UUID_CHR_DIS_PNP_ID            0x2A50U /* Actively read by BlueDroid/uhid (EVID-03/04) */

/* BAS Characteristic UUIDs (0x180F) */
#define UUID_CHR_BAS_BATTERY_LEVEL     0x2A19U /* Read & Subscribed by AceHidBattery (EVID-07/10) */

/* HIDS Characteristic & Descriptor UUIDs (0x1812) */
#define UUID_CHR_HIDS_INFO             0x2A4AU
#define UUID_CHR_HIDS_REPORT_MAP       0x2A4BU
#define UUID_CHR_HIDS_CTRL_POINT       0x2A4CU
#define UUID_CHR_HIDS_REPORT           0x2A4DU
#define UUID_CHR_HIDS_PROTO_MODE       0x2A4EU
#define UUID_DSC_EXT_REPORT_REF        0x2907U
#define UUID_DSC_REPORT_REF            0x2908U

/* Attribute Value Handles Populated by NimBLE GATT Registration */
static uint16_t s_pnp_id_val_handle       = 0;
static uint16_t s_fw_rev_val_handle       = 0;
static uint16_t s_mfg_name_val_handle     = 0;
static uint16_t s_model_num_val_handle    = 0;
static uint16_t s_serial_num_val_handle   = 0;
static uint16_t s_hw_rev_val_handle       = 0;
static uint16_t s_sw_rev_val_handle       = 0;

static uint16_t s_battery_val_handle      = 0;

static uint16_t s_hid_info_val_handle     = 0;
static uint16_t s_report_map_val_handle   = 0;
static uint16_t s_ctrl_point_val_handle   = 0;
static uint16_t s_proto_mode_val_handle   = 0;
static uint16_t s_input_report_val_handle = 0;
static uint16_t s_output_report_val_handle = 0;
static uint16_t s_consumer_report_val_handle = 0;

static bool s_auto_adv_enabled = true;
static uint8_t s_last_adv_payload[31];
static size_t s_last_adv_len = 0;
static uint8_t s_last_rsp_payload[31];
static size_t s_last_rsp_len = 0;

static int ble_hid_gap_event(struct ble_gap_event *event, void *arg);

/* =========================================================================
 * GATT Access Callbacks (With Explicit Peer Read/Write Verification)
 * ========================================================================= */

static int gatt_dis_access_cb(uint16_t conn_handle, uint16_t attr_handle,
                              struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)attr_handle;
    (void)arg;

    if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) {
        return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
    }

    uint16_t uuid16 = ble_uuid_u16(ctxt->chr->uuid);
    const device_identity_config_t *id = device_identity_get();
    diag_stats_t *st = diagnostics_get_mutable_stats();
    int rc = 0;

    switch (uuid16) {
        case UUID_CHR_DIS_PNP_ID: {
            uint8_t pnp[7];
            device_identity_get_pnp_id_bytes(pnp);
            st->dis_pnp_id_reads++;
            diag_record_event("GATT", "Peer READ PnP ID (0x2A50, conn=%u, count=%lu) -> [%02X %02X %02X %02X %02X %02X %02X]",
                              conn_handle, (unsigned long)st->dis_pnp_id_reads,
                              pnp[0], pnp[1], pnp[2], pnp[3], pnp[4], pnp[5], pnp[6]);
            rc = os_mbuf_append(ctxt->om, pnp, sizeof(pnp));
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
        }
        case UUID_CHR_DIS_FW_REV:
            st->dis_fw_rev_reads++;
            diag_record_event("GATT", "Peer READ Firmware Rev (0x2A26, conn=%u, count=%lu) -> \"%s\"",
                              conn_handle, (unsigned long)st->dis_fw_rev_reads, id->fw_revision);
            rc = os_mbuf_append(ctxt->om, id->fw_revision, strlen(id->fw_revision));
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;

        case UUID_CHR_DIS_MFG_NAME:
            st->dis_mfg_name_reads++;
            diag_record_event("GATT", "Peer READ Manufacturer (0x2A29, conn=%u) -> \"%s\"",
                              conn_handle, id->manufacturer_name);
            rc = os_mbuf_append(ctxt->om, id->manufacturer_name, strlen(id->manufacturer_name));
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;

        case UUID_CHR_DIS_MODEL_NUM:
            st->dis_model_num_reads++;
            diag_record_event("GATT", "Peer READ Model Number (0x2A24, conn=%u) -> \"%s\"",
                              conn_handle, id->model_number);
            rc = os_mbuf_append(ctxt->om, id->model_number, strlen(id->model_number));
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;

        case UUID_CHR_DIS_SERIAL_NUM:
            st->dis_serial_num_reads++;
            diag_record_event("GATT", "Peer READ Serial Number (0x2A25, conn=%u) -> \"%s\"",
                              conn_handle, id->serial_number);
            rc = os_mbuf_append(ctxt->om, id->serial_number, strlen(id->serial_number));
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;

        case UUID_CHR_DIS_HW_REV:
            st->dis_hw_rev_reads++;
            diag_record_event("GATT", "Peer READ Hardware Rev (0x2A27, conn=%u) -> \"%s\"",
                              conn_handle, id->hw_revision);
            rc = os_mbuf_append(ctxt->om, id->hw_revision, strlen(id->hw_revision));
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;

        case UUID_CHR_DIS_SW_REV:
            st->dis_sw_rev_reads++;
            diag_record_event("GATT", "Peer READ Software Rev (0x2A28, conn=%u) -> \"%s\"",
                              conn_handle, id->sw_revision);
            rc = os_mbuf_append(ctxt->om, id->sw_revision, strlen(id->sw_revision));
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;

        default:
            return BLE_ATT_ERR_UNLIKELY;
    }
}

static int gatt_bas_access_cb(uint16_t conn_handle, uint16_t attr_handle,
                              struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)attr_handle;
    (void)arg;

    if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) {
        return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
    }

    uint8_t level = battery_service_get_level();
    diag_stats_t *st = diagnostics_get_mutable_stats();
    st->bas_battery_reads++;
    diag_record_event("GATT", "Peer READ Battery Level (0x2A19, conn=%u, count=%lu) -> %u%%",
                      conn_handle, (unsigned long)st->bas_battery_reads, level);

    int rc = os_mbuf_append(ctxt->om, &level, sizeof(level));
    return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

static int gatt_hids_chr_access_cb(uint16_t conn_handle, uint16_t attr_handle,
                                   struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    uint16_t uuid16 = ble_uuid_u16(ctxt->chr->uuid);
    diag_stats_t *st = diagnostics_get_mutable_stats();
    int rc = 0;

    switch (uuid16) {
        case UUID_CHR_HIDS_INFO: {
            if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) {
                return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
            }
            /* bcdHID = 0x0111 (v1.11 LE), bCountryCode = 0x00, Flags = 0x03 (RemoteWake | NormallyConnectable) */
            static const uint8_t hid_info[4] = { 0x11, 0x01, 0x00, 0x03 };
            st->hids_info_reads++;
            diag_record_event("GATT", "Peer READ HID Info (0x2A4A, conn=%u, count=%lu) -> [11 01 00 03]",
                              conn_handle, (unsigned long)st->hids_info_reads);
            rc = os_mbuf_append(ctxt->om, hid_info, sizeof(hid_info));
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
        }

        case UUID_CHR_HIDS_REPORT_MAP: {
            if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) {
                return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
            }
            size_t map_len = 0;
            const uint8_t *map = hid_report_get_map(&map_len);
            st->hids_report_map_reads++;
            diag_record_event("GATT", "Peer READ HID Report Map (0x2A4B, conn=%u, count=%lu, len=%u)",
                              conn_handle, (unsigned long)st->hids_report_map_reads, (unsigned int)map_len);
            rc = os_mbuf_append(ctxt->om, map, map_len);
            return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
        }

        case UUID_CHR_HIDS_CTRL_POINT: {
            if (ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR) {
                return BLE_ATT_ERR_READ_NOT_PERMITTED;
            }
            uint16_t om_len = OS_MBUF_PKTLEN(ctxt->om);
            if (om_len < 1) {
                return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
            }
            uint8_t val = 0;
            ble_hs_mbuf_to_flat(ctxt->om, &val, 1, NULL);
            st->hid_control_point = val;
            st->hids_ctrl_point_writes++;
            diag_record_event("GATT", "Peer WRITE HID Control Point (0x2A4C, conn=%u) -> 0x%02X (%s)",
                              conn_handle, val, (val == 0) ? "Suspend" : "Exit Suspend");
            return 0;
        }

        case UUID_CHR_HIDS_PROTO_MODE: {
            if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR) {
                st->hids_proto_mode_reads++;
                diag_record_event("GATT", "Peer READ Protocol Mode (0x2A4E, conn=%u) -> 0x%02X",
                                  conn_handle, st->protocol_mode);
                rc = os_mbuf_append(ctxt->om, &st->protocol_mode, 1);
                return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
            } else if (ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR) {
                uint16_t om_len = OS_MBUF_PKTLEN(ctxt->om);
                if (om_len < 1) {
                    return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
                }
                uint8_t mode = 1;
                ble_hs_mbuf_to_flat(ctxt->om, &mode, 1, NULL);
                st->protocol_mode = mode;
                st->hids_proto_mode_writes++;
                diag_record_event("GATT", "Peer WRITE Protocol Mode (0x2A4E, conn=%u) -> 0x%02X (%s)",
                                  conn_handle, mode, (mode == 1) ? "Report Mode" : "Boot Mode");
                return 0;
            }
            return BLE_ATT_ERR_UNLIKELY;
        }

        case UUID_CHR_HIDS_REPORT: {
            uintptr_t rpt_type = (uintptr_t)arg; /* 1 = KB Input, 2 = KB Output, 3 = Consumer Input */
            if (rpt_type == 1U || attr_handle == s_input_report_val_handle) {
                /* Keyboard Input Report (Report ID 1, 8 bytes) */
                if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) {
                    return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
                }
                uint8_t frame[HID_KEYBOARD_INPUT_REPORT_LEN];
                hid_report_get_last_input_report(frame);
                st->hids_input_report_reads++;
                diag_record_event("GATT", "Peer READ HID Keyboard Report (0x2A4D, conn=%u, count=%lu)",
                                  conn_handle, (unsigned long)st->hids_input_report_reads);
                rc = os_mbuf_append(ctxt->om, frame, sizeof(frame));
                return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
            } else if (rpt_type == 3U || attr_handle == s_consumer_report_val_handle) {
                /* Consumer Control Input Report (Report ID 2, 2 bytes) */
                if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) {
                    return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
                }
                uint8_t frame[HID_CONSUMER_INPUT_REPORT_LEN];
                hid_report_get_last_consumer_report(frame);
                st->hids_consumer_report_reads++;
                diag_record_event("GATT", "Peer READ HID Consumer Report (0x2A4D, conn=%u, count=%lu)",
                                  conn_handle, (unsigned long)st->hids_consumer_report_reads);
                rc = os_mbuf_append(ctxt->om, frame, sizeof(frame));
                return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
            } else {
                /* Output Report (Report ID 1, 1 byte LEDs) */
                if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR) {
                    rc = os_mbuf_append(ctxt->om, &st->led_output_state, 1);
                    return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
                } else if (ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR) {
                    uint8_t leds = 0;
                    if (OS_MBUF_PKTLEN(ctxt->om) >= 1) {
                        ble_hs_mbuf_to_flat(ctxt->om, &leds, 1, NULL);
                    }
                    st->led_output_state = leds;
                    st->hids_output_writes++;
                    diag_record_event("GATT", "Peer WRITE HID Output Report (0x2A4D, conn=%u) -> 0x%02X",
                                       conn_handle, leds);
                    return 0;
                }
            }
            return BLE_ATT_ERR_UNLIKELY;
        }

        default:
            return BLE_ATT_ERR_UNLIKELY;
    }
}

static int gatt_hids_dsc_access_cb(uint16_t conn_handle, uint16_t attr_handle,
                                   struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)attr_handle;
    if (ctxt->op != BLE_GATT_ACCESS_OP_READ_DSC) {
        return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
    }

    uint16_t uuid16 = ble_uuid_u16(ctxt->dsc->uuid);
    if (uuid16 == UUID_DSC_EXT_REPORT_REF) {
        /* External Report Reference (0x2907) -> Battery Level 0x2A19 LE */
        static const uint8_t ext_ref[2] = { 0x19, 0x2A };
        diag_record_event("GATT", "Peer READ External Report Ref (0x2907, conn=%u) -> [19 2A]", conn_handle);
        int rc = os_mbuf_append(ctxt->om, ext_ref, sizeof(ext_ref));
        return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    } else if (uuid16 == UUID_DSC_REPORT_REF) {
        /* Report Reference (0x2908): [Report ID = 1 or 2, Report Type = 1 (Input) or 2 (Output)] */
        uintptr_t rpt_type = (uintptr_t)arg;
        uint8_t ref[2] = { 0, 0 };
        if (rpt_type == 1U) {
            ref[0] = HID_KEYBOARD_REPORT_ID; /* 1 */
            ref[1] = 1;                      /* Type Input */
        } else if (rpt_type == 2U) {
            ref[0] = HID_KEYBOARD_REPORT_ID; /* 1 */
            ref[1] = 2;                      /* Type Output */
        } else if (rpt_type == 3U) {
            ref[0] = HID_CONSUMER_REPORT_ID; /* 2 */
            ref[1] = 1;                      /* Type Input */
        }
        diag_record_event("GATT", "Peer READ Report Ref (0x2908, conn=%u) -> [ID=0x%02X, Type=0x%02X]",
                          conn_handle, ref[0], ref[1]);
        int rc = os_mbuf_append(ctxt->om, ref, sizeof(ref));
        return (rc == 0) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }

    return BLE_ATT_ERR_UNLIKELY;
}

/* =========================================================================
 * GATT Service Definition Table (DIS 0x180A, BAS 0x180F, HIDS 0x1812)
 * ========================================================================= */

static const struct ble_gatt_svc_def s_gatt_svcs[] = {
    /* 1. Device Information Service (0x180A) - EVID-03, EVID-04, EVID-08, EVID-09 */
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = BLE_UUID16_DECLARE(UUID_SVC_DIS),
        .characteristics = (struct ble_gatt_chr_def[]) {
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_DIS_PNP_ID),
                .access_cb  = gatt_dis_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_pnp_id_val_handle,
            },
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_DIS_FW_REV),
                .access_cb  = gatt_dis_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_fw_rev_val_handle,
            },
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_DIS_MFG_NAME),
                .access_cb  = gatt_dis_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_mfg_name_val_handle,
            },
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_DIS_MODEL_NUM),
                .access_cb  = gatt_dis_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_model_num_val_handle,
            },
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_DIS_SERIAL_NUM),
                .access_cb  = gatt_dis_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_serial_num_val_handle,
            },
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_DIS_HW_REV),
                .access_cb  = gatt_dis_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_hw_rev_val_handle,
            },
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_DIS_SW_REV),
                .access_cb  = gatt_dis_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_sw_rev_val_handle,
            },
            { 0 }
        },
    },

    /* 2. Battery Service (0x180F) - EVID-07, EVID-08, EVID-10 */
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = BLE_UUID16_DECLARE(UUID_SVC_BAS),
        .characteristics = (struct ble_gatt_chr_def[]) {
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_BAS_BATTERY_LEVEL),
                .access_cb  = gatt_bas_access_cb,
                .flags      = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
                .val_handle = &s_battery_val_handle,
            },
            { 0 }
        },
    },

    /* 3. Human Interface Device Service (0x1812) - EVID-02..08 */
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = BLE_UUID16_DECLARE(UUID_SVC_HIDS),
        .characteristics = (struct ble_gatt_chr_def[]) {
            /* HID Information (0x2A4A) */
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_HIDS_INFO),
                .access_cb  = gatt_hids_chr_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_hid_info_val_handle,
            },
            /* Report Map (0x2A4B) + External Report Reference (0x2907 -> 0x2A19) */
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_HIDS_REPORT_MAP),
                .access_cb  = gatt_hids_chr_access_cb,
                .flags      = BLE_GATT_CHR_F_READ,
                .val_handle = &s_report_map_val_handle,
                .descriptors = (struct ble_gatt_dsc_def[]) {
                    {
                        .uuid      = BLE_UUID16_DECLARE(UUID_DSC_EXT_REPORT_REF),
                        .access_cb = gatt_hids_dsc_access_cb,
                        .att_flags = BLE_ATT_F_READ,
                    },
                    { 0 }
                },
            },
            /* HID Control Point (0x2A4C) */
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_HIDS_CTRL_POINT),
                .access_cb  = gatt_hids_chr_access_cb,
                .flags      = BLE_GATT_CHR_F_WRITE_NO_RSP,
                .val_handle = &s_ctrl_point_val_handle,
            },
            /* Protocol Mode (0x2A4E) */
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_HIDS_PROTO_MODE),
                .access_cb  = gatt_hids_chr_access_cb,
                .flags      = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_WRITE_NO_RSP,
                .val_handle = &s_proto_mode_val_handle,
            },
            /* HID Input Report (0x2A4D, Report ID 1, Type 1) + CCCD (0x2902) + Report Ref (0x2908) */
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_HIDS_REPORT),
                .access_cb  = gatt_hids_chr_access_cb,
                .arg        = (void *)(uintptr_t)1U,
                .flags      = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
                .val_handle = &s_input_report_val_handle,
                .descriptors = (struct ble_gatt_dsc_def[]) {
                    {
                        .uuid      = BLE_UUID16_DECLARE(UUID_DSC_REPORT_REF),
                        .access_cb = gatt_hids_dsc_access_cb,
                        .arg       = (void *)(uintptr_t)1U,
                        .att_flags = BLE_ATT_F_READ,
                    },
                    { 0 }
                },
            },
            /* HID Output Report (0x2A4D, Report ID 1, Type 2) + Report Ref (0x2908) */
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_HIDS_REPORT),
                .access_cb  = gatt_hids_chr_access_cb,
                .arg        = (void *)(uintptr_t)2U,
                .flags      = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_NO_RSP,
                .val_handle = &s_output_report_val_handle,
                .descriptors = (struct ble_gatt_dsc_def[]) {
                    {
                        .uuid      = BLE_UUID16_DECLARE(UUID_DSC_REPORT_REF),
                        .access_cb = gatt_hids_dsc_access_cb,
                        .arg       = (void *)(uintptr_t)2U,
                        .att_flags = BLE_ATT_F_READ,
                    },
                    { 0 }
                },
            },
            /* HID Consumer Input Report (0x2A4D, Report ID 2, Type 1) + CCCD (0x2902) + Report Ref (0x2908) */
            {
                .uuid       = BLE_UUID16_DECLARE(UUID_CHR_HIDS_REPORT),
                .access_cb  = gatt_hids_chr_access_cb,
                .arg        = (void *)(uintptr_t)3U,
                .flags      = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
                .val_handle = &s_consumer_report_val_handle,
                .descriptors = (struct ble_gatt_dsc_def[]) {
                    {
                        .uuid      = BLE_UUID16_DECLARE(UUID_DSC_REPORT_REF),
                        .access_cb = gatt_hids_dsc_access_cb,
                        .arg       = (void *)(uintptr_t)3U,
                        .att_flags = BLE_ATT_F_READ,
                    },
                    { 0 }
                },
            },
            { 0 }
        },
    },

    { 0 } /* End of services */
};

/* =========================================================================
 * BLE Advertising Construction & Control
 * ========================================================================= */

static esp_err_t build_and_set_adv_payloads(void)
{
    const device_identity_config_t *id = device_identity_get();
    ble_svc_gap_device_name_set(id->device_name);
    ble_svc_gap_device_appearance_set(id->appearance);

    uint8_t adv[31];
    size_t adv_len = 0;

    /* 1. Flags (AD Type 0x01): LE General Discoverable (0x02) | BR/EDR Not Supported (0x04) = 0x06 */
    adv[adv_len++] = 0x02;
    adv[adv_len++] = BLE_HS_ADV_TYPE_FLAGS;
    adv[adv_len++] = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;

    /* 2. Complete/Incomplete 16-bit Service UUIDs (AD Type 0x03): HIDS 0x1812 */
    adv[adv_len++] = 0x03;
    adv[adv_len++] = BLE_HS_ADV_TYPE_COMP_UUIDS16;
    adv[adv_len++] = (uint8_t)(UUID_SVC_HIDS & 0xFFU);
    adv[adv_len++] = (uint8_t)((UUID_SVC_HIDS >> 8) & 0xFFU);

    /*
     * 3. Manufacturer Specific Data (AD Type 0xFF) for Kindle BtDiscoveryFilter::Classify (EVID-12/13):
     *    Bytes: [0x05, 0xFF, 0x71, 0x01, 0x04, 0x43]
     *    Parsed by libbluetooth.so (0x0ad270..0x0ad60c) -> btif_in_is_amazon_ble_remote(0x0171, 0x0443)
     *    -> BT_PROPERTY_EIR_MANF_INFO -> acsbtfd BtDiscoveryFilter::Classify VP ID match!
     */
    if (id->adv_mode != (uint8_t)BLE_ADV_MODE_GENERIC_HID_ONLY) {
        uint8_t manuf[4];
        device_identity_get_manuf_adv_bytes(manuf);
        adv[adv_len++] = 0x05;
        adv[adv_len++] = BLE_HS_ADV_TYPE_MFG_DATA;
        memcpy(&adv[adv_len], manuf, 4);
        adv_len += 4;
    }

    /* 4. Complete/Shortened Local Name (AD Type 0x09 / 0x08) */
    size_t name_len = strlen(id->device_name);
    size_t max_name_room = (31U - adv_len >= 2U) ? (31U - adv_len - 2U) : 0U;
    if (max_name_room > 0 && name_len > 0) {
        size_t copy_len = (name_len <= max_name_room) ? name_len : max_name_room;
        adv[adv_len++] = (uint8_t)(copy_len + 1U);
        adv[adv_len++] = (copy_len == name_len) ? BLE_HS_ADV_TYPE_COMP_NAME : BLE_HS_ADV_TYPE_INCOMP_NAME;
        memcpy(&adv[adv_len], id->device_name, copy_len);
        adv_len += copy_len;
    }

    /* Build Scan Response Data (SCAN_RSP, max 31 bytes) */
    uint8_t rsp[31];
    size_t rsp_len = 0;

    /* 1. Appearance (AD Type 0x19): 0x03C1 (HID Keyboard) */
    rsp[rsp_len++] = 0x03;
    rsp[rsp_len++] = BLE_HS_ADV_TYPE_APPEARANCE;
    rsp[rsp_len++] = (uint8_t)(id->appearance & 0xFFU);
    rsp[rsp_len++] = (uint8_t)((id->appearance >> 8) & 0xFFU);

    /* 2. Complete List of 16-bit Service UUIDs (AD Type 0x03): 0x1812 (HIDS), 0x180F (BAS), 0x180A (DIS) */
    rsp[rsp_len++] = 0x07;
    rsp[rsp_len++] = BLE_HS_ADV_TYPE_COMP_UUIDS16;
    rsp[rsp_len++] = 0x12; rsp[rsp_len++] = 0x18; /* 0x1812 */
    rsp[rsp_len++] = 0x0F; rsp[rsp_len++] = 0x18; /* 0x180F */
    rsp[rsp_len++] = 0x0A; rsp[rsp_len++] = 0x18; /* 0x180A */

    /* 3. Optional EIR Device ID (AD Type 0x10, 8 bytes) if BLE_ADV_MODE_EIR_DEVID_0X10 */
    if (id->adv_mode == (uint8_t)BLE_ADV_MODE_EIR_DEVID_0X10 && (rsp_len + 10U <= 31U)) {
        uint8_t devid[8];
        device_identity_get_eir_devid_bytes(devid);
        rsp[rsp_len++] = 0x09;
        rsp[rsp_len++] = 0x10; /* EIR Device ID / PnP Info */
        memcpy(&rsp[rsp_len], devid, 8);
        rsp_len += 8;
    }

    memcpy(s_last_adv_payload, adv, adv_len);
    s_last_adv_len = adv_len;
    memcpy(s_last_rsp_payload, rsp, rsp_len);
    s_last_rsp_len = rsp_len;

    int rc = ble_gap_adv_set_data(adv, adv_len);
    if (rc != 0) {
        diag_record_error(rc, "ble_gap_adv_set_data failed (len=%u)", (unsigned int)adv_len);
        return ESP_FAIL;
    }

    rc = ble_gap_adv_rsp_set_data(rsp, rsp_len);
    if (rc != 0) {
        diag_record_error(rc, "ble_gap_adv_rsp_set_data failed (len=%u)", (unsigned int)rsp_len);
        return ESP_FAIL;
    }

    return ESP_OK;
}

esp_err_t ble_hid_start_advertising(void)
{
    diag_stats_t *st = diagnostics_get_mutable_stats();
    if (!st->ble_stack_ready) {
        diag_record_error(ESP_ERR_INVALID_STATE, "Cannot start advertising: BLE stack not ready");
        return ESP_ERR_INVALID_STATE;
    }

    s_auto_adv_enabled = true;

    if (ble_gap_adv_active()) {
        ble_gap_adv_stop();
    }

    esp_err_t err = build_and_set_adv_payloads();
    if (err != ESP_OK) {
        return err;
    }

    struct ble_gap_adv_params adv_params;
    memset(&adv_params, 0, sizeof(adv_params));
    adv_params.conn_mode = BLE_GAP_CONN_MODE_UND; /* Connectable Undirected (ADV_IND) */
    adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN; /* General Discoverable */
    adv_params.itvl_min  = 0x0020;                /* 20 ms (32 * 0.625 ms) fast discovery */
    adv_params.itvl_max  = 0x0040;                /* 40 ms (64 * 0.625 ms) */

    int rc = ble_gap_adv_start(st->own_addr_type, NULL, BLE_HS_FOREVER,
                               &adv_params, ble_hid_gap_event, NULL);
    if (rc != 0) {
        st->advertising = false;
        diag_record_error(rc, "ble_gap_adv_start failed");
        return ESP_FAIL;
    }

    st->advertising = true;
    const device_identity_config_t *id = device_identity_get();
    diag_record_event("BLE", "Advertising STARTED: name=\"%s\" VID=0x%04X PID=0x%04X adv_mode=%u (adv=%uB, rsp=%uB)",
                      id->device_name, id->vendor_id, id->product_id,
                      id->adv_mode, (unsigned int)s_last_adv_len, (unsigned int)s_last_rsp_len);
    return ESP_OK;
}

esp_err_t ble_hid_stop_advertising(bool disconnect_peer)
{
    diag_stats_t *st = diagnostics_get_mutable_stats();
    s_auto_adv_enabled = false;

    if (ble_gap_adv_active()) {
        int rc = ble_gap_adv_stop();
        if (rc != 0) {
            diag_record_error(rc, "ble_gap_adv_stop failed");
        }
    }
    st->advertising = false;

    if (disconnect_peer && st->connected && st->conn_handle != 0xFFFF) {
        diag_record_event("BLE", "Terminating active connection (handle=%u)", st->conn_handle);
        ble_gap_terminate(st->conn_handle, BLE_ERR_REM_USER_CONN_TERM);
    }

    diag_record_event("BLE", "Advertising STOPPED");
    return ESP_OK;
}

/* =========================================================================
 * GAP Event Handler (Connect, Disconnect, Encryption, Subscribe, Repeat Pair)
 * ========================================================================= */

static void update_conn_security_stats(uint16_t conn_handle)
{
    struct ble_gap_conn_desc desc;
    if (ble_gap_conn_find(conn_handle, &desc) == 0) {
        diag_stats_t *st = diagnostics_get_mutable_stats();
        st->encrypted     = desc.sec_state.encrypted;
        st->authenticated = desc.sec_state.authenticated;
        st->bonded        = desc.sec_state.bonded;
        st->key_size      = desc.sec_state.key_size;
        st->peer_addr_type = desc.peer_id_addr.type;
        memcpy(st->peer_addr, desc.peer_id_addr.val, 6);
    }
}

static int ble_hid_gap_event(struct ble_gap_event *event, void *arg)
{
    (void)arg;
    diag_stats_t *st = diagnostics_get_mutable_stats();

    switch (event->type) {
        case BLE_GAP_EVENT_CONNECT: {
            if (event->connect.status == 0) {
                st->connected = true;
                st->advertising = false;
                st->conn_handle = event->connect.conn_handle;
                st->connect_count++;
                diagnostics_reset_peer_session_counters();
                update_conn_security_stats(event->connect.conn_handle);

                diag_record_event("GAP", "CONNECTED: handle=%u peer=%02X:%02X:%02X:%02X:%02X:%02X (type=%u)",
                                  st->conn_handle,
                                  st->peer_addr[5], st->peer_addr[4], st->peer_addr[3],
                                  st->peer_addr[2], st->peer_addr[1], st->peer_addr[0],
                                  st->peer_addr_type);
            } else {
                st->connected = false;
                st->conn_handle = 0xFFFF;
                diag_record_error(event->connect.status, "Connection establishment failed");
                if (s_auto_adv_enabled) {
                    ble_hid_start_advertising();
                }
            }
            return 0;
        }

        case BLE_GAP_EVENT_DISCONNECT: {
            uint16_t old_handle = event->disconnect.conn.conn_handle;
            int reason = event->disconnect.reason;
            hid_report_on_disconnect();

            st->connected = false;
            st->conn_handle = 0xFFFF;
            st->encrypted = false;
            st->authenticated = false;
            st->disconnect_count++;
            st->last_disconnect_reason = reason;
            diagnostics_reset_peer_session_counters();

            diag_record_event("GAP", "DISCONNECTED: handle=%u reason=0x%02X (%d)",
                              old_handle, (unsigned int)(reason & 0xFF), reason);

            if (s_auto_adv_enabled) {
                ble_hid_start_advertising();
            }
            return 0;
        }

        case BLE_GAP_EVENT_ADV_COMPLETE: {
            st->advertising = false;
            diag_record_event("GAP", "Advertising complete (reason=%d)", event->adv_complete.reason);
            if (s_auto_adv_enabled && !st->connected) {
                ble_hid_start_advertising();
            }
            return 0;
        }

        case BLE_GAP_EVENT_ENC_CHANGE: {
            update_conn_security_stats(event->enc_change.conn_handle);
            diag_record_event("SMP", "ENC_CHANGE: status=%d encrypted=%d auth=%d bonded=%d key_size=%u",
                              event->enc_change.status,
                              st->encrypted ? 1 : 0,
                              st->authenticated ? 1 : 0,
                              st->bonded ? 1 : 0,
                              st->key_size);
            return 0;
        }

        case BLE_GAP_EVENT_SUBSCRIBE: {
            uint16_t attr_handle = event->subscribe.attr_handle;
            bool cur_notify = event->subscribe.cur_notify;

            if (attr_handle == s_input_report_val_handle) {
                st->input_report_cccd_enabled = cur_notify;
                diag_record_event("GATT", "Peer %s Keyboard Input Report CCCD (0x2A4D, handle=%u)",
                                  cur_notify ? "SUBSCRIBED to" : "UNSUBSCRIBED from", attr_handle);
            } else if (attr_handle == s_consumer_report_val_handle) {
                st->consumer_report_cccd_enabled = cur_notify;
                diag_record_event("GATT", "Peer %s Consumer Report CCCD (0x2A4D, handle=%u)",
                                  cur_notify ? "SUBSCRIBED to" : "UNSUBSCRIBED from", attr_handle);
            } else if (attr_handle == s_battery_val_handle) {
                st->battery_cccd_enabled = cur_notify;
                diag_record_event("GATT", "Peer %s Battery Level CCCD (0x2A19, handle=%u)",
                                  cur_notify ? "SUBSCRIBED to" : "UNSUBSCRIBED from", attr_handle);
            } else {
                diag_record_event("GATT", "Peer SUBSCRIBE event: attr_handle=%u notify=%d indicate=%d",
                                  attr_handle, cur_notify ? 1 : 0, event->subscribe.cur_indicate ? 1 : 0);
            }
            return 0;
        }

        case BLE_GAP_EVENT_CONN_UPDATE_REQ: {
            diag_record_event("GAP", "CONN_UPDATE_REQ: itvl_min=%.2fms itvl_max=%.2fms latency=%u timeout=%ums",
                              event->conn_update_req.peer_params->itvl_min * 1.25f,
                              event->conn_update_req.peer_params->itvl_max * 1.25f,
                              event->conn_update_req.peer_params->latency,
                              event->conn_update_req.peer_params->supervision_timeout * 10);
            return 0; /* Accept requested low power parameters from Kindle */
        }

        case BLE_GAP_EVENT_CONN_UPDATE: {
            struct ble_gap_conn_desc desc;
            if (ble_gap_conn_find(event->conn_update.conn_handle, &desc) == 0) {
                diag_record_event("GAP", "CONN_UPDATE applied: itvl=%.2fms latency=%u timeout=%ums",
                                  desc.conn_itvl * 1.25f,
                                  desc.conn_latency,
                                  desc.supervision_timeout * 10);
            }
            return 0;
        }

        case BLE_GAP_EVENT_REPEAT_PAIRING: {
            /*
             * If the peer (Kindle) deleted its bond and initiates a fresh pairing,
             * delete the stale local bond record and allow retry so pairing succeeds.
             */
            struct ble_gap_conn_desc desc;
            if (ble_gap_conn_find(event->repeat_pairing.conn_handle, &desc) == 0) {
                ble_store_util_delete_peer(&desc.peer_id_addr);
                diag_record_event("SMP", "REPEAT_PAIRING from peer: replaced old bond key for re-pairing");
            }
            return BLE_GAP_REPEAT_PAIRING_RETRY;
        }

        case BLE_GAP_EVENT_PASSKEY_ACTION: {
            diag_record_event("SMP", "PASSKEY_ACTION action=%d", event->passkey.params.action);
            if (event->passkey.params.action == BLE_SM_IOACT_NUMCMP) {
                struct ble_sm_io pkey = {
                    .action = event->passkey.params.action,
                    .numcmp_accept = 1
                };
                ble_sm_inject_io(event->passkey.conn_handle, &pkey);
            }
            return 0;
        }

        case BLE_GAP_EVENT_MTU: {
            diag_record_event("GAP", "ATT MTU updated: conn=%u cid=%u mtu=%u",
                              event->mtu.conn_handle, event->mtu.channel_id, event->mtu.value);
            return 0;
        }

        default:
            return 0;
    }
}

/* =========================================================================
 * GATT Notification Senders (Input Report 0x2A4D & Battery Level 0x2A19)
 * ========================================================================= */

esp_err_t ble_hid_notify_input_report(const uint8_t *report_data, size_t report_len)
{
    const diag_stats_t *st = diagnostics_get_stats();
    if (!st->connected || st->conn_handle == 0xFFFF) {
        return ESP_ERR_INVALID_STATE;
    }
    if (!st->input_report_cccd_enabled) {
        return ESP_ERR_INVALID_STATE;
    }

    struct os_mbuf *om = ble_hs_mbuf_from_flat(report_data, report_len);
    if (om == NULL) {
        return ESP_ERR_NO_MEM;
    }

    int rc = ble_gatts_notify_custom(st->conn_handle, s_input_report_val_handle, om);
    if (rc != 0) {
        return ESP_FAIL;
    }
    return ESP_OK;
}

esp_err_t ble_hid_notify_consumer_report(const uint8_t *report_data, size_t report_len)
{
    const diag_stats_t *st = diagnostics_get_stats();
    if (!st->connected || st->conn_handle == 0xFFFF) {
        return ESP_ERR_INVALID_STATE;
    }
    if (!st->consumer_report_cccd_enabled) {
        return ESP_ERR_INVALID_STATE;
    }

    struct os_mbuf *om = ble_hs_mbuf_from_flat(report_data, report_len);
    if (om == NULL) {
        return ESP_ERR_NO_MEM;
    }

    int rc = ble_gatts_notify_custom(st->conn_handle, s_consumer_report_val_handle, om);
    if (rc != 0) {
        return ESP_FAIL;
    }
    return ESP_OK;
}

esp_err_t ble_hid_notify_battery(uint8_t battery_pct)
{
    const diag_stats_t *st = diagnostics_get_stats();
    if (!st->connected || st->conn_handle == 0xFFFF || !st->battery_cccd_enabled) {
        return ESP_OK; /* Value updated locally; no active subscriber */
    }

    struct os_mbuf *om = ble_hs_mbuf_from_flat(&battery_pct, sizeof(battery_pct));
    if (om == NULL) {
        return ESP_ERR_NO_MEM;
    }

    int rc = ble_gatts_notify_custom(st->conn_handle, s_battery_val_handle, om);
    return (rc == 0) ? ESP_OK : ESP_FAIL;
}

/* =========================================================================
 * Bonding Inspection & Management
 * ========================================================================= */

void ble_hid_print_bond_status(void)
{
    ble_addr_t peer_addrs[MYNEWT_VAL(BLE_STORE_MAX_BONDS)];
    int num_bonds = 0;

    int rc = ble_store_util_bonded_peers(peer_addrs, &num_bonds, MYNEWT_VAL(BLE_STORE_MAX_BONDS));
    printf("\n==================== BLE BOND STATUS ====================\n");
    if (rc != 0) {
        printf("Failed to query bonded peers (rc=%d)\n", rc);
    } else {
        printf("Bonded Peer Count: %d / %d\n", num_bonds, MYNEWT_VAL(BLE_STORE_MAX_BONDS));
        for (int i = 0; i < num_bonds; i++) {
            printf("  [%d] %02X:%02X:%02X:%02X:%02X:%02X (type=%u)\n",
                   i,
                   peer_addrs[i].val[5], peer_addrs[i].val[4], peer_addrs[i].val[3],
                   peer_addrs[i].val[2], peer_addrs[i].val[1], peer_addrs[i].val[0],
                   peer_addrs[i].type);
        }
    }
    const diag_stats_t *st = diagnostics_get_stats();
    printf("Active Connection: %s (encrypted=%s, authenticated=%s, bonded=%s)\n",
           st->connected ? "YES" : "NO",
           st->encrypted ? "YES" : "NO",
           st->authenticated ? "YES" : "NO",
           st->bonded ? "YES" : "NO");
    printf("=========================================================\n");
}

esp_err_t ble_hid_clear_bonds(void)
{
    int rc = ble_store_clear();
    if (rc != 0) {
        diag_record_error(rc, "ble_store_clear failed");
        return ESP_FAIL;
    }
    diag_record_event("SMP", "All BLE bonding records cleared from NVS");
    return ESP_OK;
}

void ble_hid_print_gatt_and_adv_table(void)
{
    printf("\n==================== GATT HANDLE & ADV TABLE ====================\n");
    printf("DIS (0x180A):\n");
    printf("  0x2A50 PnP ID              handle = %u\n", s_pnp_id_val_handle);
    printf("  0x2A26 Firmware Revision   handle = %u\n", s_fw_rev_val_handle);
    printf("  0x2A29 Manufacturer Name   handle = %u\n", s_mfg_name_val_handle);
    printf("  0x2A24 Model Number        handle = %u\n", s_model_num_val_handle);
    printf("  0x2A25 Serial Number       handle = %u\n", s_serial_num_val_handle);
    printf("  0x2A27 Hardware Revision   handle = %u\n", s_hw_rev_val_handle);
    printf("  0x2A28 Software Revision   handle = %u\n", s_sw_rev_val_handle);
    printf("BAS (0x180F):\n");
    printf("  0x2A19 Battery Level       handle = %u (Read | Notify)\n", s_battery_val_handle);
    size_t map_size = 0;
    hid_report_get_map(&map_size);
    printf("HIDS (0x1812):\n");
    printf("  0x2A4A HID Information     handle = %u\n", s_hid_info_val_handle);
    printf("  0x2A4B Report Map          handle = %u (%u bytes, Composite)\n", s_report_map_val_handle, (unsigned int)map_size);
    printf("  0x2A4C HID Control Point   handle = %u\n", s_ctrl_point_val_handle);
    printf("  0x2A4E Protocol Mode       handle = %u\n", s_proto_mode_val_handle);
    printf("  0x2A4D Input Report (ID 1) handle = %u (Keyboard, Read | Notify)\n", s_input_report_val_handle);
    printf("  0x2A4D Output Report(ID 1) handle = %u (LEDs, Read | Write | WriteNoRsp)\n", s_output_report_val_handle);
    printf("  0x2A4D Consumer Rpt (ID 2) handle = %u (Consumer Control, Read | Notify)\n", s_consumer_report_val_handle);

    printf("Primary ADV_IND Payload (%u bytes): ", (unsigned int)s_last_adv_len);
    for (size_t i = 0; i < s_last_adv_len; i++) {
        printf("%02X ", s_last_adv_payload[i]);
    }
    printf("\nScan Response Payload   (%u bytes): ", (unsigned int)s_last_rsp_len);
    for (size_t i = 0; i < s_last_rsp_len; i++) {
        printf("%02X ", s_last_rsp_payload[i]);
    }
    printf("\n=================================================================\n");
}

/* =========================================================================
 * NimBLE Host Callbacks & Initialization
 * ========================================================================= */

static void ble_hid_on_reset(int reason)
{
    diag_stats_t *st = diagnostics_get_mutable_stats();
    st->ble_stack_ready = false;
    st->advertising = false;
    st->connected = false;
    diag_record_error(reason, "NimBLE host reset");
}

static void ble_hid_on_sync(void)
{
    int rc = ble_hs_util_ensure_addr(0);
    if (rc != 0) {
        diag_record_error(rc, "ble_hs_util_ensure_addr failed");
        return;
    }

    uint8_t own_addr_type = 0;
    rc = ble_hs_id_infer_auto(0, &own_addr_type);
    if (rc != 0) {
        diag_record_error(rc, "ble_hs_id_infer_auto failed");
        return;
    }

    diag_stats_t *st = diagnostics_get_mutable_stats();
    st->own_addr_type = own_addr_type;
    ble_hs_id_copy_addr(own_addr_type, st->own_addr, NULL);
    st->ble_stack_ready = true;

    diag_record_event("BLE", "NimBLE host synced: addr=%02X:%02X:%02X:%02X:%02X:%02X (type=%u)",
                      st->own_addr[5], st->own_addr[4], st->own_addr[3],
                      st->own_addr[2], st->own_addr[1], st->own_addr[0],
                      st->own_addr_type);

    if (s_auto_adv_enabled) {
        ble_hid_start_advertising();
    }
}

static void ble_hid_host_task(void *param)
{
    (void)param;
    ESP_LOGI(TAG, "NimBLE host task started");
    nimble_port_run();
    nimble_port_freertos_deinit();
}

esp_err_t ble_hid_init(void)
{
    esp_err_t err = nimble_port_init();
    if (err != ESP_OK) {
        diag_record_error((int)err, "nimble_port_init failed");
        return err;
    }

    /* Configure NimBLE Host Callbacks & SMP Security Manager (Just Works + Bonding) */
    ble_hs_cfg.reset_cb          = ble_hid_on_reset;
    ble_hs_cfg.sync_cb           = ble_hid_on_sync;
    ble_hs_cfg.store_status_cb   = ble_store_util_status_rr;
    ble_hs_cfg.sm_io_cap         = BLE_HS_IO_NO_INPUT_OUTPUT; /* Headless Page Turner: Just Works */
    ble_hs_cfg.sm_bonding        = 1;
    ble_hs_cfg.sm_mitm           = 0;
    ble_hs_cfg.sm_sc             = 1;                         /* LE Secure Connections + Legacy fallback */
    ble_hs_cfg.sm_our_key_dist   = BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;
    ble_hs_cfg.sm_their_key_dist = BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;

    /* Initialize Standard GAP (0x1800) and GATT (0x1801) Services */
    ble_svc_gap_init();
    ble_svc_gatt_init();

    const device_identity_config_t *id = device_identity_get();
    ble_svc_gap_device_name_set(id->device_name);
    ble_svc_gap_device_appearance_set(id->appearance);

    /* Register DIS (0x180A), BAS (0x180F), and HIDS (0x1812) GATT Services */
    int rc = ble_gatts_count_cfg(s_gatt_svcs);
    if (rc != 0) {
        diag_record_error(rc, "ble_gatts_count_cfg failed");
        return ESP_FAIL;
    }

    rc = ble_gatts_add_svcs(s_gatt_svcs);
    if (rc != 0) {
        diag_record_error(rc, "ble_gatts_add_svcs failed");
        return ESP_FAIL;
    }

    /* Persist BLE bonding keys in NVS */
    ble_store_config_init();

    /* Start NimBLE FreeRTOS Host Task */
    nimble_port_freertos_init(ble_hid_host_task);

    diag_record_event("BLE", "BLE HID subsystem initialized (DIS=0x180A, BAS=0x180F, HIDS=0x1812)");
    return ESP_OK;
}
