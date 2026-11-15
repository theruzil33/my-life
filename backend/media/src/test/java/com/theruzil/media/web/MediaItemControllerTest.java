package com.theruzil.media.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.theruzil.media.mediaitem.MediaItem;
import com.theruzil.media.mediaitem.MediaItemRepository;
import com.theruzil.media.mediaitem.MediaStatus;
import com.theruzil.media.mediaitem.MediaKind;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MediaItemControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaItemRepository mediaItemRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = randomUserId();
    }

    @Test
    void createsAndListsItemsForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/items").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FILM\",\"title\":\"Дюна\",\"description\":\"Экранизация романа\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("FILM"))
                .andExpect(jsonPath("$.title").value("Дюна"))
                .andExpect(jsonPath("$.description").value("Экранизация романа"))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));

        mockMvc.perform(get("/items").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Дюна"));
    }

    @Test
    void createsItemWithoutDescription() throws Exception {
        mockMvc.perform(post("/items").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"GAME\",\"title\":\"Hades\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void createsItemWithExplicitStatus() throws Exception {
        mockMvc.perform(post("/items").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"BOOK\",\"title\":\"Солярис\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void listsOnlyRequestedType() throws Exception {
        mediaItemRepository.save(new MediaItem(userId, MediaKind.FILM, "Дюна", null, MediaStatus.NOT_STARTED, NOW));
        mediaItemRepository.save(new MediaItem(userId, MediaKind.BOOK, "Солярис", null, MediaStatus.IN_PROGRESS, NOW));

        mockMvc.perform(get("/items").param("type", "BOOK").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("BOOK"))
                .andExpect(jsonPath("$[0].title").value("Солярис"));
    }

    @Test
    void returnsEmptyListWhenNothingSaved() throws Exception {
        mockMvc.perform(get("/items").param("type", "GAME").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updatesItemKeepingType() throws Exception {
        MediaItem item = mediaItemRepository
                .save(new MediaItem(userId, MediaKind.BOOK, "Солярис", null, MediaStatus.NOT_STARTED, NOW));

        mockMvc.perform(put("/items/" + item.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FILM\",\"title\":\"Солярис (роман)\",\"description\":\"Лем\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BOOK"))
                .andExpect(jsonPath("$.title").value("Солярис (роман)"))
                .andExpect(jsonPath("$.description").value("Лем"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void changesStatusBackToInProgress() throws Exception {
        MediaItem item = mediaItemRepository
                .save(new MediaItem(userId, MediaKind.GAME, "Hades", null, MediaStatus.COMPLETED, NOW));

        mockMvc.perform(put("/items/" + item.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hades\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void deletesItem() throws Exception {
        MediaItem item = mediaItemRepository
                .save(new MediaItem(userId, MediaKind.FILM, "Дюна", null, MediaStatus.NOT_STARTED, NOW));

        mockMvc.perform(delete("/items/" + item.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/items").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsDeletingAlreadyDeletedItem() throws Exception {
        mockMvc.perform(delete("/items/999999").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsBlankTitleAndMissingType() throws Exception {
        mockMvc.perform(post("/items").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FILM\",\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/items").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Дюна\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsUnknownTypeAndStatus() throws Exception {
        mockMvc.perform(post("/items").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SERIES\",\"title\":\"Дюна\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/items").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FILM\",\"title\":\"Дюна\",\"status\":\"DROPPED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/items").param("type", "SERIES").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooLongTitle() throws Exception {
        mockMvc.perform(post("/items").header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"BOOK\",\"title\":\"" + "A".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsEditingForeignItem() throws Exception {
        MediaItem foreign = mediaItemRepository
                .save(new MediaItem(randomUserId(), MediaKind.FILM, "Чужая", null, MediaStatus.NOT_STARTED, NOW));

        mockMvc.perform(put("/items/" + foreign.getId()).header(UserIdFilter.USER_ID_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Новая\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsDeletingForeignItem() throws Exception {
        MediaItem foreign = mediaItemRepository
                .save(new MediaItem(randomUserId(), MediaKind.FILM, "Чужая", null, MediaStatus.NOT_STARTED, NOW));

        mockMvc.perform(delete("/items/" + foreign.getId()).header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void doesNotListForeignItems() throws Exception {
        mediaItemRepository
                .save(new MediaItem(randomUserId(), MediaKind.GAME, "Чужая", null, MediaStatus.IN_PROGRESS, NOW));

        mockMvc.perform(get("/items").header(UserIdFilter.USER_ID_HEADER, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsRequestWithoutUserIdHeader() throws Exception {
        mockMvc.perform(get("/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("authentication required"));
    }

    private static Long randomUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000);
    }
}
