// Package httpapi implements the HTTP surface described by api/openapi.yaml.
package httpapi

import (
	"context"
	"log"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	appcatalog "lecturo/server/internal/application/catalog"
	appchat "lecturo/server/internal/application/chat"
	appdocuments "lecturo/server/internal/application/documents"
	appidentity "lecturo/server/internal/application/identity"
	appimages "lecturo/server/internal/application/images"
	appprompts "lecturo/server/internal/application/prompts"
	appprovider "lecturo/server/internal/application/provider"
	appservers "lecturo/server/internal/application/servers"
	appskills "lecturo/server/internal/application/skills"
	"lecturo/server/internal/infrastructure/auth"
	"lecturo/server/internal/infrastructure/blob"
	chatrepo "lecturo/server/internal/infrastructure/chat"
	"lecturo/server/internal/infrastructure/images"
	"lecturo/server/internal/infrastructure/library"
	"lecturo/server/internal/infrastructure/prompts"
	"lecturo/server/internal/infrastructure/provider"
	"lecturo/server/internal/infrastructure/remotelib"
	"lecturo/server/internal/infrastructure/secretbox"
	"lecturo/server/internal/infrastructure/servers"
	skillstore "lecturo/server/internal/infrastructure/skills"
)

// Database is the readiness check the server needs; it is satisfied by the
// connection pool in the composition root.
type Database interface {
	Ping(ctx context.Context) error
}

// Server holds the request-time dependencies.
type Server struct {
	Database      Database
	Blob          blob.Store
	Identity      *appidentity.Service
	Documents     *appdocuments.Service
	Prompts       *appprompts.Service
	Provider      *appprovider.Service
	Chat          *appchat.Service
	Catalog       *appcatalog.Service
	Images        *appimages.Service
	Servers       *appservers.Service
	Skills        *appskills.Service
	SecureCookies bool
	// SPA serves the embedded web app for non-API paths. Optional.
	SPA http.Handler
}

// New wires the dependencies. box may be nil when no encryption key is configured.
func New(pool *pgxpool.Pool, b blob.Store, secureCookies bool, box *secretbox.Box) *Server {
	s := &Server{
		Blob:          b,
		SecureCookies: secureCookies,
	}
	if pool != nil {
		s.Database = pool
		s.Identity = &appidentity.Service{
			Users:    &auth.UserStore{Pool: pool},
			Sessions: &auth.SessionStore{Pool: pool},
			Hasher:   auth.PasswordHasher{},
		}
		libraryStore := &library.Store{Pool: pool, Blob: b}
		s.Documents = &appdocuments.Service{Repo: libraryStore}
		s.Prompts = &appprompts.Service{Repo: &prompts.Store{Pool: pool}}
		s.Provider = &appprovider.Service{Repo: &provider.Store{Pool: pool, Box: box}}
		s.Chat = &appchat.Service{Repo: &chatrepo.Store{Pool: pool}}
		s.Images = &appimages.Service{Repo: &images.Store{Pool: pool, Blob: b}}
		s.Servers = &appservers.Service{Repo: &servers.Store{Pool: pool, Box: box}}
		s.Skills = &appskills.Service{Repo: &skillstore.Store{Pool: pool}, Catalog: skillstore.HttpCatalog{}}
		calibre, _ := remotelib.For("calibre")
		zotero, _ := remotelib.For("zotero")
		s.Catalog = &appcatalog.Service{
			Calibre: calibre, Zotero: zotero,
			Servers: s.Servers, Documents: s.Documents, Provenance: libraryStore,
		}
	}
	return s
}

// authed requires a signed-in user.
func (s *Server) authed(h http.HandlerFunc) http.HandlerFunc {
	return s.authenticate(s.requireUser(h))
}

// authedCSRF requires a signed-in user and a valid CSRF token.
func (s *Server) authedCSRF(h http.HandlerFunc) http.HandlerFunc {
	return s.authenticate(s.requireUser(s.requireCSRF(h)))
}

