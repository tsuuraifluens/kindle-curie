#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initializes the haptic vibration motor GPIO driver.
 */
esp_err_t haptic_driver_init(void);

/**
 * Triggers a short tactile click pulse (default ~18ms) in a non-blocking hardware timer.
 */
void haptic_driver_pulse(uint32_t duration_ms);

/**
 * Enable or disable haptic vibration feedback globally.
 */
void haptic_driver_set_enabled(bool enabled);

/**
 * Query whether haptic feedback is currently enabled.
 */
bool haptic_driver_is_enabled(void);

#ifdef __cplusplus
}
#endif
