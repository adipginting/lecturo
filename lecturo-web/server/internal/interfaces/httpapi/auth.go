package httpapi

import (
	"errors"
	"net/http"
	"time"

	appidentity "lecturo/server/internal/application/identity"
	"lecturo/server/internal/infrastructure/auth"
)

type credentials struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type userResponse struct {
	ID        string    `json:"id"`
	Email     string    `json:"email"`
	CreatedAt time.Time `json:"createdAt"`
}

func toUserResponse(u appidentity.User) userResponse {
	return userResponse{ID: u.ID, Email: u.Email, CreatedAt: u.CreatedAt}
}

func (s *Server) register(w http.ResponseWriter, r *http.Request) {
	if !s.requireIdentity(w) {
		return
	}
	var body credentials
	if !decodeJSON(w, r, &body) {
		return
	}

	user, token, csrf, expires, err := s.Identity.Register(r.Context(), body.Email, body.Password)
	switch {
	case errors.Is(err, appidentity.ErrInvalidEmail):
		writeError(w, http.StatusUnprocessableEntity, codeInvalidEmail, "Enter a valid email address.")
	case errors.Is(err, appidentity.ErrWeakPassword):
		writeError(w, http.StatusUnprocessableEntity, codeWeakPassword,
			"Choose a password of at least 12 characters.")
	case errors.Is(err, appidentity.ErrEmailTaken):
		writeError(w, http.StatusConflict, codeEmailTaken, "That email is already registered.")
	case err != nil:
		logError("register", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not create the account.")
	default:
		s.setSessionCookies(w, token, csrf, expires)
		writeJSON(w, http.StatusCreated, toUserResponse(user))
	}
}

func (s *Server) login(w http.ResponseWriter, r *http.Request) {
	if !s.requireIdentity(w) {
		return
	}
	var body credentials
	if !decodeJSON(w, r, &body) {
		return
	}

	user, token, csrf, expires, err := s.Identity.Login(r.Context(), body.Email, body.Password)
	switch {
	case errors.Is(err, appidentity.ErrInvalidCredentials):
		writeError(w, http.StatusUnauthorized, codeInvalidCreds, "Wrong email or password.")
	case err != nil:
		logError("login", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not sign in.")
	default:
		s.setSessionCookies(w, token, csrf, expires)
		writeJSON(w, http.StatusOK, toUserResponse(user))
	}
}

func (s *Server) logout(w http.ResponseWriter, r *http.Request) {
	if cookie, err := r.Cookie(auth.SessionCookie); err == nil && cookie.Value != "" && s.Identity != nil {
		if err := s.Identity.Logout(r.Context(), cookie.Value); err != nil {
			logError("logout: delete", err)
		}
	}
	s.clearSessionCookies(w)
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) me(w http.ResponseWriter, r *http.Request) {
	u, _ := userFrom(r.Context())
	writeJSON(w, http.StatusOK, userResponse{ID: u.ID, Email: u.Email, CreatedAt: u.CreatedAt})
}

// requireIdentity reports whether the identity service is configured.
func (s *Server) requireIdentity(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Identity == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "The database is not configured.")
		return false
	}
	return true
}
