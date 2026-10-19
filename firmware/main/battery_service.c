#include "battery_service.h"
#include "board_config.h"
#include "ble_hid.h"
#include "diagnostics.h"

#include <stdio.h>
#include "driver/gpio.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "BAT_SVC";
static uint8_t s_battery_level = BATTERY_DEFAULT_LEVEL_PCT;
static adc_oneshot_unit_handle_t s_adc_handle = NULL;
static bool s_adc_initialized = false;

void battery_service_init(void)
{
    s_battery_level = BATTERY_DEFAULT_LEVEL_PCT;

    /* Configure P-MOS enable pin (Active Low) */
    gpio_config_t pmos_conf = {
        .pin_bit_mask = (1ULL << BOARD_GPIO_VBAT_SENSE_EN),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&pmos_conf);
    gpio_set_level(BOARD_GPIO_VBAT_SENSE_EN, 1); /* Normally OFF to eliminate leakage */

    /* Configure ADC1 */
    adc_oneshot_unit_init_cfg_t init_config = {
        .unit_id = BOARD_ADC_VBAT_UNIT,
        .ulp_mode = ADC_ULP_MODE_DISABLE,
    };
    if (adc_oneshot_new_unit(&init_config, &s_adc_handle) == ESP_OK) {
        adc_oneshot_chan_cfg_t chan_config = {
            .atten = ADC_ATTEN_DB_12,
            .bitwidth = ADC_BITWIDTH_DEFAULT,
        };
        if (adc_oneshot_config_channel(s_adc_handle, BOARD_ADC_VBAT_CHANNEL, &chan_config) == ESP_OK) {
            s_adc_initialized = true;
        }
    }

    diag_record_event("BAS", "Battery service initialized: level=%u%% (ADC %s)",
                      s_battery_level, s_adc_initialized ? "READY" : "DISABLED");
}

uint8_t battery_service_get_level(void)
{
    return s_battery_level;
}

esp_err_t battery_service_set_level(uint8_t level_pct, bool notify_peer)
{
    if (level_pct > 100) {
        return ESP_ERR_INVALID_ARG;
    }

    s_battery_level = level_pct;
    diag_record_event("BAS", "Battery level updated to %u%% (notify=%d)",
                      s_battery_level, notify_peer ? 1 : 0);

    if (notify_peer) {
        return ble_hid_notify_battery(s_battery_level);
    }
    return ESP_OK;
}

uint32_t battery_service_sample_hardware(bool notify_peer)
{
    if (!s_adc_initialized || s_adc_handle == NULL) {
        return 3800; /* Fallback nominal 3.8V */
    }

    /* 1. Turn on P-MOS to energize divider */
    gpio_set_level(BOARD_GPIO_VBAT_SENSE_EN, 0);
    vTaskDelay(pdMS_TO_TICKS(2)); /* 2ms settling */

    /* 2. Take 4 ADC readings and average */
    int raw = 0;
    int sum = 0;
    for (int i = 0; i < 4; i++) {
        adc_oneshot_read(s_adc_handle, BOARD_ADC_VBAT_CHANNEL, &raw);
        sum += raw;
    }
    int avg_raw = sum / 4;

    /* 3. Turn off P-MOS to eliminate leakage immediately */
    gpio_set_level(BOARD_GPIO_VBAT_SENSE_EN, 1);

    /* 4. Convert to mV (12-bit ADC, 0..4095 maps to approx 0..3100mV on DB_12)
     * Divider ratio = 2.0 (100k + 100k)
     */
    uint32_t vadc_mv = (uint32_t)((avg_raw * 3100) / 4095);
    uint32_t vbat_mv = vadc_mv * 2;

    /* 5. Map 3400mV (0%) .. 4200mV (100%) */
    uint8_t pct = 0;
    if (vbat_mv >= 4200) {
        pct = 100;
    } else if (vbat_mv <= 3400) {
        pct = 0;
    } else {
        pct = (uint8_t)(((vbat_mv - 3400) * 100) / (4200 - 3400));
    }

    battery_service_set_level(pct, notify_peer);
    ESP_LOGI(TAG, "Hardware Battery Sample: %lu mV -> %u%%", (unsigned long)vbat_mv, pct);
    return vbat_mv;
}
