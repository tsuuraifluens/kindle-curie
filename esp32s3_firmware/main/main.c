#include <stdio.h>
#include <string.h>
#include "esp_system.h"
#include "esp_log.h"
#include "nvs_flash.h"
#include "esp_chip_info.h"
#include "esp_flash.h"

#include "device_identity.h"
#include "hid_report.h"
#include "battery_service.h"
#include "diagnostics.h"
#include "ble_hid.h"
#include "button_input.h"
#include "serial_console.h"
#include "self_test.h"

static const char *TAG = "MAIN";

static void print_banner(void)
{
    esp_chip_info_t chip_info;
    esp_chip_info(&chip_info);

    uint32_t flash_size = 0;
    esp_flash_get_size(NULL, &flash_size);

    const device_identity_config_t *id = device_identity_get();

    printf("\n");
    printf("*******************************************************************************\n");
    printf("*            KINDLE PAPERWHITE 2024 (5.20.1) BLE HID PAGE TURNER              *\n");
    printf("*                 ESP32-S3 EMBEDDED HOGP FIRMWARE (v1.0.0)                     *\n");
    printf("*******************************************************************************\n");
    printf("  MCU Target          : ESP32-S3 (cores=%d, rev=%d, flash=%lu MB)\n",
           chip_info.cores, chip_info.revision, (unsigned long)(flash_size / (1024 * 1024)));
    printf("  Target Host         : Kindle Paperwhite 2024 (12th Gen, Sangria 5.20.1)\n");
    printf("  Advertised Name     : \"%s\"\n", id->device_name);
    printf("  PnP ID (0x2A50)     : VID=0x%04X (Lab126/Amazon), PID=0x%04X (Page Turner)\n",
           id->vendor_id, id->product_id);
    printf("  Firmware Rev (0x2A26): \"%s\" (probed by acsbtfd BtGattClient, EVID-09)\n", id->fw_revision);
    printf("  ADV Manuf Data      : [05 FF 71 01 04 43] (Amazon BLE Remote, EVID-12/13)\n");
    printf("  HID Page Turn Keys  : PREV = Usage 0x4B (PageUp) | NEXT = Usage 0x4E (PageDown)\n");
    printf("  Security Manager    : SMP Just Works + LE Secure Connections + NVS Bonding\n");
    printf("  Interactive Console : Non-blocking UART0 @ 115200 baud\n");
    printf("*******************************************************************************\n");
    printf("\n");
}

void app_main(void)
{
    /* Step 1: Initialize NVS Flash (required for BLE bonding keys & configuration) */
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_LOGW(TAG, "NVS partition needs erase/re-init (%s)", esp_err_to_name(ret));
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    /* Step 2: Initialize Core Firmware Modules */
    diagnostics_init();
    device_identity_init();
    battery_service_init();
    hid_report_init();
    button_input_init();

    /* Step 3: Run On-Target Self-Tests */
    bool self_test_ok = self_test_run_all();
    if (!self_test_ok) {
        ESP_LOGE(TAG, "Self-tests reported failures! Review test output above.");
    }

    /* Step 4: Initialize BLE NimBLE Stack & GATT Services */
    ESP_ERROR_CHECK(ble_hid_init());

    /* Step 5: Initialize Interactive Serial Console on UART0 */
    serial_console_init();

    /* Step 6: Print System Banner & Ready Prompt */
    print_banner();
    ESP_LOGI(TAG, "ESP32-S3 BLE HID Page Turner running. Type 'help' on serial console.");
}
