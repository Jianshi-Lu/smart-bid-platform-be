package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.controller.bid_inviter.BidInviterFileController;
import io.gitee.lujianshi.bidwinningbackend.controller.bidder.BidderFileController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileControllerContractTest {

    @Test
    void shouldPreserveBidderFileHttpContracts() {
        assertEquals(Set.of(
                "POST /bidder/bid-records/{id}/credential-files",
                "DELETE /bidder/bid-records/{id}/credential-files/{fileId}",
                "GET /bidder/bid-records/{id}/credential-files",
                "GET /bidder/bid-records/{id}/credential-files/events",
                "GET /bidder/bid-records/{bidRecordId}/credential-files/{fileId}/download/original",
                "GET /bidder/bid-records/{bidRecordId}/credential-files/{fileId}/download/markdown"),
                mappings(BidderFileController.class));
    }

    @Test
    void shouldPreserveBidInviterFileHttpContracts() {
        assertEquals(Set.of(
                "POST /knowledge-files",
                "GET /knowledge-files",
                "GET /knowledge-files/{documentUuid}",
                "GET /knowledge-files/{documentUuid}/history",
                "GET /knowledge-files/events",
                "POST /knowledge-files/{documentUuid}/retry",
                "DELETE /knowledge-files/{documentUuid}",
                "POST /credential-files/bid-records/{bidRecordId}/convert",
                "GET /credential-files/bid-records/{bidRecordId}",
                "GET /credential-files/{fileId}/history",
                "GET /credential-files/{fileId}/download/original",
                "GET /credential-files/{fileId}/download/markdown",
                "GET /knowledge-files/{documentUuid}/download/original",
                "GET /knowledge-files/{documentUuid}/download/markdown"),
                mappings(BidInviterFileController.class));
    }

    private Set<String> mappings(Class<?> controllerType) {
        String classPath = firstPath(AnnotatedElementUtils.findMergedAnnotation(
                controllerType, RequestMapping.class));
        Set<String> mappings = new HashSet<>();
        for (Method method : controllerType.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                    method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            String methodPath = firstPath(mapping);
            for (RequestMethod requestMethod : mapping.method()) {
                mappings.add(requestMethod.name() + " "
                        + classPath + methodPath);
            }
        }
        return mappings;
    }

    private String firstPath(RequestMapping mapping) {
        if (mapping == null) {
            return "";
        }
        String[] paths = mapping.path().length == 0
                ? mapping.value()
                : mapping.path();
        return Arrays.stream(paths).findFirst().orElse("");
    }
}
