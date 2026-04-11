package com.ecm.core.cmis;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import com.ecm.core.service.CheckOutCheckInService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CmisContentVersioningService {

    private final NodeService nodeService;
    private final ContentService contentService;
    private final VersionService versionService;
    private final CheckOutCheckInService checkOutCheckInService;
    private final CmisObjectFactory objectFactory;

    @Transactional(readOnly = true)
    public ContentStreamResponse getContentStream(String objectId, String path) throws IOException {
        Document document = resolveDocument(objectId, path);
        if (document.getContentId() == null || document.getContentId().isBlank()) {
            throw new IllegalArgumentException("Document has no content stream: " + document.getId());
        }
        return new ContentStreamResponse(
            contentService.getContent(document.getContentId()),
            document.getMimeType() != null && !document.getMimeType().isBlank()
                ? document.getMimeType()
                : "application/octet-stream",
            document.getName(),
            document.getFileSize()
        );
    }

    public CmisModels.MutationResponse setContentStream(CmisModels.MutationRequest request) throws IOException {
        Document document = resolveDocument(request.objectId(), request.path());
        byte[] content = requireContent(request.contentBase64());
        String filename = effectiveFilename(request, document);
        boolean majorVersion = Boolean.TRUE.equals(request.majorVersion());

        versionService.createVersion(
            document.getId(),
            new ByteArrayInputStream(content),
            filename,
            blankToNull(request.comment()),
            majorVersion
        );

        Document refreshed = resolveDocument(document.getId().toString(), null);
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "setContentStream",
            objectFactory.fromNode(refreshed),
            null,
            "Content stream updated"
        );
    }

    public CmisModels.MutationResponse checkOut(CmisModels.MutationRequest request) {
        Document document = resolveDocument(request.objectId(), request.path());
        Document checkedOut = nodeService.checkoutDocument(document.getId());
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "checkOut",
            objectFactory.fromNode(checkedOut),
            null,
            "Document checked out"
        );
    }

    public CmisModels.MutationResponse checkIn(CmisModels.MutationRequest request) throws IOException {
        Document document = resolveDocument(request.objectId(), request.path());
        if (request.contentBase64() != null && !request.contentBase64().isBlank()) {
            byte[] content = requireContent(request.contentBase64());
            String filename = effectiveFilename(request, document);
            Version ignored = versionService.createVersion(
                document.getId(),
                new ByteArrayInputStream(content),
                filename,
                blankToNull(request.comment()),
                Boolean.TRUE.equals(request.majorVersion())
            );
        }
        Document checkedIn = nodeService.checkinDocument(document.getId(), Boolean.TRUE.equals(request.keepCheckedOut()));
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "checkIn",
            objectFactory.fromNode(checkedIn),
            null,
            Boolean.TRUE.equals(request.keepCheckedOut()) ? "Document checked in and kept checked out" : "Document checked in"
        );
    }

    public CmisModels.MutationResponse cancelCheckOut(CmisModels.MutationRequest request) {
        Document document = resolveDocument(request.objectId(), request.path());
        Document canceled = nodeService.cancelCheckoutDocument(document.getId());
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "cancelCheckOut",
            objectFactory.fromNode(canceled),
            null,
            "Checkout canceled"
        );
    }

    public CmisModels.MutationResponse checkOutWorkingCopy(CmisModels.MutationRequest request) {
        Document document = resolveDocument(request.objectId(), request.path());
        Document workingCopy = checkOutCheckInService.checkout(document.getId());
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "checkOut",
            objectFactory.fromNode(workingCopy),
            null,
            "Checked out"
        );
    }

    public CmisModels.MutationResponse checkInWorkingCopy(CmisModels.MutationRequest request) throws IOException {
        Document workingCopy = resolveDocument(request.objectId(), request.path());
        if (!workingCopy.isWorkingCopy()) {
            throw new IllegalArgumentException("Node is not a working copy: " + workingCopy.getId());
        }

        if (request.contentBase64() != null && !request.contentBase64().isBlank()) {
            byte[] content = requireContent(request.contentBase64());
            String filename = effectiveFilename(request, workingCopy);
            versionService.createVersion(
                workingCopy.getWorkingCopyOf(),
                new ByteArrayInputStream(content),
                filename,
                blankToNull(request.comment()),
                Boolean.TRUE.equals(request.majorVersion())
            );
        }

        Document original = checkOutCheckInService.checkin(workingCopy.getId(), Boolean.TRUE.equals(request.keepCheckedOut()));
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "checkIn",
            objectFactory.fromNode(original),
            null,
            Boolean.TRUE.equals(request.keepCheckedOut()) ? "Checked in and kept checked out" : "Checked in"
        );
    }

    public CmisModels.MutationResponse cancelWorkingCopyCheckout(CmisModels.MutationRequest request) {
        Document document = resolveDocument(request.objectId(), request.path());
        Document original = checkOutCheckInService.cancelCheckout(document.getId());
        return new CmisModels.MutationResponse(
            objectFactory.getRepositoryId(),
            "cancelCheckOut",
            objectFactory.fromNode(original),
            null,
            "Checkout cancelled"
        );
    }

    private Document resolveDocument(String objectId, String path) {
        Node node;
        if (path != null && !path.isBlank()) {
            node = nodeService.getNodeByPath(path.trim());
        } else if (objectId != null && !objectId.isBlank() && !CmisObjectFactory.ROOT_OBJECT_ID.equalsIgnoreCase(objectId.trim())) {
            node = nodeService.getNode(UUID.fromString(objectId.trim()));
        } else {
            throw new IllegalArgumentException("A non-root document objectId or path is required");
        }

        if (!(node instanceof Document document)) {
            throw new IllegalArgumentException("Node is not a document: " + node.getId());
        }
        return document;
    }

    private byte[] requireContent(String contentBase64) {
        if (contentBase64 == null || contentBase64.isBlank()) {
            throw new IllegalArgumentException("contentBase64 is required");
        }
        try {
            return Base64.getDecoder().decode(contentBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("contentBase64 must be valid Base64", ex);
        }
    }

    private String effectiveFilename(CmisModels.MutationRequest request, Document document) {
        String filename = request.filename() != null && !request.filename().isBlank()
            ? request.filename().trim()
            : document.getName();
        if (filename == null || filename.isBlank()) {
            throw new NoSuchElementException("Document filename could not be resolved");
        }
        return filename;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ContentStreamResponse(
        InputStream stream,
        String mimeType,
        String filename,
        Long contentLength
    ) {
    }
}
