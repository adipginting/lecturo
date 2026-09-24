package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	appchat "lecturo/server/internal/application/chat"
	appprovider "lecturo/server/internal/application/provider"
	domainchat "lecturo/server/internal/domain/chat"
	settingsdomain "lecturo/server/internal/domain/settings"
	"lecturo/server/internal/infrastructure/llm"
)

const (
	chatTimeout     = 120 * time.Second
	defaultMaxToken = 1024
)

// errVisionUnsupported means the selected provider cannot read the crop.
var errVisionUnsupported = errors.New("the selected model cannot read images")

type messageResponse struct {
	ID             string    `json:"id"`
	ConversationID string    `json:"conversationId"`
	Role           string    `json:"role"`
	Content        string    `json:"content"`
	IsSummary      bool      `json:"isSummary"`
	Superseded     bool      `json:"superseded"`
	CreatedAt      time.Time `json:"createdAt"`
}

func toMessageResponse(m domainchat.Message) messageResponse {
	return messageResponse{
		ID: m.ID, ConversationID: m.ConversationID, Role: m.Role,
		Content: m.Content, IsSummary: m.IsSummary, Superseded: m.Superseded,
		CreatedAt: m.CreatedAt,
	}
}

type conversationResponse struct {
	ID             string     `json:"id"`
	DocumentID     *string    `json:"documentId"`
	DocumentTitle  *string    `json:"documentTitle"`
	Title          string     `json:"title"`
	Excerpt        string     `json:"excerpt"`
	Locator        *string    `json:"locator"`
	PromptID       *string    `json:"promptId"`
	ContextImageID *string    `json:"contextImageId"`
	Provider       string     `json:"provider"`
	IsDraft        bool       `json:"isDraft"`
	SentAt         *time.Time `json:"sentAt"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
}

func toConversationResponse(c domainchat.Conversation) conversationResponse {
	excerpt := ""
	if c.Excerpt.Text != nil {
		excerpt = *c.Excerpt.Text
	}
	return conversationResponse{
		ID: c.ID, DocumentID: c.DocumentID, DocumentTitle: c.DocumentTitle,
		Title: c.Title, Excerpt: excerpt, Locator: c.DocumentLocator, PromptID: c.PromptID,
		ContextImageID: c.Excerpt.ImageID, Provider: c.Provider,
		IsDraft: c.IsDraft(), SentAt: c.SentAt, CreatedAt: c.CreatedAt, UpdatedAt: c.UpdatedAt,
	}
}

type conversationDetailResponse struct {
	conversationResponse
	Messages []messageResponse `json:"messages"`
}

func (s *Server) listConversations(w http.ResponseWriter, r *http.Request) {
	if !s.requireChat(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var documentID *string
	if raw := r.URL.Query().Get("documentId"); raw != "" {
		documentID = &raw
	}

	status := r.URL.Query().Get("status")
	items, err := s.Chat.List(r.Context(), u.ID, status, documentID)
	if err != nil {
		logError("listConversations", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load conversations.")
		return
	}

	out := make([]conversationResponse, 0, len(items))
	for _, c := range items {
		out = append(out, toConversationResponse(c))
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": out})
}

func (s *Server) createConversation(w http.ResponseWriter, r *http.Request) {
	if !s.requireChat(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		DocumentID *string `json:"documentId"`
		Excerpt    string  `json:"excerpt"`
		Locator    *string `json:"locator"`
		PromptID   *string `json:"promptId"`
		ImageID    *string `json:"imageId"`
		Provider   string  `json:"provider"`
		Model      *string `json:"model"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}
	excerpt := domainchat.Excerpt{}
	if text := strings.TrimSpace(body.Excerpt); text != "" {
		excerpt.Text = &text
	}
	excerpt.ImageID = body.ImageID
	if excerpt.IsEmpty() {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload,
			"An excerpt or a crop is needed.")
		return
	}

	providerID := body.Provider
	if providerID == "" {
		settings, err := s.Provider.Get(r.Context(), u.ID)
		if err != nil {
			logError("createConversation: settings", err)
			writeError(w, http.StatusInternalServerError, codeInternal, "Could not load provider settings.")
			return
		}
		providerID = settings.Provider
	}
	if _, ok := appprovider.Lookup(providerID); !ok {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "Unknown provider.")
		return
	}

	// The excerpt keeps its trace, copied so it survives the document.
	var documentTitle, documentLocator *string
	if body.DocumentID != nil && s.Documents != nil {
		if doc, err := s.Documents.Get(r.Context(), u.ID, *body.DocumentID); err == nil {
			title := doc.Title
			documentTitle = &title
			documentLocator = doc.Locator
		}
	}
	if body.Locator != nil {
		documentLocator = body.Locator
	}

	draft, err := s.Chat.StartDraft(r.Context(), appchat.DraftInput{
		UserID: u.ID, Provider: providerID, Excerpt: excerpt,
		DocumentID: body.DocumentID, DocumentTitle: documentTitle,
		DocumentLocator: documentLocator, PromptID: body.PromptID,
	})
	if err != nil {
		logError("createConversation", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not start the draft.")
		return
	}
	writeJSON(w, http.StatusCreated, toConversationResponse(draft))
}

