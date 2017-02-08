 #ifndef XIL_PRINTF_H
 #define XIL_PRINTF_H

#ifdef __cplusplus
extern "C" {
#endif

#include <ctype.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include "./xil_types.h"
#include "./xparameters.h"

/*----------------------------------------------------*/
/* Use the following parameter passing structure to   */
/* make xil_printf re-entrant.                        */
/*----------------------------------------------------*/

struct params_s;

#define xil_printf printf

#ifdef __cplusplus
}
#endif

#endif	/* end of protection macro */
