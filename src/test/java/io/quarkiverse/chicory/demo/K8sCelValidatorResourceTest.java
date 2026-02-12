package io.quarkiverse.chicory.demo;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class K8sCelValidatorResourceTest {

    static final String CEL_POLICY;

    static {
        try {
            CEL_POLICY = readResource("require-replica-count.cel");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testCorrectDeploymentDefinitionValidation() throws IOException {
        assertValidationStatus(readResource("deployment.json"), Response.Status.OK.getStatusCode(),
                K8sCelValidatorService.VALIDATION_RESULT_ALLOWED);
    }

    @Test
    public void testInvalidDeploymentDefinitionValidation() throws IOException {
        assertValidationStatus(readResource("deployment-invalid.json"), Response.Status.FORBIDDEN.getStatusCode(),
                K8sCelValidatorService.VALIDATION_RESULT_DENIED);
    }

    private static String readResource(String fileName) throws IOException {
        final URL url = Thread.currentThread().getContextClassLoader().getResource(fileName);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + fileName);
        }
        return Files.readString(Path.of(url.getPath()));
    }

    private static void assertValidationStatus(final String manifestJson, final int expectedStatusCode,
                                               final String expectedValidationResult) {
        given()
                .multiPart("resourceJson", manifestJson)
                .multiPart("celPolicy", CEL_POLICY)
                .when()
                .post("/k8s/validate")
                .then()
                .statusCode(expectedStatusCode)
                .body("status", Matchers.is(expectedValidationResult));
    }
}