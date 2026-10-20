package cn.org.openbanking.dcc.application.announcement;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.core.announcement.Announcement;
import cn.org.openbanking.dcc.core.announcement.AnnouncementLevel;
import cn.org.openbanking.dcc.core.announcement.repository.AnnouncementRepository;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * System announcements (系统公告): the banner every signed-in console user sees.
 *
 * <p>One table, two scopes, and <b>one scope parameter</b>: every method takes {@code tenantId},
 * where {@code null} means "platform-wide". That single parameter is the whole authorization
 * story, which is why the exposure layer always passes {@code currentTenantId()} for the tenant
 * endpoints and a literal {@code null} for the platform ones — a caller never chooses its own
 * scope, and the two scopes are disjoint at the query level:
 * <ul>
 *   <li>{@code findByIdAndTenantIdIsNull} cannot load a tenant announcement,</li>
 *   <li>{@code findByTenantIdAndId} cannot load a platform one.</li>
 * </ul>
 * So "edit an announcement you do not own" is indistinguishable from "it does not exist" — a 404,
 * not a 403 that would confirm the row is out there.
 *
 * <p>Reading is deliberately unrestricted (any authenticated caller): a maintenance notice that
 * only some users may see fails at the one job a notice has.
 */
@Service
@Transactional
public class AnnouncementService {

    public record CreateCommand(
            @NotNull AnnouncementLevel level,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2000) String body,
            Instant startsAt,
            Instant endsAt) {
    }

    public record UpdateCommand(
            @NotNull AnnouncementLevel level,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2000) String body,
            Instant startsAt,
            Instant endsAt,
            Boolean enabled) {
    }

    /** @param tenantId {@code null} for a platform-wide announcement */
    public record AnnouncementView(Long id, String tenantId, AnnouncementLevel level, String title, String body,
            boolean enabled, Instant startsAt, Instant endsAt, Instant createdAt, Instant updatedAt) {
    }

    /** Urgent first; the enum's declaration order is the severity rank. */
    private static final Comparator<Announcement> BANNER_ORDER =
            Comparator.comparingInt((Announcement announcement) -> announcement.getLevel().ordinal())
                    .thenComparing(Comparator.comparing(Announcement::getId).reversed());

    private final AnnouncementRepository announcements;
    private final ChangeContext changeContext;
    private final TenantScope tenantScope;

    public AnnouncementService(AnnouncementRepository announcements, ChangeContext changeContext,
            TenantScope tenantScope) {
        this.announcements = announcements;
        this.changeContext = changeContext;
        this.tenantScope = tenantScope;
    }

    // ------------------------------------------------------------------ banner (everyone)

    /**
     * The banner payload: platform announcements plus the caller's own, enabled and inside their
     * visibility window, most urgent first.
     *
     * @param tenantId the caller's tenant, or {@code null} to receive platform announcements only
     */
    @Transactional(readOnly = true)
    public List<AnnouncementView> visible(String tenantId) {
        return announcements.findVisible(tenantId, Instant.now()).stream()
                .sorted(BANNER_ORDER)
                .map(AnnouncementService::toView)
                .toList();
    }

    // ------------------------------------------------------------------ management

    /**
     * Every announcement in scope, newest first — including disabled and out-of-window ones, which
     * the management screen must show so they can be switched back on.
     *
     * @param tenantId the owning tenant, or {@code null} for the platform scope
     */
    @Transactional(readOnly = true)
    public List<AnnouncementView> list(String tenantId) {
        List<Announcement> found = tenantId == null
                ? announcements.findByTenantIdIsNullOrderByIdDesc()
                : announcements.findByTenantIdOrderByIdDesc(tenantId);
        return found.stream().map(AnnouncementService::toView).toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementView get(String tenantId, Long id) {
        return toView(require(tenantId, id));
    }

    /** @param tenantId the owning tenant, or {@code null} to publish platform-wide */
    public AnnouncementView create(String tenantId, CreateCommand command) {
        requireOrderedWindow(command.startsAt(), command.endsAt());
        return inScope(tenantId, () -> {
            Announcement announcement = new Announcement(tenantId, command.level(), command.title(),
                    command.body(), command.startsAt(), command.endsAt());
            return toView(announcements.saveAndFlush(announcement));
        });
    }

    public AnnouncementView update(String tenantId, Long id, UpdateCommand command) {
        requireOrderedWindow(command.startsAt(), command.endsAt());
        return inScope(tenantId, () -> {
            Announcement announcement = require(tenantId, id);
            announcement.setLevel(command.level());
            announcement.setTitle(command.title());
            announcement.setBody(command.body());
            announcement.setStartsAt(command.startsAt());
            announcement.setEndsAt(command.endsAt());
            if (command.enabled() != null) {
                announcement.setEnabled(command.enabled());
            }
            return toView(announcement);
        });
    }

    /** Logically deletes an announcement (retention, not removal): the banner stops showing it. */
    public void delete(String tenantId, Long id) {
        inScope(tenantId, () -> require(tenantId, id).markDeleted(changeContext.author(),
                changeContext.messageOr(() -> "delete announcement")));
    }

    // ------------------------------------------------------------------ internals

    /**
     * Loads an announcement in scope, or fails.
     *
     * @param tenantId the owning tenant, or {@code null} for the platform scope
     */
    private Announcement require(String tenantId, Long id) {
        return (tenantId == null
                ? announcements.findByIdAndTenantIdIsNull(id)
                : announcements.findByTenantIdAndId(tenantId, id))
                .orElseThrow(() -> ResourceNotFoundException.of("Announcement", "id", id));
    }

    /**
     * Binds the tenant for the duration of a write, so {@code TenantStatusGuard} revalidates that a
     * tenant disabled after the token was issued cannot keep publishing. A null tenant is
     * platform-level work and skips the binding, exactly as elsewhere in this layer.
     */
    private <T> T inScope(String tenantId, Supplier<T> action) {
        return tenantId == null ? action.get() : tenantScope.call(tenantId, action);
    }

    private void inScope(String tenantId, Runnable action) {
        inScope(tenantId, () -> {
            action.run();
            return null;
        });
    }

    /** A window that ends before it starts would hide the notice silently; reject it instead. */
    private static void requireOrderedWindow(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new ValidationException("endsAt must be after startsAt");
        }
    }

    private static AnnouncementView toView(Announcement announcement) {
        return new AnnouncementView(announcement.getId(), announcement.getTenantId(), announcement.getLevel(),
                announcement.getTitle(), announcement.getBody(), announcement.isEnabled(),
                announcement.getStartsAt(), announcement.getEndsAt(), announcement.getCreatedAt(),
                announcement.getUpdatedAt());
    }
}
