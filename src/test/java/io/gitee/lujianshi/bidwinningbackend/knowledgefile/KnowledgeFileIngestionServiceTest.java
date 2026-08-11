package io.gitee.lujianshi.bidwinningbackend.knowledgefile;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.KnowledgeFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.FileStorageType;
import io.gitee.lujianshi.bidwinningbackend.storage.object.LocalObjectStorage;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.entity.KnowledgeFileRecord;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.storage.vector.KnowledgeFileVectorStorage;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.DocumentUuidGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.key.KnowledgeObjectKeyGenerator;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.KnowledgeFileUpload;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.model.PreparedKnowledgeDocument;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileIngestionService;
import io.gitee.lujianshi.bidwinningbackend.knowledgefile.service.KnowledgeFileObjectService;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileMapper;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.service.audit.OperationHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeFileIngestionServiceTest {

    private static final String USERNAME = "admin01";

    @TempDir
    Path storageRoot;

    @Test
    void shouldStoreConvertVectorizeAndPersistLifecycle() throws Exception {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        byte[] original = "原始知识".getBytes(StandardCharsets.UTF_8);
        byte[] markdown = "# 测试知识\n\n唯一事实。".getBytes(StandardCharsets.UTF_8);
        when(fixture.toMarkdownClient().convert(
                any(), eq("knowledge.txt"), eq("text/plain"),
                eq((long) original.length)))
                .thenAnswer(invocation -> {
                    ((java.io.InputStream) invocation.getArgument(0)).readAllBytes();
                    return new PythonMarkdownResponse(
                            markdown, "knowledge.md", "text/markdown");
                });
        when(fixture.vectorStorage().vectorizeDocument(any(
                PreparedKnowledgeDocument.class))).thenReturn(2);

        var response = fixture.service().ingest(List.of(upload(
                "knowledge.txt", original)), USERNAME);

        assertEquals(1, response.documents().size());
        var document = response.documents().getFirst();
        assertEquals(documentUuid.toString(), document.documentUuid());
        assertEquals(
                USERNAME + "/knowledge.txt/original/"
                        + documentUuid + "_knowledge.txt",
                document.originalObjectKey());
        assertEquals(
                USERNAME + "/knowledge.txt/converted/"
                        + documentUuid + "_knowledge.md",
                document.convertedObjectKey());
        assertEquals(2, document.chunkCount());
        assertEquals("CONVERTED", document.convertStatus());
        assertEquals("VECTORIZED", document.indexStatus());
        verify(fixture.mapper()).markConverted(
                eq(documentUuid.toString()), anyString(), anyString(), any());
        verify(fixture.mapper()).markVectorized(
                eq(documentUuid.toString()), eq(2), any());
        verify(fixture.fileStatusEventPublisher()).publish(
                eq(FileStatusEventPublisher.KNOWLEDGE_TOPIC),
                eq(documentUuid.toString()),
                eq("STORED"),
                eq("CONVERTED"),
                eq("VECTORIZING"));
        verify(fixture.fileStatusEventPublisher()).publish(
                eq(FileStatusEventPublisher.KNOWLEDGE_TOPIC),
                eq(documentUuid.toString()),
                eq("STORED"),
                eq("CONVERTED"),
                eq("VECTORIZED"));
        try (var input = fixture.storage().open(
                document.convertedObjectKey())) {
            assertEquals(
                    new String(markdown, StandardCharsets.UTF_8),
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldKeepObjectsAndMarkIndexFailedWhenVectorizationFails() {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        byte[] original = "原始知识".getBytes(StandardCharsets.UTF_8);
        byte[] markdown = "# 回滚不删对象".getBytes(StandardCharsets.UTF_8);
        when(fixture.toMarkdownClient().convert(
                any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    ((java.io.InputStream) invocation.getArgument(0)).readAllBytes();
                    return new PythonMarkdownResponse(
                            markdown, "knowledge.md", "text/markdown");
                });
        when(fixture.vectorStorage().vectorizeDocument(any(
                PreparedKnowledgeDocument.class)))
                .thenThrow(new AppException(
                        AppErrorCode.KNOWLEDGE_FILE_VECTORIZATION_FAILED,
                        "knowledge-file",
                        "VECTORIZE",
                        "PYTHON_REJECTED",
                        "切分失败"));
        var response = fixture.service().ingest(List.of(upload(
                "knowledge.txt", original)), USERNAME);

        var document = response.documents().getFirst();
        assertEquals("CONVERTED", document.convertStatus());
        assertEquals("FAILED", document.indexStatus());
        assertEquals("切分失败", document.errorMessage());
        verify(fixture.mapper()).markVectorizeFailed(
                eq(documentUuid.toString()), anyString());
        // 失败保留对象与记录，供重试续跑，不做补偿删除。
        verify(fixture.vectorStorage(), never()).deleteDocument(anyString());
        verify(fixture.mapper(), never()).markDeleted(anyString(), any());
        assertPresent(fixture.storage(),
                USERNAME + "/knowledge.txt/original/"
                        + documentUuid + "_knowledge.txt");
        assertPresent(fixture.storage(),
                USERNAME + "/knowledge.txt/converted/"
                        + documentUuid + "_knowledge.md");
    }

    @Test
    void shouldMarkConvertFailedAndKeepOriginalWhenConversionFails() {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        byte[] original = "原始知识".getBytes(StandardCharsets.UTF_8);
        when(fixture.toMarkdownClient().convert(
                any(), any(), any(), anyLong()))
                .thenThrow(new AppException(
                        AppErrorCode.KNOWLEDGE_FILE_CONVERSION_FAILED,
                        "knowledge-file",
                        "INGEST",
                        "CONVERT_ORIGINAL",
                        "Python 转换失败"));
        var response = fixture.service().ingest(List.of(upload(
                "knowledge.txt", original)), USERNAME);

        var document = response.documents().getFirst();
        assertEquals("FAILED", document.convertStatus());
        assertEquals("知识文件转换失败", document.errorMessage());
        verify(fixture.mapper()).markConvertFailed(
                eq(documentUuid.toString()), anyString());
        verify(fixture.vectorStorage(), never()).vectorizeDocument(any());
        assertPresent(fixture.storage(),
                USERNAME + "/knowledge.txt/original/"
                        + documentUuid + "_knowledge.txt");
        assertMissing(fixture.storage(),
                USERNAME + "/knowledge.txt/converted/"
                        + documentUuid + "_knowledge.md");
    }

    @Test
    void shouldKeepKnowledgeFileVectorizedWhenConversionHistoryFails() {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        byte[] original = "原始知识".getBytes(StandardCharsets.UTF_8);
        byte[] markdown = "# 已转换".getBytes(StandardCharsets.UTF_8);
        when(fixture.toMarkdownClient().convert(
                any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    ((java.io.InputStream) invocation.getArgument(0)).readAllBytes();
                    return new PythonMarkdownResponse(
                            markdown, "knowledge.md", "text/markdown");
                });
        when(fixture.vectorStorage().vectorizeDocument(any(
                PreparedKnowledgeDocument.class))).thenReturn(3);
        AtomicInteger historyCallCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (historyCallCount.incrementAndGet() == 2) {
                throw new IllegalStateException("history unavailable");
            }
            return null;
        }).when(fixture.operationHistoryService()).recordKnowledgeFile(
                anyString(), any(), any(), any(), any());

        var response = fixture.service().ingest(List.of(upload(
                "knowledge.txt", original)), USERNAME);

        var document = response.documents().getFirst();
        assertEquals("CONVERTED", document.convertStatus());
        assertEquals("VECTORIZED", document.indexStatus());
        verify(fixture.mapper(), never()).markConvertFailed(
                eq(documentUuid.toString()), anyString());
    }

    @Test
    void shouldRetryVectorizationFromStoredConvertedObject() throws Exception {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        String convertedKey = USERNAME + "/knowledge.txt/converted/"
                + documentUuid + "_knowledge.md";
        byte[] markdown = "# 已转换".getBytes(StandardCharsets.UTF_8);
        fixture.objectService().storeAndVerify(
                convertedKey,
                "text/markdown; charset=UTF-8",
                markdown.length,
                Map.of("document-uuid", documentUuid.toString()),
                () -> new ByteArrayInputStream(markdown));

        KnowledgeFileRecord initial = record(
                documentUuid.toString(),
                "knowledge.txt",
                "CONVERTED",
                "FAILED");
        initial.setOriginalFilePath(
                USERNAME + "/knowledge.txt/original/"
                        + documentUuid + "_knowledge.txt");
        initial.setMarkdownFilePath(convertedKey);
        initial.setFileSize((long) markdown.length);
        initial.setContentType("text/plain");
        initial.setConvertedSha256(sha256(markdown));
        KnowledgeFileRecord finalRecord = record(
                documentUuid.toString(),
                "knowledge.txt",
                "CONVERTED",
                "VECTORIZED");
        finalRecord.setChunkCount(5);
        when(fixture.mapper().selectByDocumentUuid(documentUuid.toString()))
                .thenReturn(initial, finalRecord);
        when(fixture.vectorStorage().getVectorStatus(
                documentUuid.toString())).thenReturn(0);
        when(fixture.vectorStorage().vectorizeDocument(any(
                PreparedKnowledgeDocument.class))).thenReturn(5);

        KnowledgeFileRecord result =
                fixture.service().retry(documentUuid.toString());

        assertEquals("VECTORIZED", result.getIndexStatus());
        assertEquals(5, result.getChunkCount());
        verify(fixture.mapper()).markVectorizing(documentUuid.toString());
        verify(fixture.mapper()).markVectorized(
                eq(documentUuid.toString()), eq(5), any());
    }

    @Test
    void shouldRejectTamperedMarkdownBeforeRetryVectorization() throws Exception {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        String convertedKey = USERNAME + "/knowledge.txt/converted/"
                + documentUuid + "_knowledge.md";
        byte[] markdown = "# 被篡改内容".getBytes(StandardCharsets.UTF_8);
        fixture.objectService().storeAndVerify(
                convertedKey,
                "text/markdown; charset=UTF-8",
                markdown.length,
                Map.of("document-uuid", documentUuid.toString()),
                () -> new ByteArrayInputStream(markdown));

        KnowledgeFileRecord record = record(
                documentUuid.toString(), "knowledge.txt", "CONVERTED", "FAILED");
        record.setOriginalFilePath(
                USERNAME + "/knowledge.txt/original/"
                        + documentUuid + "_knowledge.txt");
        record.setMarkdownFilePath(convertedKey);
        record.setConvertedSha256("0".repeat(64));
        when(fixture.mapper().selectByDocumentUuid(documentUuid.toString()))
                .thenReturn(record);
        when(fixture.vectorStorage().getVectorStatus(documentUuid.toString()))
                .thenReturn(0);

        fixture.service().retry(documentUuid.toString());

        verify(fixture.mapper()).markVectorizeFailed(
                eq(documentUuid.toString()), anyString());
        verify(fixture.mapper(), never()).markConvertFailed(
                eq(documentUuid.toString()), anyString());
        verify(fixture.vectorStorage(), never()).vectorizeDocument(any());
    }

    @Test
    void shouldDeleteVectorsObjectsAndSoftDeleteRecord() {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        KnowledgeFileRecord record = record(
                documentUuid.toString(),
                "knowledge.txt",
                "CONVERTED",
                "VECTORIZED");
        record.setOriginalFilePath(
                USERNAME + "/knowledge.txt/original/"
                        + documentUuid + "_knowledge.txt");
        record.setMarkdownFilePath(
                USERNAME + "/knowledge.txt/converted/"
                        + documentUuid + "_knowledge.md");
        when(fixture.mapper().selectByDocumentUuidIncludingDeleted(
                documentUuid.toString()))
                .thenReturn(record);
        when(fixture.mapper().beginDeletion(
                eq(documentUuid.toString()), any(), any())).thenReturn(1);

        fixture.service().delete(documentUuid.toString());

        verify(fixture.vectorStorage()).deleteDocument(
                documentUuid.toString());
        assertMissing(fixture.storage(), record.getOriginalFilePath());
        assertMissing(fixture.storage(), record.getMarkdownFilePath());
        verify(fixture.mapper()).markDeleted(
                eq(documentUuid.toString()), any(LocalDateTime.class));
        verify(fixture.mapper()).markDeletionSucceeded(
                documentUuid.toString());
        verify(fixture.operationHistoryService()).recordKnowledgeFile(
                eq(documentUuid.toString()),
                eq(io.gitee.lujianshi.bidwinningbackend.enums.OperationType.DELETE),
                any(),
                isNull(),
                isNull());
    }

    @Test
    void shouldMarkDeletionFailedAndKeepRecordActiveWhenVectorDeleteFails() {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        KnowledgeFileRecord record = record(
                documentUuid.toString(),
                "knowledge.txt",
                "CONVERTED",
                "VECTORIZED");
        when(fixture.mapper().selectByDocumentUuidIncludingDeleted(
                documentUuid.toString()))
                .thenReturn(record);
        when(fixture.mapper().beginDeletion(
                eq(documentUuid.toString()), any(), any())).thenReturn(1);
        doThrow(new AppException(
                        AppErrorCode.KNOWLEDGE_FILE_DELETE_FAILED,
                        "knowledge-file",
                        "DELETE",
                        "DELETE_VECTORS",
                        "向量删除失败"))
                .when(fixture.vectorStorage())
                .deleteDocument(documentUuid.toString());

        AppException failure = assertThrows(
                AppException.class,
                () -> fixture.service().delete(documentUuid.toString()));

        assertEquals("DELETE_VECTORS", failure.getStage());
        verify(fixture.mapper()).markDeletionFailed(
                eq(documentUuid.toString()),
                eq("DELETE_VECTORS"),
                anyString());
        verify(fixture.mapper(), never()).markDeleted(
                eq(documentUuid.toString()), any());
    }

    @Test
    void shouldCompleteHistoryAndMarkDeletedWhenAlreadySoftDeleted() {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        KnowledgeFileRecord record = record(
                documentUuid.toString(),
                "knowledge.txt",
                "CONVERTED",
                "VECTORIZED");
        record.setDeletedAt(LocalDateTime.now());
        record.setDeletionStatus("DELETING");
        when(fixture.mapper().selectByDocumentUuidIncludingDeleted(
                documentUuid.toString()))
                .thenReturn(record);

        fixture.service().delete(documentUuid.toString());

        verify(fixture.operationHistoryService()).recordKnowledgeFile(
                eq(documentUuid.toString()),
                eq(io.gitee.lujianshi.bidwinningbackend.enums.OperationType.DELETE),
                any(),
                isNull(),
                anyString());
        verify(fixture.mapper()).markDeletionSucceeded(
                documentUuid.toString());
        verify(fixture.vectorStorage(), never()).deleteDocument(
                documentUuid.toString());
    }

    @Test
    void shouldReturnIdempotentlyWhenAlreadyDeleted() {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        KnowledgeFileRecord record = record(
                documentUuid.toString(),
                "knowledge.txt",
                "CONVERTED",
                "VECTORIZED");
        record.setDeletedAt(LocalDateTime.now());
        record.setDeletionStatus("DELETED");
        when(fixture.mapper().selectByDocumentUuidIncludingDeleted(
                documentUuid.toString()))
                .thenReturn(record);

        fixture.service().delete(documentUuid.toString());

        verify(fixture.vectorStorage(), never()).deleteDocument(
                documentUuid.toString());
        verify(fixture.mapper(), never()).markDeleted(
                eq(documentUuid.toString()), any());
    }

    @Test
    void shouldRejectDeleteWhenAnotherRequestIsDeleting() {
        UUID documentUuid = UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440000");
        TestFixture fixture = fixture(documentUuid);
        KnowledgeFileRecord record = record(
                documentUuid.toString(),
                "knowledge.txt",
                "CONVERTED",
                "VECTORIZED");
        when(fixture.mapper().selectByDocumentUuidIncludingDeleted(
                documentUuid.toString()))
                .thenReturn(record);
        when(fixture.mapper().beginDeletion(
                eq(documentUuid.toString()), any(), any())).thenReturn(0);
        KnowledgeFileRecord latest = record(
                documentUuid.toString(),
                "knowledge.txt",
                "CONVERTED",
                "VECTORIZED");
        latest.setDeletionStatus("DELETING");
        latest.setDeletionStartedAt(LocalDateTime.now());
        when(fixture.mapper().selectByDocumentUuidIncludingDeleted(
                documentUuid.toString()))
                .thenReturn(record, latest);

        AppException failure = assertThrows(
                AppException.class,
                () -> fixture.service().delete(documentUuid.toString()));

        assertEquals(
                AppErrorCode.KNOWLEDGE_FILE_DELETE_IN_PROGRESS,
                failure.getErrorCode());
        verify(fixture.vectorStorage(), never()).deleteDocument(
                documentUuid.toString());
    }

    // ==================== fixture ====================

    private TestFixture fixture(UUID... uuids) {
        KnowledgeFileStorageProperties storageProperties =
                new KnowledgeFileStorageProperties();
        storageProperties.setType(FileStorageType.LOCAL);
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot.toString());
        KnowledgeFileObjectService objectService = new KnowledgeFileObjectService(
                storage, new Sha256FileChecksumCalculator());
        ToMarkdownClient toMarkdownClient = mock(ToMarkdownClient.class);
        KnowledgeFileVectorStorage vectorStorage =
                mock(KnowledgeFileVectorStorage.class);
        KnowledgeFileMapper mapper = mock(KnowledgeFileMapper.class);
        OperationHistoryService operationHistoryService =
                mock(OperationHistoryService.class);
        FileStatusEventPublisher fileStatusEventPublisher =
                mock(FileStatusEventPublisher.class);
        DocumentUuidGenerator uuidGenerator = mock(DocumentUuidGenerator.class);
        AtomicInteger uuidIndex = new AtomicInteger();
        when(uuidGenerator.generate()).thenAnswer(
                ignored -> uuids[uuidIndex.getAndIncrement()]);

        AtomicReference<KnowledgeFileRecord> persisted = new AtomicReference<>();
        when(mapper.insert(any(KnowledgeFileRecord.class))).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.updateOriginalMetadata(
                anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
                    KnowledgeFileRecord record = persisted.get();
                    if (record != null) {
                        record.setOriginalFilePath(invocation.getArgument(1));
                        record.setOriginalSha256(invocation.getArgument(2));
                        record.setStorageType(invocation.getArgument(3));
                        record.setOriginalStorageStatus("STORED");
                    }
                    return 1;
                });
        when(mapper.selectByDocumentUuid(anyString())).thenAnswer(
                invocation -> persisted.get());
        when(mapper.markConverting(anyString())).thenAnswer(invocation -> {
            if (persisted.get() != null) {
                persisted.get().setConvertStatus("CONVERTING");
            }
            return 1;
        });
        when(mapper.reserveMarkdownPath(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (persisted.get() != null) {
                        persisted.get().setMarkdownFilePath(
                                invocation.getArgument(1));
                    }
                    return 1;
                });
        when(mapper.markConverted(
                anyString(), anyString(), anyString(), any())).thenAnswer(invocation -> {
                    if (persisted.get() != null) {
                        persisted.get().setMarkdownFilePath(invocation.getArgument(1));
                        persisted.get().setConvertedSha256(invocation.getArgument(2));
                        persisted.get().setConvertStatus("CONVERTED");
                    }
                    return 1;
                });
        when(mapper.markVectorizing(anyString())).thenAnswer(invocation -> {
            if (persisted.get() != null) {
                persisted.get().setIndexStatus("VECTORIZING");
            }
            return 1;
        });
        when(mapper.markVectorized(anyString(), anyInt(), any())).thenAnswer(invocation -> {
            if (persisted.get() != null) {
                persisted.get().setChunkCount(invocation.getArgument(1));
                persisted.get().setIndexStatus("VECTORIZED");
            }
            return 1;
        });
        when(mapper.markConvertFailed(anyString(), anyString())).thenAnswer(invocation -> {
            if (persisted.get() != null) {
                persisted.get().setConvertStatus("FAILED");
                persisted.get().setErrorMessage(invocation.getArgument(1));
            }
            return 1;
        });
        when(mapper.markVectorizeFailed(anyString(), anyString())).thenAnswer(invocation -> {
            if (persisted.get() != null) {
                persisted.get().setIndexStatus("FAILED");
                persisted.get().setErrorMessage(invocation.getArgument(1));
            }
            return 1;
        });
        when(mapper.markDeleted(anyString(), any())).thenReturn(1);
        when(mapper.beginDeletion(anyString(), any(), any())).thenReturn(1);
        when(mapper.markDeletionFailed(
                anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.markDeletionSucceeded(anyString())).thenReturn(1);
        when(mapper.selectByDocumentUuidIncludingDeleted(anyString()))
                .thenReturn(null);

        KnowledgeFileIngestionService service = new KnowledgeFileIngestionService(
                uuidGenerator,
                new KnowledgeObjectKeyGenerator(),
                objectService,
                toMarkdownClient,
                vectorStorage,
                mapper,
                storageProperties);
        ReflectionTestUtils.setField(
                service, "operationHistoryService", operationHistoryService);
        ReflectionTestUtils.setField(
                service, "fileStatusEventPublisher", fileStatusEventPublisher);

        return new TestFixture(
                storage,
                objectService,
                toMarkdownClient,
                vectorStorage,
                mapper,
                service,
                operationHistoryService,
                fileStatusEventPublisher);
    }

    private KnowledgeFileUpload upload(String filename, byte[] content) {
        return new KnowledgeFileUpload(
                filename,
                "text/plain",
                content.length,
                () -> new ByteArrayInputStream(content));
    }

    private KnowledgeFileRecord record(
            String documentUuid,
            String filename,
            String convertStatus,
            String indexStatus) {
        KnowledgeFileRecord record = new KnowledgeFileRecord();
        record.setDocumentUuid(documentUuid);
        record.setOriginalFilename(filename);
        record.setConvertStatus(convertStatus);
        record.setIndexStatus(indexStatus);
        return record;
    }

    private String sha256(byte[] content) throws IOException {
        return new Sha256FileChecksumCalculator().calculate(
                new ByteArrayInputStream(content));
    }

    private void assertPresent(LocalObjectStorage storage, String objectKey) {
        try (var input = storage.open(objectKey)) {
            assertTrue(input.readAllBytes().length > 0);
        } catch (IOException failure) {
            throw new AssertionError("对象应存在: " + objectKey, failure);
        }
    }

    private void assertMissing(LocalObjectStorage storage, String objectKey) {
        assertThrows(IOException.class, () -> storage.open(objectKey));
    }

    private record TestFixture(
            LocalObjectStorage storage,
            KnowledgeFileObjectService objectService,
            ToMarkdownClient toMarkdownClient,
            KnowledgeFileVectorStorage vectorStorage,
            KnowledgeFileMapper mapper,
            KnowledgeFileIngestionService service,
            OperationHistoryService operationHistoryService,
            FileStatusEventPublisher fileStatusEventPublisher) {
    }
}
