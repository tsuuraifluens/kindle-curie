#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initializes the non-blocking UART0 interactive serial console task.
 */
void serial_console_init(void);

/**
 * Executes a single command line string (also exposed for self-test verification).
 */
void serial_console_process_line(const char *line);

#ifdef __cplusplus
}
#endif
