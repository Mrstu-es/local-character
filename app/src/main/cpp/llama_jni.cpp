#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <algorithm>
#include <cctype>
#include <chrono>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include "llama.h"
#include "chat.h"
#include "ggml-backend.h"

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex g_mutex;
llama_model *g_model = nullptr;
llama_context *g_context = nullptr;
std::atomic_bool g_cancelled{false};
std::atomic<int64_t> g_decode_deadline_ms{0};
int32_t g_threads = 4;
int32_t g_batch_size = 256;
bool g_backend_initialized = false;
std::string g_last_native_error;
std::string g_chat_template_mode = "AUTO";
std::string g_custom_chat_template;

void throw_java(JNIEnv *env, const char *message);

int64_t monotonic_millis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}

bool should_abort_decode(void *) {
    const int64_t deadline = g_decode_deadline_ms.load(std::memory_order_relaxed);
    return g_cancelled.load(std::memory_order_relaxed) || (deadline > 0 && monotonic_millis() >= deadline);
}

struct DecodeDeadlineReset {
    ~DecodeDeadlineReset() { g_decode_deadline_ms.store(0, std::memory_order_relaxed); }
};

bool report_decode_abort(JNIEnv *env, const char *phase) {
    if (g_cancelled.load(std::memory_order_relaxed)) {
        LOGI("Generation cancelled inside %s decode", phase);
        return true;
    }
    if (should_abort_decode(nullptr)) {
        LOGE("Native first-token deadline reached inside %s decode", phase);
        throw_java(env, "El modelo no produjo el primer token en 75 segundos y se detuvo.");
        return true;
    }
    return false;
}

bool is_valid_utf8(const std::string & value) {
    const auto * bytes = reinterpret_cast<const unsigned char *>(value.data());
    size_t index = 0;
    while (index < value.size()) {
        const unsigned char first = bytes[index];
        size_t count = 0;
        if ((first & 0x80) == 0) count = 1;
        else if ((first & 0xE0) == 0xC0) count = 2;
        else if ((first & 0xF0) == 0xE0) count = 3;
        else if ((first & 0xF8) == 0xF0) count = 4;
        else return false;
        if (index + count > value.size()) return false;
        for (size_t continuation = 1; continuation < count; ++continuation) {
            if ((bytes[index + continuation] & 0xC0) != 0x80) return false;
        }
        index += count;
    }
    return true;
}

void llama_android_log(enum ggml_log_level level, const char *text, void *) {
    if (text == nullptr) return;
    const int android_level = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
            : level == GGML_LOG_LEVEL_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_DEBUG;
    __android_log_print(android_level, LOG_TAG, "%s", text);
    if (level == GGML_LOG_LEVEL_ERROR) {
        g_last_native_error.append(text);
        if (g_last_native_error.size() > 1200) {
            g_last_native_error.erase(0, g_last_native_error.size() - 1200);
        }
    }
}

std::string friendly_native_error() {
    std::string result = g_last_native_error;
    std::replace(result.begin(), result.end(), '\n', ' ');
    std::replace(result.begin(), result.end(), '\r', ' ');
    while (!result.empty() && std::isspace(static_cast<unsigned char>(result.back()))) result.pop_back();
    return result;
}

std::string apply_model_chat_template(const std::string &raw_prompt) {
    if (g_chat_template_mode == "RAW") return raw_prompt;
    if (g_chat_template_mode == "CHAT_ML" || g_chat_template_mode == "QWEN") {
        return "<|im_start|>system\n" + raw_prompt + "<|im_end|>\n<|im_start|>assistant\n";
    }
    if (g_chat_template_mode == "LLAMA_3") {
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" + raw_prompt +
               "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";
    }
    if (g_chat_template_mode == "GEMMA") {
        return "<bos><start_of_turn>user\n" + raw_prompt + "<end_of_turn>\n<start_of_turn>model\n";
    }
    if (g_chat_template_mode == "CUSTOM" && !g_custom_chat_template.empty()) {
        std::string result = g_custom_chat_template;
        const std::string marker = "{{prompt}}";
        const size_t position = result.find(marker);
        if (position != std::string::npos) result.replace(position, marker.size(), raw_prompt);
        else result.append(raw_prompt);
        return result;
    }
    // AUTO uses llama.cpp's full Minja/Jinja path. The legacy public C API below only
    // recognizes a finite set of templates and silently loses formatting for newer GGUFs.
    try {
        auto templates = common_chat_templates_init(g_model, "");
        common_chat_templates_inputs inputs;
        inputs.messages.push_back({"user", raw_prompt});
        inputs.add_generation_prompt = true;
        inputs.use_jinja = true;
        // Reasoning models such as Qwen3 still work, but avoid spending the whole mobile
        // token budget in a hidden thinking block by default.
        inputs.enable_thinking = false;
        const auto formatted = common_chat_templates_apply(templates.get(), inputs).prompt;
        if (!formatted.empty()) {
            LOGI("Applied embedded Jinja chat template (%zu bytes)", formatted.size());
            return formatted;
        }
    } catch (const std::exception & error) {
        LOGE("Embedded Jinja template failed, trying legacy formatter: %s", error.what());
    }
    const char *chat_template = llama_model_chat_template(g_model, nullptr);
    if (chat_template == nullptr || chat_template[0] == '\0') return raw_prompt;
    const llama_chat_message message = {"user", raw_prompt.c_str()};
    int32_t required = llama_chat_apply_template(chat_template, &message, 1, true, nullptr, 0);
    if (required <= 0) return raw_prompt;
    std::vector<char> buffer(static_cast<size_t>(required) + 1, '\0');
    const int32_t written = llama_chat_apply_template(
            chat_template, &message, 1, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    if (written <= 0) return raw_prompt;
    LOGI("Applied model chat template (%d bytes)", written);
    return std::string(buffer.data(), static_cast<size_t>(written));
}

void throw_java(JNIEnv *env, const char *message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception, message);
}

