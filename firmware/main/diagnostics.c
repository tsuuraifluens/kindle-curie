#include "diagnostics.h"
#include "device_identity.h"
#include "battery_service.h"
#include "hid_report.h"

#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include "esp_timer.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

static const char *TAG = "DIAG";

static diag_stats_t s_stats;
static diag_log_entry_t s_log_ring[DIAG_LOG_CAPACITY];
static size_t s_log_head = 0;
static size_t s_log_count = 0;
static SemaphoreHandle_t s_diag_mutex = NULL;

static uint32_t get_uptime_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000ULL);
}

void diagnostics_init(void)
{
    if (s_diag_mutex == NULL) {
        s_diag_mutex = xSemaphoreCreateMutex();
    }
    memset(&s_stats, 0, sizeof(s_stats));
    s_stats.conn_handle = 0xFFFF;
    s_stats.protocol_mode = 0x01;     /* Report Mode (HOGP default) */
    s_stats.hid_control_point = 0x01; /* Exit Suspend */
    strncpy(s_stats.last_error_desc, "None", sizeof(s_stats.last_error_desc) - 1);

    memset(s_log_ring, 0, sizeof(s_log_ring));
    s_log_head = 0;
    s_log_count = 0;

    diag_record_event("SYS", "Diagnostics initialized");
}

void diag_record_event(const char *category, const char *fmt, ...)
{
    char buf[DIAG_MSG_MAX_LEN];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    uint32_t now_ms = get_uptime_ms();

    if (s_diag_mutex != NULL && xSemaphoreTake(s_diag_mutex, pdMS_TO_TICKS(20)) == pdTRUE) {
        diag_log_entry_t *entry = &s_log_ring[s_log_head];
        entry->timestamp_ms = now_ms;
        strncpy(entry->category, category ? category : "GEN", sizeof(entry->category) - 1);
        entry->category[sizeof(entry->category) - 1] = '\0';
        strncpy(entry->message, buf, sizeof(entry->message) - 1);
        entry->message[sizeof(entry->message) - 1] = '\0';

        s_log_head = (s_log_head + 1) % DIAG_LOG_CAPACITY;
        if (s_log_count < DIAG_LOG_CAPACITY) {
            s_log_count++;
        }
        xSemaphoreGive(s_diag_mutex);
    }

    ESP_LOGI(TAG, "[%s] %s", category ? category : "GEN", buf);
}

void diag_record_error(int err_code, const char *fmt, ...)
{
    char buf[DIAG_MSG_MAX_LEN];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    if (s_diag_mutex != NULL && xSemaphoreTake(s_diag_mutex, pdMS_TO_TICKS(20)) == pdTRUE) {
        s_stats.last_ble_error = err_code;
        strncpy(s_stats.last_error_desc, buf, sizeof(s_stats.last_error_desc) - 1);
        s_stats.last_error_desc[sizeof(s_stats.last_error_desc) - 1] = '\0';
        xSemaphoreGive(s_diag_mutex);
    }

    diag_record_event("ERR", "code=%d: %s", err_code, buf);
}

const diag_stats_t *diagnostics_get_stats(void)
{
    return &s_stats;
}

diag_stats_t *diagnostics_get_mutable_stats(void)
{
    return &s_stats;
}

void diagnostics_reset_peer_session_counters(void)
{
    s_stats.input_report_cccd_enabled = false;
    s_stats.consumer_report_cccd_enabled = false;
    s_stats.battery_cccd_enabled = false;
}

