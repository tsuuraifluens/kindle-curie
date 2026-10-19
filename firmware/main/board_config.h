#pragma once

#include "driver/gpio.h"
#include "esp_adc/adc_oneshot.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @file board_config.h
 * @brief Hardware Pin Assignment & Board Abstraction for Kindle Remote (ESP32-C3-WROOM-02-N4)
 * Matching PCB layout: hardware/pcb/kindle_remote_c3.kicad_pcb
 */

/* ----------------- Physical Buttons (Active Low, Pull-Up) ----------------- */
#define BOARD_GPIO_BTN_NEXT        GPIO_NUM_1   /* SW1 (NEXT Page: Usage 0x4E) */
#define BOARD_GPIO_BTN_PREV        GPIO_NUM_0   /* SW2 (PREV Page: Usage 0x4B) */
#define BOARD_GPIO_BTN_PWR         GPIO_NUM_5   /* SW3 (Power / Pair / Wakeup) */

/* ----------------- Haptic Motor Driver (N-MOS Active High) ---------------- */
#define BOARD_GPIO_HAPTIC          GPIO_NUM_6   /* MTR AO3400 N-MOS Gate */
#define BOARD_HAPTIC_PULSE_MS      18U          /* 18ms crisp tactile click */

/* ----------------- System Status Indicators ------------------------------ */
#define BOARD_GPIO_LED_BLE         GPIO_NUM_7   /* Blue LED (Active High) */

/* ----------------- Battery Voltage Sensing (P-MOS Low-Leakage) ----------- */
#define BOARD_GPIO_VBAT_SENSE_EN   GPIO_NUM_4   /* AO3401 P-MOS Gate: Low=Enable */
#define BOARD_ADC_VBAT_UNIT        ADC_UNIT_1
#define BOARD_ADC_VBAT_CHANNEL     ADC_CHANNEL_3 /* GPIO3 (ADC1_CH3) */

/* ----------------- Test / Factory Interfaces ----------------------------- */
#define BOARD_GPIO_BOOT0           GPIO_NUM_9   /* Strapping pin for ROM download */
#define BOARD_GPIO_UART0_TX        GPIO_NUM_21  /* Factory Test Point TX */
#define BOARD_GPIO_UART0_RX        GPIO_NUM_20  /* Factory Test Point RX */

/* ----------------- USB Serial/JTAG --------------------------------------- */
#define BOARD_GPIO_USB_DM          GPIO_NUM_18  /* D- */
#define BOARD_GPIO_USB_DP          GPIO_NUM_19  /* D+ */

/* ----------------- Power Management Timing ------------------------------- */
#define BOARD_INACTIVITY_SLEEP_SEC 180U         /* Auto Deep Sleep after 3 min idle */

#ifdef __cplusplus
}
#endif
