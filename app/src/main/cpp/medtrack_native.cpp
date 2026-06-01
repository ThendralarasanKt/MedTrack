#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <exception>
#include <mutex>
#include <string>
#include <vector>

struct MedTrackLlamaHandle {
    llama_model * model = nullptr;
    std::mutex mutex;
};

extern "C" JNIEXPORT jstring JNICALL
Java_com_medtrack_app_ai_NativeLlmBridge_nativeSmokeTest(
        JNIEnv *env,
        jobject /* this */) {
    std::string message = "medtrack_native_ready";
    return env->NewStringUTF(message.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medtrack_app_ai_NativeLlmBridge_nativeLlamaVersion(
        JNIEnv *env,
        jobject /* this */) {
    llama_backend_init();
    std::string message = "llama.cpp_ready: ";
    message += llama_print_system_info();
    llama_backend_free();
    return env->NewStringUTF(message.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medtrack_app_ai_NativeLlmBridge_nativeLoadModel(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath) {
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(path, model_params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        llama_backend_free();
        return 0L;
    }

    auto * handle = new MedTrackLlamaHandle();
    handle->model = model;
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medtrack_app_ai_NativeLlmBridge_nativeGenerate(
        JNIEnv *env,
        jobject /* this */,
        jlong handlePtr,
        jstring prompt,
        jint maxTokens) {
    if (handlePtr == 0L) {
        return env->NewStringUTF("Model is not loaded.");
    }

    auto * handle = reinterpret_cast<MedTrackLlamaHandle *>(handlePtr);
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->model == nullptr) {
        return env->NewStringUTF("Model is not loaded.");
    }

    const char * promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string promptText(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    const llama_vocab * vocab = llama_model_get_vocab(handle->model);
    const int promptTokenCount = -llama_tokenize(
            vocab,
            promptText.c_str(),
            static_cast<int32_t>(promptText.size()),
            nullptr,
            0,
            true,
            true
    );

    if (promptTokenCount <= 0) {
        return env->NewStringUTF("Failed to tokenize prompt.");
    }

    std::vector<llama_token> promptTokens(promptTokenCount);
    const int tokenizedCount = llama_tokenize(
            vocab,
            promptText.c_str(),
            static_cast<int32_t>(promptText.size()),
            promptTokens.data(),
            static_cast<int32_t>(promptTokens.size()),
            true,
            true
    );

    if (tokenizedCount < 0) {
        return env->NewStringUTF("Failed to tokenize prompt.");
    }

    const int safeMaxTokens = std::max(1, std::min(static_cast<int>(maxTokens), 128));
    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(promptTokens.size() + safeMaxTokens + 8);
    ctxParams.n_ctx = std::max<uint32_t>(ctxParams.n_ctx, 256);
    ctxParams.n_batch = static_cast<uint32_t>(promptTokens.size());
    ctxParams.n_ubatch = std::min<uint32_t>(ctxParams.n_batch, 32);
    ctxParams.n_threads = 2;
    ctxParams.n_threads_batch = 2;
    ctxParams.no_perf = true;

    llama_context * ctx = llama_init_from_model(handle->model, ctxParams);
    if (ctx == nullptr) {
        return env->NewStringUTF("Failed to create llama context.");
    }

    auto samplerParams = llama_sampler_chain_default_params();
    samplerParams.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    llama_batch batch = llama_batch_get_one(
            promptTokens.data(),
            static_cast<int32_t>(promptTokens.size())
    );

    std::string output;
    int decodedTokens = 0;

    for (int position = 0; position + batch.n_tokens < static_cast<int>(promptTokens.size()) + safeMaxTokens;) {
        if (llama_decode(ctx, batch) != 0) {
            output = "llama_decode failed.";
            break;
        }

        position += batch.n_tokens;
        llama_token nextToken = llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(vocab, nextToken)) {
            break;
        }

        char piece[256];
        const int pieceLength = llama_token_to_piece(
                vocab,
                nextToken,
                piece,
                sizeof(piece),
                0,
                true
        );

        if (pieceLength < 0) {
            output = "Failed to convert token to text.";
            break;
        }

        output.append(piece, pieceLength);
        batch = llama_batch_get_one(&nextToken, 1);
        decodedTokens += 1;
    }

    if (decodedTokens == 0 && output.empty()) {
        output = "";
    }

    llama_sampler_free(sampler);
    llama_free(ctx);

    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_medtrack_app_ai_NativeLlmBridge_nativeReleaseModel(
        JNIEnv * /* env */,
        jobject /* this */,
        jlong handlePtr) {
    if (handlePtr == 0L) {
        return;
    }

    auto * handle = reinterpret_cast<MedTrackLlamaHandle *>(handlePtr);
    if (handle->model != nullptr) {
        llama_model_free(handle->model);
        handle->model = nullptr;
    }
    delete handle;
    llama_backend_free();
}
