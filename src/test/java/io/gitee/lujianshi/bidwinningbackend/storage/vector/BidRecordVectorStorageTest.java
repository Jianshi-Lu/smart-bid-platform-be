package io.gitee.lujianshi.bidwinningbackend.storage.vector;

import io.gitee.lujianshi.bidwinningbackend.storage.vector.model.PreparedBidRecordDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BidRecordVectorStorageTest {

    private static final String BASE_URL = "http://127.0.0.1:8083";

    @Test
    void shouldReplaceOneCompleteStructuredBidRecord() {
        Fixture fixture = fixture();
        expectDeleteDocument(fixture.server(), 1);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/bid-records/vectorize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "documents": [{
                            "bidRecordId": 13,
                            "content": "# 投标记录\\n- 投标人名称：测试投标人",
                            "invitationId": 5,
                            "bidderName": "测试投标人",
                            "bidNo": 2,
                            "bidderAccountId": 7,
                            "projectNo": "P-001",
                            "purchaseTaskNo": "T-001",
                            "bidAt": "2026-08-07T14:56",
                            "quotePrice": "19999999.00"
                          }]
                        }
                        """, true))
                .andRespond(withSuccess(
                        """
                        {"documents":[{"bidRecordId":13,"success":true,"chunkCount":1,"message":null}]}
                        """,
                        MediaType.APPLICATION_JSON));
        expectDocumentStatus(fixture.server(), 1);

        int count = fixture.client().replaceDocument(document());

        assertEquals(1, count);
        fixture.server().verify();
    }

    @Test
    void shouldDeleteAllPrivateMaterialsAndVerifyZero() {
        Fixture fixture = fixture();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/bid-records/13"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(
                        "{\"bidRecordId\":13,\"deletedChunkCount\":8}",
                        MediaType.APPLICATION_JSON));
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/bid-records/13/materials"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"bidRecordId\":13,\"chunkCount\":0}",
                        MediaType.APPLICATION_JSON));

        fixture.client().deleteAllMaterials(13);

        fixture.server().verify();
    }

    private void expectDeleteDocument(
            MockRestServiceServer server,
            int deletedCount) {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/bid-records/13/document"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(
                        "{\"bidRecordId\":13,\"deletedChunkCount\":"
                                + deletedCount + "}",
                        MediaType.APPLICATION_JSON));
        expectDocumentStatus(server, 0);
    }

    private void expectDocumentStatus(
            MockRestServiceServer server,
            int count) {
        server.expect(once(), requestTo(
                        BASE_URL + "/internal/bid-records/13"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"bidRecordId\":13,\"chunkCount\":" + count + "}",
                        MediaType.APPLICATION_JSON));
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        return new Fixture(
                server,
                new BidRecordVectorStorage(
                        builder.baseUrl(BASE_URL).build()));
    }

    private PreparedBidRecordDocument document() {
        return new PreparedBidRecordDocument(
                13,
                "# 投标记录\n- 投标人名称：测试投标人",
                5,
                "测试投标人",
                2,
                7L,
                "P-001",
                "T-001",
                "2026-08-07T14:56",
                "19999999.00");
    }

    private record Fixture(
            MockRestServiceServer server,
            BidRecordVectorStorage client) {
    }
}
