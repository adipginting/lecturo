package httpapi

import (
	"context"
	"crypto/subtle"
	"errors"
	"net/http"
	"time"

	"lecturo/server/internal/application/identity"
	"lecturo/server/internal/infrastructure/auth"
)

type ctxKey int

const (
	ctxUser ctxKey = iota
	ctxSession
)

// currentUser is the authenticated user, loaded per request.
type currentUser struct {
	ID        string
	Email     string
	CreatedAt time.Time
}

func userFrom(ctx context.Context) (currentUser, bool) {
	u, ok := ctx.Value(ctxUser).(currentUser)
	return u, ok
}

// authenticate resolves the session cookie (if any) and loads the user. It never
// rejects; handlers decide whether a user is required.
func (s *Server) authenticate(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		cookie, err := r.Cookie(auth.SessionCookie)
		if err != nil || cookie.Value == "" {
			next(w, r)
			return
		}

		session, err := s.Identity.Session(r.Context(), cookie.Value)
		if err != nil {
			if !errors.Is(err, identity.ErrNoSession) {
				writeError(w, http.StatusInternalServerError, codeInternal, "Could not load the session.")
				return
			}
			next(w, r)
			return
		}

		account, err := s.Identity.User(r.Context(), session.UserID)
		if err != nil {
			// Session points at a missing user; treat as signed out.
			next(w, r)
			return
		}

		u := currentUser{ID: account.ID, Email: account.Email, CreatedAt: account.CreatedAt}
		ctx := context.WithValue(r.Context(), ctxUser, u)
		ctx = context.WithValue(ctx, ctxSession, session)
		next(w, r.WithContext(ctx))
	}
}

// requireUser rejects unauthenticated requests.
func (s *Server) requireUser(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if _, ok := userFrom(r.Context()); !ok {
			writeError(w, http.StatusUnauthorized, codeUnauthorized, "You are not signed in.")
			return
		}
		next(w, r)
	}
}

// requireCSRF enforces the double-submit token on state-changing requests.
func (s *Server) requireCSRF(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		session, ok := r.Context().Value(ctxSession).(identity.Session)
		if !ok {
			writeError(w, http.StatusUnauthorized, codeUnauthorized, "You are not signed in.")
			return
		}
		header := r.Header.Get("X-CSRF-Token")
		if header == "" || subtle.ConstantTimeCompare([]byte(header), []byte(session.CSRFToken)) != 1 {
			writeError(w, http.StatusForbidden, codeCSRF, "The CSRF token is missing or invalid.")
			return
		}
		next(w, r)
	}
}
