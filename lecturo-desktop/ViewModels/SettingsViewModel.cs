using System.Collections.ObjectModel;
using System.Net.Http;
using System.Text.Json.Nodes;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Lecturo.Application.Chat;
using Lecturo.Application.Settings;
using Lecturo.Application.Skills;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Lecturo.Services;

namespace Lecturo.ViewModels;

public partial class SettingsViewModel : ViewModelBase
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(30) };

    private readonly ISettingsStore _settingsService;
    private readonly ISecretStore _secrets;
    private readonly IPromptRepository _prompts;
    private readonly SkillService _skillService;

    public ProviderInfo[] Providers { get; } = ProviderCatalog.All;
    public ObservableCollection<string> AvailableModels { get; } = new();
    public ObservableCollection<Prompt> Prompts { get; } = new();
    public ObservableCollection<string> ExistingKeys { get; } = new();
    public ObservableCollection<RemoteSkill> CatalogSkills { get; } = new();
    public ObservableCollection<Skill> InstalledSkills { get; } = new();

    [ObservableProperty] public partial string MarketplaceUrl { get; set; } = "";
    [ObservableProperty] public partial string SkillStatus { get; set; } = "";
    [ObservableProperty] public partial bool SkillsBusy { get; set; }
    public bool HasCatalog => CatalogSkills.Count > 0;

    [ObservableProperty] public partial ProviderInfo SelectedProvider { get; set; }
    [ObservableProperty] public partial string BaseUrl { get; set; } = "";
    [ObservableProperty] public partial string Model { get; set; } = "";
    [ObservableProperty] public partial string ApiKey { get; set; } = "";

    [ObservableProperty] public partial string CalibreUrl { get; set; } = "";
    [ObservableProperty] public partial string CalibreUsername { get; set; } = "";
    [ObservableProperty] public partial string CalibrePassword { get; set; } = "";
    [ObservableProperty] public partial string ZoteroUserId { get; set; } = "";
    [ObservableProperty] public partial string ZoteroBaseUrl { get; set; } = "";
    [ObservableProperty] public partial string ZoteroApiKey { get; set; } = "";

    [ObservableProperty] public partial string NewPromptName { get; set; } = "";
    [ObservableProperty] public partial string NewPromptContent { get; set; } = "";
    [ObservableProperty] public partial Prompt? SelectedPrompt { get; set; }
    [ObservableProperty] public partial string? ChosenModel { get; set; }

    [ObservableProperty] public partial string StatusMessage { get; set; } = "";
    [ObservableProperty] public partial bool IsBusy { get; set; }

    public string? SecretWarning => _secrets.Warning;
    public bool HasModels => AvailableModels.Count > 0;
    public string ModelsAction => AvailableModels.Count > 0
        ? $"Choose from {AvailableModels.Count} models"
        : "Choose a model";
    public bool IsKeyRequired => SelectedProvider.RequiresKey;
    public bool IsLecturoCloud => SelectedProvider.Id == "lecturo";
    public string DeviceId => _settingsService.Settings.DeviceId;
    public string KeyFieldLabel => IsLecturoCloud ? "API key" : "API key (leave blank to keep)";

    [ObservableProperty] public partial bool SupportsImages { get; set; }
    [ObservableProperty] public partial bool TypesetFormulas { get; set; }
    [ObservableProperty] public partial string ContextThresholdText { get; set; } = "8000";
    public string VisionHint => $"Default for {SelectedProvider.Id}: {(DefaultVision(SelectedProvider.Id) ? "yes" : "no")}";

    private bool _loadingProvider;
    private static bool DefaultVision(string providerId) => providerId != "copilot";

    partial void OnSupportsImagesChanged(bool value)
    {
        if (_loadingProvider)
            return;
        _settingsService.Settings.VisionOverrides[SelectedProvider.Id] = value;
        _settingsService.Save();
    }

    partial void OnTypesetFormulasChanged(bool value)
    {
        if (_loadingProvider)
            return;
        _settingsService.Settings.TypesetFormulas = value;
        _settingsService.Save();
    }

    public SettingsViewModel(ISettingsStore settingsService, ISecretStore secrets, IPromptRepository prompts, SkillService skillService)
    {
        _settingsService = settingsService;
        _secrets = secrets;
        _prompts = prompts;
        _skillService = skillService;

        SelectedProvider = Providers.FirstOrDefault(p => p.Id == settingsService.Settings.ChatProvider)
            ?? Providers[0];
        LoadProviderFields(SelectedProvider);
        LoadServerFields();
        RefreshPrompts();
        RefreshExistingKeys();
        MarketplaceUrl = settingsService.Settings.MarketplaceUrl ?? "";
        TypesetFormulas = settingsService.Settings.TypesetFormulas;
        ContextThresholdText = settingsService.Settings.ContextThreshold.ToString();
        RefreshInstalledSkills();
    }

    private void RefreshInstalledSkills()
    {
        InstalledSkills.Clear();
        foreach (var skill in _skillService.ListInstalled())
            InstalledSkills.Add(skill);
    }

    [RelayCommand]
    private async Task BrowseSkillsAsync()
    {
        try
        {
            SkillsBusy = true;
            SkillStatus = "";
            var url = MarketplaceUrl.Trim();
            _settingsService.Settings.MarketplaceUrl = string.IsNullOrWhiteSpace(url) ? null : url;
            _settingsService.Save();

            var items = await _skillService.BrowseAsync(url);
            CatalogSkills.Clear();
            foreach (var skill in items)
                CatalogSkills.Add(skill);
            OnPropertyChanged(nameof(HasCatalog));
            SkillStatus = items.Count == 0 ? "The catalog is empty." : $"Found {items.Count} skill(s).";
        }
        catch (Exception ex)
        {
            SkillStatus = $"Could not load the catalog: {ex.Message}";
        }
        finally
        {
            SkillsBusy = false;
        }
    }

    [RelayCommand]
    private void InstallSkill(RemoteSkill skill)
    {
        _skillService.Install(skill);
        RefreshInstalledSkills();
        SkillStatus = $"Installed \u201c{skill.Name}\u201d.";
    }

    /// <summary>Development aid: browse the catalog and install the first skill.</summary>
    [RelayCommand]
    private async Task BrowseAndInstallFirstAsync()
    {
        await BrowseSkillsAsync();
        if (CatalogSkills.FirstOrDefault() is { } first)
            InstallSkill(first);
    }

    [RelayCommand]
    private void UninstallSkill(Skill skill)
    {
        _skillService.Uninstall(skill.SkillId);
        RefreshInstalledSkills();
        SkillStatus = $"Uninstalled \u201c{skill.Name}\u201d.";
    }

    [RelayCommand]
    private void ToggleSkill(Skill skill)
    {
        _skillService.SetEnabled(skill.SkillId, !skill.Enabled);
        RefreshInstalledSkills();
    }

    private void LoadProviderFields(ProviderInfo provider)
    {
        var cfg = _settingsService.Settings[provider.Id];
        BaseUrl = cfg.BaseUrl;
        Model = cfg.Model;
        ApiKey = "";

        _loadingProvider = true;
        SupportsImages = _settingsService.Settings.VisionOverrides.TryGetValue(provider.Id, out var vision)
            ? vision
            : DefaultVision(provider.Id);
        _loadingProvider = false;
    }

    private void LoadServerFields()
    {
        var s = _settingsService.Settings;
        CalibreUrl = s.CalibreUrl ?? "";
        CalibreUsername = s.CalibreUsername ?? "";
        ZoteroUserId = s.ZoteroUserId ?? "";
        ZoteroBaseUrl = s.ZoteroBaseUrl ?? "";
    }

    private void RefreshPrompts()
    {
        Prompts.Clear();
        foreach (var prompt in _prompts.List())
            Prompts.Add(prompt);
    }

    private void RefreshExistingKeys()
    {
        ExistingKeys.Clear();
        foreach (var provider in Providers)
        {
            if (_secrets.Get(provider.SecretKey) is { Length: > 0 })
                ExistingKeys.Add($"Stored: {provider.DisplayName} key");
        }
        if (_secrets.Get("calibre-password") is { Length: > 0 })
            ExistingKeys.Add("Stored: Calibre password");
        if (_secrets.Get("zotero-api-key") is { Length: > 0 })
            ExistingKeys.Add("Stored: Zotero API key");
    }

    partial void OnSelectedProviderChanged(ProviderInfo value)
    {
        AvailableModels.Clear();
        OnPropertyChanged(nameof(HasModels));
        OnPropertyChanged(nameof(ModelsAction));
        OnPropertyChanged(nameof(IsKeyRequired));
        OnPropertyChanged(nameof(IsLecturoCloud));
        OnPropertyChanged(nameof(KeyFieldLabel));
        OnPropertyChanged(nameof(VisionHint));
        StatusMessage = "";
        LoadProviderFields(value);
    }

    [RelayCommand]
    private void SaveProvider()
    {
        var cfg = _settingsService.Settings[SelectedProvider.Id];
        cfg.BaseUrl = BaseUrl.Trim();
        cfg.Model = Model.Trim();
        _settingsService.Settings.ChatProvider = SelectedProvider.Id;
        if (int.TryParse(ContextThresholdText.Trim(), out var threshold) && threshold > 0)
            _settingsService.Settings.ContextThreshold = threshold;
        _settingsService.Save();
        if (SelectedProvider.RequiresKey)
            StoreSecret(SelectedProvider.SecretKey, ApiKey);
        StatusMessage = $"Saved {SelectedProvider.DisplayName}.";
    }

    [RelayCommand]
    private void SaveServers()
    {
        var s = _settingsService.Settings;
        s.CalibreUrl = EmptyToNull(CalibreUrl);
        s.CalibreUsername = EmptyToNull(CalibreUsername);
        s.ZoteroUserId = EmptyToNull(ZoteroUserId);
        s.ZoteroBaseUrl = EmptyToNull(ZoteroBaseUrl) ?? "https://api.zotero.org";
        _settingsService.Save();
        StatusMessage = "Saved servers.";
        StoreSecret("calibre-password", CalibrePassword);
        StoreSecret("zotero-api-key", ZoteroApiKey);
    }

    [RelayCommand]
    private async Task FetchModelsAsync()
    {
        if (SelectedProvider.Stubbed)
        {
            StatusMessage = $"{SelectedProvider.DisplayName} has no public model API.";
            return;
        }

        var key = !SelectedProvider.RequiresKey
            ? _settingsService.Settings.DeviceId
            : (string.IsNullOrWhiteSpace(ApiKey) ? _secrets.Get(SelectedProvider.SecretKey) : ApiKey);
        if (string.IsNullOrWhiteSpace(key))
        {
            StatusMessage = $"Enter an API key for {SelectedProvider.DisplayName} first.";
            return;
        }

        try
        {
            IsBusy = true;
            StatusMessage = "";
            var baseUrl = string.IsNullOrWhiteSpace(BaseUrl) ? SelectedProvider.DefaultBaseUrl : BaseUrl;
            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl.TrimEnd('/')}/models");
            request.Headers.Add("Authorization", $"Bearer {key}");
            using var response = await Http.SendAsync(request);
            var payload = await response.Content.ReadAsStringAsync();
            if (!response.IsSuccessStatusCode)
            {
                StatusMessage = $"Could not fetch models ({(int)response.StatusCode}).";
                return;
            }

            var json = JsonNode.Parse(payload);
            AvailableModels.Clear();
            foreach (var node in json?["data"]?.AsArray() ?? new JsonArray())
            {
                if (node?["id"]?.GetValue<string>() is { Length: > 0 } id)
                    AvailableModels.Add(id);
            }
            StatusMessage = $"Found {AvailableModels.Count} models.";
        }
        catch (Exception ex)
        {
            StatusMessage = $"Could not fetch models: {ex.Message}";
        }
        finally
        {
            IsBusy = false;
            OnPropertyChanged(nameof(HasModels));
            OnPropertyChanged(nameof(ModelsAction));
        }
    }

    [RelayCommand]
    private void ChooseModel(string model)
    {
        Model = model;
        StatusMessage = $"Model set to {model}. Save provider to keep it.";
    }

    partial void OnChosenModelChanged(string? value)
    {
        if (!string.IsNullOrWhiteSpace(value))
        {
            Model = value;
            StatusMessage = $"Model set to {value}. Save provider to keep it.";
        }
    }

    [RelayCommand]
    private void AddPrompt()
    {
        var name = NewPromptName.Trim();
        var content = NewPromptContent.Trim();
        if (name.Length == 0 || content.Length == 0)
        {
            StatusMessage = "A prompt needs a name and content.";
            return;
        }
        if (Prompts.Count >= 12)
        {
            StatusMessage = "Saved prompts are capped at twelve.";
            return;
        }
        _prompts.Add(name, content);
        NewPromptName = "";
        NewPromptContent = "";
        RefreshPrompts();
        StatusMessage = "Prompt saved.";
    }

    [RelayCommand]
    private void UpdatePrompt()
    {
        if (SelectedPrompt is null)
        {
            StatusMessage = "Select a prompt to update.";
            return;
        }
        var name = NewPromptName.Trim();
        var content = NewPromptContent.Trim();
        if (name.Length == 0 || content.Length == 0)
        {
            StatusMessage = "A prompt needs a name and content.";
            return;
        }
        _prompts.Update(SelectedPrompt.Id, name, content);
        NewPromptName = "";
        NewPromptContent = "";
        SelectedPrompt = null;
        RefreshPrompts();
        StatusMessage = "Prompt updated.";
    }

    [RelayCommand]
    private void DeletePrompt(Prompt prompt)
    {
        _prompts.Delete(prompt.Id);
        if (SelectedPrompt?.Id == prompt.Id)
        {
            SelectedPrompt = null;
            NewPromptName = "";
            NewPromptContent = "";
        }
        RefreshPrompts();
        StatusMessage = "Prompt deleted.";
    }

    [RelayCommand]
    private void EditPrompt(Prompt prompt)
    {
        SelectedPrompt = prompt;
        NewPromptName = prompt.Name;
        NewPromptContent = prompt.Content;
    }

    private void StoreSecret(string key, string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return;
        try
        {
            _secrets.Set(key, value);
            ApiKey = "";
            RefreshExistingKeys();
        }
        catch (Exception ex)
        {
            StatusMessage = $"Settings saved, but storing '{key}' failed: {ex.Message}";
        }
    }

    private static string? EmptyToNull(string value) =>
        string.IsNullOrWhiteSpace(value) ? null : value.Trim();
}
