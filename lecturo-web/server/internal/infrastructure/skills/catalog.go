package skills

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	domain "lecturo/server/internal/domain/skills"
)

// HttpCatalog fetches and parses a skills marketplace JSON. It accepts either a
// {"skills":[…]} envelope or a bare array; an entry without an id is dropped.
type HttpCatalog struct {
	Client *http.Client
}

func (c HttpCatalog) Fetch(ctx context.Context, marketplaceURL string) ([]domain.RemoteSkill, error) {
	if strings.TrimSpace(marketplaceURL) == "" {
		return nil, errors.New("skills: no marketplace URL")
	}

	client := c.Client
	if client == nil {
		client = &http.Client{Timeout: 45 * time.Second}
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, marketplaceURL, nil)
	if err != nil {
		return nil, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("skills: fetch catalog: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("skills: catalog answered %d", resp.StatusCode)
	}

	return ParseCatalog(resp.Body)
}

// ParseCatalog reads the marketplace JSON from a reader.
func ParseCatalog(r io.Reader) ([]domain.RemoteSkill, error) {
	var root json.RawMessage
	if err := json.NewDecoder(r).Decode(&root); err != nil {
		return nil, fmt.Errorf("skills: parse catalog: %w", err)
	}

	// A bare array, or an envelope with a "skills" array.
	var array []json.RawMessage
	if len(root) > 0 && root[0] == '[' {
		if err := json.Unmarshal(root, &array); err != nil {
			return nil, fmt.Errorf("skills: parse catalog: %w", err)
		}
	} else {
		var envelope struct {
			Skills []json.RawMessage `json:"skills"`
		}
		if err := json.Unmarshal(root, &envelope); err != nil {
			return nil, fmt.Errorf("skills: parse catalog: %w", err)
		}
		array = envelope.Skills
	}

	out := make([]domain.RemoteSkill, 0, len(array))
	for _, entry := range array {
		var raw struct {
			ID          string `json:"id"`
			Name        string `json:"name"`
			Description string `json:"description"`
			Author      string `json:"author"`
			Version     string `json:"version"`
			Scope       string `json:"scope"`
			Trigger     string `json:"trigger"`
			Prompt      string `json:"prompt"`
			Price       string `json:"price"`
		}
		if err := json.Unmarshal(entry, &raw); err != nil {
			continue
		}
		if strings.TrimSpace(raw.ID) == "" {
			continue
		}
		name := raw.Name
		if name == "" {
			name = raw.ID
		}
		scope := raw.Scope
		if scope == "" {
			scope = "message"
		}
		trigger := raw.Trigger
		if trigger == "" {
			trigger = "on_demand"
		}
		out = append(out, domain.RemoteSkill{
			SkillID: raw.ID, Name: name, Description: raw.Description, Author: raw.Author,
			Version: raw.Version, Scope: scope, Trigger: trigger, Prompt: raw.Prompt, Price: raw.Price,
		})
	}
	return out, nil
}
