using System.Collections.ObjectModel;
using System.Globalization;
using Avalonia.Media.Imaging;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Lecturo.Application.Chat;
using Lecturo.Application.Settings;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Lecturo.Services;
using Title = Lecturo.Domain.Chat.ConversationTitle;

namespace Lecturo.ViewModels;

public enum ChatPanelMode
{
    Empty,
    Draft,
    Conversation,
}

/// <summary>How a sent bubble is doing: in flight, delivered, or failed.</summary>
public enum MessageDelivery
{
    None,
    Sending,
    Sent,
    Failed,
}

/// <summary>One rendered message bubble. Observable so its delivery state can change in place.</summary>
public partial class ChatBubble : ObservableObject
{
    public ChatBubble(
        string id,
        MessageRole role,
        string content,
        bool superseded = false,
        DateTime createdAt = default,
        bool hasMath = false)
    {
        Id = id;
        Role = role;
        Content = content;
        Superseded = superseded;
        CreatedAt = createdAt;
        HasMath = hasMath;
    }

    public string Id { get; set; }
    public MessageRole Role { get; }
    public string Content { get; }
    public bool Superseded { get; }
    public bool HasMath { get; }
    public DateTime CreatedAt { get; set; }

    [ObservableProperty] public partial MessageDelivery Delivery { get; set; } = MessageDelivery.None;

    public bool IsUser => Role == MessageRole.User;
    public bool IsSummary => Role == MessageRole.Summary;

    /// <summary>"11:39" in the reader's local time.</summary>
    public string TimeLabel => CreatedAt == default ? "" : CreatedAt.ToLocalTime().ToString("HH:mm");

    /// <summary>
    /// The receipt under a bubble: a clock while in flight, a double check and the
    /// time once it lands, "Not sent" if it fails — the WhatsApp/Telegram idiom,
    /// without pretending we know it was *read*.
    /// </summary>
    public string ReceiptLabel => Delivery switch
    {
        MessageDelivery.Sending => "Sending…",
        MessageDelivery.Failed => "Not sent",
        MessageDelivery.Sent => $"✓✓ {TimeLabel}",
        _ => TimeLabel,
    };

    public bool IsSending => Delivery == MessageDelivery.Sending;
    public bool IsFailed => Delivery == MessageDelivery.Failed;
    public bool IsDelivered => Delivery == MessageDelivery.Sent;

    partial void OnDeliveryChanged(MessageDelivery value)
    {
        OnPropertyChanged(nameof(ReceiptLabel));
        OnPropertyChanged(nameof(IsSending));
        OnPropertyChanged(nameof(IsFailed));
        OnPropertyChanged(nameof(IsDelivered));
    }
}

/// <summary>
/// The docked chat panel. It opens a conversation — a draft or a sent one, which
/// differ only by <see cref="Domain.Conversation.IsDraft"/> — and sends drafts by
/// adding their first turn and marking them sent. Nothing is created at send time,
/// so a reply that never arrives leaves the draft a draft.
/// </summary>
public partial class ChatPanelViewModel : ViewModelBase
{
    private readonly ConversationService _conversations;
    private readonly IChatClient _chat;
    private readonly IPromptRepository _prompts;
    private readonly ISettingsStore _settings;

    private string? _conversationId;
    private string? _documentId;
    private bool _isDraft;
    private int? _lastPromptTokens;
    private DateTime? _lastCallAt;

    public ObservableCollection<ChatBubble> Messages { get; } = new();
    public ObservableCollection<Prompt> Prompts { get; } = new();
    public ProviderInfo[] Providers { get; } = ProviderCatalog.All;

    [ObservableProperty] public partial ChatPanelMode Mode { get; set; } = ChatPanelMode.Empty;
    [ObservableProperty] public partial string Composer { get; set; } = "";
    [ObservableProperty] public partial string ExcerptText { get; set; } = "";
    [ObservableProperty] public partial string ExcerptMeta { get; set; } = "";
    [ObservableProperty] public partial string ConversationTitle { get; set; } = "";
    [ObservableProperty] public partial ProviderInfo? SelectedProvider { get; set; }
    [ObservableProperty] public partial bool IsBusy { get; set; }
    [ObservableProperty] public partial string StatusMessage { get; set; } = "";
    [ObservableProperty] public partial string RenameText { get; set; } = "";
    [ObservableProperty] public partial bool IsRenaming { get; set; }
    [ObservableProperty] public partial int ContextEstimate { get; set; }
    [ObservableProperty] public partial bool IsOverContext { get; set; }
    [ObservableProperty] public partial Bitmap? Image { get; set; }
    [ObservableProperty] public partial string? ImagePath { get; set; }

