// Checks the same Vulkan backend used by the bundled Whisper runtime, without loading a model.
#include "ggml-backend.h"
#include <stdio.h>
#include <string.h>

int main(void) {
    ggml_backend_load_all();
    for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        if (strcmp(ggml_backend_reg_name(ggml_backend_dev_backend_reg(device)), "Vulkan") != 0) continue;
        enum ggml_backend_dev_type type = ggml_backend_dev_type(device);
        if (type != GGML_BACKEND_DEVICE_TYPE_GPU && type != GGML_BACKEND_DEVICE_TYPE_IGPU) continue;
        const char *name = ggml_backend_dev_description(device);
        if (strstr(name, "llvmpipe") || strstr(name, "lavapipe") || strstr(name, "SwiftShader")) continue;
        // Check the default Vulkan device, which is also the transcription default.
        ggml_backend_t backend = ggml_backend_dev_init(device, NULL);
        if (!backend) return 2;
        ggml_backend_free(backend);
        printf("GPU\t%s\n", name);
        return 0;
    }
    puts("NO_GPU");
    return 3;
}
