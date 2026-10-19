#include "button_input.h"
#include "board_config.h"
#include "hid_report.h"
#include "haptic_driver.h"
#include "power_manager.h"
#include "diagnostics.h"

#include <stdio.h>
#include "driver/gpio.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static int s_prev_pin = BOARD_GPIO_BTN_PREV;
static int s_next_pin = BOARD_GPIO_BTN_NEXT;
static int s_pwr_pin  = BOARD_GPIO_BTN_PWR;

static bool s_prev_last_pressed = false;
static bool s_next_last_pressed = false;
static bool s_pwr_last_pressed  = false;

static uint32_t s_prev_debounce_ticks = 0;
static uint32_t s_next_debounce_ticks = 0;
static uint32_t s_pwr_debounce_ticks  = 0;

#define BTN_POLL_INTERVAL_MS 15U
#define BTN_DEBOUNCE_MS      30U
#define BTN_DEBOUNCE_COUNT   (BTN_DEBOUNCE_MS / BTN_POLL_INTERVAL_MS)

static void button_poll_task(void *param)
{
    (void)param;
    while (1) {
        vTaskDelay(pdMS_TO_TICKS(BTN_POLL_INTERVAL_MS));

        /* Poll PREV button */
        if (s_prev_pin >= 0) {
            int level = gpio_get_level((gpio_num_t)s_prev_pin);
            bool is_pressed = (level == 0);

            if (is_pressed != s_prev_last_pressed) {
                s_prev_debounce_ticks++;
                if (s_prev_debounce_ticks >= BTN_DEBOUNCE_COUNT) {
                    s_prev_last_pressed = is_pressed;
                    s_prev_debounce_ticks = 0;
                    if (is_pressed) {
                        power_manager_feed_activity();
                        haptic_driver_pulse(0);
                        diag_record_event("BTN", "Hardware button PREV pressed (GPIO %d)", s_prev_pin);
                        hid_report_send_action(PAGE_TURN_ACTION_PREV, 0);
                    }
                }
            } else {
                s_prev_debounce_ticks = 0;
            }
        }

        /* Poll NEXT button */
        if (s_next_pin >= 0) {
            int level = gpio_get_level((gpio_num_t)s_next_pin);
            bool is_pressed = (level == 0);

            if (is_pressed != s_next_last_pressed) {
                s_next_debounce_ticks++;
                if (s_next_debounce_ticks >= BTN_DEBOUNCE_COUNT) {
                    s_next_last_pressed = is_pressed;
                    s_next_debounce_ticks = 0;
                    if (is_pressed) {
                        power_manager_feed_activity();
                        haptic_driver_pulse(0);
                        diag_record_event("BTN", "Hardware button NEXT pressed (GPIO %d)", s_next_pin);
                        hid_report_send_action(PAGE_TURN_ACTION_NEXT, 0);
                    }
                }
            } else {
                s_next_debounce_ticks = 0;
            }
        }

        /* Poll PWR button */
        if (s_pwr_pin >= 0) {
            int level = gpio_get_level((gpio_num_t)s_pwr_pin);
            bool is_pressed = (level == 0);

            if (is_pressed != s_pwr_last_pressed) {
                s_pwr_debounce_ticks++;
                if (s_pwr_debounce_ticks >= BTN_DEBOUNCE_COUNT) {
                    s_pwr_last_pressed = is_pressed;
                    s_pwr_debounce_ticks = 0;
                    if (is_pressed) {
                        power_manager_feed_activity();
                        haptic_driver_pulse(BOARD_HAPTIC_PULSE_MS * 2);
                        diag_record_event("BTN", "Hardware button PWR pressed (GPIO %d) -> Toggle Sleep/Power", s_pwr_pin);
                        hid_report_send_action(PAGE_TURN_ACTION_SLEEP, 0);
                    }
                }
            } else {
                s_pwr_debounce_ticks = 0;
            }
        }
    }
}

static esp_err_t configure_pin(int pin)
{
    if (pin < 0) return ESP_OK;
    gpio_config_t io_conf = {
        .pin_bit_mask = (1ULL << pin),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE
    };
    return gpio_config(&io_conf);
}

void button_input_init(void)
{
    configure_pin(s_prev_pin);
    configure_pin(s_next_pin);
    configure_pin(s_pwr_pin);

    xTaskCreate(button_poll_task, "btn_poll", 2048, NULL, 5, NULL);
    diag_record_event("BTN", "Buttons active: NEXT=GPIO%d, PREV=GPIO%d, PWR=GPIO%d",
                      s_next_pin, s_prev_pin, s_pwr_pin);
}

esp_err_t button_input_configure(int prev_gpio_pin, int next_gpio_pin)
{
    s_prev_pin = prev_gpio_pin;
    s_next_pin = next_gpio_pin;
    configure_pin(s_prev_pin);
    configure_pin(s_next_pin);
    diag_record_event("BTN", "GPIO buttons reconfigured: PREV=GPIO%d, NEXT=GPIO%d", s_prev_pin, s_next_pin);
    return ESP_OK;
}

void button_input_disable(void)
{
    s_prev_pin = -1;
    s_next_pin = -1;
    s_pwr_pin  = -1;
    diag_record_event("BTN", "GPIO buttons disabled");
}

void button_input_print_status(void)
{
    printf("\n==================== GPIO BUTTON STATUS ====================\n");
    if (s_next_pin >= 0) {
        int lvl = gpio_get_level((gpio_num_t)s_next_pin);
        printf("NEXT Button : GPIO %d (active low, level=%d -> %s)\n",
               s_next_pin, lvl, (lvl == 0) ? "PRESSED" : "RELEASED");
    }
    if (s_prev_pin >= 0) {
        int lvl = gpio_get_level((gpio_num_t)s_prev_pin);
        printf("PREV Button : GPIO %d (active low, level=%d -> %s)\n",
               s_prev_pin, lvl, (lvl == 0) ? "PRESSED" : "RELEASED");
    }
    if (s_pwr_pin >= 0) {
        int lvl = gpio_get_level((gpio_num_t)s_pwr_pin);
        printf("PWR Button  : GPIO %d (active low, level=%d -> %s)\n",
               s_pwr_pin, lvl, (lvl == 0) ? "PRESSED" : "RELEASED");
    }
    printf("============================================================\n");
}
