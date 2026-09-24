package httpapi

import (
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"net/url"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	appdocuments "lecturo/server/internal/application/documents"
	domain "lecturo/server/internal/domain/library"
)

const maxDownloadHops = 3

var (
	metaRefreshPattern = regexp.MustCompile(`(?is)<meta[^>]+http-equiv=["']?refresh["']?[^>]*content=["'][^"']*url=([^"'\s>]+)`)
	bookLinkPattern    = regexp.MustCompile(`(?is)<a[^>]+href=["']([^"']+\.(?:pdf|epub)(?:\?[^"']*)?)["']`)
)

const (
	maxUploadBytes = 256 << 20 // 256 MiB
	uploadMemory   = 32 << 20
	urlFetchLimit  = 60 * time.Second
)

type documentResponse struct {
	ID        string    `json:"id"`
	Title     string    `json:"title"`
	Format    string    `json:"format"`
	Locator   *string   `json:"locator"`
	ByteSize  *int64    `json:"byteSize"`
	Source    *string   `json:"source"`
	RemoteID  *string   `json:"remoteId"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

func toDocumentResponse(d domain.Document) documentResponse {
	return documentResponse{
		ID: d.ID, Title: d.Title, Format: d.Format, Locator: d.Locator,
		ByteSize: d.ByteSize, Source: d.Source, RemoteID: d.RemoteID,
		CreatedAt: d.CreatedAt, UpdatedAt: d.UpdatedAt,
	}
}

func (s *Server) listDocuments(w http.ResponseWriter, r *http.Request) {
	if !s.requireLibrary(w) {
		return
	}
	u, _ := userFrom(r.Context())

	limit := 50
	if raw := r.URL.Query().Get("limit"); raw != "" {
		if v, err := strconv.Atoi(raw); err == nil {
			limit = v
		}
	}

	docs, next, err := s.Documents.List(r.Context(), u.ID, limit, r.URL.Query().Get("cursor"))
	if err != nil {
		logError("listDocuments", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not list documents.")
		return
	}

	items := make([]documentResponse, 0, len(docs))
	for _, d := range docs {
		items = append(items, toDocumentResponse(d))
	}

	var nextCursor any
	if next != "" {
		nextCursor = next
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items, "nextCursor": nextCursor})
}

func (s *Server) uploadDocument(w http.ResponseWriter, r *http.Request) {
	if !s.requireLibrary(w) {
		return
	}
	u, _ := userFrom(r.Context())

	r.Body = http.MaxBytesReader(w, r.Body, maxUploadBytes)
	if err := r.ParseMultipartForm(uploadMemory); err != nil {
		writeError(w, http.StatusRequestEntityTooLarge, codePayloadTooLarge,
			"That file is too large (the limit is 256 MiB).")
		return
	}
	defer func() { _ = r.MultipartForm.RemoveAll() }()

	file, header, err := r.FormFile("file")
	if err != nil {
		writeError(w, http.StatusBadRequest, codeInvalidPayload, "Attach a file under the `file` field.")
		return
	}
	defer file.Close()

	data, err := io.ReadAll(file)
	if err != nil {
		writeError(w, http.StatusBadRequest, codeInvalidPayload, "Could not read the uploaded file.")
		return
	}

	doc, err := s.Documents.Create(r.Context(), appdocuments.CreateInput{
		UserID: u.ID,
		Title:  titleFromFilename(header.Filename),
		Data:   data,
	})
	if err != nil {
		if errors.Is(err, appdocuments.ErrNotABook) {
			writeError(w, http.StatusUnsupportedMediaType, codeNotABook,
				titleFromFilename(header.Filename)+" is not a book — only PDF and EPUB are supported.")
			return
		}
		logError("uploadDocument", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not store the document.")
		return
	}
	writeJSON(w, http.StatusCreated, toDocumentResponse(doc))
}

func (s *Server) importDocumentFromURL(w http.ResponseWriter, r *http.Request) {
	if !s.requireLibrary(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		URL string `json:"url"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}
	target, err := url.Parse(strings.TrimSpace(body.URL))
	if err != nil || (target.Scheme != "http" && target.Scheme != "https") {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "Enter a valid http(s) URL.")
		return
	}

	client := &http.Client{Timeout: urlFetchLimit}
	data, title, err := fetchDocument(r, client, target)
	if err != nil {
		switch {
		case errors.Is(err, errTooLarge):
			writeError(w, http.StatusRequestEntityTooLarge, codePayloadTooLarge,
				"That download is too large (the limit is 256 MiB).")
		case errors.Is(err, appdocuments.ErrNotABook):
			writeError(w, http.StatusUnsupportedMediaType, codeNotABook,
				title+" is not a book — only PDF and EPUB are supported.")
		default:
			logError("importDocumentFromURL", err)
			writeError(w, http.StatusBadGateway, codeUpstreamFailed,
				"Could not download that URL.")
		}
		return
	}

	doc, err := s.Documents.Create(r.Context(), appdocuments.CreateInput{
		UserID: u.ID,
		Title:  title,
		Data:   data,
	})
	if err != nil {
		if errors.Is(err, appdocuments.ErrNotABook) {
			writeError(w, http.StatusUnsupportedMediaType, codeNotABook,
				title+" is not a book — only PDF and EPUB are supported.")
			return
		}
		logError("importDocumentFromURL", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not store the document.")
		return
	}
	writeJSON(w, http.StatusCreated, toDocumentResponse(doc))
}

