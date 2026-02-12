package io.quarkiverse.chicory.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkiverse.chicory.runtime.wasm.WasmQuarkusContext;
import org.jboss.resteasy.reactive.common.jaxrs.ResponseImpl;

/**
 * REST resource for validating Kubernetes resources using CEL policies via WebAssembly.
 *
 * This demonstrates using the Quarkus Chicory extension to run Go-based CEL evaluation
 * in WebAssembly, enabling Kubernetes-style policy validation in Java applications.
 */
@Path("/k8s")
public class K8sCelValidatorResource {

    @Inject
    K8sCelValidatorService validatorService;

    /**
     * Validates a Kubernetes resource against a CEL policy.
     *
     * @param resourceJson The Kubernetes resource as JSON (e.g., Pod, Deployment manifest)
     * @param celPolicy The CEL policy expression to evaluate
     * @return Response indicating whether the policy allows or denies the resource
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/validate")
    public Response validate(@RestForm String resourceJson, @RestForm String celPolicy) {

        final K8sCelValidatorService.ValidationResult validationResult = validatorService.validate(resourceJson, celPolicy);

        Response.Status returnCode;
        switch (validationResult.status()) {
            case K8sCelValidatorService.VALIDATION_RESULT_ALLOWED -> returnCode = Response.Status.OK;
            case K8sCelValidatorService.VALIDATION_RESULT_DENIED -> returnCode = Response.Status.FORBIDDEN;
            default -> returnCode = Response.Status.BAD_REQUEST;
        }

        return Response.status(returnCode).entity(validationResult).build();
    }
}
