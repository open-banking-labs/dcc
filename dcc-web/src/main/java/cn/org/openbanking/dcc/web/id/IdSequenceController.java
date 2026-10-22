package cn.org.openbanking.dcc.web.id;

import java.time.Instant;
import java.util.List;

import cn.org.openbanking.dcc.core.id.IdFactory;
import cn.org.openbanking.dcc.core.id.SequenceSnapshot;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the persisted identifier sequence positions.
 *
 * <p>This is platform infrastructure, not tenant data: {@code currentValue} is the
 * high-water mark already reserved from the database. A running instance may still
 * hold a small unissued buffer above it, which is why the endpoint is for operations
 * and capacity checks rather than for consuming numbers.
 */
@RestController
@RequestMapping("/api/platform/id-sequences")
@PreAuthorize("hasAuthority(@perms.tenantAdmit)")
public class IdSequenceController {

    private final IdFactory idFactory;

    public IdSequenceController(IdFactory idFactory) {
        this.idFactory = idFactory;
    }

    @GetMapping
    public List<SequenceView> list(@RequestParam(required = false) String prefix) {
        return idFactory.snapshots(prefix).stream().map(SequenceView::from).toList();
    }

    public record SequenceView(String sequenceKey, long currentValue, long maxValue, int step,
            long remaining, Instant updatedAt) {

        static SequenceView from(SequenceSnapshot snapshot) {
            return new SequenceView(snapshot.key(), snapshot.currentValue(), snapshot.maxValue(),
                    snapshot.step(), snapshot.remaining(), snapshot.updatedAt());
        }
    }
}
