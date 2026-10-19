#include "serial_console.h"
#include "device_identity.h"
#include "hid_report.h"
#include "battery_service.h"
#include "ble_hid.h"
#include "button_input.h"
#include "diagnostics.h"
#include "self_test.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include "driver/uart.h"
#include "esp_system.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#define CONSOLE_UART_NUM      UART_NUM_0
#define CONSOLE_CMD_MAX_LEN   128
#define CONSOLE_RX_BUF_SIZE   1024

static void print_help(void)
{
    printf("\n==================== SERIAL CONSOLE COMMANDS ====================\n");
    printf("  help                       - Show this command reference\n");
    printf("  status                     - Show BLE state, peer GATT read counts, CCCD & report stats\n");
    printf("  info                       - Show device identity, provenance, Evidence IDs & GATT handles\n");
    printf("  logs                       - Show recent diagnostic event log ring buffer\n");
    printf("  ble_start                  - Start or restart BLE advertising (ADV_IND)\n");
    printf("  ble_stop [disconnect]      - Stop BLE advertising (optional: disconnect active peer)\n");
    printf("  prev                       - Send PREV page turn (Usage 0x4B -> KEY_PAGEUP -> PageUp)\n");
    printf("  next                       - Send NEXT page turn (Usage 0x4E -> KEY_PAGEDOWN -> PageDown)\n");
    printf("  bright_up / bup            - Send Brightness Up (Usage 0x006F -> KEY_BRIGHTNESSUP)\n");
    printf("  bright_down / bdown        - Send Brightness Down (Usage 0x0070 -> KEY_BRIGHTNESSDOWN)\n");
    printf("  sleep                      - Put Kindle to sleep (Usage 0x66, 70ms hold + 450ms cooldown)\n");
    printf("  power                      - Toggle Power key (Usage 0x66 -> KEY_POWER)\n");
    printf("  chord <k1_hex> <k2_hex>    - Send two custom keys simultaneously (e.g. 'chord 4B 4E')\n");
    printf("  key <usage_hex>            - Send custom Keyboard Usage (e.g. 'key 4F' Right, 'key 2C' Space)\n");
    printf("  battery <0-100>            - Set Battery Level (0x2A19) and notify peer if subscribed\n");
    printf("  name <device_name>         - Set BLE Device Name (e.g. 'name Kindle Remote') & restart adv\n");
    printf("  vidpid <vid_hex> <pid_hex> - Override DIS/Adv VID/PID (e.g. 'vidpid 0171 0443')\n");
    printf("  adv_mode <0|1|2>           - Set Adv mode (0=0xFF Amazon, 1=0xFF+0x10, 2=Generic HID)\n");
    printf("  gpio <prev_pin> <next_pin> - Configure active-low GPIO buttons (-1 to disable)\n");
    printf("  bond_status                - Show NVS bonded peers and link security state\n");
    printf("  bond_clear confirm         - Clear all NVS BLE bonds (requires 'confirm' argument)\n");
    printf("  selftest                   - Run on-target unit test suite\n");
    printf("  reboot                     - Safely restart ESP32-S3\n");
    printf("=================================================================\n");
}

