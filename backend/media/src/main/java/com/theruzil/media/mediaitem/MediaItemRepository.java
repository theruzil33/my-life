package com.theruzil.media.mediaitem;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findByIdAndUserId(Long id, Long userId);

    List<MediaItem> findByUserIdOrderByIdAsc(Long userId);

    List<MediaItem> findByUserIdAndTypeOrderByIdAsc(Long userId, MediaKind type);
}
