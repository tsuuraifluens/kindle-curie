#include "power_manager.h"
#include "board_config.h"
#include "diagnostics.h"

#include <stdio.h>
#include "driver/gpio.h"
#include "esp_sleep.h"
#include "esp_timer.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "PWR_MGR";
static int64_t s_last_activity_time_us = 0;
static esp_timer_handle_t s_inactivity_timer = NULL;

static void inactivity_timer_callback(void *arg)
{
    (void)arg;
    int64_t now_us = esp_timer_get_time();
    int64_t elapsed_sec = (now_us - s_last_activity_time_us) / 1000000LL;

    if (elapsed_sec >= BOARD_INACTIVITY_SLEEP_SEC) {
        ESP_LOGI(TAG, "Inactivity threshold (%u s) reached. Entering Deep Sleep...", BOARD_INACTIVITY_SLEEP_SEC);
        diag_record_event("PWR", "Inactivity timeout (%lld s) -> Deep Sleep", (long long)elapsed_sec);
        power_manager_enter_deep_sleep();
    }
}

esp_err_t power_manager_init(void)
{
    /* Configure Blue Status LED */
    gpio_config_t led_conf = {
        .pin_bit_mask = (1ULL << BOARD_GPIO_LED_BLE),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&led_conf);
    gpio_set_level(BOARD_GPIO_LED_BLE, 0);

    s_last_activity_time_us = esp_timer_get_time();

    const esp_timer_create_args_t timer_args = {
        .callback = &inactivity_timer_callback,
        .name = "inactivity_chk",
    };
    esp_err_t err = esp_timer_create(&timer_args, &s_inactivity_timer);
    if (err == ESP_OK) {
        esp_timer_start_periodic(s_inactivity_timer, 10000000ULL); /* Check every 10s */
    }

    diag_record_event("PWR", "Power manager initialized (timeout=%u s)", BOARD_INACTIVITY_SLEEP_SEC);
    return ESP_OK;
}

void power_manager_feed_activity(void)
{
    s_last_activity_time_us = esp_timer_get_time();
}

void power_manager_enter_deep_sleep(void)
{
    /* Turn off LED */
    gpio_set_level(BOARD_GPIO_LED_BLE, 0);
    gpio_set_level(BOARD_GPIO_HAPTIC, 0);

    /* Configure GPIO Wakeup for buttons (active low: level 0) */
    const uint64_t wakeup_pins = (1ULL << BOARD_GPIO_BTN_NEXT) |
                                 (1ULL << BOARD_GPIO_BTN_PREV) |
                                 (1ULL << BOARD_GPIO_BTN_PWR);
    esp_deep_sleep_enable_gpio_wakeup(wakeup_pins, ESP_GPIO_WAKEUP_GPIO_LOW);

    ESP_LOGI(TAG, "Deep Sleep activated. Press any button to wake up.");
    vTaskDelay(pdMS_TO_TICKS(50));
    esp_deep_sleep_start();
}

void power_manager_set_led(bool state)
{
    gpio_set_level(BOARD_GPIO_LED_BLE, state ? 1 : 0);
}