std::string jstring_to_utf8(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

void unload_locked() {
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

bool emit_token(JNIEnv *env, jobject callback, jmethodID method, const std::string &piece) {
    if (piece.empty()) return true;
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(piece.size()));
    if (bytes == nullptr) return false;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(piece.size()), reinterpret_cast<const jbyte *>(piece.data()));
    jclass string_class = env->FindClass("java/lang/String");
    jmethodID constructor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    jstring token = static_cast<jstring>(env->NewObject(string_class, constructor, bytes, charset));
    if (token == nullptr) return false;
    env->CallVoidMethod(callback, method, token);
    env->DeleteLocalRef(token);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(string_class);
    return !env->ExceptionCheck();
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_localcharacter_app_llm_LlamaBridge_getVersion(JNIEnv *env, jobject) {
    std::string version = std::string("llama.cpp ") + LLAMA_BUILD_TAG;
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_localcharacter_app_llm_LlamaBridge_loadModel(
        JNIEnv *env, jobject, jstring path_value, jstring native_library_dir_value,
        jint context_size, jint threads, jint batch_size) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
    const std::string path = jstring_to_utf8(env, path_value);
    if (path.empty()) {
        throw_java(env, "La ruta del modelo está vacía.");
        return nullptr;
    }
    if (!g_backend_initialized) {
        llama_log_set(llama_android_log, nullptr);
        const std::string native_library_dir = jstring_to_utf8(env, native_library_dir_value);
        if (native_library_dir.empty()) {
            throw_java(env, "Android no proporcionó el directorio de librerías nativas.");
            return nullptr;
        }
        LOGI("Loading optimized CPU backends from %s", native_library_dir.c_str());
        ggml_backend_load_all_from_path(native_library_dir.c_str());
        llama_backend_init();
        g_backend_initialized = true;
    }
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    // Keep model weights file-backed on Android. Buffered/repacked weights made the
    // 610 MiB Qwen model plus KV cache consume > 1.2 GiB of anonymous memory on a real
    // Xiaomi and the OS swapped nearly all of it, turning a short prefill into minutes.
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    // Mmap avoids swapping the original weights; extra buffers let KleidiAI repack
    // matrix tensors into the DOTPROD layout chosen for the current SoC.
    model_params.use_extra_bufts = true;
    g_last_native_error.clear();
    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (g_model == nullptr) {
        LOGI("mmap load failed; retrying with buffered/repacked weights");
        g_last_native_error.clear();
        model_params.load_mode = LLAMA_LOAD_MODE_NONE;
        model_params.use_extra_bufts = true;
        g_model = llama_model_load_from_file(path.c_str(), model_params);
        if (g_model == nullptr) {
            const std::string detail = friendly_native_error();
            const std::string message = detail.empty()
                    ? "llama.cpp no pudo abrir este GGUF. Puede estar incompleto o usar una arquitectura no compatible."
                    : "llama.cpp rechazó el GGUF: " + detail;
            throw_java(env, message.c_str());
            return nullptr;
        }
    }
    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(context_size);
    g_batch_size = std::clamp(static_cast<int>(batch_size), 32, 512);
    context_params.n_batch = static_cast<uint32_t>(std::min(context_size, g_batch_size));
    context_params.n_ubatch = context_params.n_batch;
    context_params.n_threads = std::max(1, static_cast<int>(threads));
    context_params.n_threads_batch = std::max(1, static_cast<int>(threads));
    // Interrupt CPU graph execution inside llama_decode(), not only between batches.
    // This keeps Android responsive when a multi-billion parameter model is stopped.
    context_params.abort_callback = should_abort_decode;
    context_params.abort_callback_data = nullptr;
    context_params.no_perf = true;
    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        unload_locked();
        throw_java(env, "No hay memoria suficiente para crear el contexto del modelo.");
        return nullptr;
    }
    g_threads = std::max(1, static_cast<int>(threads));
    llama_set_n_threads(g_context, g_threads, g_threads);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    char description[512] = {};
    llama_model_desc(g_model, description, sizeof(description));
    LOGI("Model loaded: %s, vocab=%d", description, llama_vocab_n_tokens(vocab));
    return env->NewStringUTF(description);
}

