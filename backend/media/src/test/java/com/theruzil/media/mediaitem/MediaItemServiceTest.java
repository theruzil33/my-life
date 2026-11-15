package com.theruzil.media.mediaitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.theruzil.media.mediaitem.dto.MediaItemRequest;
import com.theruzil.media.mediaitem.dto.MediaItemResponse;
import com.theruzil.media.mediaitem.dto.MediaItemUpdateRequest;

@ExtendWith(MockitoExtension.class)
class MediaItemServiceTest {

    private static final Long USER_ID = 10L;

    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Mock
    private MediaItemRepository mediaItemRepository;

    private MediaItemService mediaItemService;

    @BeforeEach
    void setUp() {
        mediaItemService = new MediaItemService(mediaItemRepository);
    }

    @Test
    void createSavesItemForUserAndReturnsResponse() {
        when(mediaItemRepository.save(any(MediaItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        MediaItemResponse response = mediaItemService.create(USER_ID,
                new MediaItemRequest("FILM", "Дюна", "Экранизация романа", "IN_PROGRESS"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo(MediaKind.FILM);
        assertThat(response.title()).isEqualTo("Дюна");
        assertThat(response.description()).isEqualTo("Экранизация романа");
        assertThat(response.status()).isEqualTo(MediaStatus.IN_PROGRESS);

        MediaItem saved = savedItem();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void createDefaultsStatusToNotStarted() {
        when(mediaItemRepository.save(any(MediaItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 2L));

        MediaItemResponse response = mediaItemService.create(USER_ID, new MediaItemRequest("BOOK", "Солярис", null, null));

        assertThat(response.status()).isEqualTo(MediaStatus.NOT_STARTED);
        assertThat(response.description()).isNull();
    }

    @Test
    void createAcceptsEveryType() {
        when(mediaItemRepository.save(any(MediaItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 3L));

        assertThat(mediaItemService.create(USER_ID, new MediaItemRequest("FILM", "A", null, null)).type())
                .isEqualTo(MediaKind.FILM);
        assertThat(mediaItemService.create(USER_ID, new MediaItemRequest("BOOK", "B", null, null)).type())
                .isEqualTo(MediaKind.BOOK);
        assertThat(mediaItemService.create(USER_ID, new MediaItemRequest("GAME", "C", null, null)).type())
                .isEqualTo(MediaKind.GAME);
    }

    @Test
    void createTrimsTitleAndDescription() {
        when(mediaItemRepository.save(any(MediaItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 4L));

        MediaItemResponse response = mediaItemService.create(USER_ID,
                new MediaItemRequest("GAME", "  Hades  ", "  Рогалик  ", null));

        assertThat(response.title()).isEqualTo("Hades");
        assertThat(response.description()).isEqualTo("Рогалик");
    }

    @Test
    void createTreatsBlankDescriptionAsAbsent() {
        when(mediaItemRepository.save(any(MediaItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        MediaItemResponse response = mediaItemService.create(USER_ID,
                new MediaItemRequest("GAME", "Hades", "   ", null));

        assertThat(response.description()).isNull();
    }

    @Test
    void createRejectsBlankTitle() {
        assertThatThrownBy(() -> mediaItemService.create(USER_ID, new MediaItemRequest("FILM", "   ", null, null)))
                .isInstanceOf(InvalidMediaDataException.class);
        assertThatThrownBy(() -> mediaItemService.create(USER_ID, new MediaItemRequest("FILM", null, null, null)))
                .isInstanceOf(InvalidMediaDataException.class);
        verifyNoInteractions(mediaItemRepository);
    }

    @Test
    void createRejectsMissingType() {
        assertThatThrownBy(() -> mediaItemService.create(USER_ID, new MediaItemRequest(null, "Дюна", null, null)))
                .isInstanceOf(InvalidMediaDataException.class);
        assertThatThrownBy(() -> mediaItemService.create(USER_ID, new MediaItemRequest(" ", "Дюна", null, null)))
                .isInstanceOf(InvalidMediaDataException.class);
        verifyNoInteractions(mediaItemRepository);
    }

    @Test
    void createRejectsUnknownTypeAndStatus() {
        assertThatThrownBy(() -> mediaItemService.create(USER_ID, new MediaItemRequest("SERIES", "Дюна", null, null)))
                .isInstanceOf(InvalidMediaDataException.class);
        assertThatThrownBy(
                () -> mediaItemService.create(USER_ID, new MediaItemRequest("FILM", "Дюна", null, "DROPPED")))
                        .isInstanceOf(InvalidMediaDataException.class);
        verifyNoInteractions(mediaItemRepository);
    }

    @Test
    void createRejectsNullRequest() {
        assertThatThrownBy(() -> mediaItemService.create(USER_ID, null))
                .isInstanceOf(InvalidMediaDataException.class);
        verifyNoInteractions(mediaItemRepository);
    }

    @Test
    void createAcceptsTitleAndDescriptionAtLimit() {
        when(mediaItemRepository.save(any(MediaItem.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 6L));
        String title = "A".repeat(200);
        String description = "Б".repeat(2000);

        MediaItemResponse response = mediaItemService.create(USER_ID,
                new MediaItemRequest("BOOK", title, description, null));

        assertThat(response.title()).hasSize(200);
        assertThat(response.description()).hasSize(2000);
    }

    @Test
    void createRejectsTooLongTitleAndDescription() {
        assertThatThrownBy(() -> mediaItemService.create(USER_ID,
                new MediaItemRequest("BOOK", "A".repeat(201), null, null)))
                .isInstanceOf(InvalidMediaDataException.class);
        assertThatThrownBy(() -> mediaItemService.create(USER_ID,
                new MediaItemRequest("BOOK", "Книга", "Б".repeat(2001), null)))
                .isInstanceOf(InvalidMediaDataException.class);
        verifyNoInteractions(mediaItemRepository);
    }

    @Test
    void listWithoutTypeReturnsAllItemsOfUser() {
        when(mediaItemRepository.findByUserIdOrderByIdAsc(USER_ID)).thenReturn(List.of(
                itemWithId(1L, USER_ID, MediaKind.FILM, "Дюна", MediaStatus.COMPLETED),
                itemWithId(2L, USER_ID, MediaKind.BOOK, "Солярис", MediaStatus.IN_PROGRESS)));

        List<MediaItemResponse> items = mediaItemService.list(USER_ID, null);

        assertThat(items).extracting(MediaItemResponse::id, MediaItemResponse::type)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(1L, MediaKind.FILM),
                        org.assertj.core.api.Assertions.tuple(2L, MediaKind.BOOK));
    }

    @Test
    void listWithTypeReturnsOnlyThatType() {
        when(mediaItemRepository.findByUserIdAndTypeOrderByIdAsc(USER_ID, MediaKind.BOOK))
                .thenReturn(List.of(itemWithId(2L, USER_ID, MediaKind.BOOK, "Солярис", MediaStatus.IN_PROGRESS)));

        List<MediaItemResponse> items = mediaItemService.list(USER_ID, "BOOK");

        assertThat(items).extracting(MediaItemResponse::type).containsExactly(MediaKind.BOOK);
    }

    @Test
    void listReturnsEmptyListWhenNothingSaved() {
        when(mediaItemRepository.findByUserIdAndTypeOrderByIdAsc(USER_ID, MediaKind.GAME)).thenReturn(List.of());

        assertThat(mediaItemService.list(USER_ID, "GAME")).isEmpty();
    }

    @Test
    void listRejectsUnknownType() {
        assertThatThrownBy(() -> mediaItemService.list(USER_ID, "SERIES"))
                .isInstanceOf(InvalidMediaDataException.class);
        verifyNoInteractions(mediaItemRepository);
    }

    @Test
    void updateChangesTitleDescriptionAndStatusKeepingType() {
        MediaItem item = itemWithId(5L, USER_ID, MediaKind.FILM, "Дюна", MediaStatus.NOT_STARTED);
        when(mediaItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(item));

        MediaItemResponse response = mediaItemService.update(USER_ID, 5L,
                new MediaItemUpdateRequest("  Дюна: часть вторая  ", "  Сиквел  ", "COMPLETED"));

        assertThat(response.title()).isEqualTo("Дюна: часть вторая");
        assertThat(response.description()).isEqualTo("Сиквел");
        assertThat(response.status()).isEqualTo(MediaStatus.COMPLETED);
        assertThat(response.type()).isEqualTo(MediaKind.FILM);
        assertThat(item.getType()).isEqualTo(MediaKind.FILM);
    }

    @Test
    void updateCanClearDescription() {
        MediaItem item = itemWithId(5L, USER_ID, MediaKind.FILM, "Дюна", MediaStatus.IN_PROGRESS);
        when(mediaItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(item));

        MediaItemResponse response = mediaItemService.update(USER_ID, 5L,
                new MediaItemUpdateRequest("Дюна", "   ", "IN_PROGRESS"));

        assertThat(response.description()).isNull();
        assertThat(item.getDescription()).isNull();
    }

    @Test
    void updateRejectsMissingTitleOrStatus() {
        assertThatThrownBy(() -> mediaItemService.update(USER_ID, 5L,
                new MediaItemUpdateRequest("  ", null, "IN_PROGRESS")))
                .isInstanceOf(InvalidMediaDataException.class);
        assertThatThrownBy(() -> mediaItemService.update(USER_ID, 5L, new MediaItemUpdateRequest("Дюна", null, null)))
                .isInstanceOf(InvalidMediaDataException.class);
        assertThatThrownBy(() -> mediaItemService.update(USER_ID, 5L,
                new MediaItemUpdateRequest("Дюна", null, "DROPPED")))
                        .isInstanceOf(InvalidMediaDataException.class);
        verifyNoInteractions(mediaItemRepository);
    }

    @Test
    void updateRejectsUnknownOrForeignItem() {
        when(mediaItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaItemService.update(USER_ID, 5L,
                new MediaItemUpdateRequest("Дюна", null, "IN_PROGRESS")))
                .isInstanceOf(MediaItemNotFoundException.class);
    }

    @Test
    void deleteRemovesOwnItem() {
        MediaItem item = itemWithId(5L, USER_ID, MediaKind.FILM, "Дюна", MediaStatus.COMPLETED);
        when(mediaItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(item));

        mediaItemService.delete(USER_ID, 5L);

        verify(mediaItemRepository).delete(item);
    }

    @Test
    void deleteRejectsUnknownOrForeignItem() {
        when(mediaItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaItemService.delete(USER_ID, 5L))
                .isInstanceOf(MediaItemNotFoundException.class);
        verify(mediaItemRepository, never()).delete(any(MediaItem.class));
    }

    private MediaItem savedItem() {
        ArgumentCaptor<MediaItem> captor = ArgumentCaptor.forClass(MediaItem.class);
        verify(mediaItemRepository).save(captor.capture());
        return captor.getValue();
    }

    private static MediaItem itemWithId(Long id, Long userId, MediaKind type, String title, MediaStatus status) {
        MediaItem item = new MediaItem(userId, type, title, null, status, NOW);
        setId(item, id);
        return item;
    }

    private static MediaItem withId(MediaItem item, Long id) {
        setId(item, id);
        return item;
    }

    private static void setId(MediaItem item, Long id) {
        try {
            Field field = MediaItem.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(item, id);
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
