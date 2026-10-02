package com.pharmacy.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationPathService {

    private final StorageLocationRepository locations;

    @Transactional(readOnly = true)
    public Map<Long, String> paths() {
        List<StorageLocation> all = locations.findAll();
        Map<Long, StorageLocation> byId = new HashMap<>();
        for (StorageLocation location : all) {
            byId.put(location.getId(), location);
        }
        Map<Long, String> paths = new HashMap<>();
        for (StorageLocation location : all) {
            paths.put(location.getId(), build(location, byId));
        }
        return paths;
    }

    public String of(StorageLocation location, Map<Long, String> paths) {
        if (location == null || paths == null) {
            return null;
        }
        return paths.get(location.getId());
    }

    private String build(StorageLocation location, Map<Long, StorageLocation> byId) {
        ArrayDeque<String> parts = new ArrayDeque<>();
        StorageLocation current = location;
        int guard = 0;
        while (current != null && guard++ < 8) {
            parts.addFirst(current.getCode());
            Long parentId = current.getParent() == null ? null : current.getParent().getId();
            current = parentId == null ? null : byId.get(parentId);
        }
        return String.join(" / ", parts);
    }
}
