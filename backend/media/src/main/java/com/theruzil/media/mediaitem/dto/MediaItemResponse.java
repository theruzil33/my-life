package com.theruzil.media.mediaitem.dto;

import com.theruzil.media.mediaitem.MediaStatus;
import com.theruzil.media.mediaitem.MediaKind;

public record MediaItemResponse(Long id, MediaKind type, String title, String description, MediaStatus status) {
}
