using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Lecturo.Application.Chat;
using Lecturo.Application.Settings;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Lecturo.Services;
using Title = Lecturo.Domain.Chat.ConversationTitle;

namespace Lecturo.ViewModels;

/// <summary>A conversation row, with the display values the Chats list needs.</summary>
public partial class ConversationItem : ObservableObject
{
    public Conversation Conversation { get; }

    public ConversationItem(Conversation conversation)
    {
        Conversation = conversation;
        Title = conversation.Title;
        RenameText = conversation.Title;
    }

    public long Id => Conversation.Id;

    [ObservableProperty] public partial string Title { get; set; } = "";
    [ObservableProperty] public partial bool IsRenaming { get; set; }
    [ObservableProperty] public partial string RenameText { get; set; } = "";

    public string Excerpt => Conversation.Excerpt.TextOrEmpty;
    public bool HasExcerpt => !string.IsNullOrWhiteSpace(Conversation.Excerpt.TextOrEmpty);
    public bool IsDraft => Conversation.IsDraft;

    public string DocumentTitle =>
        string.IsNullOrWhiteSpace(Conversation.DocumentTitle) ? "Unknown document" : Conversation.DocumentTitle!;

    public string ProviderLabel => ProviderCatalog.DisplayName(Conversation.Provider);

    public string SourceLine => $"{DocumentTitle} · {FormatTime(Conversation.CreatedAt)}";
    public string ProviderLine => $"{ProviderLabel} · {FormatTime(Conversation.UpdatedAt)}";

    private static string FormatTime(DateTime value) =>
        value.ToLocalTime().ToString("M/d/yy, h:mm tt");
}

public partial class ChatsViewModel : ViewModelBase
{
    private readonly ConversationService _conversations;

    public ObservableCollection<ConversationItem> Conversations { get; } = new();
    public ObservableCollection<ConversationItem> Drafts { get; } = new();

    [ObservableProperty] public partial bool HasConversations { get; set; }
    [ObservableProperty] public partial bool HasDrafts { get; set; }
    [ObservableProperty] public partial string SelectedTab { get; set; } = "Drafts";

    public bool IsDraftsTab => SelectedTab == "Drafts";
    public bool IsConversationsTab => SelectedTab == "Conversations";

    partial void OnSelectedTabChanged(string value)
    {
        OnPropertyChanged(nameof(IsDraftsTab));
        OnPropertyChanged(nameof(IsConversationsTab));
    }

    [RelayCommand]
    private void SelectTab(string tab) => SelectedTab = tab;

    /// <summary>Set by the shell to open a conversation in the reader's chat panel.</summary>
    public Action<Conversation>? OpenChat { get; set; }

    /// <summary>Set by the shell: a draft opens in the panel.</summary>
    public Action<Conversation>? OpenDraftRequested { get; set; }

    /// <summary>Set by the shell: a new chat starts from a draft.</summary>
    public Action? NewChatRequested { get; set; }

    public event Action? DataChanged;

    public ChatsViewModel(ConversationService conversations)
    {
        _conversations = conversations;
        Refresh();
    }

    [RelayCommand]
    public void Refresh()
    {
        Conversations.Clear();
        Drafts.Clear();
        foreach (var draft in _conversations.ListDrafts())
            Drafts.Add(new ConversationItem(draft));
        HasDrafts = Drafts.Count > 0;

        Conversations.Clear();
        foreach (var conversation in _conversations.ListConversations())
            Conversations.Add(new ConversationItem(conversation));
        HasConversations = Conversations.Count > 0;
    }

    [RelayCommand]
    private void Open(ConversationItem item) => OpenChat?.Invoke(item.Conversation);

    [RelayCommand]
    private void OpenDraft(ConversationItem item) => OpenDraftRequested?.Invoke(item.Conversation);

    [RelayCommand]
    private void NewChat() => NewChatRequested?.Invoke();

    [RelayCommand]
    private void Delete(ConversationItem item)
    {
        _conversations.Delete(item.Id);
        Conversations.Remove(item);
        HasConversations = Conversations.Count > 0;
        DataChanged?.Invoke();
    }

    /// <summary>Discarding a draft is deleting the conversation it would have been.</summary>
    [RelayCommand]
    private void DeleteDraft(ConversationItem item)
    {
        _conversations.Delete(item.Id);
        Drafts.Remove(item);
        HasDrafts = Drafts.Count > 0;
        DataChanged?.Invoke();
    }

    [RelayCommand]
    private static void BeginRename(ConversationItem item)
    {
        item.RenameText = item.Title;
        item.IsRenaming = true;
    }

    [RelayCommand]
    private void CommitRename(ConversationItem item)
    {
        var name = Title.Normalize(item.RenameText);
        if (name is null)
        {
            item.IsRenaming = false;
            return;
        }
        _conversations.Rename(item.Id, name);
        item.Title = name;
        item.IsRenaming = false;
        DataChanged?.Invoke();
    }

    [RelayCommand]
    private static void CancelRename(ConversationItem item) => item.IsRenaming = false;

}
