#include "button_input.h"
#include "hid_report.h"
#include "diagnostics.h"

#include <stdio.h>
#include "driver/gpio.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static int s_prev_pin = -1;
static int s_next_pin = -1;

static bool s_prev_last_pressed = false;
static bool s_next_last_pressed = false;

static uint32_t s_prev_debounce_ticks = 0;
static uint32_t s_next_debounce_ticks = 0;

#define BTN_POLL_INTERVAL_MS 20U
#define BTN_DEBOUNCE_MS      40U
#define BTN_DEBOUNCE_COUNT   (BTN_DEBOUNCE_MS / BTN_POLL_INTERVAL_MS)

static void button_poll_task(void *param)
{
    (void)param;
    while (1) {
        vTaskDelay(pdMS_TO_TICKS(BTN_POLL_INTERVAL_MS));

        /* Poll PREV button (active low with pull-up) */
        if (s_prev_pin >= 0) {
            int level = gpio_get_level((gpio_num_t)s_prev_pin);
            bool is_pressed = (level == 0);

            if (is_pressed != s_prev_last_pressed) {
                s_prev_debounce_ticks++;
                if (s_prev_debounce_ticks >= BTN_DEBOUNCE_COUNT) {
                    s_prev_last_pressed = is_pressed;
                    s_prev_debounce_ticks = 0;
                    if (is_pressed) {
                        diag_record_event("BTN", "Hardware button PREV pressed (GPIO %d)", s_prev_pin);
                        hid_report_send_action(PAGE_TURN_ACTION_PREV, 0);
                    }
                }
            } else {
                s_prev_debounce_ticks = 0;
            }
        }

        /* Poll NEXT button (active low with pull-up) */
        if (s_next_pin >= 0) {
            int level = gpio_get_level((gpio_num_t)s_next_pin);
            bool is_pressed = (level == 0);

            if (is_pressed != s_next_last_pressed) {
                s_next_debounce_ticks++;
                if (s_next_debounce_ticks >= BTN_DEBOUNCE_COUNT) {
                    s_next_last_pressed = is_pressed;
                    s_next_debounce_ticks = 0;
                    if (is_pressed) {
                        diag_record_event("BTN", "Hardware button NEXT pressed (GPIO %d)", s_next_pin);
                        hid_report_send_action(PAGE_TURN_ACTION_NEXT, 0);
                    }
                }
            } else {
                s_next_debounce_ticks = 0;
            }
        }
    }
}

void button_input_init(void)
{
    s_prev_pin = -1;
    s_next_pin = -1;
    s_prev_last_pressed = false;
    s_next_last_pressed = false;

    xTaskCreate(button_poll_task, "btn_poll", 2048, NULL, 5, NULL);
    diag_record_event("BTN", "Button module initialized (disabled by default until configured)");
}

esp_err_t button_input_configure(int prev_gpio_pin, int next_gpio_pin)
{
    if (prev_gpio_pin >= 0) {
        gpio_config_t io_conf = {
            .pin_bit_mask = (1ULL << prev_gpio_pin),
            .mode = GPIO_MODE_INPUT,
            .pull_up_en = GPIO_PULLUP_ENABLE,
            .pull_down_en = GPIO_PULLDOWN_DISABLE,
            .intr_type = GPIO_INTR_DISABLE
        };
        esp_err_t err = gpio_config(&io_conf);
        if (err != ESP_OK) {
            diag_record_error((int)err, "Failed to configure PREV GPIO %d", prev_gpio_pin);
            return err;
        }
    }
    s_prev_pin = prev_gpio_pin;

    if (next_gpio_pin >= 0) {
        gpio_config_t io_conf = {
            .pin_bit_mask = (1ULL << next_gpio_pin),
            .mode = GPIO_MODE_INPUT,
            .pull_up_en = GPIO_PULLUP_ENABLE,
            .pull_down_en = GPIO_PULLDOWN_DISABLE,
            .intr_type = GPIO_INTR_DISABLE
        };
        esp_err_t err = gpio_config(&io_conf);
        if (err != ESP_OK) {
            diag_record_error((int)err, "Failed to configure NEXT GPIO %d", next_gpio_pin);
            return err;
        }
    }
    s_next_pin = next_gpio_pin;

    diag_record_event("BTN", "GPIO buttons configured: PREV=GPIO%d, NEXT=GPIO%d", s_prev_pin, s_next_pin);
    return ESP_OK;
}

void button_input_disable(void)
{
    s_prev_pin = -1;
    s_next_pin = -1;
    diag_record_event("BTN", "GPIO buttons disabled");
}

void button_input_print_status(void)
{
    printf("\n==================== GPIO BUTTON STATUS ====================\n");
    if (s_prev_pin >= 0) {
        int lvl = gpio_get_level((gpio_num_t)s_prev_pin);
        printf("PREV Button : GPIO %d (active low, pull-up, level=%d -> %s)\n",
               s_prev_pin, lvl, (lvl == 0) ? "PRESSED" : "RELEASED");
    } else {
        printf("PREV Button : DISABLED (-1)\n");
    }

    if (s_next_pin >= 0) {
        int lvl = gpio_get_level((gpio_num_t)s_next_pin);
        printf("NEXT Button : GPIO %d (active low, pull-up, level=%d -> %s)\n",
               s_next_pin, lvl, (lvl == 0) ? "PRESSED" : "RELEASED");
    } else {
        printf("NEXT Button : DISABLED (-1)\n");
    }
    printf("============================================================\n");
}
