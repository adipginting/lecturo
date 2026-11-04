using System.Collections.ObjectModel;
using Avalonia.Media.Imaging;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Lecturo.Application.Chat;
using Lecturo.Application.Library;
using Lecturo.Application.Skills;
using Lecturo.Application.Settings;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Lecturo.Domain.Reading;
using Lecturo.Services;

namespace Lecturo.ViewModels;

/// <summary>One rendered PDF page in the continuous reader.</summary>
public sealed record ReaderPage(
    int Index,
    Bitmap Image,
    IReadOnlyList<PdfWord> Words,
    int PixelWidth,
    int PixelHeight);

/// <summary>
/// A document open for reading. EPUB and text-based PDFs render natively
/// (scrolling + selectable text); image-only PDFs are handed to the platform viewer.
/// Positions are persisted per document.
/// </summary>
public partial class ReaderViewModel : ViewModelBase
{
    private readonly ReaderService _reader;
    private readonly LibraryService _library;
    private readonly ConversationService _conversations;
    private readonly SkillService _skills;
    private readonly ISettingsStore _settings;
    private ReaderContent? _content;

    public ObservableCollection<ReaderChapter> Chapters { get; } = new();

    /// <summary>All pages of a PDF, stacked for continuous scrolling.</summary>
    public ObservableCollection<ReaderPage> PdfPages { get; } = new();
    public ObservableCollection<Skill> Skills { get; } = new();
    public ObservableCollection<SkillRun> SkillRuns { get; } = new();

    [ObservableProperty] public partial Document? Document { get; set; }
    [ObservableProperty] public partial string Title { get; set; } = "";
    [ObservableProperty] public partial string PageMarkdown { get; set; } = "";
    [ObservableProperty] public partial string SourceUrl { get; set; } = "";
    [ObservableProperty] public partial bool UsesWebView { get; set; }
    [ObservableProperty] public partial bool HasChapters { get; set; }
    [ObservableProperty] public partial bool IsChatPanelOpen { get; set; }

    public bool ShowChapterBar => HasChapters && !IsChatPanelOpen && !IsPdfPages;

    /// <summary>The paged reading surface (EPUB Markdown / fallback), not the PDF pages.</summary>
    public bool ShowPagedSurface => !UsesWebView && !IsPdfPages;

    partial void OnHasChaptersChanged(bool value) => OnPropertyChanged(nameof(ShowChapterBar));

    partial void OnUsesWebViewChanged(bool value) => OnPropertyChanged(nameof(ShowPagedSurface));
    partial void OnIsChatPanelOpenChanged(bool value) => OnPropertyChanged(nameof(ShowChapterBar));

    partial void OnIsPdfPagesChanged(bool value)
    {
        OnPropertyChanged(nameof(ShowChapterBar));
        OnPropertyChanged(nameof(ShowPagedSurface));
    }

    /// <summary>True while a PDF is shown as stacked pages rather than paged text.</summary>
    [ObservableProperty] public partial bool IsPdfPages { get; set; }
    [ObservableProperty] public partial int CurrentIndex { get; set; }
    [ObservableProperty] public partial string ChapterTitle { get; set; } = "";
    [ObservableProperty] public partial ReaderChapter? SelectedChapter { get; set; }
    [ObservableProperty] public partial string StatusMessage { get; set; } = "";

    public bool CanGoPrevious => HasChapters && CurrentIndex > 0;
    public bool CanGoNext => HasChapters && CurrentIndex < Chapters.Count - 1;
    /// <summary>Whether this document has a draft or a conversation to come back to.</summary>
    [ObservableProperty] public partial bool HasChatInReach { get; set; }

    /// <summary>Set by the shell: leave the reader and return to the library.</summary>
    public Action? RequestClose { get; set; }

    /// <summary>
    /// Set by the shell: the reader's chat icon was used. The shell resolves the
    /// target — the document's draft, else its active conversation, else the chats.
    /// </summary>
    public Action? OpenChatRequested { get; set; }

    /// <summary>Set by the shell: a freshly captured excerpt is now a draft to open.</summary>
    public Action<Conversation>? DraftOpened { get; set; }

