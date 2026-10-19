#pragma once

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Runs all on-target self-tests verifying PnP ID encoding, Report Map bytes,
 * report frame generation, battery bounds, and identity validation.
 * Returns true if all tests pass.
 */
bool self_test_run_all(void);

#ifdef __cplusplus
}
#endif