func (s *Server) getConversation(w http.ResponseWriter, r *http.Request) {
	if !s.requireChat(w) {
		return
	}
	u, _ := userFrom(r.Context())

	c, messages, err := s.Chat.Get(r.Context(), u.ID, r.PathValue("id"))
	if errors.Is(err, appchat.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That conversation does not exist.")
		return
	}
	if err != nil {
		logError("getConversation", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load the conversation.")
		return
	}

	out := conversationDetailResponse{
		conversationResponse: toConversationResponse(c),
		Messages:             make([]messageResponse, 0, len(messages)),
	}
	for _, m := range messages {
		out.Messages = append(out.Messages, toMessageResponse(m))
	}
	writeJSON(w, http.StatusOK, out)
}

func (s *Server) updateConversation(w http.ResponseWriter, r *http.Request) {
	if !s.requireChat(w) {
		return
	}
	u, _ := userFrom(r.Context())

	var body struct {
		Title    *string `json:"title"`
		Provider *string `json:"provider"`
		Model    *string `json:"model"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}
	if body.Provider != nil {
		if _, ok := appprovider.Lookup(*body.Provider); !ok {
			writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "Unknown provider.")
			return
		}
	}

	id := r.PathValue("id")
	c, _, err := s.Chat.Get(r.Context(), u.ID, id)
	if errors.Is(err, appchat.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That conversation does not exist.")
		return
	}
	if err != nil {
		logError("updateConversation", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not update the conversation.")
		return
	}

	// A blank name leaves the old title; the domain decides.
	if body.Title != nil {
		if c, err = s.Chat.Rename(r.Context(), u.ID, id, *body.Title); err != nil {
			logError("updateConversation: rename", err)
			writeError(w, http.StatusInternalServerError, codeInternal, "Could not rename the conversation.")
			return
		}
	}
	if body.Provider != nil || body.Model != nil {
		if c, err = s.Chat.SwitchProvider(r.Context(), u.ID, id, body.Provider, body.Model); err != nil {
			logError("updateConversation: provider", err)
			writeError(w, http.StatusInternalServerError, codeInternal, "Could not switch provider.")
			return
		}
	}
	writeJSON(w, http.StatusOK, toConversationResponse(c))
}

func (s *Server) deleteConversation(w http.ResponseWriter, r *http.Request) {
	if !s.requireChat(w) {
		return
	}
	u, _ := userFrom(r.Context())

	err := s.Chat.Delete(r.Context(), u.ID, r.PathValue("id"))
	if errors.Is(err, appchat.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That conversation does not exist.")
		return
	}
	if err != nil {
		logError("deleteConversation", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not delete the conversation.")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) listMessages(w http.ResponseWriter, r *http.Request) {
	if !s.requireChat(w) {
		return
	}
	u, _ := userFrom(r.Context())

	// Confirm the conversation belongs to the user before listing.
	if _, _, err := s.Chat.Get(r.Context(), u.ID, r.PathValue("id")); err != nil {
		if errors.Is(err, appchat.ErrNotFound) {
			writeError(w, http.StatusNotFound, codeNotFound, "That conversation does not exist.")
			return
		}
		logError("listMessages", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load messages.")
		return
	}

	messages, err := s.Chat.Messages(r.Context(), u.ID, r.PathValue("id"))
	if err != nil {
		logError("listMessages", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load messages.")
		return
	}
	out := make([]messageResponse, 0, len(messages))
	for _, m := range messages {
		out = append(out, toMessageResponse(m))
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": out})
}

func (s *Server) createMessage(w http.ResponseWriter, r *http.Request) {
	if !s.requireChat(w) {
		return
	}
	u, _ := userFrom(r.Context())
	conversationID := r.PathValue("id")

	var body struct {
		Content string  `json:"content"`
		ImageID *string `json:"imageId"`
	}
	if !decodeJSON(w, r, &body) {
		return
	}
	if strings.TrimSpace(body.Content) == "" && body.ImageID == nil {
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "The message is empty.")
		return
	}

	conversation, _, err := s.Chat.Get(r.Context(), u.ID, conversationID)
	if errors.Is(err, appchat.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That conversation does not exist.")
		return
	}
	if err != nil {
		logError("createMessage: get", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load the conversation.")
		return
	}

	call, err := s.prepareCall(r.Context(), u.ID, conversation)
	if err != nil {
		s.writeCallError(w, err)
		return
	}

	// Before the turn is committed, compact if it would cross the model's budget.
	call = s.maybeAutoCompact(r.Context(), u.ID, conversation, call, body.Content)

	if body.ImageID != nil {
		part, err := s.imagePart(r.Context(), u.ID, call.request.Model, *body.ImageID)
		if err != nil {
			s.writeCallError(w, err)
			return
		}
		if _, err := s.Chat.AppendImageMessage(r.Context(), u.ID, conversationID, body.Content, *body.ImageID); err != nil {
			logError("createMessage: append image", err)
			writeError(w, http.StatusInternalServerError, codeInternal, "Could not save the message.")
			return
		}
		// The image rides the turn being sent — the latest user message.
		call.request.Images = []llm.ImagePart{part}
	} else if _, err := s.Chat.AppendMessage(r.Context(), u.ID, conversationID, "user", body.Content); err != nil {
		logError("createMessage: append user", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not save the message.")
		return
	}

	// The reply is generated from the history plus the message just sent.
	call.messages = append(call.messages, llm.Message{Role: "user", Content: body.Content})

	stream := r.URL.Query().Get("stream") != "false"
	if !stream {
		reply, err := s.runCompletion(r.Context(), call, call.messages)
		if err != nil {
			s.writeCallError(w, err)
			return
		}
		assistant, err := s.Chat.AppendMessage(r.Context(), u.ID, conversationID, "assistant", reply)
		if err != nil {
			logError("createMessage: append assistant", err)
			writeError(w, http.StatusInternalServerError, codeInternal, "Could not save the reply.")
			return
		}

		// Only now, with the reply in hand, does a draft become a conversation.
		if conversation.IsDraft() {
			if _, err := s.Chat.MarkSent(r.Context(), u.ID, conversationID, body.Content); err != nil {
				logError("createMessage: mark sent", err)
			}
		}
		writeJSON(w, http.StatusOK, toMessageResponse(assistant))
		return
	}

	s.streamCompletion(w, r, u.ID, conversationID, call, body.Content)
}

func (s *Server) compactConversation(w http.ResponseWriter, r *http.Request) {
	if !s.requireChat(w) {
		return
	}
	u, _ := userFrom(r.Context())
	conversationID := r.PathValue("id")

	conversation, _, err := s.Chat.Get(r.Context(), u.ID, conversationID)
	if errors.Is(err, appchat.ErrNotFound) {
		writeError(w, http.StatusNotFound, codeNotFound, "That conversation does not exist.")
		return
	}
	if err != nil {
		logError("compact: get", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load the conversation.")
		return
	}

	active, err := s.Chat.ActiveMessages(r.Context(), u.ID, conversationID)
	if err != nil {
		logError("compact: active", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not load messages.")
		return
	}

	// Nothing worth compacting unless there is at least one earlier exchange.
	exchanges := 0
	history := make([]llm.Message, 0, len(active))
	for _, m := range active {
		if m.Role == "user" || m.Role == "assistant" {
			exchanges++
		}
		history = append(history, llm.Message{Role: m.Role, Content: m.Content})
	}
	if exchanges < 2 {
		writeError(w, http.StatusUnprocessableEntity, codeNothingToCompact, "Nothing to compact yet.")
		return
	}

	call, err := s.prepareCall(r.Context(), u.ID, conversation)
	if err != nil {
		s.writeCallError(w, err)
		return
	}
	call.messages = history

	summary, err := s.summarise(r.Context(), call)
	if err != nil {
		s.writeCallError(w, err)
		return
	}

	message, err := s.Chat.Compact(r.Context(), u.ID, conversationID, summary)
	if err != nil {
		logError("compact: summarise", err)
		writeError(w, http.StatusInternalServerError, codeInternal, "Could not compact the conversation.")
		return
	}
	writeJSON(w, http.StatusOK, toMessageResponse(message))
}

// call bundles everything needed to reach the provider for one conversation.
type call struct {
	client   llm.Client
	request  llm.Request
	system   string
	messages []llm.Message
}

func (s *Server) prepareCall(ctx context.Context, userID string, conversation domainchat.Conversation) (*call, error) {
	if s.Provider == nil {
		return nil, appprovider.ErrEncryptionUnavailable
	}
	baseURL, model, apiKey, err := s.Provider.Credentials(ctx, userID, conversation.Provider)
	if err != nil {
		return nil, err
	}
	if model == "" {
		if info, ok := appprovider.Lookup(conversation.Provider); ok {
			model = info.DefaultModel
		}
	}

	client, err := llm.For(conversation.Provider)
	if err != nil {
		return nil, err
	}

	system, err := s.systemPrompt(ctx, userID, conversation)
	if err != nil {
		return nil, err
	}

	active, err := s.Chat.ActiveMessages(ctx, userID, conversation.ID)
	if err != nil {
		return nil, err
	}
	messages := make([]llm.Message, 0, len(active))
	for _, m := range active {
		messages = append(messages, llm.Message{Role: m.Role, Content: m.Content})
	}

	call := &call{
		client: client,
		request: llm.Request{
			Provider: conversation.Provider, BaseURL: baseURL, APIKey: apiKey,
			Model: model, MaxTokens: defaultMaxToken,
		},
		system:   system,
		messages: messages,
	}

	if conversation.Excerpt.ImageID != nil {
		part, err := s.imagePart(ctx, userID, model, *conversation.Excerpt.ImageID)
		if err != nil {
			return nil, err
		}
		call.request.Images = []llm.ImagePart{part}
	}
	return call, nil
}

// imagePart loads a stored screenshot and checks that the model can read images.
func (s *Server) imagePart(ctx context.Context, userID, model, imageID string) (llm.ImagePart, error) {
	override, err := s.Provider.VisionOverride(ctx, userID, model)
	if err != nil {
		return llm.ImagePart{}, err
	}
	if !settingsdomain.VisionWithOverride(model, override) {
		return llm.ImagePart{}, errVisionUnsupported
	}
	if s.Images == nil {
		return llm.ImagePart{}, errors.New("image storage is not configured")
	}
	data, mediaType, err := s.Images.Load(ctx, userID, imageID)
	if err != nil {
		return llm.ImagePart{}, err
	}
	return llm.ImagePart{MediaType: mediaType, Data: data}, nil
}

func (s *Server) systemPrompt(ctx context.Context, userID string, conversation domainchat.Conversation) (string, error) {
	var parts []string

	if conversation.PromptID != nil {
		if prompt, err := s.Prompts.Get(ctx, userID, *conversation.PromptID); err == nil && strings.TrimSpace(prompt.Body) != "" {
			parts = append(parts, strings.TrimSpace(prompt.Body))
		}
	}

	parts = append(parts,
		"You are helping a reader understand what they were reading. Answer their questions about it; quote the passage when it helps.")
	parts = append(parts,
		"Write mathematics as TeX: \\( … \\) inline and \\[ … \\] for display.")

	if conversation.Excerpt.Text != nil && strings.TrimSpace(*conversation.Excerpt.Text) != "" {
		parts = append(parts, "Passage:\n"+*conversation.Excerpt.Text)
	} else {
		parts = append(parts, "The reader attached a screenshot of the page rather than a text selection.")
	}
	return strings.Join(parts, "\n\n"), nil
}

const (
	summariseSystem      = "You compress conversations. Preserve decisions, facts and open questions; drop pleasantries."
	summariseInstruction = "Summarise the conversation so far in one compact paragraph."
)

// summarise asks the conversation's own model to fold its history into one
// summary message. The excerpt is never compacted.
func (s *Server) summarise(parent context.Context, c *call) (string, error) {
	ctx, cancel := context.WithTimeout(parent, chatTimeout)
	defer cancel()

	history := append([]llm.Message{}, c.messages...)
	history = append(history, llm.Message{Role: "user", Content: summariseInstruction})

	req := c.request
	req.System = summariseSystem
	req.Messages = history

	var builder strings.Builder
	_, err := c.client.Stream(ctx, req, func(text string) error {
		builder.WriteString(text)
		return nil
	})
	if err != nil {
		return "", err
	}
	return builder.String(), nil
}

// maybeAutoCompact compacts before a turn that would cross the model's budget.
// A failed compaction is swallowed: the message goes anyway, because losing a
// turn to save tokens is a bad trade.
func (s *Server) maybeAutoCompact(parent context.Context, userID string, conversation domainchat.Conversation, c *call, incoming string) *call {
	budget := domainchat.Budget(domainchat.WindowFor(c.request.Model))

	estimate := domainchat.EstimateTokens(c.system) + domainchat.EstimateTokens(incoming)
	if conversation.Excerpt.Text != nil {
		estimate += domainchat.EstimateTokens(*conversation.Excerpt.Text)
	}
	for _, m := range c.messages {
		estimate += domainchat.EstimateTokens(m.Content)
	}
	if estimate <= budget {
		return c
	}

	summary, err := s.summarise(parent, c)
	if err != nil {
		logError("maybeAutoCompact: summarise", err)
		return c
	}
	if _, err := s.Chat.Compact(parent, userID, conversation.ID, summary); err != nil {
		logError("maybeAutoCompact: store", err)
		return c
	}

	active, err := s.Chat.ActiveMessages(parent, userID, conversation.ID)
	if err != nil {
		return c
	}
	messages := make([]llm.Message, 0, len(active))
	for _, m := range active {
		messages = append(messages, llm.Message{Role: m.Role, Content: m.Content})
	}
	c.messages = messages
	return c
}

func (s *Server) runCompletion(parent context.Context, c *call, messages []llm.Message) (string, error) {
	ctx, cancel := context.WithTimeout(parent, chatTimeout)
	defer cancel()

	req := c.request
	req.System = c.system
	req.Messages = messages

	var builder strings.Builder
	_, err := c.client.Stream(ctx, req, func(text string) error {
		builder.WriteString(text)
		return nil
	})
	if err != nil {
		return "", err
	}
	return builder.String(), nil
}

func (s *Server) streamCompletion(w http.ResponseWriter, r *http.Request, userID, conversationID string, c *call, firstMessage string) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		writeError(w, http.StatusInternalServerError, codeInternal, "Streaming is not supported.")
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), chatTimeout)
	defer cancel()

	req := c.request
	req.System = c.system
	req.Messages = c.messages

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
	flusher.Flush()

	writeEvent := func(event string, payload any) {
		body, err := json.Marshal(payload)
		if err != nil {
			return
		}
		_, _ = fmt.Fprintf(w, "event: %s\ndata: %s\n\n", event, body)
		flusher.Flush()
	}

	var builder strings.Builder
	_, err := c.client.Stream(ctx, req, func(text string) error {
		builder.WriteString(text)
		writeEvent("delta", map[string]string{"text": text})
		return nil
	})
	if err != nil {
		writeEvent("error", map[string]apiError{"error": {Code: codeUpstreamFailed, Message: err.Error()}})
		return
	}

	assistant, err := s.Chat.AppendMessage(ctx, userID, conversationID, "assistant", builder.String())
	if err != nil {
		logError("streamCompletion: append", err)
		writeEvent("error", map[string]apiError{"error": {Code: codeInternal, Message: "The reply could not be saved."}})
		return
	}

	// The reply landed: a draft becomes a conversation (no-op once sent).
	if _, err := s.Chat.MarkSent(ctx, userID, conversationID, firstMessage); err != nil {
		logError("streamCompletion: mark sent", err)
	}
	writeEvent("done", map[string]any{"message": toMessageResponse(assistant)})
}

func (s *Server) writeCallError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, appprovider.ErrNoKey):
		writeError(w, http.StatusUnprocessableEntity, codeProviderUnconfigured,
			"No API key is configured for this provider.")
	case errors.Is(err, appprovider.ErrEncryptionUnavailable):
		writeError(w, http.StatusServiceUnavailable, codeEncryptionUnavailable,
			"The server has no encryption key configured.")
	case errors.Is(err, llm.ErrUnsupported):
		writeError(w, http.StatusUnprocessableEntity, codeInvalidPayload, "That provider is not supported.")
	case errors.Is(err, errVisionUnsupported):
		writeError(w, http.StatusUnprocessableEntity, codeVisionUnsupported,
			"This model cannot read images. Choose a vision-capable provider in Settings.")
	default:
		logError("provider call", err)
		writeError(w, http.StatusBadGateway, codeUpstreamFailed, "The model could not be reached.")
	}
}

func (s *Server) requireChat(w http.ResponseWriter) bool {
	if !s.requireDB(w) {
		return false
	}
	if s.Chat == nil {
		writeError(w, http.StatusServiceUnavailable, codeInternal, "The database is not configured.")
		return false
	}
	return true
}