    /// <summary>
    /// Set by the shell: append the excerpt to the document's open conversation,
    /// returning true when it did (ADR 0005). False falls back to a new draft.
    /// </summary>
    public Func<string, bool>? AppendToOpenConversation { get; set; }

    /// <summary>
    /// Set by the view: dismisse any selection or crop overlay, reporting whether
    /// there was one. The reader's first line of the back stack (G1/S21).
    /// </summary>
    public Func<bool>? DismissOverlays { get; set; }

    /// <summary>Esc's first step: a selection or crop mode goes before anything else.</summary>
    public bool TryDismissOverlays() => DismissOverlays?.Invoke() == true;

    /// <summary>Set by the shell: run an installed skill against this document.</summary>
    public Action<Skill>? RunSkillRequested { get; set; }

    [ObservableProperty] public partial string SkillStatus { get; set; } = "";

    public bool HasSkills => Skills.Count > 0;
    public bool HasSkillRuns => SkillRuns.Count > 0;

    public ReaderViewModel(
        ReaderService reader,
        LibraryService library,
        ConversationService conversations,
        SkillService skills,
        ISettingsStore settings)
    {
        _reader = reader;
        _library = library;
        _conversations = conversations;
        _skills = skills;
        _settings = settings;
    }

    public void Load(Document document)
    {
        Document = document;
        Title = document.Title;
        StatusMessage = "";
        SelectedChapter = null;

        Chapters.Clear();
        try
        {
            _content = _reader.Load(document);
        }
        catch (Exception ex)
        {
            // A missing or unreadable file must not take the app down.
            _content = null;
            PageMarkdown = "";
            SourceUrl = "";
            UsesWebView = false;
            HasChapters = false;
            ChapterTitle = "";
            CurrentIndex = 0;
            NotifyNavigation();
            StatusMessage = $"Could not open \u201c{document.Title}\u201d: {ex.Message}";
            return;
        }

        foreach (var chapter in _content.Chapters)
            Chapters.Add(chapter);
        HasChapters = Chapters.Count > 0;
        LoadSkills(document.Id);

        PdfPages.Clear();
        IsPdfPages = _content.Mode == RenderMode.PdfPages;
        if (IsPdfPages)
        {
            for (var i = 0; i < Chapters.Count; i++)
            {
                var chapter = Chapters[i];
                if (chapter.ImagePng is { Length: > 0 } png && LoadImage(png) is { } image)
                    PdfPages.Add(new ReaderPage(i, image, chapter.Words ?? Array.Empty<PdfWord>(), chapter.PixelWidth, chapter.PixelHeight));
            }
        }

        UsesWebView = _content.Mode.UsesWebView();
        if (UsesWebView)
        {
            var anchor = int.TryParse(document.LastLocator, out var page) && page > 0 ? $"#page={page}" : "";
            SourceUrl = new Uri(_content.SourcePath).AbsoluteUri + anchor;
            PageMarkdown = "";
            ChapterTitle = "PDF";            CurrentIndex = 0;
        }
        else
        {
            var index = _content.StartIndex;
            if (index < 0 || index >= Chapters.Count)
                index = 0;
            RenderChapter(index);
        }

        NotifyNavigation();
        RefreshChatReach();
    }

    /// <summary>An empty reader surface, used when a conversation has no document.</summary>
    public void Clear()
    {
        Document = null;
        Title = "";
        Chapters.Clear();
        PdfPages.Clear();
        IsPdfPages = false;
        PageMarkdown = "";
        SourceUrl = "";
        UsesWebView = false;
        HasChapters = false;
        ChapterTitle = "";
        CurrentIndex = 0;
        _content = null;
        Skills.Clear();
        SkillRuns.Clear();
        OnPropertyChanged(nameof(HasSkills));
        OnPropertyChanged(nameof(HasSkillRuns));
        NotifyNavigation();
        RefreshChatReach();
    }

