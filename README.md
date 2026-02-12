# Quarkus CEL Kubernetes Validator

An example application showcasing the **Quarkus Chicory extension** for a WebAssembly-based CEL 
(Common Expression Language) policy validation of Kubernetes resources use case.

This example implements a [Kubernetes-style policy validation](https://kubernetes.io/docs/reference/using-api/cel/) by 
leveraging:
- **Google CEL-Go**: The popular Go-based CEL library used by many Kubernetes operators
- **WebAssembly**: Compiling the Go CEL library to WASM for portability
- **Quarkus Chicory**: Seamlessly integrating WASM execution into Quarkus applications


## Use Case

When building Kubernetes operators in Java (like the Keycloak operator), you might need CEL-based policy validation 
similar to what Go operators provide. 
Instead of reimplementing CEL from scratch in Java, this demo shows how to reuse the mature Go CEL library via WebAssembly.

## Prerequisites

- Java 21 or higher
- Maven 3.9+
- Docker (optional, for rebuilding the WASM module)

## Quick Start

### 1. Run the Tests

```bash
# Run unit tests
mvn test

# Run integration tests (packaged mode)
mvn verify
```

### 2. Start in Development Mode

```bash
mvn quarkus:dev
```

The application will start on `http://localhost:8080`

### 3. Test the API Manually

```bash
# Valid deployment (3 replicas - should ALLOW)
curl -X POST http://localhost:8080/k8s/validate \
  -F "resourceJson=$(cat src/test/resources/deployment.json)" \
  -F "celPolicy=$(cat src/test/resources/require-replica-count.cel)"

# Invalid deployment (1 replica - should DENY)
curl -X POST http://localhost:8080/k8s/validate \
  -F "resourceJson=$(cat src/test/resources/deployment-invalid.json)" \
  -F "celPolicy=$(cat src/test/resources/require-replica-count.cel)"
```

## Application Architecture

**K8sCelValidatorService** (`@ApplicationScoped`)
- Manages WASM module lifecycle
- Handles memory allocation and cleanup
- Executes CEL policy evaluation via WASM
- Returns structured validation results

**K8sCelValidatorResource** (`@Path("/k8s")`)
- REST endpoint for validation requests
- Delegates to service layer
- Translates validation results to HTTP responses


## How It Works

### 1. Go CEL to WebAssembly

The `main.go` file implements a CEL policy evaluator in Go, with exported functions:

```go
//go:wasmexport malloc
func malloc(size uint32) uint32

//go:wasmexport free
func free(ptr uint32)

//go:wasmexport evalPolicy
func evalPolicy(policyPtr, policyLen, inputPtr, inputLen uint32) int32
```

The Go code is compiled to WASM targeting `wasip1`:

```bash
GOOS=wasip1 GOARCH=wasm go build -o go-cel.wasm main.go
```

### 2. Quarkus Chicory Integration

Configuration in `application.properties`:

```properties
quarkus.chicory.modules.go-cel.name=io.quarkiverse.chicory.demo.GoCelModule
quarkus.chicory.modules.go-cel.wasm-file=src/main/resources/wasm/go-cel.wasm
```

This tells Quarkus Chicory to:
- Generate a `GoCelModule` class and additional classes at build time from the WASM file 
- Configure the appropriate `MachineFactory` based on the runtime environment
- Watch the WASM file for changes in development mode

### 3. Service Layer Integration

The service injects the WASM context and manages the validation lifecycle:

```java
@ApplicationScoped
public class K8sCelValidatorService {

    @Inject
    @Named("go-cel")
    WasmQuarkusContext wasmQuarkusContext;

    @PostConstruct
    public void init() {
        // Initialize WASM instance with WASI support
        // Export malloc, free, evalPolicy functions
        // Start Go runtime (i.e. run the main() function)
    }

    public ValidationResult validate(String resourceJson, String celPolicy) {
        // 1. Allocate WASM memory
        // 2. Write data to WASM memory
        // 3. Call evalPolicy WASM function
        // 4. Interpret result (1=allow, 0=deny, negative=error)
        // 5. Free allocated memory
    }
}
```


## Example CEL Policy

The test suite uses a replica count validation policy:

```cel
object.kind != "Deployment" || (has(object.spec.replicas) && object.spec.replicas >= 2)
```

This policy ensures that Deployments have at least 2 replicas for high availability.


## Rebuilding the WASM Module

If you modify the Go source code and rebuild the Wasm module:

```bash
cd src/main/resources/wasm
./build.sh
```

This will:
1. Build a Docker image with Go 1.24 toolchain
2. Compile `main.go` to `go-cel.wasm` with WASI support
3. Output the updated WASM module

In development mode (`mvn quarkus:dev`), changes are automatically detected and reloaded!

**IMPORTANT**: The output could show the following error as soon as the Wasm module is rebuilt:

```
java.lang.RuntimeException: io.quarkus.builder.BuildException: Build failure: Build failed due to errors
          [error]: Build step io.quarkiverse.chicory.deployment.QuarkusWasmProcessor#generate threw an exception: com.dylibso.chicory.wasm.MalformedException: section size mismatch, unexpected end of section 
   or function, length out of bounds
```

which is due to [a known issue](https://github.com/quarkiverse/quarkus-chicory/issues/21) that is causing a race 
condition - the file watcher is too eager and doesn't wait for the file write to complete.

At the moment, just ignore it and re-run the tests, Quarkus Chicory will perform the generation process again.


## API Reference

### POST `/k8s/validate`

Validates a Kubernetes resource against a CEL policy.

**Request** (multipart/form-data):
- `resourceJson`: The Kubernetes resource as JSON
- `celPolicy`: The CEL policy expression to evaluate

**Response** (application/json):
```json
{
  "status": "allowed|denied|error",
  "message": "Policy ALLOWS the request",
  "policy": "object.kind != \"Deployment\" || ..."
}
```

**Status Codes**:
- `200 OK`: Policy allows the resource
- `403 Forbidden`: Policy denies the resource
- `400 Bad Request`: CEL evaluation error (syntax, compilation, runtime)

## Building for Production

### JVM Mode

```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

### Native Mode

```bash
mvn clean package -Pnative
./target/quarkus-cel-k8s-validator-1.0.0-SNAPSHOT-runner
```


## Learn More

- [Quarkus Chicory Extension](https://docs.quarkiverse.io/quarkus-chicory/dev)
- [Chicory WebAssembly Runtime](https://github.com/dylibso/chicory)
- [Google CEL](https://github.com/google/cel-go)
- [Kubernetes CEL Validation](https://kubernetes.io/docs/reference/using-api/cel/)
- [WebAssembly System Interface (WASI)](https://wasi.dev/)
