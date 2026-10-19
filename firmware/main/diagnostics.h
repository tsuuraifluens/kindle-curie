#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define DIAG_LOG_CAPACITY 64
#define DIAG_MSG_MAX_LEN  96

typedef struct {
    uint32_t timestamp_ms;
    char category[12];
    char message[DIAG_MSG_MAX_LEN];
} diag_log_entry_t;

typedef struct {
    /* BLE Link & Security State */
    bool ble_stack_ready;
    bool advertising;
    bool connected;
    uint16_t conn_handle;
    bool encrypted;
    bool authenticated;
    bool bonded;
    uint8_t key_size;
    uint8_t own_addr_type;
    uint8_t own_addr[6];
    uint8_t peer_addr_type;
    uint8_t peer_addr[6];
    uint32_t connect_count;
    uint32_t disconnect_count;
    int last_disconnect_reason;
    int last_ble_error;
    char last_error_desc[64];

    /* Peer GATT Access Tracking (Distinguishes Local Config vs Peer Read) */
    uint32_t dis_pnp_id_reads;        /* 0x2A50 */
    uint32_t dis_fw_rev_reads;        /* 0x2A26 */
    uint32_t dis_mfg_name_reads;      /* 0x2A29 */
    uint32_t dis_model_num_reads;     /* 0x2A24 */
    uint32_t dis_serial_num_reads;    /* 0x2A25 */
    uint32_t dis_hw_rev_reads;        /* 0x2A27 */
    uint32_t dis_sw_rev_reads;        /* 0x2A28 */
    uint32_t bas_battery_reads;       /* 0x2A19 */
    uint32_t hids_info_reads;         /* 0x2A4A */
    uint32_t hids_report_map_reads;   /* 0x2A4B */
    uint32_t hids_input_report_reads; /* 0x2A4D (Keyboard Input) */
    uint32_t hids_consumer_report_reads; /* 0x2A4D (Consumer Input) */
    uint32_t hids_proto_mode_reads;   /* 0x2A4E */

    uint32_t hids_ctrl_point_writes;  /* 0x2A4C */
    uint32_t hids_proto_mode_writes;  /* 0x2A4E */
    uint32_t hids_output_writes;      /* 0x2A4D (Output) */

    uint8_t protocol_mode;            /* 0=Boot, 1=Report (default) */
    uint8_t hid_control_point;        /* 0=Suspend, 1=Exit Suspend */
    uint8_t led_output_state;

    /* CCCD Subscription State */
    bool input_report_cccd_enabled;
    bool consumer_report_cccd_enabled;
    bool battery_cccd_enabled;

    /* HID Report Action Counters */
    uint32_t prev_action_count;
    uint32_t next_action_count;
    uint32_t bright_up_action_count;
    uint32_t bright_down_action_count;
    uint32_t sleep_action_count;
    uint32_t custom_key_action_count;
    uint32_t notify_tx_ok_count;
    uint32_t notify_tx_fail_count;
    uint32_t debounced_drop_count;
    uint32_t rate_limited_drop_count;
    uint32_t not_connected_drop_count;
} diag_stats_t;

void diagnostics_init(void);
void diag_record_event(const char *category, const char *fmt, ...);
void diag_record_error(int err_code, const char *fmt, ...);
const diag_stats_t *diagnostics_get_stats(void);
diag_stats_t *diagnostics_get_mutable_stats(void);
void diagnostics_reset_peer_session_counters(void);
void diagnostics_print_status(void);
void diagnostics_print_logs(size_t max_entries);

#ifdef __cplusplus
}
#endif
