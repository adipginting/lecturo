using System;
using System.IO;
using System.Linq;
using Avalonia;
using Avalonia.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Lecturo.Application.Chat;
using Lecturo.Application.Library;
using Lecturo.Application.Skills;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Lecturo.Services;

namespace Lecturo.ViewModels;

/// <summary>
/// The shell: a navigation rail over the Library / Chats / Settings screens,
/// with the Reader as a full surface and the chat panel docked beside it.
/// </summary>
public partial class MainViewModel : ViewModelBase
{
    private readonly LibraryService _library;
    private readonly SkillService _skills;
    private readonly ConversationService _conversations;

    public NavItem[] NavItems { get; }
    public LibraryViewModel Library { get; }
    public ChatsViewModel Chats { get; }
    public SettingsViewModel Settings { get; }
    public ReaderViewModel Reader { get; }
    public ChatPanelViewModel ChatPanel { get; }

    [ObservableProperty] public partial ViewModelBase CurrentView { get; set; }
    [ObservableProperty] public partial NavItem? SelectedNav { get; set; }
    [ObservableProperty] public partial bool IsChatPanelOpen { get; set; }
    [ObservableProperty] public partial bool IsReading { get; set; }

    public MainViewModel(
        LibraryService library,
        SkillService skills,
        ConversationService conversations,
        LibraryViewModel libraryView,
        ChatsViewModel chats,
        SettingsViewModel settings,
        ReaderViewModel reader,
        ChatPanelViewModel chatPanel)
    {
        _library = library;
        _skills = skills;
        _conversations = conversations;
        Library = libraryView;
        Chats = chats;
        Settings = settings;
        Reader = reader;
        ChatPanel = chatPanel;

        NavItems =
        [
            new NavItem("Library", "Library", Icon("IconBook")),
            new NavItem("Chats", "Chats", Icon("IconChat")),
            new NavItem("Settings", "Settings", Icon("IconSettings")),
        ];

        Library.OpenDocument = OpenDocument;
        Chats.OpenChat = OpenConversation;
        Chats.NewChatRequested = () =>
        {
            Chats.SelectedTab = "Drafts";
            NavigateTo("Chats");
        };
        Chats.OpenDraftRequested = OpenConversation;

        Reader.RequestClose = CloseReader;
        Reader.OpenChatRequested = OpenReaderChat;
        Reader.DraftOpened = conversation =>
        {
            ChatPanel.Open(conversation);
            IsChatPanelOpen = true;
        };
        Reader.AppendToOpenConversation = text =>
        {
            // Only while a conversation for this document is actually on screen.
            if (!IsChatPanelOpen || !ChatPanel.IsConversation)
                return false;
            if (ChatPanel.DocumentId is not { } documentId || documentId != Reader.Document?.Id)
                return false;

            ChatPanel.AppendExcerpt(text);
            return true;
        };
        Reader.RunSkillRequested = skill => _ = RunSkillAsync(skill);
        ChatPanel.CloseRequested = () => IsChatPanelOpen = false;
        ChatPanel.DataChanged = () =>
        {
            Chats.Refresh();
            Reader.RefreshChatReach();
        };
        Library.DataChanged += () =>
        {
            Chats.Refresh();
        };

        CurrentView = libraryView;
        SelectedNav = NavItems[0];
    }

    partial void OnSelectedNavChanged(NavItem? value)
    {
        if (value is not null)
            NavigateTo(value.Key);
    }

    partial void OnIsChatPanelOpenChanged(bool value) => Reader.IsChatPanelOpen = value;

    [RelayCommand]
    private void NavigateTo(string key)
    {
        IsReading = key == "Reader";
        CurrentView = key switch
        {
            "Library" => Library,
            "Chats" => Chats,
            "Settings" => Settings,
            _ => CurrentView,
        };
    }

    /// <summary>
    /// The global back stack (G1), innermost first: a selection or crop, then the
    /// chat panel, then the reader, then a top-level screen. Returns whether
    /// anything was dismissed, so a window-level key handler can mark the event.
    /// </summary>
    public bool TryGoBack()
    {
        if (IsReading)
        {
            if (Reader.TryDismissOverlays())
                return true;
            if (IsChatPanelOpen)
            {
                IsChatPanelOpen = false;
                return true;
            }
            CloseReader();
            return true;
        }

        if (CurrentView is ChatsViewModel or SettingsViewModel)
        {
            NavigateTo("Library");
            SelectedNav = NavItems[0];
            return true;
        }

        return false;
    }

