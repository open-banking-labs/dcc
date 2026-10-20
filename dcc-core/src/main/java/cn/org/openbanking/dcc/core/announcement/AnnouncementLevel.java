package cn.org.openbanking.dcc.core.announcement;

/**
 * How loudly the console banner should present an announcement.
 *
 * <p>The declaration order is the display priority: an active maintenance window outranks a
 * warning, which outranks a plain notice. Do not reorder these constants for cosmetic reasons —
 * the announcement service sorts the banner by this rank.
 */
public enum AnnouncementLevel {

    /** A system maintenance window: something will stop working, so it shows first. */
    MAINTENANCE,

    /** Degraded or unsupported behaviour the reader should know about before it bites. */
    WARNING,

    /** An ordinary notice. */
    INFO
}