void diagnostics_print_status(void)
{
    const device_identity_config_t *id = device_identity_get();
    printf("\n==================== STATUS REPORT ====================\n");
    printf("Uptime                : %lu ms\n", (unsigned long)get_uptime_ms());
    printf("BLE Stack Ready       : %s\n", s_stats.ble_stack_ready ? "YES" : "NO");
    printf("BLE Advertising       : %s\n", s_stats.advertising ? "ACTIVE" : "STOPPED");
    printf("Own BLE Address       : %02X:%02X:%02X:%02X:%02X:%02X (type=%u)\n",
           s_stats.own_addr[5], s_stats.own_addr[4], s_stats.own_addr[3],
           s_stats.own_addr[2], s_stats.own_addr[1], s_stats.own_addr[0],
           s_stats.own_addr_type);
    printf("Connection State      : %s (conn_handle=0x%04X, connects=%lu, disconnects=%lu)\n",
           s_stats.connected ? "CONNECTED" : "DISCONNECTED",
           s_stats.conn_handle,
           (unsigned long)s_stats.connect_count,
           (unsigned long)s_stats.disconnect_count);
    if (s_stats.connected) {
        printf("Peer BLE Address      : %02X:%02X:%02X:%02X:%02X:%02X (type=%u)\n",
               s_stats.peer_addr[5], s_stats.peer_addr[4], s_stats.peer_addr[3],
               s_stats.peer_addr[2], s_stats.peer_addr[1], s_stats.peer_addr[0],
               s_stats.peer_addr_type);
    }
    printf("Link Security (SMP)   : encrypted=%s, authenticated=%s, bonded=%s, key_size=%u\n",
           s_stats.encrypted ? "YES" : "NO",
           s_stats.authenticated ? "YES" : "NO",
           s_stats.bonded ? "YES" : "NO",
           s_stats.key_size);
    printf("Last Disconnect Reason: 0x%02X (%d)\n",
           (unsigned int)(s_stats.last_disconnect_reason & 0xFF),
           s_stats.last_disconnect_reason);
    printf("Last Error            : [%d] %s\n", s_stats.last_ble_error, s_stats.last_error_desc);

    printf("\n--- [1] Local GATT Configuration (ESP32-S3 Side) ---\n");
    printf("Advertised Name       : \"%s\"\n", id->device_name);
    printf("PnP ID (0x2A50)       : src=0x%02X VID=0x%04X PID=0x%04X Ver=0x%04X\n",
           id->vendor_id_source, id->vendor_id, id->product_id, id->product_version);
    printf("Firmware Rev (0x2A26) : \"%s\"\n", id->fw_revision);
    printf("Battery Level (0x2A19): %u%%\n", battery_service_get_level());
    printf("HID Protocol Mode     : %s (0x%02X)\n",
           s_stats.protocol_mode == 1 ? "Report Mode" : "Boot Mode",
           s_stats.protocol_mode);

    printf("\n--- [2] Peer GATT Access Verification (Central Side) ---\n");
    printf("DIS PnP ID (0x2A50) Reads       : %lu %s\n",
           (unsigned long)s_stats.dis_pnp_id_reads,
           s_stats.dis_pnp_id_reads > 0 ? "[PEER READ VERIFIED]" : "[NOT YET READ BY PEER]");
    printf("DIS FW Rev (0x2A26) Reads       : %lu %s\n",
           (unsigned long)s_stats.dis_fw_rev_reads,
           s_stats.dis_fw_rev_reads > 0 ? "[PEER READ VERIFIED (acsbtfd)]" : "[NOT YET READ BY PEER]");
    printf("HIDS Report Map (0x2A4B) Reads  : %lu %s\n",
           (unsigned long)s_stats.hids_report_map_reads,
           s_stats.hids_report_map_reads > 0 ? "[PEER READ VERIFIED]" : "[NOT YET READ BY PEER]");
    printf("HIDS HID Info (0x2A4A) Reads    : %lu\n", (unsigned long)s_stats.hids_info_reads);
    printf("HIDS Consumer Rpt Reads         : %lu\n", (unsigned long)s_stats.hids_consumer_report_reads);
    printf("BAS Battery (0x2A19) Reads      : %lu\n", (unsigned long)s_stats.bas_battery_reads);
    printf("Keyboard Input CCCD (0x2902)    : %s\n",
           s_stats.input_report_cccd_enabled ? "SUBSCRIBED" : "UNSUBSCRIBED");
    printf("Consumer Control CCCD (0x2902)  : %s\n",
           s_stats.consumer_report_cccd_enabled ? "SUBSCRIBED" : "UNSUBSCRIBED");
    printf("Battery CCCD (0x2902)           : %s\n",
           s_stats.battery_cccd_enabled ? "SUBSCRIBED" : "UNSUBSCRIBED");

    printf("\n--- [3] Page Turn HID Report Telemetry ---\n");
    printf("State Machine         : %s\n", hid_report_state_name(hid_report_get_state()));
    printf("PREV (0x4B PageUp)    : %lu triggered\n", (unsigned long)s_stats.prev_action_count);
    printf("NEXT (0x4E PageDown)  : %lu triggered\n", (unsigned long)s_stats.next_action_count);
    printf("BRIGHT_UP (0x006F)    : %lu triggered\n", (unsigned long)s_stats.bright_up_action_count);
    printf("BRIGHT_DOWN (0x0070)  : %lu triggered\n", (unsigned long)s_stats.bright_down_action_count);
    printf("SLEEP (0x66 Power)    : %lu triggered\n", (unsigned long)s_stats.sleep_action_count);
    printf("Custom Key Actions    : %lu triggered\n", (unsigned long)s_stats.custom_key_action_count);
    printf("GATT Notify Frames TX : %lu OK / %lu FAIL (2 frames per key tap: press+release)\n",
           (unsigned long)s_stats.notify_tx_ok_count,
           (unsigned long)s_stats.notify_tx_fail_count);
    printf("Dropped (Debounce/RL) : debounced=%lu, rate_limited=%lu, disconnected=%lu\n",
           (unsigned long)s_stats.debounced_drop_count,
           (unsigned long)s_stats.rate_limited_drop_count,
           (unsigned long)s_stats.not_connected_drop_count);
    printf("=======================================================\n");
}

void diagnostics_print_logs(size_t max_entries)
{
    if (max_entries == 0 || max_entries > DIAG_LOG_CAPACITY) {
        max_entries = DIAG_LOG_CAPACITY;
    }

    printf("\n==================== RECENT LOGS ====================\n");
    if (s_diag_mutex != NULL && xSemaphoreTake(s_diag_mutex, pdMS_TO_TICKS(50)) == pdTRUE) {
        size_t count = s_log_count < max_entries ? s_log_count : max_entries;
        size_t start_idx = (s_log_head + DIAG_LOG_CAPACITY - count) % DIAG_LOG_CAPACITY;
        for (size_t i = 0; i < count; i++) {
            size_t idx = (start_idx + i) % DIAG_LOG_CAPACITY;
            printf("[%8lu ms] [%-8s] %s\n",
                   (unsigned long)s_log_ring[idx].timestamp_ms,
                   s_log_ring[idx].category,
                   s_log_ring[idx].message);
        }
        xSemaphoreGive(s_diag_mutex);
    }
    printf("=====================================================\n");
}