void serial_console_process_line(const char *raw_line)
{
    if (raw_line == NULL) {
        return;
    }

    /* Skip leading whitespace */
    while (*raw_line != '\0' && isspace((unsigned char)*raw_line)) {
        raw_line++;
    }
    if (*raw_line == '\0') {
        return;
    }

    char buf[CONSOLE_CMD_MAX_LEN + 1];
    strncpy(buf, raw_line, CONSOLE_CMD_MAX_LEN);
    buf[CONSOLE_CMD_MAX_LEN] = '\0';

    /* Trim trailing whitespace */
    size_t len = strlen(buf);
    while (len > 0 && isspace((unsigned char)buf[len - 1])) {
        buf[--len] = '\0';
    }
    if (len == 0) {
        return;
    }

    /* Split command token and remainder argument string */
    char *cmd = buf;
    char *args = NULL;
    char *space = strchr(buf, ' ');
    if (space != NULL) {
        *space = '\0';
        args = space + 1;
        while (*args != '\0' && isspace((unsigned char)*args)) {
            args++;
        }
        if (*args == '\0') {
            args = NULL;
        }
    }

    if (strcasecmp(cmd, "help") == 0 || strcmp(cmd, "?") == 0) {
        print_help();
    } else if (strcasecmp(cmd, "status") == 0) {
        diagnostics_print_status();
    } else if (strcasecmp(cmd, "info") == 0) {
        device_identity_print_info();
        ble_hid_print_gatt_and_adv_table();
        button_input_print_status();
    } else if (strcasecmp(cmd, "logs") == 0) {
        diagnostics_print_logs(DIAG_LOG_CAPACITY);
    } else if (strcasecmp(cmd, "ble_start") == 0) {
        esp_err_t err = ble_hid_start_advertising();
        printf("CMD ble_start: %s (err=%d)\n", (err == ESP_OK) ? "OK" : "FAILED", (int)err);
    } else if (strcasecmp(cmd, "ble_stop") == 0) {
        bool disc = (args != NULL && strcasecmp(args, "disconnect") == 0);
        esp_err_t err = ble_hid_stop_advertising(disc);
        printf("CMD ble_stop (disconnect=%d): %s (err=%d)\n", disc ? 1 : 0, (err == ESP_OK) ? "OK" : "FAILED", (int)err);
    } else if (strcasecmp(cmd, "prev") == 0) {
        esp_err_t err = hid_report_send_action(PAGE_TURN_ACTION_PREV, 0);
        printf("CMD prev (Usage 0x4B PageUp): %s (err=%d)\n",
               (err == ESP_OK) ? "SENT" : "REJECTED (check connection/CCCD/debounce)", (int)err);
    } else if (strcasecmp(cmd, "next") == 0) {
        esp_err_t err = hid_report_send_action(PAGE_TURN_ACTION_NEXT, 0);
        printf("CMD next (Usage 0x4E PageDown): %s (err=%d)\n",
               (err == ESP_OK) ? "SENT" : "REJECTED (check connection/CCCD/debounce)", (int)err);
    } else if (strcasecmp(cmd, "bright_up") == 0 || strcasecmp(cmd, "bup") == 0) {
        esp_err_t err = hid_report_send_action(PAGE_TURN_ACTION_BRIGHT_UP, 0);
        printf("CMD bright_up (Consumer Usage 0x006F Brightness Up): %s (err=%d)\n",
               (err == ESP_OK) ? "SENT" : "REJECTED (check connection/Consumer CCCD)", (int)err);
    } else if (strcasecmp(cmd, "bright_down") == 0 || strcasecmp(cmd, "bdown") == 0) {
        esp_err_t err = hid_report_send_action(PAGE_TURN_ACTION_BRIGHT_DOWN, 0);
        printf("CMD bright_down (Consumer Usage 0x0070 Brightness Down): %s (err=%d)\n",
               (err == ESP_OK) ? "SENT" : "REJECTED (check connection/Consumer CCCD)", (int)err);
    } else if (strcasecmp(cmd, "sleep") == 0) {
        esp_err_t err = hid_report_send_action(PAGE_TURN_ACTION_SLEEP, 0);
        printf("CMD sleep (Usage 0x66 Power/Sleep): %s (err=%d)\n",
               (err == ESP_OK) ? "SENT (70ms hold + 450ms cooldown)" : "REJECTED (check connection/CCCD/cooldown)", (int)err);
    } else if (strcasecmp(cmd, "power") == 0) {
        esp_err_t err = hid_report_send_action(PAGE_TURN_ACTION_POWER, 0);
        printf("CMD power (Usage 0x66 Power Toggle): %s (err=%d)\n",
               (err == ESP_OK) ? "SENT (70ms hold + 450ms cooldown)" : "REJECTED (check connection/CCCD/cooldown)", (int)err);
    } else if (strcasecmp(cmd, "chord") == 0) {
        if (args == NULL) {
            printf("ERR: Usage: chord <key1_hex> <key2_hex> (e.g. 'chord 4B 4E')\n");
            return;
        }
        unsigned int k1 = 0, k2 = 0;
        if (sscanf(args, "%x %x", &k1, &k2) != 2 || k1 > 0xFFU || k2 > 0xFFU) {
            printf("ERR: Invalid hex keys '%s'\n", args);
            return;
        }
        esp_err_t err = hid_report_send_chord((uint8_t)k1, (uint8_t)k2);
        printf("CMD chord (0x%02X + 0x%02X): %s (err=%d)\n",
               k1, k2, (err == ESP_OK) ? "SENT" : "REJECTED", (int)err);
    } else if (strcasecmp(cmd, "key") == 0) {
        if (args == NULL) {
            printf("ERR: Usage: key <usage_hex> (e.g. 'key 4B' or 'key 4E')\n");
            return;
        }
        char *endptr = NULL;
        unsigned long usage = strtoul(args, &endptr, 16);
        if (endptr == args || usage > 0xFFUL) {
            printf("ERR: Invalid hex usage '%s'\n", args);
            return;
        }
        esp_err_t err = hid_report_send_action(PAGE_TURN_ACTION_CUSTOM, (uint8_t)usage);
        printf("CMD key 0x%02X: %s (err=%d)\n",
               (unsigned int)usage, (err == ESP_OK) ? "SENT" : "REJECTED", (int)err);
    } else if (strcasecmp(cmd, "battery") == 0) {
        if (args == NULL) {
            printf("Current Battery Level: %u%%\n", battery_service_get_level());
            return;
        }
        char *endptr = NULL;
        long lvl = strtol(args, &endptr, 10);
        if (endptr == args || lvl < 0 || lvl > 100) {
            printf("ERR: Battery level must be 0..100 (got '%s')\n", args);
            return;
        }
        esp_err_t err = battery_service_set_level((uint8_t)lvl, true);
        printf("CMD battery %ld%%: %s (err=%d)\n", lvl, (err == ESP_OK) ? "OK" : "FAILED", (int)err);
    } else if (strcasecmp(cmd, "name") == 0) {
        if (args == NULL) {
            printf("ERR: Usage: name <device_name>\n");
            return;
        }
        esp_err_t err = device_identity_set_name(args);
        if (err == ESP_OK) {
            ble_hid_start_advertising();
            printf("CMD name: Updated to \"%s\" and restarted advertising\n", args);
        } else {
            printf("ERR: Invalid device name (max %u chars)\n", DEVICE_NAME_MAX_LEN);
        }
    } else if (strcasecmp(cmd, "vidpid") == 0) {
        if (args == NULL) {
            printf("ERR: Usage: vidpid <vid_hex> <pid_hex> (e.g. 'vidpid 0171 0443')\n");
            return;
        }
        unsigned int vid = 0, pid = 0;
        if (sscanf(args, "%x %x", &vid, &pid) != 2 || vid > 0xFFFFU || pid > 0xFFFFU) {
            printf("ERR: Invalid VID/PID hex format. Example: vidpid 0171 0443\n");
            return;
        }
        device_identity_set_vid_pid((uint16_t)vid, (uint16_t)pid);
        ble_hid_start_advertising();
        printf("CMD vidpid: Set VID=0x%04X PID=0x%04X and restarted advertising\n", vid, pid);
    } else if (strcasecmp(cmd, "adv_mode") == 0) {
        if (args == NULL) {
            printf("ERR: Usage: adv_mode <0|1|2> (0=0xFF Amazon, 1=0xFF+0x10, 2=Generic HID)\n");
            return;
        }
        int mode = atoi(args);
        if (device_identity_set_adv_mode((uint8_t)mode) == ESP_OK) {
            ble_hid_start_advertising();
            printf("CMD adv_mode: Set to %d and restarted advertising\n", mode);
        } else {
            printf("ERR: adv_mode must be 0, 1, or 2\n");
        }
    } else if (strcasecmp(cmd, "gpio") == 0) {
        if (args == NULL) {
            button_input_print_status();
            return;
        }
        int prev_pin = -1, next_pin = -1;
        if (sscanf(args, "%d %d", &prev_pin, &next_pin) != 2) {
            printf("ERR: Usage: gpio <prev_pin> <next_pin> (use -1 -1 to disable)\n");
            return;
        }
        esp_err_t err = button_input_configure(prev_pin, next_pin);
        printf("CMD gpio %d %d: %s (err=%d)\n", prev_pin, next_pin, (err == ESP_OK) ? "OK" : "FAILED", (int)err);
    } else if (strcasecmp(cmd, "bond_status") == 0) {
        ble_hid_print_bond_status();
    } else if (strcasecmp(cmd, "bond_clear") == 0) {
        if (args == NULL || strcasecmp(args, "confirm") != 0) {
            printf("WARN: Clearing BLE bonds removes saved pairing keys in NVS.\n");
            printf("      To proceed, run: bond_clear confirm\n");
            return;
        }
        esp_err_t err = ble_hid_clear_bonds();
        printf("CMD bond_clear confirm: %s (err=%d)\n", (err == ESP_OK) ? "CLEARED" : "FAILED", (int)err);
    } else if (strcasecmp(cmd, "selftest") == 0) {
        self_test_run_all();
    } else if (strcasecmp(cmd, "reboot") == 0) {
        printf("CMD reboot: Restarting ESP32-S3 now...\n");
        fflush(stdout);
        vTaskDelay(pdMS_TO_TICKS(100));
        esp_restart();
    } else {
        printf("ERR: Unknown command '%s'. Type 'help' for available commands.\n", cmd);
    }
}

