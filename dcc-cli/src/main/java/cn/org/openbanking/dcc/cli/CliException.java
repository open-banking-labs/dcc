package cn.org.openbanking.dcc.cli;

/** A failure surfaced to the user; the CLI prints it to stderr and exits non-zero. */
final class CliException extends RuntimeException {

    CliException(String message) {
        super(message);
    }
}
