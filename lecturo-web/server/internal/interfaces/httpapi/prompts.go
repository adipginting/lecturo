package httpapi

import (
	"errors"
	"net/http"

	"lecturo/server/internal/application/prompts"
)

type promptResponse struct {
	ID    string `json:"id"`
	Title string `json:"title"`
	Body  string `json:"body"`
}

func (s *Server) listPrompts(w http.ResponseWriter, r *http.Request) {
	if !s.requirePrompts(w) {
		return
	}
	u, _ := userFrom(r.Context())

	items, err := s.Prompts.List(r.Context(), u.ID)
	if err != nil {
		logError("listPrompts", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load saved prompts.")
		return
	}

	out := make([]promptResponse, 0, len(items))
	for _, p := range items {
		out = append(out, promptResponse{ID: p.ID, Title: p.Title, Body: p.Body})
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": out})
}

func (s *Server) createPrompt(w http.ResponseWriter, r *http.Request) {
	if !s.requirePrompts(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		Title string `json:"title"`
		Body  string `json:"body"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}
	if body.Title == "" || body.Body == "" {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload,
			"A saved prompt needs a title and a body.")
		return
	}

	p, err := s.Prompts.Create(r.Context(), u.ID, body.Title, body.Body)
	if errors.Is(err, prompts.ErrCapReached) {
		writeError(w, http.StatusConflict, codePromptCap,
			"You can keep at most twelve saved prompts.")
		return
	}
	if err != nil {
		logError("createPrompt", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not save the prompt.")
		return
	}
	writeJSON(w, http.StatusCreated, promptResponse{ID: p.ID, Title: p.Title, Body: p.Body})
}

func (s *Server) updatePrompt(w http.ResponseWriter, r *http.Request) {
	if !s.requirePrompts(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		Title *string `json:"title"`
		Body  *string `json:"body"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}

	p, err := s.Prompts.Update(r.Context(), u.ID, r.PathValue("id"), body.Title, body.Body)
	if errors.Is(err, prompts.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That prompt does not exist.")
		return
	}
	if err != nil {
		logError("updatePrompt", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not update the prompt.")
		return
	}
	writeJSON(w, http.StatusOK, promptResponse{ID: p.ID, Title: p.Title, Body: p.Body})
}

func (s *Server) deletePrompt(w http.ResponseWriter, r *http.Request) {
	if !s.requirePrompts(w) {
		return
	}
	u, _ := userFrom(r.Context())

	err := s.Prompts.Delete(r.Context(), u.ID, r.PathValue("id"))
	if errors.Is(err, prompts.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That prompt does not exist.")
		return
	}
	if err != nil {
		logError("deletePrompt", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not delete the prompt.")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) requirePrompts(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Prompts == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "The database is not configured.")
		return false
	}
	return true
}