extern "C" JNIEXPORT void JNICALL
Java_com_localcharacter_app_llm_LlamaBridge_unloadModel(JNIEnv *, jobject) {
    g_cancelled.store(true, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
    LOGI("Model unloaded");
}

extern "C" JNIEXPORT void JNICALL
Java_com_localcharacter_app_llm_LlamaBridge_setChatTemplate(
        JNIEnv *env, jobject, jstring mode_value, jstring custom_value) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_chat_template_mode = jstring_to_utf8(env, mode_value);
    g_custom_chat_template = jstring_to_utf8(env, custom_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_localcharacter_app_llm_LlamaBridge_stopGeneration(JNIEnv *, jobject) {
    g_cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_localcharacter_app_llm_LlamaBridge_generate(
        JNIEnv *env,
        jobject,
        jstring prompt_value,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p,
        jint top_k,
        jfloat min_p,
        jfloat repeat_penalty,
        jint seed,
        jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr) {
        throw_java(env, "No hay ningún modelo cargado.");
        return;
    }
    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
    if (on_token == nullptr) {
        throw_java(env, "Callback de streaming inválido.");
        return;
    }
    const std::string raw_prompt = jstring_to_utf8(env, prompt_value);
    const std::string prompt = apply_model_chat_template(raw_prompt);
    const auto generation_started = std::chrono::steady_clock::now();
    DecodeDeadlineReset deadline_reset;
    g_decode_deadline_ms.store(monotonic_millis() + 75'000, std::memory_order_relaxed);
    // Cancellation must also cover prompt tokenization/prefill. Previously a stop requested
    // here was overwritten immediately before sampling, leaving the next chat waiting.
    g_cancelled.store(false, std::memory_order_relaxed);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    int32_t token_count = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (token_count == 0) {
        throw_java(env, "El prompt no produjo tokens.");
        return;
    }
    if (token_count < 0) token_count = -token_count;
    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    token_count = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), token_count, true, true);
    if (token_count < 0) {
        throw_java(env, "No se pudo tokenizar el prompt.");
        return;
    }
    tokens.resize(static_cast<size_t>(token_count));
    LOGI("Generation started: raw=%zu bytes, formatted=%zu bytes, prompt_tokens=%d",
         raw_prompt.size(), prompt.size(), token_count);
    llama_memory_clear(llama_get_memory(g_context), false);
    if (static_cast<uint32_t>(token_count) >= llama_n_ctx(g_context)) {
        throw_java(env, "El prompt excede el contexto configurado.");
        return;
    }
    for (int32_t offset = 0; offset < token_count; offset += g_batch_size) {
        if (g_cancelled.load(std::memory_order_relaxed)) {
            LOGI("Generation cancelled during prompt prefill");
            return;
        }
        const int32_t count = std::min(g_batch_size, token_count - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        const int32_t decode_result = llama_decode(g_context, batch);
        if (decode_result == 2 && report_decode_abort(env, "prompt")) {
            return;
        }
        if (decode_result != 0) {
            throw_java(env, "llama.cpp no pudo evaluar el prompt.");
            return;
        }
    }
    const auto prefill_finished = std::chrono::steady_clock::now();
    g_decode_deadline_ms.store(monotonic_millis() + 180'000, std::memory_order_relaxed);
    LOGI("Prompt prefill finished in %lld ms",
         static_cast<long long>(std::chrono::duration_cast<std::chrono::milliseconds>(
                 prefill_finished - generation_started).count()));

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, repeat_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(std::max(1, static_cast<int>(top_k))));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(min_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    std::vector<char> piece_buffer(256);
    std::string pending_utf8;
    bool first_token_logged = false;
    for (int generated = 0; generated < max_tokens && !g_cancelled.load(std::memory_order_relaxed); ++generated) {
        llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        int32_t piece_size = llama_token_to_piece(vocab, token, piece_buffer.data(), static_cast<int32_t>(piece_buffer.size()), 0, true);
        if (piece_size < 0) {
            piece_buffer.resize(static_cast<size_t>(-piece_size));
            piece_size = llama_token_to_piece(vocab, token, piece_buffer.data(), static_cast<int32_t>(piece_buffer.size()), 0, true);
        }
        if (piece_size > 0) {
            if (!first_token_logged) {
                first_token_logged = true;
                const auto first_token_at = std::chrono::steady_clock::now();
                LOGI("First token sampled in %lld ms",
                     static_cast<long long>(std::chrono::duration_cast<std::chrono::milliseconds>(
                             first_token_at - generation_started).count()));
            }
            pending_utf8.append(piece_buffer.data(), static_cast<size_t>(piece_size));
            if (is_valid_utf8(pending_utf8)) {
                if (!emit_token(env, callback, on_token, pending_utf8)) break;
                pending_utf8.clear();
            }
        }
        llama_batch next = llama_batch_get_one(&token, 1);
        const int32_t decode_result = llama_decode(g_context, next);
        if (decode_result == 2 && report_decode_abort(env, "token")) {
            break;
        }
        if (decode_result != 0) {
            LOGE("Decode failed after %d generated tokens", generated);
            break;
        }
    }
    llama_sampler_free(sampler);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *, void *) {
    return JNI_VERSION_1_6;
}
