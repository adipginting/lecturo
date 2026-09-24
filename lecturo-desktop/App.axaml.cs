using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using Lecturo.Application.Catalog;
using Lecturo.Application.Chat;
using Lecturo.Application.Library;
using Lecturo.Application.Skills;
using Lecturo.Data;
using Lecturo.Infrastructure;
using Lecturo.Services;
using Lecturo.ViewModels;
using Lecturo.Views;

namespace Lecturo;

public partial class App : Avalonia.Application
{
    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            // Composition root: no DI container, dependencies are built here.
            AppPaths.EnsureDirectories();
            var database = new Database(AppPaths.DatabasePath);
            var documentRepository = new DocumentRepository(database);
            var fileStore = new FileStore(AppPaths.DocumentsDir);
            var libraryService = new LibraryService(documentRepository, fileStore, new HttpDocumentFetcher());
            var chatRepository = new ChatRepository(database);
            var promptRepository = new PromptRepository(database);
            var documentSources = new DocumentSourceRepository(database);
            var settings = new JsonSettingsStore(AppPaths.SettingsPath);
            var secrets = SecretStore.Create();
            if (Environment.GetEnvironmentVariable("LECTURO_ZOTERO_KEY") is { Length: > 0 } zoteroKey)
            {
                try { secrets.Set("zotero-api-key", zoteroKey); } catch { /* dev aid */ }
            }
            var readerService = new ReaderService(libraryService, new PdfiumPdfRenderer());
            var chatClient = new ChatClient(settings, secrets);
            var catalog = new CatalogService(
                new CalibreSource(settings, secrets),
                new ZoteroSource(settings, secrets),
                documentSources,
                libraryService);
            var covers = new CoverService();
            var skillRepository = new SkillRepository(database);
            var conversations = new ConversationService(chatRepository, promptRepository, chatClient);
            var skillService = new SkillService(skillRepository, new HttpSkillCatalog(), conversations, chatClient);

            var library = new LibraryViewModel(libraryService, catalog, covers);
            var chats = new ChatsViewModel(conversations);
            var settingsVm = new SettingsViewModel(settings, secrets, promptRepository, skillService);
            var reader = new ReaderViewModel(readerService, libraryService, conversations, skillService, settings);
            var chatPanel = new ChatPanelViewModel(conversations, chatClient, promptRepository, settings);

            var main = new MainViewModel(
                libraryService, skillService, conversations, library, chats, settingsVm, reader, chatPanel);
            desktop.MainWindow = new MainWindow { DataContext = main };

            if (Environment.GetEnvironmentVariable("LECTURO_START") is { Length: > 0 } start)
                main.StartAt(start);

            // "Open with" / a URL on the command line (S4): the OS hands us a
            // document path or a link; run the normal import flow.
            var externalInput = Environment.GetCommandLineArgs()
                .Skip(1)
                .FirstOrDefault(a => !a.StartsWith("-", StringComparison.Ordinal));
            if (externalInput is not null)
                main.HandleExternalInput(externalInput);
        }

        base.OnFrameworkInitializationCompleted();
    }
}