    public bool HasImage => Image is not null;
    public string ContextTitle => HasImage ? "Screenshot" : "Original excerpt";

    partial void OnImageChanged(Bitmap? value)
    {
        OnPropertyChanged(nameof(HasImage));
        OnPropertyChanged(nameof(ContextTitle));
    }

    /// <summary>
    /// The size this conversation may reach before compaction. The model decides,
    /// unless the reader typed a threshold of their own — the setting keeps its
    /// default value to mean "ask the model".
    /// </summary>
    public int ContextThreshold
    {
        get
        {
            var configured = _settings.Settings.ContextThreshold;
            var over = configured == ModelWindow.DefaultBudget ? (int?)null : configured;
            var model = SelectedProvider is null ? null : _chat.ModelFor(SelectedProvider.Id);
            return ModelWindow.BudgetFor(model, over);
        }
    }

    public string ContextLabel => $"{FormatTokens(ContextEstimate)} / {FormatTokens(ContextThreshold)}";

    /// <summary>"~800", "~4.2k", "~12k".</summary>
    private static string FormatTokens(int tokens) => tokens switch
    {
        < 1_000 => $"~{tokens}",
        < 10_000 => $"~{(tokens / 1_000.0).ToString("0.0", CultureInfo.InvariantCulture)}k",
        _ => $"~{tokens / 1_000}k",
    };

    public bool IsEmpty => Mode == ChatPanelMode.Empty;
    public bool HasContext => Mode != ChatPanelMode.Empty;
    public bool IsConversation => Mode == ChatPanelMode.Conversation;

    /// <summary>The document the open conversation belongs to, if any.</summary>
    public string? DocumentId => _documentId;

    /// <summary>
    /// Appends an excerpt to this open conversation as its next message and sends
    /// it, so a selection made while reading joins the conversation rather than
    /// starting a draft (ADR 0005).
    /// </summary>
    public void AppendExcerpt(string text)
    {
        Composer = text;
        if (SendCommand.CanExecute(null))
            SendCommand.Execute(null);
    }
    public bool CanSend => !IsBusy && Composer.Trim().Length > 0;
    public bool HasPrompts => Prompts.Count > 0;

    /// <summary>Set by the shell to close (collapse) the panel.</summary>
    public Action? CloseRequested { get; set; }

    /// <summary>Raised after a send mutates conversations or drafts.</summary>
    public Action? DataChanged { get; set; }

    public ChatPanelViewModel(
        ConversationService conversations,
        IChatClient chat,
        IPromptRepository prompts,
        ISettingsStore settings)
    {
        _conversations = conversations;
        _chat = chat;
        _prompts = prompts;
        _settings = settings;
        SelectedProvider = Providers.FirstOrDefault(x => x.Id == settings.Settings.ChatProvider) ?? Providers[0];
        ReloadPrompts();
    }

    public void ReloadPrompts()
    {
        Prompts.Clear();
        foreach (var prompt in _prompts.List())
            Prompts.Add(prompt);
        OnPropertyChanged(nameof(HasPrompts));
    }

    public void Clear()
    {
        Mode = ChatPanelMode.Empty;
        _conversationId = null;
        _documentId = null;
        _isDraft = false;
        _lastPromptTokens = null;
        _lastCallAt = null;
        Image = null;
        ImagePath = null;
        Messages.Clear();
        ExcerptText = "";
        ExcerptMeta = "";
        ConversationTitle = "";
        Composer = "";
        StatusMessage = "";
        IsRenaming = false;
        NotifyDerived();
    }

