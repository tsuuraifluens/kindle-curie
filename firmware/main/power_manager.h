#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initializes the power manager, LED indicator and inactivity deep-sleep watchdog.
 */
esp_err_t power_manager_init(void);

/**
 * Feeds the inactivity watchdog (called upon any button press or CLI input).
 */
void power_manager_feed_activity(void);

/**
 * Explicitly triggers deep sleep mode with GPIO wake-up enabled.
 */
void power_manager_enter_deep_sleep(void);

/**
 * Sets LED state (0 = Off, 1 = On, or blink mode).
 */
void power_manager_set_led(bool state);

#ifdef __cplusplus
}
#endif
