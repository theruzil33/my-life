package com.theruzil.media.mediaitem;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MediaItemRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private MediaItemRepository mediaItemRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = randomUserId();
    }

    @Test
    void findByUserIdOrderByIdAscReturnsOnlyOwnItems() {
        MediaItem first = save(userId, MediaKind.FILM, "Дюна");
        MediaItem second = save(userId, MediaKind.BOOK, "Солярис");
        save(randomUserId(), MediaKind.GAME, "Чужая");

        List<MediaItem> items = mediaItemRepository.findByUserIdOrderByIdAsc(userId);

        assertThat(items).extracting(MediaItem::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void findByUserIdAndTypeReturnsOnlyRequestedTypeInIdOrder() {
        MediaItem book = save(userId, MediaKind.BOOK, "Солярис");
        save(userId, MediaKind.FILM, "Дюна");
        MediaItem otherBook = save(userId, MediaKind.BOOK, "Пикник на обочине");

        List<MediaItem> books = mediaItemRepository.findByUserIdAndTypeOrderByIdAsc(userId, MediaKind.BOOK);

        assertThat(books).extracting(MediaItem::getId).containsExactly(book.getId(), otherBook.getId());
    }

    @Test
    void findByIdAndUserIdDoesNotReturnForeignItem() {
        MediaItem item = save(userId, MediaKind.FILM, "Дюна");

        assertThat(mediaItemRepository.findByIdAndUserId(item.getId(), userId)).contains(item);
        assertThat(mediaItemRepository.findByIdAndUserId(item.getId(), randomUserId())).isEmpty();
    }

    @Test
    void persistsDescriptionIncludingAbsence() {
        MediaItem withDescription = mediaItemRepository.save(
                new MediaItem(userId, MediaKind.FILM, "Дюна", "Экранизация романа", MediaStatus.IN_PROGRESS, NOW));
        MediaItem withoutDescription = mediaItemRepository
                .save(new MediaItem(userId, MediaKind.GAME, "Hades", null, MediaStatus.NOT_STARTED, NOW));

        assertThat(mediaItemRepository.findByIdAndUserId(withDescription.getId(), userId)).get()
                .extracting(MediaItem::getDescription).isEqualTo("Экранизация романа");
        assertThat(mediaItemRepository.findByIdAndUserId(withoutDescription.getId(), userId)).get()
                .extracting(MediaItem::getDescription).isNull();
    }

    @Test
    void persistsTypeAndStatus() {
        MediaItem item = save(userId, MediaKind.GAME, "Hades");

        assertThat(mediaItemRepository.findByIdAndUserId(item.getId(), userId)).get()
                .extracting(MediaItem::getType, MediaItem::getStatus)
                .containsExactly(MediaKind.GAME, MediaStatus.NOT_STARTED);
    }

    private MediaItem save(Long ownerId, MediaKind type, String title) {
        return mediaItemRepository.save(new MediaItem(ownerId, type, title, null, MediaStatus.NOT_STARTED, NOW));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
