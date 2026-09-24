// Package settings is the domain model for LLM provider selection and for the
// Calibre/Zotero connection details. Secrets are never part of these types;
// only their presence is.
package settings

import "errors"

// Provider describes one LLM vendor the app can talk to.
type Provider struct {
	ID             string
	Label          string
	DefaultBaseURL string
	DefaultModel   string
	RequiresKey    bool
	SupportsImages bool
	Supported      bool
	Anthropic      bool
}

// Catalog is the known providers. Lecturo Cloud's base URL is filled in from
// configuration at wiring time.
var Catalog = []Provider{
	{ID: "openai", Label: "OpenAI", DefaultBaseURL: "https://api.openai.com/v1", DefaultModel: "gpt-4o-mini", RequiresKey: true, SupportsImages: true, Supported: true},
	{ID: "kimi", Label: "Kimi", DefaultBaseURL: "https://api.moonshot.ai/v1", DefaultModel: "moonshot-v1-8k", RequiresKey: true, Supported: true},
	{ID: "openrouter", Label: "OpenRouter", DefaultBaseURL: "https://openrouter.ai/api/v1", DefaultModel: "openai/gpt-4o-mini", RequiresKey: true, SupportsImages: true, Supported: true},
	{ID: "deepseek", Label: "DeepSeek", DefaultBaseURL: "https://api.deepseek.com", DefaultModel: "deepseek-chat", RequiresKey: true, Supported: true},
	{ID: "anthropic", Label: "Anthropic Claude", DefaultBaseURL: "https://api.anthropic.com", DefaultModel: "claude-3-5-sonnet-latest", RequiresKey: true, SupportsImages: true, Supported: true, Anthropic: true},
	{ID: "lecturo", Label: "Lecturo Cloud", SupportsImages: true, Supported: true},
	{ID: "copilot", Label: "GitHub Copilot", Supported: false},
}

// DefaultProvider is used before a user chooses one.
const DefaultProvider = "deepseek"

// Lookup finds a provider by id.
func Lookup(id string) (Provider, bool) {
	for _, p := range Catalog {
		if p.ID == id {
			return p, true
		}
	}
	return Provider{}, false
}

// Status is the client-facing view of one provider.
type Status struct {
	ID             string
	Label          string
	SupportsImages bool
	Supported      bool
	Configured     bool
}

// Selection is the resolved provider configuration for a user.
type Selection struct {
	Provider  string
	BaseURL   string
	Model     string
	HasKey    bool
	Providers []Status
	// ModelSupportsImages is the resolved vision capability for Model (family
	// default, or the stored override).
	ModelSupportsImages bool
	// Destination says where a call is routed, for the chat to surface:
	// "Your key" or "Lecturo relay".
	Destination string
}

// DestinationFor reports where a provider's calls are routed.
func DestinationFor(providerID string) string {
	if providerID == "lecturo" {
		return "Lecturo relay"
	}
	return "Your key"
}

// Credentials are the values needed to call a provider (decrypted key).
type Credentials struct {
	BaseURL string
	Model   string
	APIKey  string
}

// Servers is the client-facing view of Calibre/Zotero settings.
type Servers struct {
	CalibreURL         string
	CalibreUser        string
	HasCalibrePassword bool
	ZoteroUserID       string
	HasZoteroKey       bool
}

// ServerCredentials are the decrypted Calibre/Zotero values.
type ServerCredentials struct {
	CalibreURL      string
	CalibreUser     string
	CalibrePassword string
	ZoteroUserID    string
	ZoteroAPIKey    string
}

// Domain errors.
var (
	ErrUnknownProvider       = errors.New("settings: unknown provider")
	ErrUnsupportedProvider   = errors.New("settings: provider not supported")
	ErrNoKey                 = errors.New("settings: no API key configured")
	ErrEncryptionUnavailable = errors.New("settings: encryption key not configured")
)
