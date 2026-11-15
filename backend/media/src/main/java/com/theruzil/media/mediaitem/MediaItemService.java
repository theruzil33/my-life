package com.theruzil.media.mediaitem;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.media.mediaitem.dto.MediaItemRequest;
import com.theruzil.media.mediaitem.dto.MediaItemResponse;
import com.theruzil.media.mediaitem.dto.MediaItemUpdateRequest;

@Service
public class MediaItemService {

    private static final int MAX_TITLE_LENGTH = 200;

    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private static final MediaStatus DEFAULT_STATUS = MediaStatus.NOT_STARTED;

    private final MediaItemRepository mediaItemRepository;

    public MediaItemService(MediaItemRepository mediaItemRepository) {
        this.mediaItemRepository = mediaItemRepository;
    }

    @Transactional
    public MediaItemResponse create(Long userId, MediaItemRequest request) {
        if (request == null) {
            throw new InvalidMediaDataException("Media item data is required");
        }
        MediaKind type = parseType(request.type());
        String title = normalizeTitle(request.title());
        String description = normalizeDescription(request.description());
        MediaStatus status = parseStatus(request.status(), DEFAULT_STATUS);
        MediaItem item = new MediaItem(userId, type, title, description, status, Instant.now());
        return toResponse(mediaItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<MediaItemResponse> list(Long userId, String type) {
        List<MediaItem> items = (type == null || type.isBlank())
                ? mediaItemRepository.findByUserIdOrderByIdAsc(userId)
                : mediaItemRepository.findByUserIdAndTypeOrderByIdAsc(userId, parseType(type));
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MediaItemResponse update(Long userId, Long itemId, MediaItemUpdateRequest request) {
        if (request == null) {
            throw new InvalidMediaDataException("Media item data is required");
        }
        String title = normalizeTitle(request.title());
        String description = normalizeDescription(request.description());
        MediaStatus status = parseStatus(request.status(), null);
        MediaItem item = requireItem(userId, itemId);
        item.edit(title, description, status, Instant.now());
        return toResponse(item);
    }

    @Transactional
    public void delete(Long userId, Long itemId) {
        mediaItemRepository.delete(requireItem(userId, itemId));
    }

    private MediaItem requireItem(Long userId, Long itemId) {
        return mediaItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new MediaItemNotFoundException("Media item not found"));
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidMediaDataException("Media item title must not be blank");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidMediaDataException(
                    "Media item title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidMediaDataException(
                    "Media item description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    private MediaKind parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new InvalidMediaDataException("Media item type is required");
        }
        try {
            return MediaKind.valueOf(type.trim());
        }
        catch (IllegalArgumentException ex) {
            throw new InvalidMediaDataException("Media item type must be FILM, BOOK or GAME");
        }
    }

    private MediaStatus parseStatus(String status, MediaStatus defaultStatus) {
        if (status == null || status.isBlank()) {
            if (defaultStatus == null) {
                throw new InvalidMediaDataException("Media item status is required");
            }
            return defaultStatus;
        }
        try {
            return MediaStatus.valueOf(status.trim());
        }
        catch (IllegalArgumentException ex) {
            throw new InvalidMediaDataException(
                    "Media item status must be NOT_STARTED, IN_PROGRESS or COMPLETED");
        }
    }

    private MediaItemResponse toResponse(MediaItem item) {
        return new MediaItemResponse(item.getId(), item.getType(), item.getTitle(), item.getDescription(),
                item.getStatus());
    }
}
