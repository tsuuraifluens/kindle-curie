#include "haptic_driver.h"
#include "board_config.h"
#include "diagnostics.h"

#include "driver/gpio.h"
#include "esp_timer.h"
#include "esp_log.h"

static const char *TAG = "HAPTIC";
static bool s_haptic_enabled = true;
static esp_timer_handle_t s_haptic_timer = NULL;

static void haptic_timer_callback(void *arg)
{
    (void)arg;
    gpio_set_level(BOARD_GPIO_HAPTIC, 0);
}

esp_err_t haptic_driver_init(void)
{
    gpio_config_t io_conf = {
        .pin_bit_mask = (1ULL << BOARD_GPIO_HAPTIC),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_ENABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    esp_err_t err = gpio_config(&io_conf);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Failed to configure Haptic GPIO %d (%s)", BOARD_GPIO_HAPTIC, esp_err_to_name(err));
        return err;
    }
    gpio_set_level(BOARD_GPIO_HAPTIC, 0);

    const esp_timer_create_args_t timer_args = {
        .callback = &haptic_timer_callback,
        .name = "haptic_stop_timer",
    };
    err = esp_timer_create(&timer_args, &s_haptic_timer);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Failed to create haptic timer (%s)", esp_err_to_name(err));
        return err;
    }

    diag_record_event("HAP", "Haptic motor driver initialized on GPIO %d", BOARD_GPIO_HAPTIC);
    return ESP_OK;
}

void haptic_driver_pulse(uint32_t duration_ms)
{
    if (!s_haptic_enabled || s_haptic_timer == NULL) {
        return;
    }
    if (duration_ms == 0) {
        duration_ms = BOARD_HAPTIC_PULSE_MS;
    }

    gpio_set_level(BOARD_GPIO_HAPTIC, 1);
    esp_timer_stop(s_haptic_timer);
    esp_timer_start_once(s_haptic_timer, (uint64_t)duration_ms * 1000ULL);
}

void haptic_driver_set_enabled(bool enabled)
{
    s_haptic_enabled = enabled;
    diag_record_event("HAP", "Haptic feedback %s", enabled ? "ENABLED" : "DISABLED");
}

bool haptic_driver_is_enabled(void)
{
    return s_haptic_enabled;
}
