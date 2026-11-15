package com.theruzil.media.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.theruzil.media.mediaitem.MediaItemService;
import com.theruzil.media.mediaitem.dto.MediaItemRequest;
import com.theruzil.media.mediaitem.dto.MediaItemResponse;
import com.theruzil.media.mediaitem.dto.MediaItemUpdateRequest;

@RestController
@RequestMapping("/items")
public class MediaItemController {

    private final MediaItemService mediaItemService;

    public MediaItemController(MediaItemService mediaItemService) {
        this.mediaItemService = mediaItemService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MediaItemResponse create(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestBody MediaItemRequest request) {
        return mediaItemService.create(userId, request);
    }

    @GetMapping
    public List<MediaItemResponse> list(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestParam(required = false) String type) {
        return mediaItemService.list(userId, type);
    }

    @PutMapping("/{id}")
    public MediaItemResponse update(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId, @PathVariable Long id,
            @RequestBody MediaItemUpdateRequest request) {
        return mediaItemService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId, @PathVariable Long id) {
        mediaItemService.delete(userId, id);
    }
}
