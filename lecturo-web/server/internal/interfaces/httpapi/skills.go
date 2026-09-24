package httpapi

import (
	"net/http"
	"time"

	appskills "lecturo/server/internal/application/skills"
)

type skillResponse struct {
	ID          string `json:"id"`
	SkillID     string `json:"skillId"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Author      string `json:"author"`
	Version     string `json:"version"`
	Scope       string `json:"scope"`
	Trigger     string `json:"trigger"`
	Prompt      string `json:"prompt"`
	Price       string `json:"price"`
	Enabled     bool   `json:"enabled"`
}

func toSkillResponse(s appskills.Skill) skillResponse {
	return skillResponse{
		ID: s.ID, SkillID: s.SkillID, Name: s.Name, Description: s.Description,
		Author: s.Author, Version: s.Version, Scope: s.Scope, Trigger: s.Trigger,
		Prompt: s.Prompt, Price: s.Price, Enabled: s.Enabled,
	}
}

type remoteSkillResponse struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Author      string `json:"author"`
	Version     string `json:"version"`
	Scope       string `json:"scope"`
	Trigger     string `json:"trigger"`
	Prompt      string `json:"prompt"`
	Price       string `json:"price"`
	Paid        bool   `json:"paid"`
}

type skillRunResponse struct {
	ID         string    `json:"id"`
	SkillID    string    `json:"skillId"`
	SkillName  string    `json:"skillName"`
	DocumentID *string   `json:"documentId"`
	Status     string    `json:"status"`
	Result     string    `json:"result"`
	Error      string    `json:"error"`
	CreatedAt  time.Time `json:"createdAt"`
}

func (s *Server) listSkills(w http.ResponseWriter, r *http.Request) {
	if !s.requireSkills(w) {
		return
	}
	u, _ := userFrom(r.Context())

	installed, err := s.Skills.List(r.Context(), u.ID)
	if err != nil {
		logError("listSkills", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load skills.")
		return
	}
	items := make([]skillResponse, 0, len(installed))
	for _, k := range installed {
		items = append(items, toSkillResponse(k))
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

func (s *Server) browseSkills(w http.ResponseWriter, r *http.Request) {
	if !s.requireSkills(w) {
		return
	}
	var body struct {
		URL string `json:"url"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}

	catalog, err := s.Skills.Browse(r.Context(), body.URL)
	if err != nil {
		logError("browseSkills", err)
		writeError(w, http.StatusBadGateway, codeUpstreamFailed, "Could not reach the marketplace.")
		return
	}
	items := make([]remoteSkillResponse, 0, len(catalog))
	for _, k := range catalog {
		items = append(items, remoteSkillResponse{
			ID: k.SkillID, Name: k.Name, Description: k.Description, Author: k.Author,
			Version: k.Version, Scope: k.Scope, Trigger: k.Trigger, Prompt: k.Prompt,
			Price: k.Price, Paid: k.IsPaid(),
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

func (s *Server) installSkill(w http.ResponseWriter, r *http.Request) {
	if !s.requireSkills(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body remoteSkillResponse
	if !decodeJSON(w, r, &body) {
		return
	}
	if body.ID == "" {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "A skill needs an id.")
		return
	}

	err := s.Skills.Install(r.Context(), u.ID, appskills.RemoteSkill{
		SkillID: body.ID, Name: body.Name, Description: body.Description, Author: body.Author,
		Version: body.Version, Scope: body.Scope, Trigger: body.Trigger, Prompt: body.Prompt, Price: body.Price,
	})
	if err != nil {
		logError("installSkill", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not install the skill.")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) deleteSkill(w http.ResponseWriter, r *http.Request) {
	if !s.requireSkills(w) {
		return
	}
	u, _ := userFrom(r.Context())

	if err := s.Skills.Uninstall(r.Context(), u.ID, r.PathValue("id")); err != nil {
		logError("deleteSkill", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not remove the skill.")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) setSkillEnabled(w http.ResponseWriter, r *http.Request) {
	if !s.requireSkills(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		Enabled bool `json:"enabled"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}
	if err := s.Skills.SetEnabled(r.Context(), u.ID, r.PathValue("id"), body.Enabled); err != nil {
		logError("setSkillEnabled", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not update the skill.")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) listSkillRuns(w http.ResponseWriter, r *http.Request) {
	if !s.requireSkills(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var documentID *string
	if raw := r.URL.Query().Get("documentId"); raw != "" {
		documentID = &raw
	}
	runs, err := s.Skills.Runs(r.Context(), u.ID, documentID)
	if err != nil {
		logError("listSkillRuns", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load skill runs.")
		return
	}
	items := make([]skillRunResponse, 0, len(runs))
	for _, run := range runs {
		items = append(items, skillRunResponse{
			ID: run.ID, SkillID: run.SkillID, SkillName: run.SkillName, DocumentID: run.DocumentID,
			Status: string(run.Status), Result: run.Result, Error: run.Error, CreatedAt: run.CreatedAt,
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

// requireSkills reports whether the skills service is configured.
func (s *Server) requireSkills(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Skills == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "The database is not configured.")
		return false
	}
	return true
}
