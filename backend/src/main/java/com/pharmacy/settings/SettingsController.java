package com.pharmacy.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/display")
    @PreAuthorize("isAuthenticated()")
    public SettingsService.DisplayProfile display() {
        return settingsService.display();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public List<SettingsService.SettingResponse> list() {
        return settingsService.list();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public List<SettingsService.SettingResponse> update(@Valid @RequestBody SettingsUpdateRequest request) {
        return settingsService.update(request.values());
    }

    public record SettingsUpdateRequest(@NotEmpty Map<String, String> values) {
    }
}
