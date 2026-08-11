package io.gitee.lujianshi.bidwinningbackend.storage.vector;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.model.PreparedCredentialDocument;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CredentialFileVectorStorageTest {

    private static final String BASE_URL = "http://127.0.0.1:8083";
    private static final String FILE_UUID =
            "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void shouldReplaceCredentialWithExactServerControlledScopeContract() {
        Fixture fixture = fixture();
        expectDeleteAndZeroStatus(fixture.server(), 3);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/credential-files/vectorize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "documents": [{
                            "fileUuid": "%s",
                            "content": "# 资信唯一标记",
                            "credentialFileId": 71,
                            "bidRecordId": 13,
                            "originalFilename": "credential.pdf",
                            "originalObjectKey": "bidder/credential/original.pdf",
                            "convertedObjectKey": "bidder/credential/converted.md",
                            "originalSha256": "%s",
                            "convertedSha256": "%s"
                          }]
                        }
                        """.formatted(
                        FILE_UUID,
                        "a".repeat(64),
                        "b".repeat(64))))
                .andRespond(withSuccess(
                        """
                        {"documents":[{"fileUuid":"%s","success":true,"chunkCount":3,"message":null}]}
                        """.formatted(FILE_UUID),
                        MediaType.APPLICATION_JSON));
        expectStatus(fixture.server(), 3);

        int chunkCount = fixture.client().replaceDocument(document());

        assertEquals(3, chunkCount);
        fixture.server().verify();
    }

    @Test
    void shouldCleanAllChunksWhenPythonCountDoesNotMatchVerifiedCount() {
        Fixture fixture = fixture();
        expectDeleteAndZeroStatus(fixture.server(), 0);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/credential-files/vectorize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"documents":[{"fileUuid":"%s","success":true,"chunkCount":3,"message":null}]}
                        """.formatted(FILE_UUID),
                        MediaType.APPLICATION_JSON));
        expectStatus(fixture.server(), 2);
        expectDeleteAndZeroStatus(fixture.server(), 2);

        AppException exception = assertThrows(
                AppException.class,
                () -> fixture.client().replaceDocument(document()));

        assertEquals(
                AppErrorCode.CREDENTIAL_FILE_VECTORIZATION_FAILED,
                exception.getErrorCode());
        fixture.server().verify();
    }

    @Test
    void shouldCleanUnknownChunksWhenPostResponseIsLost() {
        Fixture fixture = fixture();
        expectDeleteAndZeroStatus(fixture.server(), 0);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/credential-files/vectorize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        expectDeleteAndZeroStatus(fixture.server(), 3);

        AppException exception = assertThrows(
                AppException.class,
                () -> fixture.client().replaceDocument(document()));

        assertEquals(
                AppErrorCode.CREDENTIAL_FILE_VECTORIZATION_FAILED,
                exception.getErrorCode());
        fixture.server().verify();
    }

    private void expectDeleteAndZeroStatus(
            MockRestServiceServer server,
            int deletedChunkCount) {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/credential-files/" + FILE_UUID))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(
                        """
                        {"fileUuid":"%s","deletedChunkCount":%d}
                        """.formatted(FILE_UUID, deletedChunkCount),
                        MediaType.APPLICATION_JSON));
        expectStatus(server, 0);
    }

    private void expectStatus(MockRestServiceServer server, int chunkCount) {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/credential-files/" + FILE_UUID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"fileUuid":"%s","chunkCount":%d}
                        """.formatted(FILE_UUID, chunkCount),
                        MediaType.APPLICATION_JSON));
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        CredentialFileVectorStorage client = new CredentialFileVectorStorage(
                builder.baseUrl(BASE_URL).build());
        return new Fixture(server, client);
    }

    private PreparedCredentialDocument document() {
        return new PreparedCredentialDocument(
                FILE_UUID,
                "# 资信唯一标记",
                71L,
                13,
                "credential.pdf",
                "bidder/credential/original.pdf",
                "bidder/credential/converted.md",
                "a".repeat(64),
                "b".repeat(64));
    }

    private record Fixture(
            MockRestServiceServer server,
            CredentialFileVectorStorage client) {
    }
}
