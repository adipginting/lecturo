package httpapi

import (
	"errors"
	"net/http"
	"strings"

	appcatalog "lecturo/server/internal/application/catalog"
	appdocuments "lecturo/server/internal/application/documents"
)

type remoteItemResponse struct {
	RemoteID   string  `json:"remoteId"`
	Title      string  `json:"title"`
	Format     string  `json:"format"`
	Author     *string `json:"author"`
	URL        *string `json:"url"`
	IsCatalog  bool    `json:"isCatalog"`
	Downloaded bool    `json:"downloaded"`
	DocumentID *string `json:"documentId"`
}

type remotePageResponse struct {
	Title      *string              `json:"title"`
	Path       *string              `json:"path"`
	Searchable bool                 `json:"searchable"`
	Items      []remoteItemResponse `json:"items"`
}

func (s *Server) browseRemote(w http.ResponseWriter, r *http.Request) {
	if !s.requireCatalog(w) {
		return
	}
	u, _ := userFrom(r.Context())
	result, err := s.Catalog.Browse(r.Context(), u.ID, r.PathValue("source"), r.URL.Query().Get("path"))
	s.writeRemotePage(w, r, result, err)
}

func (s *Server) searchRemote(w http.ResponseWriter, r *http.Request) {
	if !s.requireCatalog(w) {
		return
	}
	query := strings.TrimSpace(r.URL.Query().Get("q"))
	if query == "" {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "Enter something to search for.")
		return
	}
	u, _ := userFrom(r.Context())
	result, err := s.Catalog.Search(r.Context(), u.ID, r.PathValue("source"), query)
	s.writeRemotePage(w, r, result, err)
}

func (s *Server) writeRemotePage(w http.ResponseWriter, r *http.Request, result appcatalog.PageResult, err error) {
	switch {
	case errors.Is(err, appcatalog.ErrUnknownSource):
		writeError(w, http.StatusNotFound, codeNotFound, "Unknown source.")
		return
	case errors.Is(err, appcatalog.ErrNotConfigured):
		writeError(w, http.StatusUnprocessableEntity, codeProviderUnconfigured, "That source is not configured yet.")
		return
	case err != nil:
		logError("remote: "+r.PathValue("source"), err)
		writeError(w, http.StatusBadGateway, codeUpstreamFailed, "The source could not be reached.")
		return
	}

	page := result.Page
	out := remotePageResponse{
		Title:      emptyToNil(page.Title),
		Path:       emptyToNil(page.Path),
		Searchable: page.Searchable,
		Items:      make([]remoteItemResponse, 0, len(page.Items)),
	}
	for _, it := range page.Items {
		item := remoteItemResponse{
			RemoteID:  it.RemoteID,
			Title:     it.Title,
			Format:    it.Format,
			Author:    emptyToNil(it.Author),
			URL:       emptyToNil(it.URL),
			IsCatalog: it.IsCatalog,
		}
		if id, ok := result.Downloaded[it.RemoteID]; ok {
			item.Downloaded = true
			docID := id
			item.DocumentID = &docID
		}
		out.Items = append(out.Items, item)
	}
	writeJSON(w, http.StatusOK, out)
}

func (s *Server) downloadRemote(w http.ResponseWriter, r *http.Request) {
	if !s.requireCatalog(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		RemoteID string `json:"remoteId"`
		URL      string `json:"url"`
		Title    string `json:"title"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}
	if body.URL == "" {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "A download needs a URL.")
		return
	}

	doc, err := s.Catalog.Download(r.Context(), u.ID, r.PathValue("source"), body.RemoteID, body.URL, body.Title)
	switch {
	case errors.Is(err, appcatalog.ErrUnknownSource):
		writeError(w, http.StatusNotFound, codeNotFound, "Unknown source.")
	case errors.Is(err, appcatalog.ErrNotConfigured):
		writeError(w, http.StatusUnprocessableEntity, codeProviderUnconfigured, "That source is not configured yet.")
	case errors.Is(err, appdocuments.ErrNotABook):
		writeError(w, http.StatusUnsupportedMediaType, codeNotABook,
			body.Title+" is not a book — only PDF and EPUB are supported.")
	case err != nil:
		logError("downloadRemote", err)
		writeError(w, http.StatusBadGateway, codeUpstreamFailed, "The download failed.")
	default:
		writeJSON(w, http.StatusCreated, toDocumentResponse(doc))
	}
}

// requireCatalog reports whether the catalog service is configured.
func (s *Server) requireCatalog(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Catalog == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "The catalog is not configured.")
		return false
	}
	return true
}
