#pragma once

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initializes the optional hardware GPIO button polling task.
 * By default, both PREV and NEXT GPIO pins are disabled (-1) so no unconfirmed
 * board pins are driven or pulled. Pins can be enabled at runtime via serial.
 */
void button_input_init(void);

/**
 * Configures active-low GPIO pins (with internal pull-up) for PREV and NEXT buttons.
 * Pass -1 for either pin to disable that button.
 */
esp_err_t button_input_configure(int prev_gpio_pin, int next_gpio_pin);

/**
 * Disables hardware GPIO buttons.
 */
void button_input_disable(void);

/**
 * Prints current GPIO button configuration and state.
 */
void button_input_print_status(void);

#ifdef __cplusplus
}
#endif
