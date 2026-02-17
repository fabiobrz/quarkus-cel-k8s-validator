package io.quarkiverse.chicory.demo;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.WasmModule;
import io.quarkiverse.chicory.runtime.wasm.WasmQuarkusContext;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class K8sCelValidatorService {

    @Inject
    @Named("go-cel")
    WasmQuarkusContext wasmQuarkusContext;

    Instance instance;
    K8sCel_ModuleExports exports;

    @PostConstruct
    public void init() throws IOException {
        WasmModule wasmModule = wasmQuarkusContext.getWasmModule();
        if (wasmModule == null) {
            throw new IllegalStateException("Wasm module " + wasmQuarkusContext.getName() + " not found!");
        }

        // Create WASI support for stdout/stderr
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        WasiOptions options = WasiOptions.builder()
                .withStdout(stdout)
                .withStderr(stderr)
                .build();

        WasiPreview1 wasi = WasiPreview1.builder()
                .withOptions(options)
                .build();

        Store store = new Store().addFunction(wasi.toHostFunctions());

        try {
            // WasmQuarkusContext provides Instance with MachineFactory dynamically
            // configured based on environment (dev, prod, native)
            instance = Instance.builder(wasmModule)
                    .withMachineFactory(wasmQuarkusContext.getMachineFactory())
                    .withImportValues(store.toImportValues())
                    .build();

            exports = new K8sCel_ModuleExports(instance);
        } catch (com.dylibso.chicory.wasi.WasiExitException e) {
            // Expected - Go main() exits after completing
            if (e.exitCode() != 0) {
                throw new RuntimeException("Go runtime initialization failed with exit code: " + e.exitCode());
            }
            // Exit code 0 is success - runtime is now initialized and exported functions are ready
        }
    }

    public ValidationResult validate(
            final String resourceJson,
            final String celPolicy) {

        byte[] policyBytes = celPolicy.getBytes(StandardCharsets.UTF_8);
        byte[] inputBytes = resourceJson.getBytes(StandardCharsets.UTF_8);

        // Allocate memory for policy string in WASM
        int policyPtr = exports.malloc(policyBytes.length);
        if (policyPtr == 0) {
            throw new IllegalStateException("Failed to allocate memory for policy");
        }

        // Allocate memory for input JSON in WASM
        int inputPtr = exports.malloc(inputBytes.length);
        if (inputPtr == 0) {
            throw new IllegalStateException("Failed to allocate memory for input");
        }

        try {
            // Write policy and input to WASM memory
            exports.memory().write(policyPtr, policyBytes);
            exports.memory().write(inputPtr, inputBytes);

            // Call evalPolicy(policyPtr, policyLen, inputPtr, inputLen)
            int returnCode = exports.evalPolicy(policyPtr, policyBytes.length, inputPtr, inputBytes.length);

            // Interpret result
            if (returnCode == 11) {
                return new ValidationResult(VALIDATION_RESULT_ALLOWED, "Policy ALLOWS the request", celPolicy);
            } else if (returnCode == 0) {
                return new ValidationResult(VALIDATION_RESULT_DENIED, "Policy DENIES the request", celPolicy);
            } else {
                // Negative values are errors
                String errorMsg = switch (returnCode) {
                    case -1 -> "JSON parse error";
                    case -2 -> "CEL environment creation error";
                    case -3 -> "CEL compilation error";
                    case -4 -> "CEL program creation error";
                    case -5 -> "CEL runtime error";
                    default -> "Unknown error: " + returnCode;
                };
                return new ValidationResult(VALIDATION_RESULT_ERROR, "CEL evaluation failed: " + errorMsg, celPolicy);
            }
        } finally {
            // Free allocated memory in WASM
            exports.free(policyPtr);
            exports.free(inputPtr);
        }
    }

    public static final String VALIDATION_RESULT_ALLOWED = "allowed";
    public static final String VALIDATION_RESULT_DENIED = "denied";
    public static final String VALIDATION_RESULT_ERROR = "error";

    /**
     * Simple record to return structured validation results
     */
    public record ValidationResult(String status, String message, String policy) {}
}
