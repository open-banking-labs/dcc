package cn.org.openbanking.dcc.web.announcement;

import java.util.List;

import cn.org.openbanking.dcc.application.announcement.AnnouncementService;
import cn.org.openbanking.dcc.application.announcement.AnnouncementService.AnnouncementView;
import cn.org.openbanking.dcc.application.announcement.AnnouncementService.CreateCommand;
import cn.org.openbanking.dcc.application.announcement.AnnouncementService.UpdateCommand;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

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
 * Tenant-scoped announcements: the banner feed plus authoring the caller's own notices.
 *
 * <p>Reading takes no permission point beyond authentication — see
 * {@link AnnouncementService}. Authoring takes {@code announcement.write}, and the owning tenant is
 * always {@code currentTenantId()}: it is never read from the body, so a tenant administrator
 * cannot publish into someone else's tenant.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController extends AbstractTenantController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    /** The banner: platform announcements plus this tenant's, enabled and inside their window. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AnnouncementView> visible() {
        return service.visible(currentTenantId());
    }

    /** This tenant's announcements, including disabled and out-of-window ones. */
    @GetMapping("/managed")
    @PreAuthorize("hasAuthority(@perms.announcementWrite)")
    public List<AnnouncementView> managed() {
        return service.list(currentTenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(@perms.announcementWrite)")
    public AnnouncementView create(@Valid @RequestBody CreateCommand command) {
        return service.create(currentTenantId(), command);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(@perms.announcementWrite)")
    public AnnouncementView update(@PathVariable Long id, @Valid @RequestBody UpdateCommand command) {
        return service.update(currentTenantId(), id, command);
    }

    /** Logical delete; a platform announcement is simply not found here (404, not 403). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(@perms.announcementWrite)")
    public void delete(@PathVariable Long id) {
        service.delete(currentTenantId(), id);
    }
}
