# Backends GPU en Windows

## CPU

Siempre disponible. Es el baseline para separar errores de llama.cpp, GGUF, UI y drivers.

## CUDA / NVIDIA

`HardwareDetector` consulta `nvidia-smi` cuando está disponible y muestra nombre, VRAM y driver. La aplicación solo debe activar CUDA si el binario se compiló con `GGML_CUDA=ON` y `llama-cli --list-devices` enumera un dispositivo.

## Vulkan

`HardwareDetector` consulta `vulkaninfo --summary` cuando está disponible. El build usa `GGML_VULKAN=ON`. Vulkan permite investigar GPU offload en tarjetas NVIDIA, AMD e Intel sin inventar un proveedor específico; el dispositivo real lo decide el loader y el driver.

## Límites

- La VRAM es aproximada cuando procede de una herramienta externa.
- La memoria del GGUF no equivale exactamente a RAM/VRAM: contexto, KV cache, buffers y GPU layers cambian el consumo.
- Si falla CUDA, se debe ofrecer Vulkan o CPU de forma explícita, sin cambiar silenciosamente el backend que el usuario está probando.