    /// <summary>
    /// A development aid: jump straight to a screen on launch (LECTURO_START),
    /// used for screenshots and smoke tests.
    /// </summary>
    public void StartAt(string key)
    {
        switch (key)
        {
            case "drafts":
                SelectedNav = NavItems[1];
                Chats.SelectedTab = "Drafts";
                break;
            case "chats":
                SelectedNav = NavItems[1];
                Chats.SelectedTab = "Conversations";
                break;
            case "settings":
                SelectedNav = NavItems[2];
                break;
            case "reader":
                if (Library.Documents.FirstOrDefault() is { } document)
                    OpenDocument(document.Document);
                break;
            case "reader-chat":
                if (Library.Documents.FirstOrDefault() is { } docChat)
                {
                    OpenDocument(docChat.Document);
                    OpenReaderChat();
                }
                break;
            case "draft-chat":
                if (Chats.Drafts.FirstOrDefault() is { } draft)
                    OpenConversation(draft.Conversation);
                break;
            case "conversation":
                if (Chats.Conversations.FirstOrDefault() is { } conversation)
                    OpenConversation(conversation.Conversation);
                break;
            case "calibre":
                SelectedNav = NavItems[0];
                Library.SelectedTab = "Calibre";
                Library.SyncRemoteCommand.Execute(null);
                break;
            case "zotero":
                SelectedNav = NavItems[0];
                Library.SelectedTab = "Zotero";
                Library.SyncRemoteCommand.Execute(null);
                break;
            case "zotero-download":
                SelectedNav = NavItems[0];
                Library.SelectedTab = "Zotero";
                Library.BrowseAndDownloadFirstCommand.Execute(null);
                break;
            case "calibre-download":
                SelectedNav = NavItems[0];
                Library.SelectedTab = "Calibre";
                Library.BrowseAndDownloadFirstCommand.Execute(null);
                break;
            case "skills":
                SelectedNav = NavItems[2];
                Settings.BrowseAndInstallFirstCommand.Execute(null);
                break;
        }
    }

    private void OpenDocument(Document document)
    {
        Reader.Load(document);
        ChatPanel.Clear();
        IsChatPanelOpen = false;
        IsReading = true;
        CurrentView = Reader;
    }

    /// <summary>
    /// The desktop "share intent" (S4): a URL or a document path handed to the app
    /// by the OS (command line, "open with"). Runs the normal S3/S2 flow and shows
    /// the Library, so the result lands where the reader can see it.
    /// </summary>
    public void HandleExternalInput(string argument)
    {
        if (string.IsNullOrWhiteSpace(argument))
            return;

        NavigateTo("Library");
        SelectedNav = NavItems[0];

        if (Uri.TryCreate(argument, UriKind.Absolute, out var uri)
            && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps))
        {
            Library.DownloadUrl = argument;
            Library.DownloadCommand.Execute(null);
            return;
        }

        if (File.Exists(argument))
            _ = Library.ImportPathsAsync(new[] { argument });
    }

    private void OpenConversation(Conversation conversation)
    {
        var document = conversation.DocumentId is { } id ? _library.Get(id) : null;
        if (document is not null)
            Reader.Load(document);
        else
            Reader.Clear();

        ChatPanel.Open(conversation);
        IsChatPanelOpen = true;
        IsReading = true;
        CurrentView = Reader;
    }

    /// <summary>
    /// The reader's chat icon: the document's newest draft, failing that the sent
    /// conversation it was last engaged with, failing that the chats surface.
    /// </summary>
    private void OpenReaderChat()
    {
        if (Reader.Document is { } document)
        {
            if (_conversations.DraftForDocument(document.Id) is { } draft)
            {
                OpenConversation(draft);
                return;
            }

            if (_conversations.ActiveForDocument(document.Id) is { } active)
            {
                OpenConversation(active);
                return;
            }
        }

        Chats.SelectedTab = "Conversations";
        NavigateTo("Chats");
    }

    private void CloseReader()
    {
        IsChatPanelOpen = false;
        IsReading = false;
        CurrentView = Library;
        SelectedNav = NavItems[0];
    }

    private async Task RunSkillAsync(Skill skill)
    {
        if (Reader.Document is not { } document)
            return;

        Reader.SkillStatus = $"Running \u201c{skill.Name}\u201d\u2026";
        try
        {
            var provider = ChatPanel.SelectedProvider?.Id ?? "deepseek";
            var conversationId = await _skills.RunAsync(document, skill, Reader.PageMarkdown, provider);
            Reader.SkillStatus = "Done.";
            Reader.RefreshSkillRuns();

            if (_conversations.Get(conversationId) is { } conversation)
            {
                ChatPanel.Open(conversation);
                IsChatPanelOpen = true;
            }
            Chats.Refresh();
        }
        catch (Exception ex)
        {
            Reader.SkillStatus = ex.Message;
            Reader.RefreshSkillRuns();
        }
    }

    private static Geometry Icon(string key)
    {
        if (Avalonia.Application.Current is { } app
            && app.Resources.TryGetResource(key, null, out var value)
            && value is Geometry geometry)
        {
            return geometry;
        }
        return new StreamGeometry();
    }
}