func (s *Server) getDocument(w http.ResponseWriter, r *http.Request) {
	if !s.requireLibrary(w) {
		return
	}
	u, _ := userFrom(r.Context())

	doc, err := s.Documents.Get(r.Context(), u.ID, r.PathValue("id"))
	if errors.Is(err, appdocuments.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That document does not exist.")
		return
	}
	if err != nil {
		logError("getDocument", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load the document.")
		return
	}
	writeJSON(w, http.StatusOK, toDocumentResponse(doc))
}

func (s *Server) getDocumentFile(w http.ResponseWriter, r *http.Request) {
	if !s.requireLibrary(w) {
		return
	}
	u, _ := userFrom(r.Context())

	rc, contentType, err := s.Documents.Open(r.Context(), u.ID, r.PathValue("id"))
	if errors.Is(err, appdocuments.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That document does not exist.")
		return
	}
	if err != nil {
		logError("getDocumentFile", err)
		writeError(w, http.StatusBadGateway, codeUpstreamFailed, "Could not read the document's file.")
		return
	}
	defer rc.Close()

	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Cache-Control", "private, max-age=0, must-revalidate")
	if _, err := io.Copy(w, rc); err != nil {
		logError("getDocumentFile: copy", err)
	}
}

func (s *Server) deleteDocument(w http.ResponseWriter, r *http.Request) {
	if !s.requireLibrary(w) {
		return
	}
	u, _ := userFrom(r.Context())

	err := s.Documents.Delete(r.Context(), u.ID, r.PathValue("id"))
	if errors.Is(err, appdocuments.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That document does not exist.")
		return
	}
	if err != nil {
		logError("deleteDocument", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not delete the document.")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) updateDocumentPosition(w http.ResponseWriter, r *http.Request) {
	if !s.requireLibrary(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		Locator string `json:"locator"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}

	err := s.Documents.UpdatePosition(r.Context(), u.ID, r.PathValue("id"), body.Locator)
	if errors.Is(err, appdocuments.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That document does not exist.")
		return
	}
	if err != nil {
		logError("updateDocumentPosition", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not save the position.")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// requireLibrary reports whether the library store is configured.
func (s *Server) requireLibrary(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Documents == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "Blob storage is not configured.")
		return false
	}
	return true
}

// errTooLarge is returned when a download exceeds the payload cap.
var errTooLarge = errors.New("download is too large")

// fetchDocument downloads a URL, following an HTML "download page" (a meta
// refresh or a direct link to a PDF/EPUB) up to maxDownloadHops times. The
// payload is judged by its bytes, never the URL.
func fetchDocument(r *http.Request, client *http.Client, start *url.URL) ([]byte, string, error) {
	current := start
	title := titleFromURL(start, "")

	for hop := 0; hop < maxDownloadHops; hop++ {
		resp, err := client.Get(current.String())
		if err != nil {
			return nil, title, err
		}
		status := resp.StatusCode
		disposition := resp.Header.Get("Content-Disposition")
		data, readErr := io.ReadAll(io.LimitReader(resp.Body, maxUploadBytes+1))
		_ = resp.Body.Close()

		if status != http.StatusOK {
			return nil, title, fmt.Errorf("upstream answered %d", status)
		}
		if readErr != nil {
			return nil, title, readErr
		}
		if int64(len(data)) > maxUploadBytes {
			return nil, title, errTooLarge
		}

		title = titleFromURL(current, disposition)
		if _, ok := domain.Detect(data); ok {
			return data, title, nil
		}

		next := nextDownloadURL(data, current)
		if next == "" {
			return nil, title, appdocuments.ErrNotABook
		}
		parsed, err := url.Parse(next)
		if err != nil || (parsed.Scheme != "http" && parsed.Scheme != "https") {
			return nil, title, appdocuments.ErrNotABook
		}
		current = parsed
	}
	return nil, title, appdocuments.ErrNotABook
}

// nextDownloadURL finds where an HTML page points for its download, if it does.
func nextDownloadURL(body []byte, from *url.URL) string {
	if m := metaRefreshPattern.FindSubmatch(body); len(m) == 2 {
		return resolveRef(from, strings.TrimSpace(string(m[1])))
	}
	if m := bookLinkPattern.FindSubmatch(body); len(m) == 2 {
		return resolveRef(from, strings.TrimSpace(string(m[1])))
	}
	return ""
}

func resolveRef(base *url.URL, href string) string {
	ref, err := url.Parse(href)
	if err != nil {
		return ""
	}
	return base.ResolveReference(ref).String()
}

func titleFromFilename(name string) string {
	base := filepath.Base(name)
	title := strings.TrimSuffix(base, filepath.Ext(base))
	return strings.TrimSpace(title)
}

func titleFromURL(target *url.URL, contentDisposition string) string {
	if contentDisposition != "" {
		if _, params, err := mime.ParseMediaType(contentDisposition); err == nil {
			if name := params["filename"]; name != "" {
				if t := titleFromFilename(name); t != "" {
					return t
				}
			}
		}
	}
	if t := titleFromFilename(target.Path); t != "" {
		return t
	}
	return "Untitled"
}