    /// <summary>Opens a conversation in the panel: a draft waits for its first message.</summary>
    public void Open(Conversation conversation)
    {
        _conversationId = conversation.Id;
        _documentId = conversation.DocumentId;
        _isDraft = conversation.IsDraft;
        _lastPromptTokens = conversation.LastPromptTokens;
        _lastCallAt = conversation.LastCallAt;
        ImagePath = conversation.Excerpt.ImagePath;
        Image = LoadImage(ImagePath);
        LoadMessages(conversation.Id);
        Mode = conversation.IsDraft ? ChatPanelMode.Draft : ChatPanelMode.Conversation;
        ExcerptText = conversation.Excerpt.TextOrEmpty;
        ExcerptMeta = MetaFor(conversation);
        ConversationTitle = conversation.Title;
        RenameText = conversation.Title;
        SelectedProvider = Providers.FirstOrDefault(p => p.Id == conversation.Provider)
            ?? Providers.FirstOrDefault(p => p.Id == _settings.Settings.ChatProvider)
            ?? Providers[0];
        StatusMessage = "";
        NotifyDerived();
    }

    private static Bitmap? LoadImage(string? path)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
            return null;
        try { return new Bitmap(path); } catch { return null; }
    }

    partial void OnComposerChanged(string value)
    {
        OnPropertyChanged(nameof(CanSend));
        SendCommand.NotifyCanExecuteChanged();
        UpdateContext();
    }

    partial void OnExcerptTextChanged(string value) => UpdateContext();

    /// <summary>
    /// A live estimate of what the next call would send. Once a provider has
    /// reported what a reply cost, that count is the base and only what arrived
    /// since is added; until then the whole payload is estimated. The standing
    /// instruction is approximated by the excerpt, which dominates it.
    /// </summary>
    private void UpdateContext()
    {
        var active = Messages.Where(m => !m.Superseded).ToList();
        var sinceLastCall = _lastCallAt is { } at
            ? active.Where(m => m.CreatedAt > at).Sum(m => m.Content.Length)
            : 0;

        ContextEstimate = ContextCounter.Tokens(
            _lastPromptTokens,
            systemChars: ExcerptText.Length,
            activeChars: active.Sum(m => m.Content.Length),
            charsSinceLastCall: sinceLastCall,
            draftChars: Composer.Length);

        IsOverContext = ContextEstimate > ContextThreshold;
        OnPropertyChanged(nameof(ContextLabel));
        OnPropertyChanged(nameof(ContextThreshold));
    }

    partial void OnSelectedProviderChanged(ProviderInfo? value)
    {
        if (value is null)
            return;
        _settings.Settings.ChatProvider = value.Id;
        _settings.Save();
        if (_conversationId is { } id)
            _conversations.SwitchProvider(id, value.Id);
    }

    partial void OnModeChanged(ChatPanelMode value) => NotifyDerived();

    private void NotifyDerived()
    {
        OnPropertyChanged(nameof(IsEmpty));
        OnPropertyChanged(nameof(HasContext));
        OnPropertyChanged(nameof(IsConversation));
        OnPropertyChanged(nameof(CanSend));
        SendCommand.NotifyCanExecuteChanged();
        UpdateContext();
    }

    [RelayCommand]
    private void UsePrompt(Prompt prompt)
    {
        // A saved prompt is a send: whatever is already typed plus the prompt's
        // text goes as the message (S31).
        Composer = string.IsNullOrWhiteSpace(Composer)
            ? prompt.Content
            : $"{Composer.TrimEnd()}\n\n{prompt.Content}";

        if (SendCommand.CanExecute(null))
            SendCommand.Execute(null);
    }

    [RelayCommand]
    private void BeginRename()
    {
        RenameText = ConversationTitle;
        IsRenaming = true;
    }

    [RelayCommand]
    private void CommitRename()
    {
        var name = Title.Normalize(RenameText);
        if (_conversationId is { } id && name is not null)
        {
            _conversations.Rename(id, name);
            ConversationTitle = name;
            DataChanged?.Invoke();
        }
        IsRenaming = false;
    }

    [RelayCommand]
    private void CancelRename() => IsRenaming = false;

    [RelayCommand]
    private void Collapse() => CloseRequested?.Invoke();

    [RelayCommand(CanExecute = nameof(CanSend))]
    private async Task SendAsync()
    {
        var text = Composer.Trim();
        if (text.Length == 0 || SelectedProvider is null || IsBusy || _conversationId is not { } conversationId)
            return;

        if (SelectedProvider.Stubbed)
        {
            StatusMessage = $"{SelectedProvider.DisplayName} has no public chat API.";
            return;
        }

        IsBusy = true;
        StatusMessage = "";

        // The reader's message appears at once, marked "Sending…", so the composer
        // is never the only sign it was sent.
        var pending = new ChatBubble("", MessageRole.User, text, createdAt: DateTime.UtcNow)
        {
            Delivery = MessageDelivery.Sending,
        };
        Messages.Add(pending);
        Composer = "";

        try
        {
            var result = await _conversations.SendAsync(conversationId, SelectedProvider.Id, text);

            pending.Id = result.User.Id;
            pending.CreatedAt = result.User.CreatedAt;
            pending.Delivery = result.Error is null ? MessageDelivery.Sent : MessageDelivery.Failed;

            if (result.Assistant is { } assistant)
                Messages.Add(Bubble(assistant.Id, assistant.Role, assistant.Content, assistant.CreatedAt));

            // From here the counter starts from what the provider counted.
            if (result.PromptTokens is { } tokens)
            {
                _lastPromptTokens = tokens;
                _lastCallAt = result.AnsweredAt;
            }

            if (result.BecameSent)
            {
                // The reply is what makes it a conversation, and what names it.
                _isDraft = false;
                Mode = ChatPanelMode.Conversation;
                ConversationTitle = result.Title;
                RenameText = result.Title;
            }

            if (result.Error is { } error)
                StatusMessage = error;
            else
                DataChanged?.Invoke();
        }
        catch (Exception ex)
        {
            // The draft is still a draft: retry sends it, it never became a conversation.
            pending.Delivery = MessageDelivery.Failed;
            StatusMessage = ex.Message;
        }
        finally
        {
            IsBusy = false;
            UpdateContext();
            NotifyDerived();
        }
    }

    private void LoadMessages(string conversationId)
    {
        Messages.Clear();
        foreach (var message in _conversations.Messages(conversationId))
        {
            var bubble = Bubble(message.Id, message.Role, message.Content, message.CreatedAt, message.Superseded);
            if (bubble.IsUser)
                bubble.Delivery = MessageDelivery.Sent;
            Messages.Add(bubble);
        }
    }

    /// <summary>
    /// A bubble knows whether it carries maths, so a reply of plain prose never
    /// pays for the WebView that typesets formulas — and the setting is obeyed
    /// when the message is shown rather than when it was written.
    /// </summary>
    private ChatBubble Bubble(string id, MessageRole role, string content, DateTime createdAt, bool superseded = false) =>
        new(id, role, content, superseded, createdAt,
            hasMath: _settings.Settings.TypesetFormulas && MathSplit.HasMath(content));

    /// <summary>Folds the active turns into one summary; nothing to compact is not an error.</summary>
    [RelayCommand]
    private async Task CompactAsync()
    {
        if (_conversationId is not { } id || SelectedProvider is null || IsBusy)
            return;

        if (SelectedProvider.Stubbed)
        {
            StatusMessage = $"{SelectedProvider.DisplayName} has no public chat API.";
            return;
        }

        try
        {
            IsBusy = true;
            StatusMessage = "Compacting\u2026";
            var result = await _conversations.CompactAsync(id, SelectedProvider.Id);

            if (result.NothingToCompact)
            {
                StatusMessage = "Nothing to compact yet.";
                return;
            }
            if (result.Error is { } error)
            {
                // A failed compaction must not cost the reader their turn.
                StatusMessage = error;
                return;
            }

            // The summary replaces the history those counts described.
            _lastPromptTokens = null;
            _lastCallAt = null;
            LoadMessages(id);
            StatusMessage = "Conversation compacted.";
            DataChanged?.Invoke();
        }
        finally
        {
            IsBusy = false;
            UpdateContext();
            NotifyDerived();
        }
    }

    private static string MetaFor(Conversation conversation)
    {
        var source = string.IsNullOrWhiteSpace(conversation.DocumentTitle)
            ? "Unknown document"
            : conversation.DocumentTitle;
        if (conversation.Excerpt.HasImage)
            return $"{source} · screenshot";
        if (string.IsNullOrWhiteSpace(conversation.Locator))
            return source;
        var where = conversation.DocumentFormat == DocumentFormat.Pdf
            ? $"page {conversation.Locator}"
            : $"chapter {conversation.Locator}";
        return $"{source} · {where}";
    }
}
