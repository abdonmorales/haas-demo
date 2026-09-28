package edu.utexas.haas.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Server-side session holding only the signed-in user's internal primary key. */
final class SessionUser {

    private static final String ATTR = "haas.userPk";

    private SessionUser() {
    }

    static String require(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object pk = session == null ? null : session.getAttribute(ATTR);
        if (pk == null) {
            throw ApiException.unauthorized("Please sign in.");
        }
        return (String) pk;
    }

    static void signIn(HttpServletRequest request, String userPk) {
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate(); // new session ID on login prevents session fixation
        }
        request.getSession(true).setAttribute(ATTR, userPk);
    }

    static void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
