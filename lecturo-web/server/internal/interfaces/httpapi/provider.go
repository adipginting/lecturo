package httpapi

import (
	"errors"
	"net/http"

	appprovider "lecturo/server/internal/application/provider"
)

type providerSettingsResponse struct {
	Provider            string                       `json:"provider"`
	BaseURL             *string                      `json:"baseUrl"`
	Model               *string                      `json:"model"`
	HasKey              bool                         `json:"hasKey"`
	ModelSupportsImages bool                         `json:"modelSupportsImages"`
	Destination         string                       `json:"destination"`
	Providers           []appprovider.ProviderStatus `json:"providers"`
}

func toProviderSettingsResponse(s appprovider.Settings) providerSettingsResponse {
	return providerSettingsResponse{
		Provider:            s.Provider,
		BaseURL:             emptyToNil(s.BaseURL),
		Model:               emptyToNil(s.Model),
		HasKey:              s.HasKey,
		ModelSupportsImages: s.ModelSupportsImages,
		Destination:         s.Destination,
		Providers:           s.Providers,
	}
}

func (s *Server) getProviderSettings(w http.ResponseWriter, r *http.Request) {
	if !s.requireProvider(w) {
		return
	}
	u, _ := userFrom(r.Context())

	settings, err := s.Provider.Get(r.Context(), u.ID)
	if err != nil {
		logError("getProviderSettings", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load provider settings.")
		return
	}
	writeJSON(w, http.StatusOK, toProviderSettingsResponse(settings))
}

func (s *Server) updateProviderSettings(w http.ResponseWriter, r *http.Request) {
	if !s.requireProvider(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		Provider       string  `json:"provider"`
		BaseURL        *string `json:"baseUrl"`
		Model          *string `json:"model"`
		APIKey         *string `json:"apiKey"`
		VisionOverride *bool   `json:"visionOverride"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}
	if body.Provider == "" {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "Choose a provider.")
		return
	}

	settings, err := s.Provider.Save(r.Context(), u.ID, appprovider.Update{
		Provider:       body.Provider,
		BaseURL:        body.BaseURL,
		Model:          body.Model,
		APIKey:         body.APIKey,
		VisionOverride: body.VisionOverride,
	})
	switch {
	case errors.Is(err, appprovider.ErrUnknownProvider):
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "Unknown provider.")
		return
	case errors.Is(err, appprovider.ErrUnsupported):
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "That provider is not supported yet.")
		return
	case errors.Is(err, appprovider.ErrEncryptionUnavailable):
		writeError(w, http.StatusServiceUnavailable, codeEncryptionUnavailable,
			"The server has no encryption key configured, so keys cannot be stored.")
		return
	case err != nil:
		logError("updateProviderSettings", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not save provider settings.")
		return
	}
	writeJSON(w, http.StatusOK, toProviderSettingsResponse(settings))
}

func (s *Server) listProviderModels(w http.ResponseWriter, r *http.Request) {
	if !s.requireProvider(w) {
		return
	}
	u, _ := userFrom(r.Context())

	settings, err := s.Provider.Get(r.Context(), u.ID)
	if err != nil {
		logError("listProviderModels: settings", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load provider settings.")
		return
	}

	baseURL, _, apiKey, err := s.Provider.Credentials(r.Context(), u.ID, settings.Provider)
	info, _ := appprovider.Lookup(settings.Provider)
	switch {
	case errors.Is(err, appprovider.ErrNoKey):
		writeError(w, http.StatusUnprocessableEntity, codeProviderUnconfigured,
			"No API key configured for "+info.Label+".")
		return
	case err != nil:
		logError("listProviderModels: credentials", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not read provider credentials.")
		return
	}

	models, err := s.Provider.Models(r.Context(), settings.Provider, baseURL, apiKey)
	if err != nil {
		logError("listProviderModels: fetch", err)
		writeError(w, http.StatusBadGateway, codeUpstreamFailed,
			"The provider did not return a model list.")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": models})
}

func (s *Server) requireProvider(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Provider == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "The database is not configured.")
		return false
	}
	return true
}

func emptyToNil(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
