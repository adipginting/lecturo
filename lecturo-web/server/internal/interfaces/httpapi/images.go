package httpapi

import (
	"errors"
	"io"
	"net/http"
	"time"

	appimages "lecturo/server/internal/application/images"
)

const maxImageBytes = 12 << 20

type imageResponse struct {
	ID          string    `json:"id"`
	ContentType string    `json:"contentType"`
	ByteSize    int64     `json:"byteSize"`
	CreatedAt   time.Time `json:"createdAt"`
}

func (s *Server) uploadImage(w http.ResponseWriter, r *http.Request) {
	if !s.requireImages(w) {
		return
	}
	u, _ := userFrom(r.Context())

	r.Body = http.MaxBytesReader(w, r.Body, maxImageBytes)
	if err := r.ParseMultipartForm(maxImageBytes); err != nil {
		writeError(w, http.StatusRequestEntityTooLarge, codePayloadTooLarge,
			"That image is too large (the limit is 12 MiB).")
		return
	}
	defer func() { _ = r.MultipartForm.RemoveAll() }()

	file, _, err := r.FormFile("file")
	if err != nil {
		writeError(w, http.StatusBadRequest, codeInvalidPayload, "Attach an image under the `file` field.")
		return
	}
	defer file.Close()

	data, err := io.ReadAll(file)
	if err != nil {
		writeError(w, http.StatusBadRequest, codeInvalidPayload, "Could not read the image.")
		return
	}

	img, err := s.Images.Create(r.Context(), u.ID, data)
	if errors.Is(err, appimages.ErrNotAnImage) {
		writeError(w, http.StatusUnsupportedMediaType, codeNotAnImage,
			"Only PNG, JPEG, GIF and WEBP images are supported.")
		return
	}
	if err != nil {
		logError("uploadImage", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not store the image.")
		return
	}

	writeJSON(w, http.StatusCreated, imageResponse{
		ID: img.ID, ContentType: img.ContentType, ByteSize: img.ByteSize, CreatedAt: img.CreatedAt,
	})
}

func (s *Server) getImage(w http.ResponseWriter, r *http.Request) {
	if !s.requireImages(w) {
		return
	}
	u, _ := userFrom(r.Context())

	rc, contentType, err := s.Images.Open(r.Context(), u.ID, r.PathValue("id"))
	if errors.Is(err, appimages.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That image does not exist.")
		return
	}
	if err != nil {
		logError("getImage", err)
		writeError(w, http.StatusBadGateway, codeUpstreamFailed, "Could not read the image.")
		return
	}
	defer rc.Close()

	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Cache-Control", "private, max-age=3600")
	if _, err := io.Copy(w, rc); err != nil {
		logError("getImage: copy", err)
	}
}

func (s *Server) requireImages(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Images == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "Blob storage is not configured.")
		return false
	}
	return true
}
