#include "battery_service.h"
#include "ble_hid.h"
#include "diagnostics.h"

static uint8_t s_battery_level = BATTERY_DEFAULT_LEVEL_PCT;

void battery_service_init(void)
{
    s_battery_level = BATTERY_DEFAULT_LEVEL_PCT;
    diag_record_event("BAS", "Battery service initialized: level=%u%%", s_battery_level);
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
