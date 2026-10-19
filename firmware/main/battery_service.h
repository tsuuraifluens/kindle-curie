#pragma once

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Default battery level: 95%.
 * Evidence EVID-10 & acsbtfd BleHidPageTurnerPolicy vtable[12..13]:
 *   - Low battery threshold      = 20% ("pageTurnerBatteryLow")
 *   - Critical battery threshold =  5% ("pageTurnerBatteryCritical")
 */
#define BATTERY_DEFAULT_LEVEL_PCT 95

void battery_service_init(void);
uint8_t battery_service_get_level(void);
esp_err_t battery_service_set_level(uint8_t level_pct, bool notify_peer);

/**
 * Samples physical battery voltage via ADC with low-leakage P-MOS switch.
 * Returns measured millivolts and updates internal percentage.
 */
uint32_t battery_service_sample_hardware(bool notify_peer);

#ifdef __cplusplus
}
#endif
