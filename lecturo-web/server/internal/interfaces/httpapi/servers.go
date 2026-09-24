package httpapi

import (
	"errors"
	"net/http"

	appservers "lecturo/server/internal/application/servers"
)

type serverSettingsResponse struct {
	CalibreURL         *string `json:"calibreUrl"`
	CalibreUser        *string `json:"calibreUser"`
	CalibreHasPassword bool    `json:"calibreHasPassword"`
	ZoteroUserID       *string `json:"zoteroUserId"`
	ZoteroHasKey       bool    `json:"zoteroHasKey"`
}

func toServerSettingsResponse(s appservers.Settings) serverSettingsResponse {
	return serverSettingsResponse{
		CalibreURL:         emptyToNil(s.CalibreURL),
		CalibreUser:        emptyToNil(s.CalibreUser),
		CalibreHasPassword: s.HasCalibrePassword,
		ZoteroUserID:       emptyToNil(s.ZoteroUserID),
		ZoteroHasKey:       s.HasZoteroKey,
	}
}

func (s *Server) getServerSettings(w http.ResponseWriter, r *http.Request) {
	if !s.requireServers(w) {
		return
	}
	u, _ := userFrom(r.Context())

	settings, err := s.Servers.Get(r.Context(), u.ID)
	if err != nil {
		logError("getServerSettings", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load server settings.")
		return
	}
	writeJSON(w, http.StatusOK, toServerSettingsResponse(settings))
}

func (s *Server) updateServerSettings(w http.ResponseWriter, r *http.Request) {
	if !s.requireServers(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		CalibreURL      *string `json:"calibreUrl"`
		CalibreUser     *string `json:"calibreUser"`
		CalibrePassword *string `json:"calibrePassword"`
		ZoteroUserID    *string `json:"zoteroUserId"`
		ZoteroAPIKey    *string `json:"zoteroApiKey"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}

	settings, err := s.Servers.Save(r.Context(), u.ID, appservers.Update{
		CalibreURL:      body.CalibreURL,
		CalibreUser:     body.CalibreUser,
		CalibrePassword: body.CalibrePassword,
		ZoteroUserID:    body.ZoteroUserID,
		ZoteroAPIKey:    body.ZoteroAPIKey,
	})
	if errors.Is(err, appservers.ErrEncryptionUnavailable) {
		writeError(w, http.StatusServiceUnavailable, codeEncryptionUnavailable,
			"The server has no encryption key configured, so passwords cannot be stored.")
		return
	}
	if err != nil {
		logError("updateServerSettings", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not save server settings.")
		return
	}
	writeJSON(w, http.StatusOK, toServerSettingsResponse(settings))
}

func (s *Server) requireServers(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Servers == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "The database is not configured.")
		return false
	}
	return true
}