static void serial_console_task(void *param)
{
    (void)param;
    char line_buf[CONSOLE_CMD_MAX_LEN + 1];
    size_t line_pos = 0;
    bool overflow_discarding = false;
    bool last_was_cr = false;

    printf("\nType 'help' for serial console commands.\nkind-curie> ");
    fflush(stdout);

    uint8_t ch = 0;
    while (1) {
        int rx = uart_read_bytes(CONSOLE_UART_NUM, &ch, 1, pdMS_TO_TICKS(50));
        if (rx <= 0) {
            continue;
        }

        /* Handle CR, LF, and CRLF line endings cleanly */
        if (ch == '\r' || ch == '\n') {
            if (ch == '\n' && last_was_cr) {
                last_was_cr = false;
                continue;
            }
            last_was_cr = (ch == '\r');
            printf("\n");

            if (overflow_discarding) {
                printf("ERR: Command line exceeded max length (%d chars) and was discarded.\n",
                       CONSOLE_CMD_MAX_LEN);
                overflow_discarding = false;
                line_pos = 0;
            } else if (line_pos > 0) {
                line_buf[line_pos] = '\0';
                serial_console_process_line(line_buf);
                line_pos = 0;
            }

            printf("kind-curie> ");
            fflush(stdout);
            continue;
        }

        last_was_cr = false;

        /* Handle backspace / delete */
        if (ch == 0x08 || ch == 0x7F) {
            if (line_pos > 0 && !overflow_discarding) {
                line_pos--;
                printf("\b \b");
                fflush(stdout);
            }
            continue;
        }

        /* Ignore non-printable control characters */
        if (ch < 0x20 || ch > 0x7E) {
            continue;
        }

        if (overflow_discarding) {
            continue;
        }

        if (line_pos < CONSOLE_CMD_MAX_LEN) {
            line_buf[line_pos++] = (char)ch;
            putchar((int)ch);
            fflush(stdout);
        } else {
            overflow_discarding = true;
        }
    }
}

void serial_console_init(void)
{
    const uart_config_t uart_config = {
        .baud_rate  = 115200,
        .data_bits  = UART_DATA_8_BITS,
        .parity     = UART_PARITY_DISABLE,
        .stop_bits  = UART_STOP_BITS_1,
        .flow_ctrl  = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };

    /* Install UART driver for non-blocking RX on UART0 */
    if (!uart_is_driver_installed(CONSOLE_UART_NUM)) {
        ESP_ERROR_CHECK(uart_driver_install(CONSOLE_UART_NUM, CONSOLE_RX_BUF_SIZE, 0, 0, NULL, 0));
        ESP_ERROR_CHECK(uart_param_config(CONSOLE_UART_NUM, &uart_config));
    }

    xTaskCreate(serial_console_task, "serial_cli", 4096, NULL, 5, NULL);
    diag_record_event("CLI", "UART0 interactive serial console started (115200 8N1)");
}