    private void RenderChapter(int index)
    {
        if (Document is null || index < 0 || index >= Chapters.Count)
            return;

        CurrentIndex = index;
        var chapter = Chapters[index];
        ChapterTitle = chapter.Title;
        if (!ReferenceEquals(SelectedChapter, chapter))
            SelectedChapter = chapter;

        // A rendered PDF page is shown from PdfPages, not here; this surface only
        // carries EPUB Markdown.
        PageMarkdown = chapter.IsImage ? "" : chapter.Markdown;

        _library.UpdateLocator(Document.Id, index.ToString());
        NotifyNavigation();
    }

    private static Bitmap? LoadImage(byte[] bytes)
    {
        try
        {
            using var stream = new MemoryStream(bytes);
            return new Bitmap(stream);
        }
        catch
        {
            return null;
        }
    }

    partial void OnSelectedChapterChanged(ReaderChapter? value)
    {
        if (value is null)
            return;
        var index = Chapters.IndexOf(value);
        if (index >= 0 && index != CurrentIndex)
            RenderChapter(index);
    }

    private void LoadSkills(string? documentId)
    {
        Skills.Clear();
        foreach (var skill in _skills.ListInstalled().Where(s => s.Enabled))
            Skills.Add(skill);

        SkillRuns.Clear();
        foreach (var run in _skills.ListRuns(documentId))
            SkillRuns.Add(run);

        OnPropertyChanged(nameof(HasSkills));
        OnPropertyChanged(nameof(HasSkillRuns));
    }

    public void RefreshSkillRuns() => LoadSkills(Document?.Id);

    [RelayCommand]
    private void RunSkill(Skill skill) => RunSkillRequested?.Invoke(skill);

    private void NotifyNavigation()
    {
        OnPropertyChanged(nameof(CanGoPrevious));
        OnPropertyChanged(nameof(CanGoNext));
        NextCommand.NotifyCanExecuteChanged();
        PreviousCommand.NotifyCanExecuteChanged();
    }

    [RelayCommand]
    private void Back() => RequestClose?.Invoke();

    [RelayCommand]
    private void OpenChat() => OpenChatRequested?.Invoke();

    [RelayCommand(CanExecute = nameof(CanGoNext))]
    private void Next() => RenderChapter(CurrentIndex + 1);

    [RelayCommand(CanExecute = nameof(CanGoPrevious))]
    private void Previous() => RenderChapter(CurrentIndex - 1);

    /// <summary>
    /// Called by the view with a selection. Asking makes a draft: a conversation
    /// whose first message has not been sent, keeping this excerpt as its frozen
    /// context. It survives the app; nothing else has to be remembered.
    /// </summary>
    public void AskAboutSelection(string text)
    {
        if (Document is null || string.IsNullOrWhiteSpace(text))
        {
            StatusMessage = "Select some text first.";
            return;
        }

        var locator = UsesWebView ? "pdf" : (CurrentIndex + 1).ToString();

        // A conversation already open for this document takes the excerpt (ADR 0005).
        if (AppendToOpenConversation?.Invoke(text.Trim()) == true)
        {
            StatusMessage = "";
            return;
        }

        var id = _conversations.CreateDraft(
            Excerpt.FromText(text.Trim()),
            locator,
            _settings.Settings.ChatProvider,
            documentId: Document.Id);
        StatusMessage = "";
        OpenDraft(id);
    }

    /// <summary>Asking about a screenshot makes a draft whose context is the image.</summary>
    public void AskAboutImage(string imagePath)
    {
        if (Document is null || string.IsNullOrWhiteSpace(imagePath))
            return;

        var id = _conversations.CreateDraft(
            Excerpt.FromImage(imagePath),
            "",
            _settings.Settings.ChatProvider,
            documentId: Document.Id);
        StatusMessage = "";
        OpenDraft(id);
    }

    /// <summary>Recomputes whether this document has anything to come back to.</summary>
    public void RefreshChatReach()
    {
        HasChatInReach = Document is { } document
            && (_conversations.DraftForDocument(document.Id) is not null
                || _conversations.ActiveForDocument(document.Id) is not null);
    }

    private void OpenDraft(string id)
    {
        if (_conversations.Get(id) is { } draft)
        {
            HasChatInReach = true;
            DraftOpened?.Invoke(draft);
        }
    }
}