// Handler returns the routed HTTP handler.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /health", s.health)

	mux.HandleFunc("POST /api/v1/auth/register", s.register)
	mux.HandleFunc("POST /api/v1/auth/login", s.login)
	mux.HandleFunc("POST /api/v1/auth/logout", s.authenticate(s.requireCSRF(s.logout)))
	mux.HandleFunc("GET /api/v1/auth/me", s.authenticate(s.requireUser(s.me)))

	mux.HandleFunc("GET /api/v1/documents", s.authenticate(s.requireUser(s.listDocuments)))
	mux.HandleFunc("POST /api/v1/documents",
		s.authenticate(s.requireUser(s.requireCSRF(s.uploadDocument))))
	mux.HandleFunc("POST /api/v1/documents/import-url",
		s.authenticate(s.requireUser(s.requireCSRF(s.importDocumentFromURL))))
	mux.HandleFunc("GET /api/v1/documents/{id}",
		s.authenticate(s.requireUser(s.getDocument)))
	mux.HandleFunc("DELETE /api/v1/documents/{id}",
		s.authenticate(s.requireUser(s.requireCSRF(s.deleteDocument))))
	mux.HandleFunc("GET /api/v1/documents/{id}/file",
		s.authenticate(s.requireUser(s.getDocumentFile)))
	mux.HandleFunc("PUT /api/v1/documents/{id}/position",
		s.authenticate(s.requireUser(s.requireCSRF(s.updateDocumentPosition))))

	mux.HandleFunc("GET /api/v1/prompts", s.authed(s.listPrompts))
	mux.HandleFunc("POST /api/v1/prompts", s.authedCSRF(s.createPrompt))
	mux.HandleFunc("PUT /api/v1/prompts/{id}", s.authedCSRF(s.updatePrompt))
	mux.HandleFunc("DELETE /api/v1/prompts/{id}", s.authedCSRF(s.deletePrompt))

	mux.HandleFunc("GET /api/v1/skills", s.authed(s.listSkills))
	mux.HandleFunc("GET /api/v1/skills/runs", s.authed(s.listSkillRuns))
	mux.HandleFunc("POST /api/v1/skills/browse", s.authedCSRF(s.browseSkills))
	mux.HandleFunc("POST /api/v1/skills", s.authedCSRF(s.installSkill))
	mux.HandleFunc("PATCH /api/v1/skills/{id}", s.authedCSRF(s.setSkillEnabled))
	mux.HandleFunc("DELETE /api/v1/skills/{id}", s.authedCSRF(s.deleteSkill))

	mux.HandleFunc("GET /api/v1/provider", s.authed(s.getProviderSettings))
	mux.HandleFunc("PUT /api/v1/provider", s.authedCSRF(s.updateProviderSettings))
	mux.HandleFunc("GET /api/v1/provider/models", s.authed(s.listProviderModels))

	mux.HandleFunc("GET /api/v1/conversations", s.authed(s.listConversations))
	mux.HandleFunc("POST /api/v1/conversations", s.authedCSRF(s.createConversation))
	mux.HandleFunc("GET /api/v1/conversations/{id}", s.authed(s.getConversation))
	mux.HandleFunc("PATCH /api/v1/conversations/{id}", s.authedCSRF(s.updateConversation))
	mux.HandleFunc("DELETE /api/v1/conversations/{id}", s.authedCSRF(s.deleteConversation))
	mux.HandleFunc("GET /api/v1/conversations/{id}/messages", s.authed(s.listMessages))
	mux.HandleFunc("POST /api/v1/conversations/{id}/messages", s.authedCSRF(s.createMessage))
	mux.HandleFunc("POST /api/v1/conversations/{id}/compact", s.authedCSRF(s.compactConversation))

	mux.HandleFunc("POST /api/v1/images", s.authedCSRF(s.uploadImage))
	mux.HandleFunc("GET /api/v1/images/{id}", s.authed(s.getImage))

	mux.HandleFunc("GET /api/v1/servers", s.authed(s.getServerSettings))
	mux.HandleFunc("PUT /api/v1/servers", s.authedCSRF(s.updateServerSettings))

	mux.HandleFunc("GET /api/v1/remote/{source}/browse", s.authed(s.browseRemote))
	mux.HandleFunc("GET /api/v1/remote/{source}/search", s.authed(s.searchRemote))
	mux.HandleFunc("POST /api/v1/remote/{source}/download", s.authedCSRF(s.downloadRemote))

	mux.HandleFunc("/api/v1/", s.notImplemented)

	if s.SPA != nil {
		mux.Handle("/", s.SPA)
	}

	return mux
}

func (s *Server) health(w http.ResponseWriter, r *http.Request) {
	status := "ok"
	if s.Database != nil {
		ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer cancel()
		if err := s.Database.Ping(ctx); err != nil {
			status = "degraded"
		}
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": status})
}

func (s *Server) notImplemented(w http.ResponseWriter, r *http.Request) {
	writeError(w, http.StatusNotImplemented, codeNotImplemented, "Not implemented yet.")
}

// requireDB reports whether the database is available, writing a 503 if not.
func (s *Server) requireDB(w http.ResponseWriter) bool {
	if s.Database == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "The database is not configured.")
		return false
	}
	return true
}

func (s *Server) setSessionCookies(w http.ResponseWriter, token, csrf string, expires time.Time) {
	http.SetCookie(w, &http.Cookie{
		Name:     auth.SessionCookie,
		Value:    token,
		Path:     "/",
		HttpOnly: true,
		Secure:   s.SecureCookies,
		SameSite: http.SameSiteLaxMode,
		Expires:  expires,
	})
	http.SetCookie(w, &http.Cookie{
		Name:     auth.CSRFCookie,
		Value:    csrf,
		Path:     "/",
		HttpOnly: false,
		Secure:   s.SecureCookies,
		SameSite: http.SameSiteLaxMode,
		Expires:  expires,
	})
}

func (s *Server) clearSessionCookies(w http.ResponseWriter) {
	for _, name := range []string{auth.SessionCookie, auth.CSRFCookie} {
		http.SetCookie(w, &http.Cookie{
			Name:     name,
			Value:    "",
			Path:     "/",
			MaxAge:   -1,
			HttpOnly: name == auth.SessionCookie,
			Secure:   s.SecureCookies,
			SameSite: http.SameSiteLaxMode,
		})
	}
}

func logError(scope string, err error) {
	if err != nil {
		log.Printf("%s: %v", scope, err)
	}
}
