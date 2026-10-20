package cn.org.openbanking.dcc.web.announcement;

import java.util.List;

import cn.org.openbanking.dcc.application.announcement.AnnouncementService;
import cn.org.openbanking.dcc.application.announcement.AnnouncementService.AnnouncementView;
import cn.org.openbanking.dcc.application.announcement.AnnouncementService.CreateCommand;
import cn.org.openbanking.dcc.application.announcement.AnnouncementService.UpdateCommand;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-wide announcements (ADR 0005 §D1 pattern: a platform plane expressed as a permission
 * point, at the {@code /api/platform/*} prefix the audit trail already uses).
 *
 * <p>The scope is the literal {@code null} passed to the service, not a request parameter: these
 * endpoints only ever see rows with {@code tenant_id is null}, and a tenant announcement is
 * unreachable from here — 404 rather than 403, so the endpoint never confirms what other tenants
 * have published.
 */
@RestController
@RequestMapping("/api/platform/announcements")
public class PlatformAnnouncementController {

    private final AnnouncementService service;

    public PlatformAnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(@perms.announcementWritePlatform)")
    public List<AnnouncementView> list() {
        return service.list(null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(@perms.announcementWritePlatform)")
    public AnnouncementView create(@Valid @RequestBody CreateCommand command) {
        return service.create(null, command);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(@perms.announcementWritePlatform)")
    public AnnouncementView update(@PathVariable Long id, @Valid @RequestBody UpdateCommand command) {
        return service.update(null, id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(@perms.announcementWritePlatform)")
    public void delete(@PathVariable Long id) {
        service.delete(null, id);
    }
}
