# Quick Start Guide

This guide will get you up and running with the example Quarkus CEL K8s Validator application in less than 5 minutes.

## Step 1: Verify Prerequisites

Make sure you have Java 21 or higher:

```bash
java -version
# Should show version 21 or higher
```

## Step 2: Run the Tests

```bash
cd quarkus-cel-k8s-validator

# Run unit tests
mvn test

# Run all tests including integration tests
mvn verify
```

Expected output:
```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Step 3: Start the Application

Start in development mode with live reload:

```bash
mvn quarkus:dev
```

Wait for the message: `Listening on: http://localhost:8080`

## Step 4: Test the Validation API

In a new terminal, test the API manually:

### Test 1: Valid Deployment (should ALLOW)

```bash
curl -X POST http://localhost:8080/k8s/validate \
  -F "resourceJson=$(cat src/test/resources/deployment.json)" \
  -F "celPolicy=$(cat src/test/resources/require-replica-count.cel)"
```

Expected response:
```json
{
  "status": "allowed",
  "message": "Policy ALLOWS the request",
  "policy": "object.kind != \"Deployment\" || ..."
}
```

### Test 2: Invalid Deployment (should DENY)

```bash
curl -X POST http://localhost:8080/k8s/validate \
  -F "resourceJson=$(cat src/test/resources/deployment-invalid.json)" \
  -F "celPolicy=$(cat src/test/resources/require-replica-count.cel)"
```

Expected response:
```json
{
  "status": "denied",
  "message": "Policy DENIES the request",
  "policy": "object.kind != \"Deployment\" || ..."
}
```

## Step 5: Try Continuous Testing

With `mvn quarkus:dev` running:

1. Press `r` to run all tests
2. Press `o` to toggle test output


## What's Next?

- Read the full [README.md](README.md) for architecture details
- Explore the test suite in `src/test/java`
- Try different CEL policies in `src/test/resources`
- Integrate into your Kubernetes operator

Happy coding! 🚀
