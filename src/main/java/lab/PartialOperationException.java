package lab;

import javax.naming.NamingException;

/** A remote multi-step operation may already have changed AD; never retry it blindly. */
final class PartialOperationException extends NamingException {
    PartialOperationException(String message) { super(message); }
}
