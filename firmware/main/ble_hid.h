#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initializes the ESP32-S3 BLE Controller, NimBLE Host, Security Manager (SMP),
 * and GATT Server (GAP 0x1800, GATT 0x1801, DIS 0x180A, BAS 0x180F, HIDS 0x1812).
 */
esp_err_t ble_hid_init(void);

/**
 * Starts or restarts BLE connectable undirected advertising (ADV_IND).
 */
esp_err_t ble_hid_start_advertising(void);

/**
 * Stops BLE advertising (and optionally disconnects any active peer if disconnect_peer=true).
 */
esp_err_t ble_hid_stop_advertising(bool disconnect_peer);

/**
 * Sends an 8-byte HID Input Report notification on characteristic 0x2A4D (Report ID 1).
 */
esp_err_t ble_hid_notify_input_report(const uint8_t *report_data, size_t report_len);

/**
 * Sends a 2-byte Consumer Control HID Input Report notification on characteristic 0x2A4D (Report ID 2).
 */
esp_err_t ble_hid_notify_consumer_report(const uint8_t *report_data, size_t report_len);

/**
 * Sends a 1-byte Battery Level notification on characteristic 0x2A19.
 */
esp_err_t ble_hid_notify_battery(uint8_t battery_pct);

/**
 * Prints bonded peer records stored in NVS without deleting or modifying them.
 */
void ble_hid_print_bond_status(void);

/**
 * Clears all BLE bonding keys from NVS storage.
 * Must only be invoked after explicit user confirmation via serial console.
 */
esp_err_t ble_hid_clear_bonds(void);

/**
 * Prints the full GATT attribute table, handle assignments, and raw advertising payload hex.
 */
void ble_hid_print_gatt_and_adv_table(void);

#ifdef __cplusplus
}
#endif
